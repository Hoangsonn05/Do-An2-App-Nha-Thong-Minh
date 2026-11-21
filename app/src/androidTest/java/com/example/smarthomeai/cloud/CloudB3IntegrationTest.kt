package com.example.smarthomeai.cloud

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smarthomeai.data.DeviceType
import com.example.smarthomeai.data.HomeMode
import com.example.smarthomeai.data.SecurityPhase
import com.example.smarthomeai.data.SecurityStatus
import com.example.smarthomeai.data.SmartHomeState
import com.example.smarthomeai.ui.components.CloudHomeManagementCard
import com.example.smarthomeai.ui.screens.HomeScreen
import com.example.smarthomeai.ui.theme.SmartHomeTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android instrumented integration & Compose UI tests for P1.1-B3:
 * - Sign-in & session restore
 * - Sign-out listener cancellation & state cleanup
 * - Account switching (uA -> uB) with zero data leakage
 * - Multi-Home switching & Member observer switching
 * - Multi-path atomic delete (leaveHome / removeMember) on isolated local RTDB Emulator
 * - UI stability when Cloud is offline/unavailable/revoked + zero impact on LAN ESP32 controls
 * - Role-based UI presentation & confirmation dialogs (OWNER vs MEMBER)
 *
 * STRICT PRODUCTION ISOLATION:
 * Never touches [DEFAULT] FirebaseApp or production appaintm RTDB.
 */
@RunWith(AndroidJUnit4::class)
class CloudB3IntegrationTest {

    @get:Rule
    val compose = createComposeRule()

    private fun isLocalEmulatorReachable(): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("10.0.2.2", 9000), 500)
        }
        true
    }.getOrDefault(false)

    private class InstrumentedFakeSession(
        initial: CloudSessionState
    ) : CloudSession {
        val state = MutableStateFlow(initial)
        override fun observeSession(): Flow<CloudSessionState> = state
    }

    private class InstrumentedFakeCloudRepo : CloudRepository {
        var uid: String? = null
        val homesByUid = MutableStateFlow<Map<String, List<CloudHome>>>(emptyMap())
        val membersByHome = MutableStateFlow<Map<String, List<CloudMember>>>(emptyMap())
        val activeHomeObservers = AtomicInteger(0)
        val activeMemberObservers = mutableMapOf<String, AtomicInteger>()

        override fun observeHomes(): Flow<CloudResult<List<CloudHome>>> {
            val boundUid = uid ?: return flowOf(CloudResult.Err(CloudError.Unauthenticated))
            return callbackFlow {
                activeHomeObservers.incrementAndGet()
                val job = launch {
                    homesByUid.collect { map ->
                        trySend(CloudResult.Ok(map[boundUid].orEmpty()))
                    }
                }
                awaitClose {
                    job.cancel()
                    activeHomeObservers.decrementAndGet()
                }
            }
        }

        override suspend fun createHome(name: String): CloudResult<String> {
            val boundUid = uid ?: return CloudResult.Err(CloudError.Unauthenticated)
            val id = "home_INST_${System.nanoTime().toString().takeLast(6)}"
            val current = homesByUid.value[boundUid].orEmpty()
            homesByUid.value = homesByUid.value + (boundUid to (current + CloudHome(id, name.trim(), CloudRole.OWNER)))
            membersByHome.value = membersByHome.value + (id to listOf(CloudMember(boundUid, CloudRole.OWNER, 1000L, boundUid)))
            return CloudResult.Ok(id)
        }

        override fun observeMembers(homeId: String): Flow<CloudResult<List<CloudMember>>> {
            if (uid == null) return flowOf(CloudResult.Err(CloudError.Unauthenticated))
            return callbackFlow {
                val counter = synchronized(activeMemberObservers) {
                    activeMemberObservers.getOrPut(homeId) { AtomicInteger(0) }
                }
                counter.incrementAndGet()
                val job = launch {
                    membersByHome.collect { map ->
                        trySend(CloudResult.Ok(map[homeId].orEmpty()))
                    }
                }
                awaitClose {
                    job.cancel()
                    counter.decrementAndGet()
                }
            }
        }

        override suspend fun leaveHome(homeId: String): CloudResult<Unit> {
            val boundUid = uid ?: return CloudResult.Err(CloudError.Unauthenticated)
            val current = homesByUid.value[boundUid].orEmpty()
            homesByUid.value = homesByUid.value + (boundUid to current.filterNot { it.id == homeId })
            membersByHome.value = membersByHome.value + (homeId to membersByHome.value[homeId].orEmpty().filterNot { it.uid == boundUid })
            return CloudResult.Ok(Unit)
        }

        override suspend fun removeMember(homeId: String, memberUid: String): CloudResult<Unit> {
            membersByHome.value = membersByHome.value + (homeId to membersByHome.value[homeId].orEmpty().filterNot { it.uid == memberUid })
            return CloudResult.Ok(Unit)
        }
    }

    private suspend fun awaitCondition(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                delay(25L)
            }
        }
    }

    @Test
    fun authSessionRestore_signOut_accountSwitch_homeAndMemberSwitch() = runBlocking {
        val repo = InstrumentedFakeCloudRepo().apply {
            uid = "uA"
            homesByUid.value = mapOf(
                "uA" to listOf(
                    CloudHome("home_A001", "Nha A1", CloudRole.OWNER),
                    CloudHome("home_A002", "Nha A2", CloudRole.MEMBER)
                ),
                "uB" to listOf(
                    CloudHome("home_B001", "Nha B1", CloudRole.OWNER)
                )
            )
            membersByHome.value = mapOf(
                "home_A001" to listOf(
                    CloudMember("uA", CloudRole.OWNER, 1000L, "uA"),
                    CloudMember("uC", CloudRole.MEMBER, 2000L, "uA")
                ),
                "home_A002" to listOf(
                    CloudMember("uOwner2", CloudRole.OWNER, 1000L, "uOwner2"),
                    CloudMember("uA", CloudRole.MEMBER, 3000L, "uOwner2")
                ),
                "home_B001" to listOf(
                    CloudMember("uB", CloudRole.OWNER, 1000L, "uB")
                )
            )
        }

        val session = InstrumentedFakeSession(CloudSessionState.SignedIn("uA"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // 1. Session restore with existing signed-in user uA
            val manager = CloudHomeManager(scope, session, repo)
            awaitCondition {
                manager.state.value.status == CloudStatus.READY &&
                    manager.state.value.membersStatus == MembersStatus.READY
            }
            assertEquals("uA", manager.state.value.currentUid)
            assertEquals("home_A001", manager.state.value.selectedHomeId)
            assertEquals(listOf("uA", "uC"), manager.state.value.members.map { it.uid })
            assertEquals(1, repo.activeMemberObservers["home_A001"]?.get())

            // 2. Switch Home A1 -> A2 cancels A1 member observer and observes A2 members
            manager.selectHome("home_A002")
            awaitCondition {
                manager.state.value.selectedHomeId == "home_A002" &&
                    manager.state.value.membersStatus == MembersStatus.READY &&
                    repo.activeMemberObservers["home_A001"]?.get() == 0
            }
            assertEquals(listOf("uOwner2", "uA"), manager.state.value.members.map { it.uid })
            assertEquals(1, repo.activeMemberObservers["home_A002"]?.get())

            // 3. Switch account uA -> uB cancels old observers and isolates homes/members
            repo.uid = "uB"
            session.state.value = CloudSessionState.SignedIn("uB")
            awaitCondition {
                manager.state.value.currentUid == "uB" &&
                    manager.state.value.status == CloudStatus.READY &&
                    manager.state.value.membersStatus == MembersStatus.READY &&
                    repo.activeMemberObservers["home_A002"]?.get() == 0
            }
            assertEquals(listOf("home_B001"), manager.state.value.homes.map { it.id })
            assertEquals(listOf("uB"), manager.state.value.members.map { it.uid })
            assertEquals(1, repo.activeMemberObservers["home_B001"]?.get())

            // 4. Sign out cancels all observers and clears all UI state
            repo.uid = null
            manager.onSignedOut()
            session.state.value = CloudSessionState.SignedOut
            awaitCondition {
                manager.state.value.status == CloudStatus.SIGNED_OUT &&
                    repo.activeHomeObservers.get() == 0 &&
                    repo.activeMemberObservers["home_B001"]?.get() == 0
            }
            assertNull(manager.state.value.currentUid)
            assertTrue(manager.state.value.homes.isEmpty())
            assertTrue(manager.state.value.members.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun rtdbRepository_leaveAndRemoveMember_againstIsolatedLocalEmulator_mapsRulesDenialAndOfflineTimeout() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (offlineApp, offlineDb) = isolatedDatabase("b3-isolated-offline", goOffline = true)
        val offlineProvider = FirebaseProvider(
            context = context,
            appResolver = { offlineApp },
            databaseUrlResolver = { offlineApp.options.databaseUrl },
            uidResolver = { "uB" },
            databaseResolver = { offlineDb }
        )
        val offlineRepo = RtdbCloudRepository(offlineProvider, timeoutMs = 300L)

        // Offline leaveHome / removeMember timeout cleanly
        val leaveTimeout = offlineRepo.leaveHome("home_A001")
        assertTrue("Expected Timeout, got $leaveTimeout", leaveTimeout is CloudResult.Err && leaveTimeout.error == CloudError.Timeout)

        // Self-remove via removeMember blocked before hitting network
        val selfRemove = offlineRepo.removeMember("home_A001", "uB")
        assertTrue("Expected PermissionDenied, got $selfRemove", selfRemove is CloudResult.Err && selfRemove.error == CloudError.PermissionDenied)

        // Invalid IDs rejected cleanly
        val badLeave = offlineRepo.leaveHome("short")
        assertTrue("Expected InvalidData, got $badLeave", badLeave is CloudResult.Err && badLeave.error == CloudError.InvalidData)

        // Live local RTDB Emulator check (10.0.2.2:9000, demo-smarthomeai)
        assumeTrue("Local RTDB Emulator (10.0.2.2:9000) not running", isLocalEmulatorReachable())
        val (onlineApp, onlineDb) = isolatedDatabase("b3-isolated-online", goOffline = false)
        val onlineProvider = FirebaseProvider(
            context = context,
            appResolver = { onlineApp },
            databaseUrlResolver = { onlineApp.options.databaseUrl },
            uidResolver = { "uEmuUnauth" },
            databaseResolver = { onlineDb }
        )
        val onlineRepo = RtdbCloudRepository(onlineProvider, timeoutMs = 5_000L)
        val emuLeave = onlineRepo.leaveHome("home_A001")
        val emuRemove = onlineRepo.removeMember("home_A001", "uB")
        assertTrue("Expected PermissionDenied from local Emulator, got $emuLeave", emuLeave is CloudResult.Err && emuLeave.error == CloudError.PermissionDenied)
        assertTrue("Expected PermissionDenied from local Emulator, got $emuRemove", emuRemove is CloudResult.Err && emuRemove.error == CloudError.PermissionDenied)
    }

    @Test
    fun homeScreen_whenCloudOfflineOrRevoked_doesNotCrashAndKeepsLanControlsWorking() {
        val offlineCloudState = MutableStateFlow(
            CloudUiState(
                status = CloudStatus.ERROR,
                currentUid = "uA",
                error = CloudError.Offline,
                errorMessage = "Không có kết nối mạng Cloud. Điều khiển ESP32 qua LAN vẫn hoạt động."
            )
        )
        val fakeBridge = object : CloudUiBridge {
            override val state: StateFlow<CloudUiState> = offlineCloudState
            override fun selectHome(homeId: String) = Unit
            override fun createHome(name: String) = Unit
            override fun leaveSelectedHome() = Unit
            override fun removeMember(memberUid: String) = Unit
            override fun retry() = Unit
            override fun dismissFeedback() = Unit
        }

        val lanCalls = mutableListOf<Pair<DeviceType, Boolean>>()
        val lanState = SmartHomeState(
            esp32Online = true,
            lastSeen = System.currentTimeMillis(),
            temperature = 28,
            humidity = 65,
            security = SecurityStatus(HomeMode.HOME, SecurityPhase.DISARMED, 0, "test-board", 1, 0)
        )

        compose.setContent {
            SmartHomeTheme {
                HomeScreen(
                    state = lanState,
                    onSetDevice = { type, on -> lanCalls += type to on },
                    onMode = {},
                    onRefresh = {},
                    onNavigate = {},
                    cloudBridge = fakeBridge
                )
            }
        }

        // Cloud error banner is shown clearly
        compose.onNodeWithText("Lỗi kết nối Cloud").assertIsDisplayed()

        // LAN ESP32 controls remain 100% enabled and responsive
        compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription(DeviceType.LIGHT.label))
        compose.onNodeWithContentDescription(DeviceType.LIGHT.label).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(DeviceType.LIGHT to true), lanCalls)
        }
    }

    @Test
    fun cloudHomeManagementCard_enforcesOwnerVsMemberActionsWithConfirmation() {
        var removedUid: String? = null
        var leftHome = false

        val ownerState = CloudUiState(
            status = CloudStatus.READY,
            currentUid = "uA",
            homes = listOf(CloudHome("home_A001", "Nha Chinh", CloudRole.OWNER)),
            selectedHomeId = "home_A001",
            membersStatus = MembersStatus.READY,
            members = listOf(
                CloudMember("uA", CloudRole.OWNER, 1_700_000_000_000L, "uA"),
                CloudMember("uB", CloudRole.MEMBER, 1_700_000_100_000L, "uA")
            )
        )

        val stateHolder = MutableStateFlow(ownerState)
        compose.setContent {
            val current = stateHolder.value
            SmartHomeTheme {
                CloudHomeManagementCard(
                    cloudState = current,
                    onSelectHome = {},
                    onCreateHome = {},
                    onLeaveHome = { leftHome = true },
                    onRemoveMember = { removedUid = it },
                    onRetry = {}
                )
            }
        }

        // OWNER sees remove icon for uB (MEMBER), does NOT see Leave button
        compose.onNodeWithText("Rời khỏi nhà").assertDoesNotExist()
        compose.onNodeWithContentDescription("Loại bỏ thành viên uB").performClick()
        // Confirmation dialog appears before action
        compose.onNodeWithText("Loại bỏ thành viên?").assertIsDisplayed()
        compose.onNodeWithText("Loại bỏ").performClick()
        compose.runOnIdle {
            assertEquals("uB", removedUid)
            assertFalse(leftHome)
        }
    }

    @Test
    fun cloudHomeManagementCard_memberSeesLeaveWithConfirmationAndNoRemoveButtons() {
        var leftHome = false
        val memberState = CloudUiState(
            status = CloudStatus.READY,
            currentUid = "uB",
            homes = listOf(CloudHome("home_A001", "Nha Chia Se", CloudRole.MEMBER)),
            selectedHomeId = "home_A001",
            membersStatus = MembersStatus.READY,
            members = listOf(
                CloudMember("uA", CloudRole.OWNER, 1_700_000_000_000L, "uA"),
                CloudMember("uB", CloudRole.MEMBER, 1_700_000_100_000L, "uA"),
                CloudMember("uC", CloudRole.MEMBER, 1_700_000_200_000L, "uA")
            )
        )

        compose.setContent {
            SmartHomeTheme {
                CloudHomeManagementCard(
                    cloudState = memberState,
                    onSelectHome = {},
                    onCreateHome = {},
                    onLeaveHome = { leftHome = true },
                    onRemoveMember = {},
                    onRetry = {}
                )
            }
        }

        // MEMBER never sees remove buttons for anyone
        compose.onNodeWithContentDescription("Loại bỏ thành viên uA").assertDoesNotExist()
        compose.onNodeWithContentDescription("Loại bỏ thành viên uC").assertDoesNotExist()

        // MEMBER sees Leave button with confirmation dialog
        compose.onNodeWithText("Rời khỏi nhà").performClick()
        compose.onNodeWithText("Rời khỏi nhà?").assertIsDisplayed()
        compose.onAllNodes(hasText("Rời khỏi nhà"))[1].performClick()
        compose.runOnIdle {
            assertTrue(leftHome)
        }
    }

    companion object {
        private const val EMULATOR_DUMMY_URL = "https://demo-smarthomeai-default-rtdb.firebaseio.com"
        private val cache = mutableMapOf<String, Pair<FirebaseApp, FirebaseDatabase>>()

        @Synchronized
        private fun isolatedDatabase(name: String, goOffline: Boolean): Pair<FirebaseApp, FirebaseDatabase> {
            cache[name]?.let { return it }
            val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            val app = runCatching { FirebaseApp.getInstance(name) }.getOrElse {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:0000000000000002")
                    .setApiKey("fake-local-emulator-api-key")
                    .setProjectId("demo-smarthomeai")
                    .setDatabaseUrl(EMULATOR_DUMMY_URL)
                    .build()
                FirebaseApp.initializeApp(context, options, name)
            }
            val db = FirebaseDatabase.getInstance(app, EMULATOR_DUMMY_URL)
            db.useEmulator("10.0.2.2", 9000)
            if (goOffline) db.goOffline() else db.goOnline()
            return (app to db).also { cache[name] = it }
        }
    }
}
