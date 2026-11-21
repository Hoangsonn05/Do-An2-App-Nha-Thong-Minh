package com.example.smarthomeai.cloud

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSessionLifecycleTest {

    private class FakeCloudSession(
        initial: CloudSessionState = CloudSessionState.SignedOut
    ) : CloudSession {
        val flow = MutableStateFlow(initial)
        override fun observeSession(): Flow<CloudSessionState> = flow
    }

    @Test
    fun wanOffline_withCachedFirebaseUser_keepsSessionAndDoesNotRequireReauth() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_A001", "Nha A", ownerUid = "uA")
        repo.failNext(CloudError.Offline, "No WAN")

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo, singleHomeMode = true)
        runCurrent()

        val uiState = manager.state.value
        assertEquals(CloudStatus.ERROR, uiState.status)
        assertEquals(CloudError.Offline, uiState.error)
        assertEquals("uA", uiState.currentUid)
        assertFalse(uiState.reauthRequired)

        val decision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = true,
            prevObservedUid = "uA",
            cloudStatus = uiState.status,
            cloudUid = uiState.currentUid
        )
        assertFalse(decision.shouldRequireReauth)
        assertFalse(decision.shouldRefreshUserEmail)
        assertEquals("uA", decision.nextObservedUid)
    }

    @Test
    fun externalSessionRevocation_orUnauthenticatedError_requiresReauthWithoutClearingLanSession() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_A001", "Nha A", ownerUid = "uA")

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo, singleHomeMode = true)
        runCurrent()

        assertEquals(CloudStatus.READY, manager.state.value.status)
        val initialDecision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = true,
            prevObservedUid = null,
            cloudStatus = manager.state.value.status,
            cloudUid = manager.state.value.currentUid
        )
        assertFalse(initialDecision.shouldRequireReauth)
        assertTrue(initialDecision.shouldRefreshUserEmail)
        assertEquals("uA", initialDecision.nextObservedUid)

        // External token revocation / sign-out from another place while local LAN remains logged in
        session.flow.value = CloudSessionState.SignedOut
        runCurrent()

        assertEquals(CloudStatus.SIGNED_OUT, manager.state.value.status)
        assertNull(manager.state.value.currentUid)

        val revokedDecision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = true,
            prevObservedUid = initialDecision.nextObservedUid,
            cloudStatus = manager.state.value.status,
            cloudUid = manager.state.value.currentUid
        )
        assertTrue(revokedDecision.shouldRequireReauth)
        assertFalse(revokedDecision.shouldRefreshUserEmail)
        assertNull(revokedDecision.nextObservedUid)

        manager.setReauthRequired(revokedDecision.shouldRequireReauth)
        assertTrue(manager.state.value.reauthRequired)

        // Also verify CloudError.Unauthenticated from repository triggers SIGNED_OUT -> reauthRequired
        val unauthRepo = FakeCloudRepository(initialUid = "uA")
        unauthRepo.failNext(CloudError.Unauthenticated, "Token expired")
        val session2 = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager2 = CloudHomeManager(backgroundScope, session2, unauthRepo, singleHomeMode = true)
        runCurrent()

        assertEquals(CloudStatus.SIGNED_OUT, manager2.state.value.status)
        val unauthDecision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = true,
            prevObservedUid = "uA",
            cloudStatus = manager2.state.value.status,
            cloudUid = manager2.state.value.currentUid
        )
        assertTrue(unauthDecision.shouldRequireReauth)
    }

    @Test
    fun accountSwitch_clearsPreviousUidState_refreshesUserEmail_andClearsReauth() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_A001", "Nha A", ownerUid = "uA")
        repo.seedHomeForUid("uB", "home_B001", "Nha B", ownerUid = "uB")

        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo, singleHomeMode = true)
        runCurrent()

        var observedUid = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = true,
            prevObservedUid = null,
            cloudStatus = manager.state.value.status,
            cloudUid = manager.state.value.currentUid
        ).nextObservedUid
        assertEquals("uA", observedUid)

        // Revoked then re-authenticated with uB
        session.flow.value = CloudSessionState.SignedOut
        runCurrent()
        val signedOutDecision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = true,
            prevObservedUid = observedUid,
            cloudStatus = manager.state.value.status,
            cloudUid = manager.state.value.currentUid
        )
        assertTrue(signedOutDecision.shouldRequireReauth)
        manager.setReauthRequired(true)
        observedUid = signedOutDecision.nextObservedUid

        repo.setSignedInUid("uB")
        session.flow.value = CloudSessionState.SignedIn("uB")
        runCurrent()

        assertEquals(CloudStatus.READY, manager.state.value.status)
        assertEquals("uB", manager.state.value.currentUid)
        assertEquals(listOf("home_B001"), manager.state.value.homes.map { it.id })
        assertFalse(manager.state.value.reauthRequired)

        val switchDecision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = true,
            prevObservedUid = observedUid,
            cloudStatus = manager.state.value.status,
            cloudUid = manager.state.value.currentUid
        )
        assertFalse(switchDecision.shouldRequireReauth)
        assertTrue(switchDecision.shouldRefreshUserEmail)
        assertEquals("uB", switchDecision.nextObservedUid)

        // Subsequent updates for the same UID do not redundantly refresh userEmail
        val steadyDecision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = true,
            prevObservedUid = switchDecision.nextObservedUid,
            cloudStatus = CloudStatus.READY,
            cloudUid = "uB"
        )
        assertFalse(steadyDecision.shouldRequireReauth)
        assertFalse(steadyDecision.shouldRefreshUserEmail)
        assertEquals("uB", steadyDecision.nextObservedUid)
    }

    @Test
    fun demoUnavailableMode_neverRequiresReauthOrDisruptsLan() = runTest {
        val repo = FakeCloudRepository(initialUid = null)
        val session = FakeCloudSession(CloudSessionState.Unavailable)
        val manager = CloudHomeManager(backgroundScope, session, repo, singleHomeMode = true)
        runCurrent()

        assertEquals(CloudStatus.UNAVAILABLE, manager.state.value.status)
        assertFalse(manager.state.value.reauthRequired)

        val unconfiguredDecision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = false,
            localLoggedIn = true,
            prevObservedUid = null,
            cloudStatus = manager.state.value.status,
            cloudUid = manager.state.value.currentUid
        )
        assertFalse(unconfiguredDecision.shouldRequireReauth)
        assertFalse(unconfiguredDecision.shouldRefreshUserEmail)
        assertNull(unconfiguredDecision.nextObservedUid)

        // Even if firebaseConfigured was passed true while status is UNAVAILABLE
        val unavailableDecision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = true,
            prevObservedUid = "uA",
            cloudStatus = CloudStatus.UNAVAILABLE,
            cloudUid = null
        )
        assertFalse(unavailableDecision.shouldRequireReauth)
        assertFalse(unavailableDecision.shouldRefreshUserEmail)
        assertEquals("uA", unavailableDecision.nextObservedUid)
    }

    @Test
    fun explicitLogout_neverSetsReauthRequired() = runTest {
        val repo = FakeCloudRepository(initialUid = "uA")
        repo.seedHomeForUid("uA", "home_A001", "Nha A", ownerUid = "uA")
        val session = FakeCloudSession(CloudSessionState.SignedIn("uA"))
        val manager = CloudHomeManager(backgroundScope, session, repo, singleHomeMode = true)
        runCurrent()

        manager.setReauthRequired(true)
        assertTrue(manager.state.value.reauthRequired)

        // Explicit user logout sets localLoggedIn = false and calls cloudManager.onSignedOut()
        manager.onSignedOut()
        session.flow.value = CloudSessionState.SignedOut
        runCurrent()

        assertEquals(CloudStatus.SIGNED_OUT, manager.state.value.status)
        assertFalse(manager.state.value.reauthRequired)

        val logoutDecision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = false,
            prevObservedUid = "uA",
            cloudStatus = manager.state.value.status,
            cloudUid = manager.state.value.currentUid
        )
        assertFalse(logoutDecision.shouldRequireReauth)
        assertFalse(logoutDecision.shouldRefreshUserEmail)
        assertNull(logoutDecision.nextObservedUid)
    }

    @Test
    fun coldStartTransientLoadingWithNullUid_doesNotTriggerFalseReauth() {
        val decision = CloudSessionSyncEvaluator.evaluate(
            firebaseConfigured = true,
            localLoggedIn = true,
            prevObservedUid = null,
            cloudStatus = CloudStatus.LOADING,
            cloudUid = null
        )
        assertFalse(decision.shouldRequireReauth)
        assertFalse(decision.shouldRefreshUserEmail)
        assertNull(decision.nextObservedUid)
    }
}
