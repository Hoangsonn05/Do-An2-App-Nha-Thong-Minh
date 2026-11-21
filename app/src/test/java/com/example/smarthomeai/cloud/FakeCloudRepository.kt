package com.example.smarthomeai.cloud

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory test double for CloudRepository (src/test ONLY).
 * Supports multi-UID isolation, observer lifecycle tracking, and RTDB Rules-equivalent
 * enforcement for leaveHome() and removeMember().
 */
class FakeCloudRepository(
    initialUid: String? = "test_user_uA",
    initialAvailable: Boolean = true
) : CloudRepository {

    private var available: Boolean = initialAvailable
    private var currentUid: String? = initialUid
    private var nextFailure: Pair<CloudError, String?>? = null
    private var idCounter: Int = 1000

    private val userHomeIdsByUid = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val homeMetas = MutableStateFlow<Map<String, HomeMetaFetchResult>>(emptyMap())
    private val membersByHome = MutableStateFlow<Map<String, List<CloudMember>>>(emptyMap())
    private val memberErrorsByHome = MutableStateFlow<Map<String, CloudError>>(emptyMap())

    val activeHomesCollectors = AtomicInteger(0)
    val totalHomesCollectorCancellations = AtomicInteger(0)
    val activeMemberCollectorsByHome = mutableMapOf<String, AtomicInteger>()
    val totalMemberCollectorCancellations = AtomicInteger(0)

    fun setAvailable(isAvailable: Boolean) {
        available = isAvailable
    }

    fun setSignedInUid(uid: String?) {
        currentUid = uid
    }

    fun failNext(error: CloudError, message: String? = null) {
        nextFailure = error to message
    }

    fun seedHomeForUid(
        uid: String,
        homeId: String,
        name: String,
        ownerUid: String,
        members: List<CloudMember> = listOf(
            CloudMember(uid = ownerUid, role = CloudRole.OWNER, addedAt = 1000L, addedBy = ownerUid)
        ) + if (uid != ownerUid) {
            listOf(CloudMember(uid = uid, role = CloudRole.MEMBER, addedAt = 2000L, addedBy = ownerUid))
        } else {
            emptyList()
        }
    ) {
        homeMetas.value = homeMetas.value + (homeId to HomeMetaFetchResult.Found(homeId, name, ownerUid))
        val existingMembers = membersByHome.value[homeId].orEmpty()
        val mergedMembers = (existingMembers + members).associateBy { it.uid }.values.toList()
        membersByHome.value = membersByHome.value + (homeId to mergedMembers)
        val curIds = userHomeIdsByUid.value[uid].orEmpty()
        userHomeIdsByUid.value = userHomeIdsByUid.value + (uid to (curIds + homeId).distinct())
    }

    fun seedStaleHomePermissionDenied(homeId: String, uid: String? = currentUid) {
        val targetUid = uid ?: return
        val curIds = userHomeIdsByUid.value[targetUid].orEmpty()
        userHomeIdsByUid.value = userHomeIdsByUid.value + (targetUid to (curIds + homeId).distinct())
        homeMetas.value = homeMetas.value + (homeId to HomeMetaFetchResult.PermissionDenied)
    }

    fun seedMember(homeId: String, member: CloudMember) {
        val current = membersByHome.value[homeId].orEmpty()
        membersByHome.value = membersByHome.value + (homeId to (current.filterNot { it.uid == member.uid } + member))
        val curIds = userHomeIdsByUid.value[member.uid].orEmpty()
        if (homeId !in curIds) {
            userHomeIdsByUid.value = userHomeIdsByUid.value + (member.uid to (curIds + homeId))
        }
    }

    fun revokeHomeAccess(uid: String, homeId: String, alsoFailMemberListener: Boolean = true) {
        val current = membersByHome.value[homeId].orEmpty().filterNot { it.uid == uid }
        membersByHome.value = membersByHome.value + (homeId to current)
        val curIds = userHomeIdsByUid.value[uid].orEmpty().filterNot { it == homeId }
        userHomeIdsByUid.value = userHomeIdsByUid.value + (uid to curIds)
        if (alsoFailMemberListener) {
            memberErrorsByHome.value = memberErrorsByHome.value + (homeId to CloudError.PermissionDenied)
        }
    }

    fun setMembersError(homeId: String, error: CloudError?) {
        memberErrorsByHome.value = if (error == null) {
            memberErrorsByHome.value - homeId
        } else {
            memberErrorsByHome.value + (homeId to error)
        }
    }

    override fun observeHomes(): Flow<CloudResult<List<CloudHome>>> {
        if (!available) return flowOf(CloudResult.Unavailable)
        val uid = currentUid ?: return flowOf(
            CloudResult.Err(CloudError.Unauthenticated, "User is not signed in.")
        )
        nextFailure?.let { (err, msg) ->
            nextFailure = null
            return flowOf(CloudResult.Err(err, msg))
        }
        return callbackFlow {
            activeHomesCollectors.incrementAndGet()
            val collectJob = launch {
                combine(userHomeIdsByUid, homeMetas) { idsMap, metas ->
                    RtdbHomeMerger.mergeHomes(uid, idsMap[uid].orEmpty(), metas)
                }.collect { result ->
                    trySend(result)
                }
            }
            awaitClose {
                collectJob.cancel()
                activeHomesCollectors.decrementAndGet()
                totalHomesCollectorCancellations.incrementAndGet()
            }
        }
    }

    override suspend fun createHome(name: String): CloudResult<String> {
        if (!available) return CloudResult.Unavailable
        val uid = currentUid ?: return CloudResult.Err(CloudError.Unauthenticated, "User is not signed in.")
        nextFailure?.let { (err, msg) ->
            nextFailure = null
            return CloudResult.Err(err, msg)
        }

        val cleanName = try {
            RtdbPayloadBuilder.validateAndNormalizeHomeName(name)
        } catch (e: IllegalArgumentException) {
            return CloudResult.Err(CloudError.InvalidData, e.message)
        }

        val homeId = "home_FAKE_${idCounter++}"
        val nowMs = System.currentTimeMillis()
        homeMetas.value = homeMetas.value + (homeId to HomeMetaFetchResult.Found(homeId, cleanName, uid))
        membersByHome.value = membersByHome.value + (
            homeId to listOf(
                CloudMember(uid = uid, role = CloudRole.OWNER, addedAt = nowMs, addedBy = uid)
            )
        )
        val curIds = userHomeIdsByUid.value[uid].orEmpty()
        userHomeIdsByUid.value = userHomeIdsByUid.value + (uid to (curIds + homeId))
        return CloudResult.Ok(homeId)
    }

    override fun observeMembers(homeId: String): Flow<CloudResult<List<CloudMember>>> {
        if (!available) return flowOf(CloudResult.Unavailable)
        if (currentUid == null) {
            return flowOf(CloudResult.Err(CloudError.Unauthenticated, "User is not signed in."))
        }
        if (!RtdbPaths.isValidEntityId(homeId)) {
            return flowOf(CloudResult.Err(CloudError.InvalidData, "Invalid homeId: $homeId"))
        }
        nextFailure?.let { (err, msg) ->
            nextFailure = null
            return flowOf(CloudResult.Err(err, msg))
        }
        return callbackFlow {
            val counter = synchronized(activeMemberCollectorsByHome) {
                activeMemberCollectorsByHome.getOrPut(homeId) { AtomicInteger(0) }
            }
            counter.incrementAndGet()
            val collectJob = launch {
                combine(membersByHome, memberErrorsByHome) { membersMap, errorsMap ->
                    val err = errorsMap[homeId]
                    if (err != null) {
                        CloudResult.Err(err, "Simulated error: $err")
                    } else {
                        CloudResult.Ok(membersMap[homeId].orEmpty())
                    }
                }.collect { result ->
                    trySend(result)
                }
            }
            awaitClose {
                collectJob.cancel()
                counter.decrementAndGet()
                totalMemberCollectorCancellations.incrementAndGet()
            }
        }
    }

    override suspend fun leaveHome(homeId: String): CloudResult<Unit> {
        if (!available) return CloudResult.Unavailable
        val uid = currentUid ?: return CloudResult.Err(CloudError.Unauthenticated, "User is not signed in.")
        if (!RtdbPaths.isValidEntityId(homeId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId: $homeId")
        }
        nextFailure?.let { (err, msg) ->
            nextFailure = null
            return CloudResult.Err(err, msg)
        }
        val members = membersByHome.value[homeId].orEmpty()
        val self = members.firstOrNull { it.uid == uid }
            ?: return CloudResult.Err(CloudError.PermissionDenied, "Not a member of $homeId")
        if (self.role != CloudRole.MEMBER) {
            return CloudResult.Err(CloudError.PermissionDenied, "Owner cannot leave home.")
        }
        // Atomic removal from both members/{uid} and userHomes/{uid}/{homeId}
        membersByHome.value = membersByHome.value + (homeId to members.filterNot { it.uid == uid })
        val curIds = userHomeIdsByUid.value[uid].orEmpty().filterNot { it == homeId }
        userHomeIdsByUid.value = userHomeIdsByUid.value + (uid to curIds)
        return CloudResult.Ok(Unit)
    }

    override suspend fun removeMember(homeId: String, memberUid: String): CloudResult<Unit> {
        if (!available) return CloudResult.Unavailable
        val uid = currentUid ?: return CloudResult.Err(CloudError.Unauthenticated, "User is not signed in.")
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidUid(memberUid)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId or memberUid.")
        }
        if (memberUid == uid) {
            return CloudResult.Err(CloudError.PermissionDenied, "Cannot remove yourself via removeMember.")
        }
        nextFailure?.let { (err, msg) ->
            nextFailure = null
            return CloudResult.Err(err, msg)
        }
        val members = membersByHome.value[homeId].orEmpty()
        val caller = members.firstOrNull { it.uid == uid }
        val target = members.firstOrNull { it.uid == memberUid }
        if (caller?.role != CloudRole.OWNER || target?.role != CloudRole.MEMBER) {
            return CloudResult.Err(CloudError.PermissionDenied, "Only owner can remove a member.")
        }
        // Atomic removal from both members/{memberUid} and userHomes/{memberUid}/{homeId}
        membersByHome.value = membersByHome.value + (homeId to members.filterNot { it.uid == memberUid })
        val targetIds = userHomeIdsByUid.value[memberUid].orEmpty().filterNot { it == homeId }
        userHomeIdsByUid.value = userHomeIdsByUid.value + (memberUid to targetIds)
        return CloudResult.Ok(Unit)
    }
}
