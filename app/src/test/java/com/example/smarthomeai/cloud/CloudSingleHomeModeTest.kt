package com.example.smarthomeai.cloud

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSingleHomeModeTest {

    private class FakeCloudSession(
        initial: CloudSessionState = CloudSessionState.SignedOut
    ) : CloudSession {
        val flow = MutableStateFlow(initial)
        override fun observeSession(): Flow<CloudSessionState> = flow
    }

    @Test
    fun singleHomeMode_autoSelectsDeterministicOldestOwnedHome_ignoringMemberHomes() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_000_MEMBER", "Nha Chia Se", ownerUid = "uOther")
        repo.seedHomeForUid("uA", "home_OWN_B", "Nha Owner B", ownerUid = "uA")
        repo.seedHomeForUid("uA", "home_OWN_A", "Nha Owner A", ownerUid = "uA")

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo, singleHomeMode = true)
        runCurrent()

        val state = manager.state.value
        assertEquals(CloudStatus.READY, state.status)
        assertEquals("home_OWN_A", state.selectedHomeId)
        assertEquals("Nha Owner A", state.selectedHome?.name)
        assertEquals(CloudRole.OWNER, state.selectedHome?.role)
        assertEquals(2, state.ownedHomesCount)
        assertTrue(state.hasMultipleOwnedHomes)
        assertEquals(1, state.ignoredMemberHomesCount)
        assertFalse(state.hasOnlyMemberHomes)
        assertEquals(MembersStatus.IDLE, state.membersStatus)
        assertTrue(state.members.isEmpty())
        assertEquals(0, repo.activeMemberCollectorsByHome.values.sumOf { it.get() })
    }

    @Test
    fun singleHomeMode_blocksCreateHome_whenAlreadyOwnsHome_orWhileLoading() = runTest {
        val homesFlow = MutableSharedFlow<CloudResult<List<CloudHome>>>(replay = 0)
        val createCalls = AtomicInteger(0)
        val loadingRepo = object : CloudRepository {
            override fun observeHomes(): Flow<CloudResult<List<CloudHome>>> = homesFlow
            override suspend fun createHome(name: String): CloudResult<String> {
                createCalls.incrementAndGet()
                return CloudResult.Ok("home_UNEXPECTED_1")
            }
            override fun observeMembers(homeId: String): Flow<CloudResult<List<CloudMember>>> =
                flowOf(CloudResult.Ok(emptyList()))
            override suspend fun leaveHome(homeId: String): CloudResult<Unit> = CloudResult.Ok(Unit)
            override suspend fun removeMember(homeId: String, memberUid: String): CloudResult<Unit> =
                CloudResult.Ok(Unit)
        }

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val loadingManager = CloudHomeManager(backgroundScope, session, loadingRepo, singleHomeMode = true)
        runCurrent()

        assertEquals(CloudStatus.LOADING, loadingManager.state.value.status)
        loadingManager.createHome("Nha Khi Dang Tai")
        runCurrent()

        assertEquals(0, createCalls.get())
        assertEquals(CloudError.Conflict, loadingManager.state.value.feedback?.error)
        assertEquals(
            "Tài khoản đã có nhà Cloud. Mô hình hiện tại chỉ hỗ trợ một nhà.",
            loadingManager.state.value.feedback?.message
        )

        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_OWN_001", "Nha Chinh", ownerUid = "uA")
        val readyManager = CloudHomeManager(backgroundScope, session, repo, singleHomeMode = true)
        runCurrent()

        assertEquals(CloudStatus.READY, readyManager.state.value.status)
        readyManager.createHome("Nha Thu 2")
        runCurrent()

        assertEquals(1, readyManager.state.value.homes.size)
        assertEquals("home_OWN_001", readyManager.state.value.selectedHomeId)
        assertTrue(readyManager.state.value.feedback?.isError == true)
        assertEquals(CloudError.Conflict, readyManager.state.value.feedback?.error)
        assertEquals(
            "Tài khoản đã có nhà Cloud. Mô hình hiện tại chỉ hỗ trợ một nhà.",
            readyManager.state.value.feedback?.message
        )
    }

    @Test
    fun singleHomeMode_preventsDoubleTapCreateHome_whenEmpty() = runTest {
        val homesFlow = MutableStateFlow<CloudResult<List<CloudHome>>>(CloudResult.Ok(emptyList()))
        val createGate = CompletableDeferred<CloudResult<String>>()
        val createCalls = AtomicInteger(0)

        val repo = object : CloudRepository {
            override fun observeHomes(): Flow<CloudResult<List<CloudHome>>> = homesFlow
            override suspend fun createHome(name: String): CloudResult<String> {
                createCalls.incrementAndGet()
                return createGate.await()
            }
            override fun observeMembers(homeId: String): Flow<CloudResult<List<CloudMember>>> =
                flowOf(CloudResult.Ok(emptyList()))
            override suspend fun leaveHome(homeId: String): CloudResult<Unit> = CloudResult.Ok(Unit)
            override suspend fun removeMember(homeId: String, memberUid: String): CloudResult<Unit> =
                CloudResult.Ok(Unit)
        }

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo, singleHomeMode = true)
        runCurrent()

        assertEquals(CloudStatus.EMPTY, manager.state.value.status)

        manager.createHome("Nha Chinh")
        runCurrent()
        assertEquals(1, createCalls.get())
        assertTrue(manager.state.value.busy)

        // Second tap while busy must be ignored without starting a second creation or clearing busy
        manager.createHome("Nha Double Tap")
        runCurrent()
        assertEquals(1, createCalls.get())
        assertTrue(manager.state.value.busy)
        assertNull(manager.state.value.feedback)

        createGate.complete(CloudResult.Ok("home_NEW_001"))
        homesFlow.value = CloudResult.Ok(listOf(CloudHome("home_NEW_001", "Nha Chinh", CloudRole.OWNER)))
        runCurrent()

        assertEquals(CloudStatus.READY, manager.state.value.status)
        assertFalse(manager.state.value.busy)
        assertEquals("home_NEW_001", manager.state.value.selectedHomeId)
        assertEquals(listOf("home_NEW_001"), manager.state.value.homes.map { it.id })
        assertFalse(manager.state.value.feedback?.isError ?: true)
    }

    @Test
    fun singleHomeMode_legacyMultiOwnerAccount_preservesAllHomesAndAllowsSessionSwitchToOwnedOnly() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_000_MEM", "Nha Chia Se", ownerUid = "uOther")
        repo.seedHomeForUid("uA", "home_OWN_001", "Nha Cu 1", ownerUid = "uA")
        repo.seedHomeForUid("uA", "home_OWN_002", "Nha Cu 2", ownerUid = "uA")

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo, singleHomeMode = true)
        runCurrent()

        assertEquals("home_OWN_001", manager.state.value.selectedHomeId)
        assertEquals(3, manager.state.value.homes.size)
        assertEquals(2, manager.state.value.ownedHomesCount)
        assertTrue(manager.state.value.hasMultipleOwnedHomes)
        assertEquals(1, manager.state.value.ignoredMemberHomesCount)

        // Cannot select a MEMBER home in singleHomeMode
        manager.selectHome("home_000_MEM")
        runCurrent()
        assertEquals("home_OWN_001", manager.state.value.selectedHomeId)

        // Can temporarily switch between owned homes in session without deleting any home
        manager.selectHome("home_OWN_002")
        runCurrent()
        assertEquals("home_OWN_002", manager.state.value.selectedHomeId)
        assertEquals(3, manager.state.value.homes.size)

        // Subsequent emission preserves the active owned home selected in session
        repo.seedHomeForUid("uA", "home_OWN_003", "Nha Cu 3", ownerUid = "uA")
        runCurrent()
        assertEquals("home_OWN_002", manager.state.value.selectedHomeId)
        assertEquals(4, manager.state.value.homes.size)
        assertEquals(3, manager.state.value.ownedHomesCount)
    }

    @Test
    fun singleHomeMode_memberOnlyAccount_staysEmptyWithoutSelectingSharedHome_andAllowsCreatingOwnHome() = runTest {
        val repo = FakeCloudRepository(initialUid = "uB")
        repo.seedHomeForUid("uB", "home_SHARED_1", "Nha Duoc Chia Se", ownerUid = "uA")

        val session = FakeCloudSession(CloudSessionState.SignedIn("uB"))
        val manager = CloudHomeManager(backgroundScope, session, repo, singleHomeMode = true)
        runCurrent()

        val emptyState = manager.state.value
        assertEquals(CloudStatus.EMPTY, emptyState.status)
        assertNull(emptyState.selectedHomeId)
        assertEquals(1, emptyState.homes.size)
        assertEquals(0, emptyState.ownedHomesCount)
        assertEquals(1, emptyState.ignoredMemberHomesCount)
        assertTrue(emptyState.hasOnlyMemberHomes)
        assertEquals(0, repo.activeMemberCollectorsByHome.values.sumOf { it.get() })

        // User can still create their own primary OWNER home
        manager.createHome("Nha Rieng ESP32")
        runCurrent()

        val readyState = manager.state.value
        assertEquals(CloudStatus.READY, readyState.status)
        assertEquals("Nha Rieng ESP32", readyState.selectedHome?.name)
        assertEquals(CloudRole.OWNER, readyState.selectedHome?.role)
        assertEquals(1, readyState.ownedHomesCount)
        assertEquals(1, readyState.ignoredMemberHomesCount)
        assertFalse(readyState.hasOnlyMemberHomes)
        assertEquals(0, repo.activeMemberCollectorsByHome.values.sumOf { it.get() })
    }

    @Test
    fun multiHomeModeDefaultFalse_preservesFullB3Behavior() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_000_MEM", "Nha Chia Se", ownerUid = "uOther")
        repo.seedHomeForUid("uA", "home_OWN_001", "Nha Owner 1", ownerUid = "uA")

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo)
        runCurrent()

        assertEquals(CloudStatus.READY, manager.state.value.status)
        assertEquals("home_000_MEM", manager.state.value.selectedHomeId)
        assertEquals(1, repo.activeMemberCollectorsByHome["home_000_MEM"]?.get())

        manager.createHome("Nha Thu 2")
        runCurrent()

        assertEquals(3, manager.state.value.homes.size)
        assertFalse(manager.state.value.feedback?.isError ?: true)
    }
}
