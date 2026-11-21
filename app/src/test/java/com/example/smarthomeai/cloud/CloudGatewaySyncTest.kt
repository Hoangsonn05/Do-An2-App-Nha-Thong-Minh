package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.DeviceEvent
import com.example.smarthomeai.data.HomeMode
import com.example.smarthomeai.data.SensorSample
import com.example.smarthomeai.data.SmartHomeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudGatewaySyncTest {

    private companion object {
        const val BASE_TIME_MS: Long = 1_795_305_570_000L
        const val UID_A: String = "test_user_uA"
        const val UID_B: String = "test_user_uB"
        const val HOME_A: String = "home_A_0001"
        const val HOME_B: String = "home_B_0001"
        const val RAW_STREAM: String = "a1b2c3d4e5f6-98765432"
    }

    @After
    fun tearDown() {
        CloudDeviceUiBridgeRegistry.currentOrNull()?.let {
            CloudDeviceUiBridgeRegistry.unregister(it)
        }
    }

    private fun ownerCloudState(
        uid: String = UID_A,
        homeId: String = HOME_A,
        status: CloudStatus = CloudStatus.READY,
        role: CloudRole = CloudRole.OWNER
    ): CloudUiState = CloudUiState(
        status = status,
        currentUid = uid,
        homes = listOf(CloudHome(id = homeId, name = "Nha Chinh", role = role)),
        selectedHomeId = homeId
    )

    private fun makeEvent(
        seq: Long,
        kind: String = "armed",
        stream: String = RAW_STREAM,
        ts: Long = BASE_TIME_MS + seq * 1_000L
    ): DeviceEvent = DeviceEvent(
        id = "$stream:$seq",
        seq = seq,
        stream = stream,
        boot = 1L,
        incident = 0L,
        kind = kind,
        timestamp = ts,
        uptime = seq * 1_000L
    )

    @Test
    fun deviceResolution_registersWhenEmpty_warnsOnMultipleActive_andBlocksWhenRevokedOnly() = runTest {
        // 1. Empty devices -> auto-registers active device and waits offline cleanly
        val repo1 = FakeCloudDeviceRepository(initialUid = UID_A)
        val cloudFlow1 = MutableStateFlow(ownerCloudState())
        val inputFlow1 = MutableStateFlow(GatewayInputSnapshot(esp32Online = false))
        val sync1 = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = cloudFlow1,
            gatewayInputFlow = inputFlow1,
            deviceRepository = repo1,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()

        val devices1 = repo1.getDevices(HOME_A)
        assertEquals(1, devices1.size)
        assertEquals(DeviceStatus.ACTIVE, devices1.first().status)
        assertEquals(GatewaySyncPhase.PAUSED_LAN_OFFLINE, sync1.gatewaySync.value.phase)
        assertEquals(devices1.first().id, sync1.gatewaySync.value.activeDeviceId)
        assertNull(sync1.gatewaySync.value.warningMessage)

        // 2. Multiple active devices -> picks oldest by (createdAt, id) and attaches warning without deleting
        val repo2 = FakeCloudDeviceRepository(initialUid = UID_A)
        val older = CloudDevice("dev_old_0001", "Gateway Cu", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_A)
        val newer = CloudDevice("dev_new_0002", "Gateway Moi", "esp32-smarthome", DeviceStatus.ACTIVE, 200L, UID_A)
        repo2.seedDevice(HOME_A, newer)
        repo2.seedDevice(HOME_A, older)
        val sync2 = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = MutableStateFlow(ownerCloudState()),
            gatewayInputFlow = MutableStateFlow(GatewayInputSnapshot(esp32Online = true, lastSeen = BASE_TIME_MS)),
            deviceRepository = repo2,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()

        assertEquals(2, repo2.getDevices(HOME_A).size)
        assertEquals(GatewaySyncPhase.OK, sync2.gatewaySync.value.phase)
        assertEquals(older.id, sync2.gatewaySync.value.activeDeviceId)
        assertNotNull(sync2.gatewaySync.value.warningMessage)
        assertEquals(1, repo2.getPublishedGatewayStates(HOME_A, older.id).size)
        assertEquals(0, repo2.getPublishedGatewayStates(HOME_A, newer.id).size)

        // 3. RevokedOnly -> BLOCKED and zero writes to deviceData
        val repo3 = FakeCloudDeviceRepository(initialUid = UID_A)
        repo3.seedDevice(HOME_A, CloudDevice("dev_rev_0001", "Bi Thu Hoi", "esp32-smarthome", DeviceStatus.REVOKED, 100L, UID_A))
        val sync3 = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = MutableStateFlow(ownerCloudState()),
            gatewayInputFlow = MutableStateFlow(GatewayInputSnapshot(esp32Online = true, lastSeen = BASE_TIME_MS)),
            deviceRepository = repo3,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()

        assertEquals(GatewaySyncPhase.BLOCKED, sync3.gatewaySync.value.phase)
        assertNull(sync3.gatewaySync.value.activeDeviceId)
        assertEquals("Thiết bị Cloud đã bị thu hồi", sync3.gatewaySync.value.errorMessage)
        assertEquals(0, repo3.publishGatewayCalls.get())
        assertEquals(0, repo3.publishConfigCalls.get())
        assertEquals(0, repo3.publishTelemetryCalls.get())
        assertEquals(0, repo3.publishEventCalls.get())
    }

    @Test
    fun sessionLifecycle_ignoresMember_cancelsInFlightOnUidSwitch_andResetsOnSignOut() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        val devA = CloudDevice("dev_homeA_01", "ESP A", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_A)
        val devB = CloudDevice("dev_homeB_01", "ESP B", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_B)
        repo.seedDevice(HOME_A, devA)
        repo.seedDevice(HOME_B, devB)

        // MEMBER role must never trigger device registration or writes
        val cloudFlow = MutableStateFlow(ownerCloudState(role = CloudRole.MEMBER))
        val inputFlow = MutableStateFlow(GatewayInputSnapshot(esp32Online = true, lastSeen = BASE_TIME_MS))
        val sync = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = cloudFlow,
            gatewayInputFlow = inputFlow,
            deviceRepository = repo,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()

        assertEquals(GatewaySyncPhase.WAITING_HOME, sync.gatewaySync.value.phase)
        assertEquals(0, repo.resolveCalls.get())

        // Switch to OWNER on HOME_A, then switch UID mid-flight before publishGatewayState finishes
        var switched = false
        repo.onBeforePostUidCheck = {
            if (!switched && repo.publishGatewayCalls.get() == 1) {
                switched = true
                repo.setSignedInUid(UID_B)
                cloudFlow.value = ownerCloudState(uid = UID_B, homeId = HOME_B, role = CloudRole.OWNER)
            }
        }
        cloudFlow.value = ownerCloudState(uid = UID_A, homeId = HOME_A, role = CloudRole.OWNER)
        runCurrent()

        assertTrue(switched)
        assertEquals(0, repo.getPublishedGatewayStates(HOME_A, devA.id).size)
        assertEquals(1, repo.getPublishedGatewayStates(HOME_B, devB.id).size)
        assertEquals(devB.id, sync.gatewaySync.value.activeDeviceId)
        assertEquals(GatewaySyncPhase.OK, sync.gatewaySync.value.phase)

        // Sign out cancels sync loop and transitions immediately to IDLE
        repo.setSignedInUid(null)
        cloudFlow.value = CloudUiState(status = CloudStatus.SIGNED_OUT)
        runCurrent()

        assertEquals(GatewaySyncPhase.IDLE, sync.gatewaySync.value.phase)
        assertNull(sync.gatewaySync.value.activeDeviceId)

        // Bridge registry register/unregister helper check
        CloudDeviceUiBridgeRegistry.register(sync)
        assertEquals(sync, CloudDeviceUiBridgeRegistry.currentOrNull())
        CloudDeviceUiBridgeRegistry.unregister(sync)
        assertNull(CloudDeviceUiBridgeRegistry.currentOrNull())
    }

    @Test
    fun sessionCancellation_abortsSuspendedCallImmediatelyOnHomeLoss() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        val devA = CloudDevice("dev_homeA_01", "ESP A", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_A)
        repo.seedDevice(HOME_A, devA)
        val blockingRepo = object : CloudDeviceRepository by repo {
            override suspend fun publishGatewayState(
                expectedUid: String,
                homeId: String,
                deviceId: String,
                payload: Map<String, Any>
            ): CloudResult<Unit> {
                gate.await()
                return repo.publishGatewayState(expectedUid, homeId, deviceId, payload)
            }
        }

        val cloudFlow = MutableStateFlow(ownerCloudState())
        val inputFlow = MutableStateFlow(GatewayInputSnapshot(esp32Online = true, lastSeen = BASE_TIME_MS))
        val sync = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = cloudFlow,
            gatewayInputFlow = inputFlow,
            deviceRepository = blockingRepo,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()
        assertEquals(GatewaySyncPhase.SYNCING, sync.gatewaySync.value.phase)

        // Revoke home access while publishGatewayState is suspended -> job cancelled before gate completes
        cloudFlow.value = CloudUiState(status = CloudStatus.ACCESS_REVOKED, currentUid = UID_A)
        runCurrent()
        assertEquals(GatewaySyncPhase.WAITING_HOME, sync.gatewaySync.value.phase)

        gate.complete(Unit)
        runCurrent()
        assertEquals(0, repo.getPublishedGatewayStates(HOME_A, devA.id).size)
    }

    @Test
    fun gatewayState_enforcesOfflineStartup_freshness_2sDebounce_60sHeartbeat_andSingleOfflineWrite() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        val dev = CloudDevice("dev_gw_0001", "ESP", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_A)
        repo.seedDevice(HOME_A, dev)
        repo.seedConfig(HOME_A, dev.id, CloudConfigState(1L, 35, 80, 18, true))

        val cloudFlow = MutableStateFlow(ownerCloudState())
        val inputFlow = MutableStateFlow(GatewayInputSnapshot(esp32Online = false, lastSeen = 0L))
        val sync = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = cloudFlow,
            gatewayInputFlow = inputFlow,
            deviceRepository = repo,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()

        // Starting offline never pushes online=false
        assertEquals(0, repo.publishGatewayCalls.get())
        assertEquals(GatewaySyncPhase.PAUSED_LAN_OFFLINE, sync.gatewaySync.value.phase)

        // Stale lastSeen (> 5s) with esp32Online=true does not push online=true
        inputFlow.value = GatewayInputSnapshot(
            esp32Online = true,
            lastSeen = BASE_TIME_MS - 6_000L,
            light = false,
            temperature = 27,
            humidity = 60
        )
        runCurrent()
        assertEquals(0, repo.publishGatewayCalls.get())

        // Fresh snapshot pushes online=true immediately
        fun now(): Long = BASE_TIME_MS + testScheduler.currentTime
        inputFlow.value = GatewayInputSnapshot(
            esp32Online = true,
            lastSeen = now(),
            light = false,
            doorOpen = false,
            alarm = false,
            motionDetected = false,
            monitoring = false,
            mode = HomeMode.HOME,
            temperature = 27,
            humidity = 60
        )
        runCurrent()
        assertEquals(1, repo.publishGatewayCalls.get())
        assertEquals(true, repo.getLatestGatewayState(HOME_A, dev.id)?.get("online"))
        assertEquals(GatewaySyncPhase.OK, sync.gatewaySync.value.phase)

        // Change value after 500ms -> suppressed by 2s debounce
        advanceTimeBy(500L)
        runCurrent()
        inputFlow.value = inputFlow.value.copy(lastSeen = now(), light = true)
        runCurrent()
        assertEquals(1, repo.publishGatewayCalls.get())

        // At 1999ms total -> still suppressed
        advanceTimeBy(1_499L)
        runCurrent()
        assertEquals(1, repo.publishGatewayCalls.get())

        // At 2000ms total -> debounced change flushes
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, repo.publishGatewayCalls.get())
        @Suppress("UNCHECKED_CAST")
        val values2 = repo.getLatestGatewayState(HOME_A, dev.id)?.get("values") as Map<String, Any>
        assertEquals(true, values2["light"])

        // Unchanged 8 values at 30s (even with lastSeen ticking) -> no write
        advanceTimeBy(30_000L)
        inputFlow.value = inputFlow.value.copy(lastSeen = now())
        runCurrent()
        assertEquals(2, repo.publishGatewayCalls.get())

        // At 59_999ms since write #2 -> still no heartbeat
        advanceTimeBy(29_999L)
        runCurrent()
        assertEquals(2, repo.publishGatewayCalls.get())

        // At 60_000ms since write #2 -> heartbeat triggers
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(3, repo.publishGatewayCalls.get())
        assertEquals(true, repo.getLatestGatewayState(HOME_A, dev.id)?.get("online"))

        // Transition to offline after having been online -> single online=false write with last valid values
        advanceTimeBy(1_000L)
        inputFlow.value = inputFlow.value.copy(esp32Online = false, lastSeen = 0L)
        runCurrent()
        assertEquals(4, repo.publishGatewayCalls.get())
        assertEquals(false, repo.getLatestGatewayState(HOME_A, dev.id)?.get("online"))
        @Suppress("UNCHECKED_CAST")
        val offlineValues = repo.getLatestGatewayState(HOME_A, dev.id)?.get("values") as Map<String, Any>
        assertEquals(true, offlineValues["light"])
        assertEquals(GatewaySyncPhase.PAUSED_LAN_OFFLINE, sync.gatewaySync.value.phase)

        // Further offline updates do NOT spam online=false
        advanceTimeBy(5_000L)
        inputFlow.value = inputFlow.value.copy(temperature = 28)
        runCurrent()
        assertEquals(4, repo.publishGatewayCalls.get())
    }

    @Test
    fun eventsSync_usesCursors_sortsAscending_capsAt20PerCycle_dedupsAlreadyExists_andStopsOnError() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        val dev = CloudDevice("dev_evt_0001", "ESP", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_A)
        repo.seedDevice(HOME_A, dev)
        repo.seedConfig(HOME_A, dev.id, CloudConfigState(1L, 35, 80, 18, true))

        // Pre-existing events 1..2 in repo -> cursor initialized at 2
        for (seq in 1L..2L) {
            val (eid, payload) = CloudMapper.buildEventPayload(
                GatewayEventInput.fromDeviceEvent(makeEvent(seq)),
                UID_A,
                BASE_TIME_MS + seq
            )
            repo.seedEventRecord(HOME_A, dev.id, eid, payload)
        }

        val cloudFlow = MutableStateFlow(ownerCloudState())
        val inputFlow = MutableStateFlow(GatewayInputSnapshot(esp32Online = false))
        val sync = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = cloudFlow,
            gatewayInputFlow = inputFlow,
            deviceRepository = repo,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()
        assertEquals(1, repo.readEventCursorsCalls.get())

        // Seed seq=3 directly after init so publishEvent hits ALREADY_EXISTS and still advances cursor
        val (id3, payload3) = CloudMapper.buildEventPayload(
            GatewayEventInput.fromDeviceEvent(makeEvent(3L)),
            UID_A,
            BASE_TIME_MS + 3L
        )
        repo.seedEventRecord(HOME_A, dev.id, id3, payload3)

        // Provide events 1..25 newest-first (as SmartHomeState stores them)
        val eventsReversed = (25L downTo 1L).map { makeEvent(it) }
        inputFlow.value = GatewayInputSnapshot(
            esp32Online = true,
            lastSeen = BASE_TIME_MS + testScheduler.currentTime,
            recentEvents = eventsReversed
        )
        runCurrent()

        // First cycle pushes max 20 events (seq 3..22) in ascending order
        assertEquals(20, repo.publishEventCalls.get())
        assertEquals(22, repo.getEvents(HOME_A, dev.id).size)
        val cloudStream = CloudMapper.toCloudStream(RAW_STREAM)
        assertTrue(repo.getEvents(HOME_A, dev.id).containsKey(RtdbPaths.eventId(cloudStream, 22L)))
        assertFalse(repo.getEvents(HOME_A, dev.id).containsKey(RtdbPaths.eventId(cloudStream, 23L)))

        // Next cycle pushes remaining 3 events (seq 23..25)
        advanceTimeBy(1_000L)
        inputFlow.value = inputFlow.value.copy(lastSeen = BASE_TIME_MS + testScheduler.currentTime)
        runCurrent()
        assertEquals(23, repo.publishEventCalls.get())
        assertEquals(25, repo.getEvents(HOME_A, dev.id).size)

        // Error on seq=27 stops batch right before 27 (26 written, 27 failed, 28 not attempted)
        val deniedEvent27 = RtdbPaths.eventId(cloudStream, 27L)
        repo.denyPathWithoutData(RtdbPaths.deviceEventPath(HOME_A, dev.id, deniedEvent27))
        advanceTimeBy(1_000L)
        inputFlow.value = inputFlow.value.copy(
            lastSeen = BASE_TIME_MS + testScheduler.currentTime,
            recentEvents = (28L downTo 20L).map { makeEvent(it) }
        )
        runCurrent()

        assertEquals(GatewaySyncPhase.ERROR, sync.gatewaySync.value.phase)
        assertEquals(CloudError.PermissionDenied, sync.gatewaySync.value.lastError)
        assertTrue(repo.getEvents(HOME_A, dev.id).containsKey(RtdbPaths.eventId(cloudStream, 26L)))
        assertFalse(repo.getEvents(HOME_A, dev.id).containsKey(deniedEvent27))
        assertFalse(repo.getEvents(HOME_A, dev.id).containsKey(RtdbPaths.eventId(cloudStream, 28L)))

        // Hard error must NOT auto-retry over time
        val callsAtError = repo.publishEventCalls.get()
        advanceTimeBy(120_000L)
        runCurrent()
        assertEquals(callsAtError, repo.publishEventCalls.get())

        // Clearing error path and calling retrySync() resumes from cursor 26 -> writes 27 and 28
        repo.clearDeniedPath(RtdbPaths.deviceEventPath(HOME_A, dev.id, deniedEvent27))
        inputFlow.value = inputFlow.value.copy(lastSeen = BASE_TIME_MS + testScheduler.currentTime)
        sync.retrySync()
        runCurrent()

        assertEquals(GatewaySyncPhase.OK, sync.gatewaySync.value.phase)
        assertTrue(repo.getEvents(HOME_A, dev.id).containsKey(deniedEvent27))
        assertTrue(repo.getEvents(HOME_A, dev.id).containsKey(RtdbPaths.eventId(cloudStream, 28L)))
        assertEquals(28, repo.getEvents(HOME_A, dev.id).size)
    }

    @Test
    fun telemetrySync_pushesLatestSampleOnlyOncePerUtcMinute_andHandlesAlreadyExists() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        val dev = CloudDevice("dev_tel_0001", "ESP", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_A)
        repo.seedDevice(HOME_A, dev)
        repo.seedConfig(HOME_A, dev.id, CloudConfigState(1L, 35, 80, 18, true))

        val minute1Sample = SensorSample(timestamp = 1_795_305_570_000L, temperature = 28, humidity = 62) // 202611212359_g
        val inputFlow = MutableStateFlow(
            GatewayInputSnapshot(
                esp32Online = true,
                lastSeen = BASE_TIME_MS,
                latestSample = minute1Sample
            )
        )
        val sync = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = MutableStateFlow(ownerCloudState()),
            gatewayInputFlow = inputFlow,
            deviceRepository = repo,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()

        assertEquals(1, repo.publishTelemetryCalls.get())
        assertEquals(setOf("202611212359_g"), repo.getTelemetryRecords(HOME_A, dev.id).keys)
        assertEquals(GatewaySyncPhase.OK, sync.gatewaySync.value.phase)

        // Another sample in the same UTC minute does not publish again
        advanceTimeBy(15_000L)
        inputFlow.value = inputFlow.value.copy(
            lastSeen = BASE_TIME_MS + testScheduler.currentTime,
            latestSample = SensorSample(timestamp = 1_795_305_585_000L, temperature = 29, humidity = 63)
        )
        runCurrent()
        assertEquals(1, repo.publishTelemetryCalls.get())

        // Pre-seed next minute record to verify ALREADY_EXISTS updates lastPublishedTelemetryId cleanly
        val minute2Sample = SensorSample(timestamp = 1_795_305_605_000L, temperature = 30, humidity = 64) // 202611220000_g
        val (rec2, payload2) = CloudMapper.buildTelemetryPayload(minute2Sample, UID_A, BASE_TIME_MS)!!
        repo.seedTelemetryRecord(HOME_A, dev.id, rec2, payload2)

        advanceTimeBy(20_000L)
        inputFlow.value = inputFlow.value.copy(
            lastSeen = BASE_TIME_MS + testScheduler.currentTime,
            latestSample = minute2Sample
        )
        runCurrent()
        assertEquals(2, repo.publishTelemetryCalls.get())
        assertEquals(GatewaySyncPhase.OK, sync.gatewaySync.value.phase)

        // Re-emitting same minute2 sample does not call publishTelemetry again
        advanceTimeBy(1_000L)
        inputFlow.value = inputFlow.value.copy(lastSeen = BASE_TIME_MS + testScheduler.currentTime)
        runCurrent()
        assertEquals(2, repo.publishTelemetryCalls.get())
    }

    @Test
    fun configSync_skipsUnchanged_retriesOnceOnRevConflict_andStopsOnPersistentDenial() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        val dev = CloudDevice("dev_cfg_0001", "ESP", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_A)
        repo.seedDevice(HOME_A, dev)
        repo.seedConfig(HOME_A, dev.id, CloudConfigState(rev = 1L, tempLimit = 35, humLimit = 80, coldLimit = 18, autoAlarm = true))

        val inputFlow = MutableStateFlow(
            GatewayInputSnapshot(
                esp32Online = true,
                lastSeen = BASE_TIME_MS,
                temperatureLimit = 35,
                humidityLimit = 80,
                coldLimit = 18,
                autoAlarm = true
            )
        )
        val sync = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = MutableStateFlow(ownerCloudState()),
            gatewayInputFlow = inputFlow,
            deviceRepository = repo,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()

        // Unchanged config does not write
        assertEquals(1, repo.readConfigCalls.get())
        assertEquals(0, repo.publishConfigCalls.get())

        // Simulate concurrent rev advance to rev=2 on RTDB, then change local temperatureLimit -> 40
        repo.seedConfig(HOME_A, dev.id, CloudConfigState(rev = 2L, tempLimit = 35, humLimit = 80, coldLimit = 18, autoAlarm = true))
        advanceTimeBy(2_000L)
        inputFlow.value = inputFlow.value.copy(
            lastSeen = BASE_TIME_MS + testScheduler.currentTime,
            temperatureLimit = 40
        )
        runCurrent()

        // Attempt 1 (rev=2 -> denied), reread (rev=2), retry (rev=3 -> OK)
        assertEquals(2, repo.readConfigCalls.get())
        assertEquals(2, repo.publishConfigCalls.get())
        assertEquals(3L, repo.getConfig(HOME_A, dev.id)?.rev)
        assertEquals(40, repo.getConfig(HOME_A, dev.id)?.tempLimit)
        assertEquals(GatewaySyncPhase.OK, sync.gatewaySync.value.phase)

        // Persistent PermissionDenied stops after 1 reread + 1 retry
        val repo2 = FakeCloudDeviceRepository(initialUid = UID_A)
        val dev2 = CloudDevice("dev_cfg_0002", "ESP", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_A)
        repo2.seedDevice(HOME_A, dev2)
        val alwaysDeniedConfigRepo = object : CloudDeviceRepository by repo2 {
            override suspend fun publishConfig(
                expectedUid: String,
                homeId: String,
                deviceId: String,
                payload: Map<String, Any>
            ): CloudResult<Unit> {
                repo2.publishConfigCalls.incrementAndGet()
                return CloudResult.Err(CloudError.PermissionDenied, "Config write denied")
            }
        }
        val sync2 = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = MutableStateFlow(ownerCloudState()),
            gatewayInputFlow = MutableStateFlow(GatewayInputSnapshot(esp32Online = true, lastSeen = BASE_TIME_MS)),
            deviceRepository = alwaysDeniedConfigRepo,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()

        assertEquals(2, repo2.readConfigCalls.get())
        assertEquals(2, repo2.publishConfigCalls.get())
        assertEquals(GatewaySyncPhase.ERROR, sync2.gatewaySync.value.phase)
        assertEquals(CloudError.PermissionDenied, sync2.gatewaySync.value.lastError)
    }

    @Test
    fun retryBackoff_appliesBounded5sTo60sSequence_resetsOnSuccess_andHaltsOnHardErrors() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        val dev = CloudDevice("dev_bo_0001", "ESP", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_A)
        repo.seedDevice(HOME_A, dev)
        repo.seedConfig(HOME_A, dev.id, CloudConfigState(1L, 35, 80, 18, true))

        val inputFlow = MutableStateFlow(GatewayInputSnapshot(esp32Online = false))
        val sync = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = MutableStateFlow(ownerCloudState()),
            gatewayInputFlow = inputFlow,
            deviceRepository = repo,
            timeProvider = { BASE_TIME_MS + testScheduler.currentTime }
        )
        runCurrent()
        assertEquals(GatewaySyncPhase.PAUSED_LAN_OFFLINE, sync.gatewaySync.value.phase)

        fun now(): Long = BASE_TIME_MS + testScheduler.currentTime

        // Failure #1 -> Offline, schedules 5_000L
        repo.failNext(CloudError.Offline, "No WAN")
        inputFlow.value = GatewayInputSnapshot(esp32Online = true, lastSeen = now(), light = false)
        runCurrent()
        assertEquals(1, repo.publishGatewayCalls.get())
        assertEquals(GatewaySyncPhase.ERROR, sync.gatewaySync.value.phase)
        assertEquals(CloudError.Offline, sync.gatewaySync.value.lastError)

        // Expected backoff sequence: 5s, 10s, 20s, 40s, 60s, 60s
        val expectedDelays = listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L)
        expectedDelays.forEachIndexed { index, delayMs ->
            val beforeCalls = repo.publishGatewayCalls.get()
            advanceTimeBy(delayMs - 1L)
            runCurrent()
            assertEquals("Retried too early at step $index", beforeCalls, repo.publishGatewayCalls.get())

            // Fail all except final recovery step
            if (index < expectedDelays.lastIndex) {
                val err = if (index == 1) CloudError.Timeout else CloudError.Offline
                repo.failNext(err, "Retry failed $index")
            }
            advanceTimeBy(1L)
            runCurrent()
            assertEquals(beforeCalls + 1, repo.publishGatewayCalls.get())
        }

        // Last step succeeded -> phase OK & backoff index reset to 0
        assertEquals(GatewaySyncPhase.OK, sync.gatewaySync.value.phase)
        assertNull(sync.gatewaySync.value.lastError)

        // Verify backoff index reset to 5_000L on next Offline
        advanceTimeBy(2_000L)
        repo.failNext(CloudError.Offline, "Offline again")
        inputFlow.value = inputFlow.value.copy(lastSeen = now(), light = true)
        runCurrent()
        val callsAtNewError = repo.publishGatewayCalls.get()
        assertEquals(GatewaySyncPhase.ERROR, sync.gatewaySync.value.phase)

        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(callsAtNewError, repo.publishGatewayCalls.get())
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(callsAtNewError + 1, repo.publishGatewayCalls.get())
        assertEquals(GatewaySyncPhase.OK, sync.gatewaySync.value.phase)

        // Hard error (InvalidData) stops auto-retry until manual retrySync()
        advanceTimeBy(2_000L)
        repo.failNext(CloudError.InvalidData, "Bad payload")
        inputFlow.value = inputFlow.value.copy(lastSeen = now(), doorOpen = true)
        runCurrent()
        assertEquals(GatewaySyncPhase.ERROR, sync.gatewaySync.value.phase)
        assertEquals(CloudError.InvalidData, sync.gatewaySync.value.lastError)

        val callsAtHardError = repo.publishGatewayCalls.get()
        advanceTimeBy(120_000L)
        runCurrent()
        assertEquals(callsAtHardError, repo.publishGatewayCalls.get())

        inputFlow.value = inputFlow.value.copy(lastSeen = now())
        sync.retrySync()
        runCurrent()
        assertEquals(GatewaySyncPhase.OK, sync.gatewaySync.value.phase)
        assertEquals(callsAtHardError + 1, repo.publishGatewayCalls.get())
    }

    @Test
    fun smartHomeStateToGatewayInput_mapsValuesCleanlyWithoutLeakingLocalSecrets() {
        val oldSample = SensorSample(1_795_305_500_000L, 25, 60)
        val latestSample = SensorSample(1_795_305_570_000L, 29, 65)
        val ev = makeEvent(10L)
        val state = SmartHomeState(
            esp32Online = true,
            lastSeen = BASE_TIME_MS,
            esp32Ip = "192.168.1.99",
            deviceKey = "super_secret_lan_key",
            light = true,
            doorOpen = false,
            alarm = true,
            motionDetected = true,
            monitoring = true,
            mode = HomeMode.AWAY,
            temperature = 29,
            humidity = 65,
            temperatureLimit = 36,
            humidityLimit = 82,
            coldLimit = 16,
            autoAlarm = false,
            samples = listOf(oldSample, latestSample),
            deviceEvents = listOf(ev)
        )

        val snapshot = state.toGatewayInput()
        assertTrue(snapshot.esp32Online)
        assertEquals(BASE_TIME_MS, snapshot.lastSeen)
        assertTrue(snapshot.light)
        assertFalse(snapshot.doorOpen)
        assertTrue(snapshot.alarm)
        assertTrue(snapshot.motionDetected)
        assertTrue(snapshot.monitoring)
        assertEquals(HomeMode.AWAY, snapshot.mode)
        assertEquals(29, snapshot.temperature)
        assertEquals(65, snapshot.humidity)
        assertEquals(36, snapshot.temperatureLimit)
        assertEquals(82, snapshot.humidityLimit)
        assertEquals(16, snapshot.coldLimit)
        assertFalse(snapshot.autoAlarm)
        assertEquals(latestSample, snapshot.latestSample)
        assertEquals(listOf(ev), snapshot.recentEvents)

        val serialized = snapshot.toString()
        assertFalse(serialized.contains("super_secret_lan_key"))
        assertFalse(serialized.contains("192.168.1.99"))
    }
}
