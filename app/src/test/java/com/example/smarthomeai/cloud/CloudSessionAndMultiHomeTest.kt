package com.example.smarthomeai.cloud

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive JVM unit tests for P1.1-B3:
 * - B3.1 / B2-F5: Auth UID transitions, sign-out, account switching, old flow cancellation, zero cross-UID leakage,
 *   stale result prevention.
 * - B3.2: Multi-Home state, creation, selection, switching, deleted/revoked home removal.
 * - B3.3: Member observation, role sorting, OWNER/MEMBER permission presentation, Leave Home, Remove Member.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudSessionAndMultiHomeTest {

    private class FakeCloudSession(
        initial: CloudSessionState = CloudSessionState.SignedOut
    ) : CloudSession {
        val flow = MutableStateFlow(initial)
        override fun observeSession(): Flow<CloudSessionState> = flow
    }

    @Test
    fun firebaseCloudSession_mapsConfigAndUidChangesCleanly() = runTest {
        val rawUidFlow = MutableStateFlow<String?>(null)
        val provider = FirebaseProvider(
            context = null,
            appResolver = { null },
            databaseUrlResolver = { "https://demo-smarthomeai-default-rtdb.firebaseio.com" },
            uidResolver = { rawUidFlow.value },
            databaseResolver = { error("Must not instantiate DB during session observation") },
            authUidFlowResolver = { rawUidFlow }
        )

        val session = FirebaseCloudSession(provider)
        val collected = mutableListOf<CloudSessionState>()
        val job = launch { session.observeSession().toList(collected) }
        runCurrent()

        rawUidFlow.value = "uA_1001"
        runCurrent()
        rawUidFlow.value = "   " // blank -> SignedOut
        runCurrent()
        rawUidFlow.value = "bad/uid" // forbidden RTDB char -> SignedOut (distinctUntilChanged collapses)
        runCurrent()
        rawUidFlow.value = "uB_2002"
        runCurrent()
        job.cancel()

        assertEquals(
            listOf(
                CloudSessionState.SignedOut,
                CloudSessionState.SignedIn("uA_1001"),
                CloudSessionState.SignedOut,
                CloudSessionState.SignedIn("uB_2002")
            ),
            collected
        )
    }

    @Test
    fun firebaseCloudSession_whenUnconfigured_emitsUnavailableWithoutTouchingAuthOrDb() = runTest {
        val provider = FirebaseProvider(
            context = null,
            appResolver = { null },
            databaseUrlResolver = { null },
            uidResolver = { error("Must not read uid when unconfigured") },
            databaseResolver = { error("Must not open DB when unconfigured") },
            authUidFlowResolver = { error("Must not observe Auth when unconfigured") }
        )
        val session = FirebaseCloudSession(provider)
        val states = session.observeSession().toList()
        assertEquals(listOf(CloudSessionState.Unavailable), states)
    }

    @Test
    fun signOut_cancelsActiveFlowsAndClearsAllHomeAndMemberState() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_A001", "Nha A", ownerUid = "uA")
        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))

        val manager = CloudHomeManager(backgroundScope, session, repo)
        runCurrent()

        assertEquals(CloudStatus.READY, manager.state.value.status)
        assertEquals(1, manager.state.value.homes.size)
        assertEquals("home_A001", manager.state.value.selectedHomeId)
        assertEquals(1, manager.state.value.members.size)
        assertEquals(1, repo.activeHomesCollectors.get())
        assertEquals(1, repo.activeMemberCollectorsByHome["home_A001"]?.get())

        // Sign out
        repo.setSignedInUid(null)
        manager.onSignedOut()
        session.flow.value = CloudSessionState.SignedOut
        runCurrent()

        assertEquals(CloudStatus.SIGNED_OUT, manager.state.value.status)
        assertNull(manager.state.value.currentUid)
        assertTrue(manager.state.value.homes.isEmpty())
        assertNull(manager.state.value.selectedHomeId)
        assertTrue(manager.state.value.members.isEmpty())
        assertEquals(0, repo.activeHomesCollectors.get())
        assertEquals(0, repo.activeMemberCollectorsByHome["home_A001"]?.get())
        assertTrue(repo.totalHomesCollectorCancellations.get() >= 1)
        assertTrue(repo.totalMemberCollectorCancellations.get() >= 1)
    }

    @Test
    fun accountSwitch_cancelsOldUidFlowsAndPreventsCrossUidLeakage() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_A001", "Nha Rieng A", ownerUid = "uA")
        repo.seedHomeForUid("uB", "home_B001", "Nha Rieng B", ownerUid = "uB")

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo)
        runCurrent()

        assertEquals("uA", manager.state.value.currentUid)
        assertEquals(listOf("home_A001"), manager.state.value.homes.map { it.id })
        assertEquals(listOf("uA"), manager.state.value.members.map { it.uid })

        // Switch account uA -> uB
        repo.setSignedInUid("uB")
        session.flow.value = CloudSessionState.SignedIn("uB")
        runCurrent()

        assertEquals("uB", manager.state.value.currentUid)
        assertEquals(CloudStatus.READY, manager.state.value.status)
        assertEquals(listOf("home_B001"), manager.state.value.homes.map { it.id })
        assertEquals("home_B001", manager.state.value.selectedHomeId)
        assertEquals(listOf("uB"), manager.state.value.members.map { it.uid })
        // Old uA collectors were cancelled before uB collectors settled
        assertEquals(0, repo.activeMemberCollectorsByHome["home_A001"]?.get())
        assertEquals(1, repo.activeMemberCollectorsByHome["home_B001"]?.get())
        assertEquals(1, repo.activeHomesCollectors.get())
        assertEquals(1, repo.totalHomesCollectorCancellations.get())
    }

    @Test
    fun lateEmissionFromPreviousAccount_neverOverwritesNewAccountState() = runTest {
        val oldHomesFlow = MutableSharedFlow<CloudResult<List<CloudHome>>>(replay = 0)
        val newHomesFlow = MutableSharedFlow<CloudResult<List<CloudHome>>>(replay = 1)
        val createHomeGate = CompletableDeferred<CloudResult<String>>()
        var callIndex = 0

        val controlledRepo = object : CloudRepository {
            override fun observeHomes(): Flow<CloudResult<List<CloudHome>>> {
                callIndex++
                return if (callIndex == 1) oldHomesFlow else newHomesFlow
            }
            override suspend fun createHome(name: String): CloudResult<String> = createHomeGate.await()
            override fun observeMembers(homeId: String): Flow<CloudResult<List<CloudMember>>> =
                flowOf(CloudResult.Ok(emptyList()))
            override suspend fun leaveHome(homeId: String): CloudResult<Unit> = CloudResult.Ok(Unit)
            override suspend fun removeMember(homeId: String, memberUid: String): CloudResult<Unit> = CloudResult.Ok(Unit)
        }

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, controlledRepo)
        runCurrent()

        // Start a slow createHome mutation under uA
        manager.createHome("Slow Home A")
        runCurrent()

        // Switch to uB BEFORE uA's old flow or mutation finishes
        session.flow.value = CloudSessionState.SignedIn("uB")
        newHomesFlow.tryEmit(CloudResult.Ok(listOf(CloudHome("home_B001", "Nha B", CloudRole.OWNER))))
        runCurrent()

        // Attempt late emissions from uA's old flow and slow mutation
        oldHomesFlow.tryEmit(CloudResult.Ok(listOf(CloudHome("home_A_LEAK", "Leak A", CloudRole.OWNER))))
        createHomeGate.complete(CloudResult.Ok("home_A_LATE"))
        runCurrent()

        assertEquals("uB", manager.state.value.currentUid)
        assertEquals(listOf("home_B001"), manager.state.value.homes.map { it.id })
        assertEquals("home_B001", manager.state.value.selectedHomeId)
        assertNull(manager.state.value.feedback)
    }

    @Test
    fun homeSelection_switchesActiveHomeAndCancelsOldMemberObserver() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid(
            uid = "uA",
            homeId = "home_A001",
            name = "Nha 1",
            ownerUid = "uA",
            members = listOf(
                CloudMember("uB", CloudRole.MEMBER, 2000L, "uA"),
                CloudMember("uA", CloudRole.OWNER, 1000L, "uA")
            )
        )
        repo.seedHomeForUid(
            uid = "uA",
            homeId = "home_A002",
            name = "Nha 2",
            ownerUid = "uOther",
            members = listOf(
                CloudMember("uOther", CloudRole.OWNER, 1000L, "uOther"),
                CloudMember("uA", CloudRole.MEMBER, 3000L, "uOther")
            )
        )

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo)
        runCurrent()

        assertEquals("home_A001", manager.state.value.selectedHomeId)
        // OWNER sorted before MEMBER
        assertEquals(listOf("uA", "uB"), manager.state.value.members.map { it.uid })
        assertEquals(CloudRole.OWNER, manager.state.value.currentUserRole)
        assertTrue(manager.state.value.canRemoveMember(CloudMember("uB", CloudRole.MEMBER)))
        assertFalse(manager.state.value.canRemoveMember(CloudMember("uA", CloudRole.OWNER)))
        assertFalse(manager.state.value.canLeaveSelectedHome)
        assertEquals(1, repo.activeMemberCollectorsByHome["home_A001"]?.get())

        // Switch to home_A002 where uA is MEMBER
        manager.selectHome("home_A002")
        runCurrent()

        assertEquals("home_A002", manager.state.value.selectedHomeId)
        assertEquals(CloudRole.MEMBER, manager.state.value.currentUserRole)
        assertEquals(listOf("uOther", "uA"), manager.state.value.members.map { it.uid })
        assertTrue(manager.state.value.canLeaveSelectedHome)
        assertFalse(manager.state.value.canRemoveMember(CloudMember("uOther", CloudRole.OWNER)))
        assertEquals(0, repo.activeMemberCollectorsByHome["home_A001"]?.get())
        assertEquals(1, repo.activeMemberCollectorsByHome["home_A002"]?.get())
    }

    @Test
    fun leaveHome_memberSucceeds_ownerRejected() = runTest {
        val repo = FakeCloudRepository(initialUid = "uB")
        repo.seedHomeForUid("uB", "home_A001", "Nha Chia Se", ownerUid = "uA")
        repo.seedHomeForUid("uB", "home_B001", "Nha Rieng B", ownerUid = "uB")

        val session = FakeCloudSession(CloudSessionState.SignedIn("uB"))
        val manager = CloudHomeManager(backgroundScope, session, repo)
        runCurrent()

        // Currently on home_A001 as MEMBER -> leave succeeds and switches to home_B001
        assertEquals("home_A001", manager.state.value.selectedHomeId)
        assertTrue(manager.state.value.canLeaveSelectedHome)
        manager.leaveSelectedHome()
        runCurrent()

        assertEquals(CloudStatus.READY, manager.state.value.status)
        assertEquals(listOf("home_B001"), manager.state.value.homes.map { it.id })
        assertEquals("home_B001", manager.state.value.selectedHomeId)
        assertFalse(manager.state.value.feedback?.isError ?: true)

        // Now on home_B001 as OWNER -> leave is denied
        assertFalse(manager.state.value.canLeaveSelectedHome)
        manager.leaveSelectedHome()
        runCurrent()

        assertEquals(CloudError.PermissionDenied, manager.state.value.feedback?.error)
        assertEquals(listOf("home_B001"), manager.state.value.homes.map { it.id })
    }

    @Test
    fun removeMember_ownerRemovesMember_memberCannotRemoveOthers() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid(
            uid = "uA",
            homeId = "home_A001",
            name = "Nha A",
            ownerUid = "uA",
            members = listOf(
                CloudMember("uA", CloudRole.OWNER, 1000L, "uA"),
                CloudMember("uB", CloudRole.MEMBER, 2000L, "uA"),
                CloudMember("uC", CloudRole.MEMBER, 3000L, "uA")
            )
        )
        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo)
        runCurrent()

        // Owner removes uB
        manager.removeMember("uB")
        runCurrent()
        assertEquals(listOf("uA", "uC"), manager.state.value.members.map { it.uid })
        assertFalse(manager.state.value.feedback?.isError ?: true)

        // Owner cannot remove self
        manager.removeMember("uA")
        runCurrent()
        assertEquals(CloudError.PermissionDenied, manager.state.value.feedback?.error)

        // Switch to MEMBER uC: cannot remove anyone
        repo.setSignedInUid("uC")
        repo.seedHomeForUid("uC", "home_A001", "Nha A", ownerUid = "uA")
        session.flow.value = CloudSessionState.SignedIn("uC")
        runCurrent()

        manager.removeMember("uA")
        runCurrent()
        assertEquals(CloudError.PermissionDenied, manager.state.value.feedback?.error)
    }

    @Test
    fun revokedHomeAccess_removesStaleHomeFromValidDisplayImmediately() = runTest {
        val repo = FakeCloudRepository(initialUid = "uB")
        repo.seedHomeForUid("uB", "home_A001", "Nha Bi Thu Hoi", ownerUid = "uA")
        val session = FakeCloudSession(CloudSessionState.SignedIn("uB"))
        val manager = CloudHomeManager(backgroundScope, session, repo)
        runCurrent()

        assertEquals(CloudStatus.READY, manager.state.value.status)
        assertEquals("home_A001", manager.state.value.selectedHomeId)

        // Even if only member listener receives PERMISSION_DENIED (stale userHomes),
        // the home MUST be removed from valid homes immediately and status -> ACCESS_REVOKED.
        repo.setMembersError("home_A001", CloudError.PermissionDenied)
        runCurrent()

        assertEquals(CloudStatus.ACCESS_REVOKED, manager.state.value.status)
        assertTrue(manager.state.value.homes.isEmpty())
        assertNull(manager.state.value.selectedHomeId)
        assertTrue(manager.state.value.members.isEmpty())
        assertTrue(manager.state.value.revokedNotice?.contains("Nha Bi Thu Hoi") == true)
    }

    @Test
    fun createHome_trimsNameSelectsNewHomeAndHandlesRulesError() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo)
        runCurrent()

        assertEquals(CloudStatus.EMPTY, manager.state.value.status)

        manager.createHome("  Nha Chinh  ")
        runCurrent()

        assertEquals(CloudStatus.READY, manager.state.value.status)
        assertEquals(1, manager.state.value.homes.size)
        assertEquals("Nha Chinh", manager.state.value.selectedHome?.name)

        // Simulated Rules denial on second createHome
        repo.failNext(CloudError.PermissionDenied, "Denied by rules")
        manager.createHome("Nha Bi Chan")
        runCurrent()

        assertEquals(CloudError.PermissionDenied, manager.state.value.feedback?.error)
        assertTrue(manager.state.value.feedback?.message?.contains("Rules") == true)
        // Existing valid home remains intact
        assertEquals(1, manager.state.value.homes.size)
    }

    @Test
    fun offlineError_classifiesDistinctlyAndRetryRecovers() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_A001", "Nha A", ownerUid = "uA")
        repo.failNext(CloudError.Offline, "No internet")

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo)
        runCurrent()

        assertEquals(CloudStatus.ERROR, manager.state.value.status)
        assertEquals(CloudError.Offline, manager.state.value.error)

        // Retry recovers cleanly
        manager.retry()
        runCurrent()

        assertEquals(CloudStatus.READY, manager.state.value.status)
        assertEquals(listOf("home_A001"), manager.state.value.homes.map { it.id })
    }
}
