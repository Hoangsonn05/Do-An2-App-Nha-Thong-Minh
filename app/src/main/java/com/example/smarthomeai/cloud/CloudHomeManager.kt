package com.example.smarthomeai.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

enum class CloudStatus {
    UNAVAILABLE,
    SIGNED_OUT,
    LOADING,
    EMPTY,
    READY,
    ACCESS_REVOKED,
    ERROR
}

enum class MembersStatus {
    IDLE,
    LOADING,
    READY,
    ACCESS_REVOKED,
    ERROR
}

data class CloudActionFeedback(
    val isError: Boolean,
    val error: CloudError? = null,
    val message: String
)

data class CloudUiState(
    val status: CloudStatus = CloudStatus.LOADING,
    val currentUid: String? = null,
    val homes: List<CloudHome> = emptyList(),
    val selectedHomeId: String? = null,
    val error: CloudError? = null,
    val errorMessage: String? = null,
    val membersStatus: MembersStatus = MembersStatus.IDLE,
    val members: List<CloudMember> = emptyList(),
    val membersError: CloudError? = null,
    val membersErrorMessage: String? = null,
    val busy: Boolean = false,
    val revokedNotice: String? = null,
    val feedback: CloudActionFeedback? = null
) {
    val selectedHome: CloudHome?
        get() = selectedHomeId?.let { id -> homes.firstOrNull { it.id == id } }

    val currentUserRole: CloudRole?
        get() = selectedHome?.role

    val isCurrentUserOwner: Boolean
        get() = currentUserRole == CloudRole.OWNER

    val canLeaveSelectedHome: Boolean
        get() = selectedHome != null && currentUserRole == CloudRole.MEMBER && !busy

    fun canRemoveMember(member: CloudMember): Boolean =
        selectedHome != null &&
            currentUserRole == CloudRole.OWNER &&
            member.role == CloudRole.MEMBER &&
            member.uid != currentUid &&
            !busy
}

/**
 * UI-facing bridge exposing read-only Cloud state and actions without leaking ViewModel or Firebase SDK.
 */
interface CloudUiBridge {
    val state: StateFlow<CloudUiState>
    fun selectHome(homeId: String)
    fun createHome(name: String)
    fun leaveSelectedHome()
    fun removeMember(memberUid: String)
    fun retry()
    fun dismissFeedback()
}

/**
 * Weakly-held bridge registry connecting SmartHomeViewModel to Compose screens
 * without modifying LOCKED MainActivity.kt and without instantiating ViewModel in isolated Compose tests.
 */
object CloudUiBridgeRegistry {
    @Volatile
    private var activeRef: WeakReference<CloudUiBridge>? = null

    fun register(bridge: CloudUiBridge) {
        activeRef = WeakReference(bridge)
    }

    fun unregister(bridge: CloudUiBridge) {
        val current = activeRef?.get()
        if (current == null || current === bridge) {
            activeRef = null
        }
    }

    fun currentOrNull(): CloudUiBridge? = activeRef?.get()
}

/**
 * Pure Kotlin session-aware coordinator for Multi-Home and Member management (B3.1, B3.2, B3.3, B2-F5).
 *
 * Guarantees:
 * - Reacts to Auth UID changes via CloudSession.observeSession().
 * - Cancels previous UID's observeHomes() and observeMembers() flows before starting new UID flows.
 * - Clears all Home/Member UI state immediately on sign-out and account switch (zero cross-UID leakage).
 * - Uses monotonic session generation to guarantee late emissions/mutations from an old UID never overwrite new UID state.
 * - Cancels previous Home's observeMembers() flow before collecting the newly selected Home.
 * - Removes revoked homes from valid display immediately upon PERMISSION_DENIED.
 * - Never swallows non-timeout CancellationException.
 */
class CloudHomeManager(
    private val scope: CoroutineScope,
    private val session: CloudSession,
    private val repository: CloudRepository
) : CloudUiBridge {

    private val _uiState = MutableStateFlow(CloudUiState())
    override val state: StateFlow<CloudUiState> = _uiState.asStateFlow()

    private val sessionGen = AtomicLong(0L)
    private val revokedHomeIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val lifecycleMutex = Mutex()

    @Volatile
    private var activeSelectedHomeId: String? = null

    @Volatile
    private var lastSessionState: CloudSessionState? = null

    @Volatile
    private var pendingCreatedHomeId: String? = null

    @Volatile
    private var selfLeavingHomeId: String? = null

    private var activeMutationJob: Job? = null
    private var homesJob: Job? = null
    private var membersJob: Job? = null
    private var memberSwitchJob: Job? = null
    private var observerJob: Job? = null

    init {
        start()
    }

    private fun start() {
        observerJob?.cancel()
        observerJob = scope.launch {
            session.observeSession()
                .distinctUntilChanged()
                .collect { sessionState ->
                    lastSessionState = sessionState
                    applySessionState(sessionState)
                }
        }
    }

    private suspend fun applySessionState(sessionState: CloudSessionState) {
        lifecycleMutex.withLock {
            val gen = sessionGen.incrementAndGet()
            // Cancel and await previous session's flows & mutations BEFORE starting new session (B3.1 / B2-F5).
            memberSwitchJob?.cancelAndJoin()
            memberSwitchJob = null
            membersJob?.cancelAndJoin()
            membersJob = null
            homesJob?.cancelAndJoin()
            homesJob = null
            activeMutationJob?.cancelAndJoin()
            activeMutationJob = null

            pendingCreatedHomeId = null
            selfLeavingHomeId = null
            revokedHomeIds.clear()
            activeSelectedHomeId = null

            when (sessionState) {
                CloudSessionState.Unavailable -> {
                    _uiState.value = CloudUiState(status = CloudStatus.UNAVAILABLE)
                }
                CloudSessionState.SignedOut -> {
                    _uiState.value = CloudUiState(status = CloudStatus.SIGNED_OUT)
                }
                is CloudSessionState.SignedIn -> {
                    val uid = sessionState.uid
                    _uiState.value = CloudUiState(
                        status = CloudStatus.LOADING,
                        currentUid = uid
                    )
                    homesJob = scope.launch {
                        repository.observeHomes().collect { result ->
                            if (sessionGen.get() != gen) return@collect
                            handleHomesResult(gen, uid, result)
                        }
                    }
                }
            }
        }
    }

    private fun switchMemberObserver(gen: Long, uid: String, homeId: String?) {
        activeSelectedHomeId = homeId
        val previousSwitch = memberSwitchJob
        val previousMembers = membersJob
        membersJob = null
        memberSwitchJob = scope.launch {
            previousSwitch?.cancelAndJoin()
            previousMembers?.cancelAndJoin()
            if (sessionGen.get() != gen || activeSelectedHomeId != homeId) return@launch
            if (homeId == null) {
                _uiState.update { cur ->
                    if (sessionGen.get() != gen) cur
                    else cur.copy(
                        membersStatus = if (cur.status == CloudStatus.ACCESS_REVOKED) MembersStatus.ACCESS_REVOKED else MembersStatus.IDLE,
                        members = emptyList(),
                        membersError = if (cur.status == CloudStatus.ACCESS_REVOKED) cur.membersError else null,
                        membersErrorMessage = if (cur.status == CloudStatus.ACCESS_REVOKED) cur.membersErrorMessage else null
                    )
                }
                return@launch
            }
            _uiState.update { cur ->
                if (sessionGen.get() != gen || cur.selectedHomeId != homeId) cur
                else cur.copy(
                    membersStatus = MembersStatus.LOADING,
                    members = emptyList(),
                    membersError = null,
                    membersErrorMessage = null
                )
            }
            membersJob = scope.launch {
                repository.observeMembers(homeId).collect { memberResult ->
                    if (sessionGen.get() != gen || activeSelectedHomeId != homeId) return@collect
                    handleMembersResult(gen, uid, homeId, memberResult)
                }
            }
        }
    }

    /**
     * Synchronously invalidates the active session on explicit logout so UI state is cleared immediately
     * without waiting for asynchronous AuthStateListener delivery.
     */
    fun onSignedOut() {
        sessionGen.incrementAndGet()
        memberSwitchJob?.cancel()
        memberSwitchJob = null
        membersJob?.cancel()
        membersJob = null
        homesJob?.cancel()
        homesJob = null
        activeMutationJob?.cancel()
        activeMutationJob = null
        pendingCreatedHomeId = null
        selfLeavingHomeId = null
        revokedHomeIds.clear()
        activeSelectedHomeId = null
        _uiState.value = CloudUiState(status = CloudStatus.SIGNED_OUT)
    }

    fun clear() {
        sessionGen.incrementAndGet()
        memberSwitchJob?.cancel()
        memberSwitchJob = null
        membersJob?.cancel()
        membersJob = null
        homesJob?.cancel()
        homesJob = null
        activeMutationJob?.cancel()
        activeMutationJob = null
        observerJob?.cancel()
        observerJob = null
        revokedHomeIds.clear()
        activeSelectedHomeId = null
    }

    override fun selectHome(homeId: String) {
        val gen = sessionGen.get()
        val current = _uiState.value
        val uid = current.currentUid ?: return
        if (current.status != CloudStatus.READY) return
        if (current.homes.none { it.id == homeId }) return
        if (current.selectedHomeId == homeId) return
        _uiState.update {
            it.copy(
                selectedHomeId = homeId,
                membersStatus = MembersStatus.LOADING,
                members = emptyList(),
                membersError = null,
                membersErrorMessage = null,
                feedback = null
            )
        }
        switchMemberObserver(gen, uid, homeId)
    }

    override fun createHome(name: String) {
        val gen = sessionGen.get()
        val current = _uiState.value
        if (current.status == CloudStatus.UNAVAILABLE) {
            setFeedback(gen, isError = true, error = null, message = "Cloud chưa khả dụng.")
            return
        }
        if (current.currentUid == null || current.status == CloudStatus.SIGNED_OUT) {
            setFeedback(gen, isError = true, error = CloudError.Unauthenticated, message = "Vui lòng đăng nhập để tạo nhà.")
            return
        }
        if (current.busy) return

        val normalized = try {
            RtdbPayloadBuilder.validateAndNormalizeHomeName(name)
        } catch (e: IllegalArgumentException) {
            setFeedback(gen, isError = true, error = CloudError.InvalidData, message = e.message ?: "Tên nhà không hợp lệ.")
            return
        }

        launchMutation(gen) {
            when (val res = repository.createHome(normalized)) {
                is CloudResult.Ok -> {
                    if (sessionGen.get() != gen) return@launchMutation
                    pendingCreatedHomeId = res.data
                    _uiState.update { cur ->
                        if (sessionGen.get() != gen) cur
                        else {
                            val hasHomeAlready = cur.homes.any { it.id == res.data }
                            val updatedSelected = if (hasHomeAlready) res.data else cur.selectedHomeId
                            cur.copy(
                                busy = false,
                                selectedHomeId = updatedSelected,
                                revokedNotice = null,
                                feedback = CloudActionFeedback(
                                    isError = false,
                                    message = "Đã tạo nhà \"$normalized\"."
                                )
                            )
                        }
                    }
                    val uid = _uiState.value.currentUid
                    if (uid != null && _uiState.value.homes.any { it.id == res.data }) {
                        switchMemberObserver(gen, uid, res.data)
                    }
                }
                is CloudResult.Err -> {
                    if (sessionGen.get() != gen) return@launchMutation
                    setFeedback(gen, isError = true, error = res.error, message = describeActionError(res.error, res.message))
                }
                CloudResult.Unavailable -> {
                    if (sessionGen.get() != gen) return@launchMutation
                    _uiState.value = CloudUiState(status = CloudStatus.UNAVAILABLE)
                }
            }
        }
    }

    override fun leaveSelectedHome() {
        val gen = sessionGen.get()
        val current = _uiState.value
        val home = current.selectedHome ?: return
        if (current.busy) return
        if (home.role != CloudRole.MEMBER) {
            setFeedback(
                gen,
                isError = true,
                error = CloudError.PermissionDenied,
                message = "Chủ nhà (OWNER) không thể tự rời khỏi nhà theo quy tắc bảo mật."
            )
            return
        }

        selfLeavingHomeId = home.id
        launchMutation(gen) {
            when (val res = repository.leaveHome(home.id)) {
                is CloudResult.Ok -> {
                    if (sessionGen.get() != gen) return@launchMutation
                    val remaining = _uiState.value.homes.filterNot { it.id == home.id }
                    val nextSelected = remaining.firstOrNull()?.id
                    _uiState.update { cur ->
                        if (sessionGen.get() != gen) cur
                        else cur.copy(
                            status = if (remaining.isEmpty()) CloudStatus.EMPTY else CloudStatus.READY,
                            homes = remaining,
                            selectedHomeId = nextSelected,
                            membersStatus = if (nextSelected == null) MembersStatus.IDLE else MembersStatus.LOADING,
                            members = emptyList(),
                            membersError = null,
                            membersErrorMessage = null,
                            busy = false,
                            feedback = CloudActionFeedback(
                                isError = false,
                                message = "Đã rời khỏi nhà \"${home.name}\"."
                            )
                        )
                    }
                    val uid = _uiState.value.currentUid
                    if (uid != null) {
                        switchMemberObserver(gen, uid, nextSelected)
                    }
                }
                is CloudResult.Err -> {
                    selfLeavingHomeId = null
                    if (sessionGen.get() != gen) return@launchMutation
                    setFeedback(gen, isError = true, error = res.error, message = describeActionError(res.error, res.message))
                }
                CloudResult.Unavailable -> {
                    selfLeavingHomeId = null
                    if (sessionGen.get() != gen) return@launchMutation
                    _uiState.value = CloudUiState(status = CloudStatus.UNAVAILABLE)
                }
            }
        }
    }

    override fun removeMember(memberUid: String) {
        val gen = sessionGen.get()
        val current = _uiState.value
        val home = current.selectedHome ?: return
        if (current.busy) return

        val target = current.members.firstOrNull { it.uid == memberUid }
        if (home.role != CloudRole.OWNER || memberUid == current.currentUid || target?.role == CloudRole.OWNER) {
            setFeedback(
                gen,
                isError = true,
                error = CloudError.PermissionDenied,
                message = "Chỉ chủ nhà (OWNER) mới được loại bỏ thành viên (MEMBER) khác."
            )
            return
        }

        launchMutation(gen) {
            when (val res = repository.removeMember(home.id, memberUid)) {
                is CloudResult.Ok -> {
                    if (sessionGen.get() != gen) return@launchMutation
                    _uiState.update { cur ->
                        if (sessionGen.get() != gen) cur
                        else cur.copy(
                            busy = false,
                            feedback = CloudActionFeedback(
                                isError = false,
                                message = "Đã loại bỏ thành viên $memberUid."
                            )
                        )
                    }
                }
                is CloudResult.Err -> {
                    if (sessionGen.get() != gen) return@launchMutation
                    setFeedback(gen, isError = true, error = res.error, message = describeActionError(res.error, res.message))
                }
                CloudResult.Unavailable -> {
                    if (sessionGen.get() != gen) return@launchMutation
                    _uiState.value = CloudUiState(status = CloudStatus.UNAVAILABLE)
                }
            }
        }
    }

    override fun retry() {
        revokedHomeIds.clear()
        val sessionSnapshot = lastSessionState ?: return
        scope.launch {
            applySessionState(sessionSnapshot)
        }
    }

    override fun dismissFeedback() {
        _uiState.update { it.copy(feedback = null, revokedNotice = null) }
    }

    private fun launchMutation(gen: Long, block: suspend () -> Unit) {
        activeMutationJob?.cancel()
        _uiState.update { cur ->
            if (sessionGen.get() != gen) cur else cur.copy(busy = true, feedback = null)
        }
        activeMutationJob = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (sessionGen.get() == gen) {
                    val mapped = CloudErrorMapper.mapException(t)
                    setFeedback(gen, isError = true, error = mapped, message = describeActionError(mapped, t.message))
                }
            } finally {
                _uiState.update { cur ->
                    if (sessionGen.get() == gen && cur.busy) cur.copy(busy = false) else cur
                }
            }
        }
    }

    private fun handleHomesResult(gen: Long, uid: String, result: CloudResult<List<CloudHome>>) {
        if (sessionGen.get() != gen) return
        when (result) {
            CloudResult.Unavailable -> {
                switchMemberObserver(gen, uid, null)
                _uiState.value = CloudUiState(status = CloudStatus.UNAVAILABLE)
            }
            is CloudResult.Err -> {
                switchMemberObserver(gen, uid, null)
                if (result.error == CloudError.Unauthenticated) {
                    _uiState.value = CloudUiState(status = CloudStatus.SIGNED_OUT)
                } else {
                    _uiState.value = CloudUiState(
                        status = CloudStatus.ERROR,
                        currentUid = uid,
                        error = result.error,
                        errorMessage = describeLoadError(result.error, result.message)
                    )
                }
            }
            is CloudResult.Ok -> {
                val validHomes = result.data.filterNot { it.id in revokedHomeIds }
                val previous = _uiState.value
                val prevSelectedId = previous.selectedHomeId
                val prevSelectedHome = previous.selectedHome

                val pendingId = pendingCreatedHomeId
                val nextSelectedId: String?
                var nextRevokedNotice = previous.revokedNotice

                if (pendingId != null && validHomes.any { it.id == pendingId }) {
                    nextSelectedId = pendingId
                    pendingCreatedHomeId = null
                    nextRevokedNotice = null
                } else if (prevSelectedId != null && validHomes.any { it.id == prevSelectedId }) {
                    nextSelectedId = prevSelectedId
                } else {
                    if (prevSelectedHome != null && prevSelectedId != selfLeavingHomeId) {
                        nextRevokedNotice = "Bạn không còn quyền truy cập nhà \"${prevSelectedHome.name}\" hoặc nhà đã bị xóa."
                    }
                    if (prevSelectedId == selfLeavingHomeId) {
                        selfLeavingHomeId = null
                    }
                    nextSelectedId = validHomes.firstOrNull()?.id
                }

                val nextStatus = when {
                    validHomes.isNotEmpty() -> CloudStatus.READY
                    nextRevokedNotice != null || previous.status == CloudStatus.ACCESS_REVOKED -> CloudStatus.ACCESS_REVOKED
                    else -> CloudStatus.EMPTY
                }

                val selectionChanged = nextSelectedId != prevSelectedId
                _uiState.update { cur ->
                    if (sessionGen.get() != gen) cur
                    else cur.copy(
                        status = nextStatus,
                        currentUid = uid,
                        homes = validHomes,
                        selectedHomeId = nextSelectedId,
                        error = null,
                        errorMessage = null,
                        revokedNotice = nextRevokedNotice,
                        membersStatus = when {
                            nextSelectedId == null && nextStatus == CloudStatus.ACCESS_REVOKED -> MembersStatus.ACCESS_REVOKED
                            nextSelectedId == null -> MembersStatus.IDLE
                            selectionChanged -> MembersStatus.LOADING
                            else -> cur.membersStatus
                        },
                        members = if (nextSelectedId == null || selectionChanged) emptyList() else cur.members,
                        membersError = if (nextSelectedId == null || selectionChanged) null else cur.membersError,
                        membersErrorMessage = if (nextSelectedId == null || selectionChanged) null else cur.membersErrorMessage
                    )
                }
                if (activeSelectedHomeId != nextSelectedId) {
                    switchMemberObserver(gen, uid, nextSelectedId)
                }
            }
        }
    }

    private fun handleMembersResult(
        gen: Long,
        uid: String,
        homeId: String,
        result: CloudResult<List<CloudMember>>
    ) {
        if (sessionGen.get() != gen) return
        if (activeSelectedHomeId != homeId) return

        when (result) {
            CloudResult.Unavailable -> {
                switchMemberObserver(gen, uid, null)
                _uiState.value = CloudUiState(status = CloudStatus.UNAVAILABLE)
            }
            is CloudResult.Ok -> {
                val sorted = result.data.sortedWith(
                    compareBy<CloudMember> { if (it.role == CloudRole.OWNER) 0 else 1 }
                        .thenBy { it.addedAt ?: Long.MAX_VALUE }
                        .thenBy { it.uid }
                )
                _uiState.update { cur ->
                    if (sessionGen.get() != gen || cur.selectedHomeId != homeId) cur
                    else cur.copy(
                        membersStatus = MembersStatus.READY,
                        members = sorted,
                        membersError = null,
                        membersErrorMessage = null
                    )
                }
            }
            is CloudResult.Err -> {
                when (result.error) {
                    CloudError.Unauthenticated -> {
                        switchMemberObserver(gen, uid, null)
                        _uiState.value = CloudUiState(status = CloudStatus.SIGNED_OUT)
                    }
                    CloudError.PermissionDenied -> {
                        // Access to this home has been revoked: remove immediately from valid homes so
                        // stale home is never displayed as valid.
                        revokedHomeIds.add(homeId)
                        val cur = _uiState.value
                        val lostName = cur.homes.firstOrNull { it.id == homeId }?.name ?: homeId
                        val remaining = cur.homes.filterNot { it.id == homeId }
                        val nextSelected = remaining.firstOrNull()?.id
                        val notice = "Quyền truy cập nhà \"$lostName\" đã bị thu hồi."

                        _uiState.update { stateNow ->
                            if (sessionGen.get() != gen) stateNow
                            else stateNow.copy(
                                status = if (remaining.isNotEmpty()) CloudStatus.READY else CloudStatus.ACCESS_REVOKED,
                                currentUid = uid,
                                homes = remaining,
                                selectedHomeId = nextSelected,
                                membersStatus = if (nextSelected != null) MembersStatus.LOADING else MembersStatus.ACCESS_REVOKED,
                                members = emptyList(),
                                membersError = CloudError.PermissionDenied,
                                membersErrorMessage = notice,
                                revokedNotice = notice
                            )
                        }
                        switchMemberObserver(gen, uid, nextSelected)
                    }
                    else -> {
                        _uiState.update { cur ->
                            if (sessionGen.get() != gen || cur.selectedHomeId != homeId) cur
                            else cur.copy(
                                membersStatus = MembersStatus.ERROR,
                                members = emptyList(),
                                membersError = result.error,
                                membersErrorMessage = describeLoadError(result.error, result.message)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setFeedback(gen: Long, isError: Boolean, error: CloudError?, message: String) {
        _uiState.update { cur ->
            if (sessionGen.get() != gen) cur
            else cur.copy(
                busy = false,
                feedback = CloudActionFeedback(isError = isError, error = error, message = message)
            )
        }
    }

    companion object {
        fun describeLoadError(error: CloudError, detail: String?): String = when (error) {
            CloudError.Offline -> "Không có kết nối mạng Cloud. Điều khiển ESP32 qua LAN vẫn hoạt động."
            CloudError.Timeout -> "Kết nối Cloud quá thời gian chờ. Thử lại sau."
            CloudError.PermissionDenied -> "Bị từ chối quyền truy cập bởi quy tắc bảo mật (Rules)."
            CloudError.Unauthenticated -> "Phiên đăng nhập Cloud đã hết hạn. Vui lòng đăng nhập lại."
            CloudError.InvalidData -> "Dữ liệu Cloud không hợp lệ."
            else -> detail?.takeIf { it.isNotBlank() } ?: "Không thể tải dữ liệu Cloud."
        }

        fun describeActionError(error: CloudError, detail: String?): String = when (error) {
            CloudError.PermissionDenied -> "Thao tác bị từ chối bởi quy tắc bảo mật (Rules)."
            CloudError.Offline -> "Mất kết nối Cloud. Không thể thực hiện thao tác."
            CloudError.Timeout -> "Thao tác Cloud quá thời gian chờ."
            CloudError.Unauthenticated -> "Chưa đăng nhập tài khoản Cloud."
            CloudError.InvalidData -> detail?.takeIf { it.isNotBlank() } ?: "Dữ liệu không hợp lệ."
            CloudError.Conflict -> "Xung đột dữ liệu Cloud. Vui lòng thử lại."
            CloudError.NotFound -> "Không tìm thấy dữ liệu nhà hoặc thành viên."
            CloudError.Unknown -> detail?.takeIf { it.isNotBlank() } ?: "Thao tác Cloud thất bại."
        }
    }
}
