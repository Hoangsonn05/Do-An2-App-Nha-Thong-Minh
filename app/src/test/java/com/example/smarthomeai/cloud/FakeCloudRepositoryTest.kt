package com.example.smarthomeai.cloud

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCloudRepositoryTest {

    @Test
    fun observeHomes_whenEmpty_returnsOkEmptyList() = runTest {
        val repo = FakeCloudRepository()
        val result = repo.observeHomes().first()
        assertEquals(CloudResult.Ok(emptyList<CloudHome>()), result)
    }

    @Test
    fun createHome_trimsName_andUpdatesHomesAndMembers() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA_owner")
        val createRes = repo.createHome("  Nha Chinh  ")
        assertTrue(createRes is CloudResult.Ok)
        val homeId = (createRes as CloudResult.Ok).data

        val homesRes = repo.observeHomes().first()
        assertTrue(homesRes is CloudResult.Ok)
        val homes = (homesRes as CloudResult.Ok).data
        assertEquals(1, homes.size)
        assertEquals(CloudHome(id = homeId, name = "Nha Chinh", role = CloudRole.OWNER), homes.first())

        val membersRes = repo.observeMembers(homeId).first()
        assertTrue(membersRes is CloudResult.Ok)
        val members = (membersRes as CloudResult.Ok).data
        assertEquals(1, members.size)
        assertEquals("uA_owner", members.first().uid)
        assertEquals(CloudRole.OWNER, members.first().role)
        assertEquals("uA_owner", members.first().addedBy)
    }

    @Test
    fun createHome_rejectsBlankOverlongOrForbiddenName() = runTest {
        val repo = FakeCloudRepository()
        val blankRes = repo.createHome("   ")
        assertTrue(blankRes is CloudResult.Err && blankRes.error == CloudError.InvalidData)

        val longRes = repo.createHome("A".repeat(61))
        assertTrue(longRes is CloudResult.Err && longRes.error == CloudError.InvalidData)

        val secretRes = repo.createHome("Home deviceKey 123")
        assertTrue(secretRes is CloudResult.Err && secretRes.error == CloudError.InvalidData)
    }

    @Test
    fun unavailableAndUnauthenticatedStates_returnExpectedContractResults() = runTest {
        val unavailableRepo = FakeCloudRepository(initialAvailable = false)
        assertEquals(CloudResult.Unavailable, unavailableRepo.observeHomes().first())
        assertEquals(CloudResult.Unavailable, unavailableRepo.createHome("Valid Home"))
        assertEquals(CloudResult.Unavailable, unavailableRepo.observeMembers("home_A001").first())

        val signedOutRepo = FakeCloudRepository(initialUid = null)
        val homesRes = signedOutRepo.observeHomes().first()
        assertTrue(homesRes is CloudResult.Err && homesRes.error == CloudError.Unauthenticated)
        val createRes = signedOutRepo.createHome("Valid Home")
        assertTrue(createRes is CloudResult.Err && createRes.error == CloudError.Unauthenticated)
    }

    @Test
    fun failNext_propagatesConfiguredErrorWithOptionalMessage() = runTest {
        val repo = FakeCloudRepository()
        repo.failNext(CloudError.Timeout, "Simulated timeout")
        val res = repo.createHome("Valid Home")
        assertEquals(CloudResult.Err(CloudError.Timeout, "Simulated timeout"), res)

        repo.failNext(CloudError.Offline, null)
        val nextRes = repo.observeHomes().first()
        assertEquals(CloudResult.Err(CloudError.Offline, null), nextRes)
    }

    @Test
    fun observeMembers_rejectsInvalidHomeId() = runTest {
        val repo = FakeCloudRepository()
        val res = repo.observeMembers("short")
            .first()
        assertTrue(res is CloudResult.Err && res.error == CloudError.InvalidData)
    }
}
