package com.example.smarthomeai.data

import org.junit.Assert.*
import org.junit.Test

class SmokeVentilationCoordinatorTest {
    private fun makeHardware(
        smokePpm: Int? = 100,
        smokeAlert: Boolean? = null,
        hasWindow: Boolean = true,
        windowOpen: Boolean? = false,
        hasFan: Boolean = true,
        fanOn: Boolean? = false,
        smokeAutoEnabled: Boolean? = null
    ) = HardwareStatus(
        profile = "mq2-ventilation",
        hasMq2 = true,
        smokePpm = smokePpm,
        smokeAlert = smokeAlert,
        hasWindow = hasWindow,
        windowOpen = windowOpen,
        hasFan = hasFan,
        fanOn = fanOn,
        smokeAutoEnabled = smokeAutoEnabled
    )

    private fun makeState(
        mode: HomeMode = HomeMode.HOME,
        protecting: Boolean = false,
        alertsEnabled: Boolean = true,
        smokeLimit: Int = 300,
        smokeAutoVentilation: Boolean = true,
        hardware: HardwareStatus? = makeHardware()
    ) = SmartHomeState(
        esp32Online = true,
        mode = mode,
        security = if (protecting || mode != HomeMode.HOME) {
            SecurityStatus(
                mode = mode,
                phase = if (protecting) SecurityPhase.ARMED else SecurityPhase.DISARMED,
                remaining = 0,
                stream = "s1",
                boot = 1,
                incident = 0
            )
        } else null,
        smokeAlertsEnabled = alertsEnabled,
        smokeLimit = smokeLimit,
        smokeAutoVentilation = smokeAutoVentilation,
        hardware = hardware
    )

    @Test
    fun belowThreshold299PpmProducesNoAlertAndNoCommands() {
        val coordinator = SmokeVentilationCoordinator()
        val hw = makeHardware(smokePpm = 299, windowOpen = false, fanOn = false)
        val plan = coordinator.onSensorUpdated(makeState(hardware = hw), hw, elapsedMs = 1_000L)

        assertNull(plan.alertTitle)
        assertFalse(plan.openWindow)
        assertFalse(plan.turnOnFan)
        assertFalse(plan.hasCommands)
    }

    @Test
    fun reaches300PpmWithClosedWindowAndOffFanTriggersAlertAndBothCommands() {
        val coordinator = SmokeVentilationCoordinator()
        val hw = makeHardware(smokePpm = 300, windowOpen = false, fanOn = false)
        val plan = coordinator.onSensorUpdated(makeState(hardware = hw), hw, elapsedMs = 1_000L)

        assertNotNull(plan.alertTitle)
        assertTrue(plan.alertTitle!!.contains("300 ppm"))
        assertTrue(plan.openWindow)
        assertTrue(plan.turnOnFan)
        assertTrue(plan.hasCommands)
    }

    @Test
    fun reaches320PpmWhenFirmwareAlreadyOpenedWindowAndFanAlertsOnceWithoutRedundantCommands() {
        val coordinator = SmokeVentilationCoordinator()
        val hw = makeHardware(smokePpm = 320, smokeAlert = true, windowOpen = true, fanOn = true)
        val state = makeState(hardware = hw)

        val firstPlan = coordinator.onSensorUpdated(state, hw, elapsedMs = 1_000L)
        assertNotNull(firstPlan.alertTitle)
        assertFalse(firstPlan.openWindow)
        assertFalse(firstPlan.turnOnFan)
        assertFalse(firstPlan.hasCommands)

        val secondPlan = coordinator.onSensorUpdated(state, hw, elapsedMs = 2_000L)
        assertNull(secondPlan.alertTitle)
        assertFalse(secondPlan.hasCommands)
    }

    @Test
    fun smoke350PpmDuringAwayProtectedModeStillDispatchesAutoVentilation() {
        val coordinator = SmokeVentilationCoordinator()
        val hw = makeHardware(smokePpm = 350, windowOpen = false, fanOn = false)
        val state = makeState(mode = HomeMode.AWAY, protecting = true, hardware = hw)

        val plan = coordinator.onSensorUpdated(state, hw, elapsedMs = 1_000L)
        assertNotNull(plan.alertTitle)
        assertTrue(plan.openWindow)
        assertTrue(plan.turnOnFan)
    }

    @Test
    fun autoDispatchRespects5000MsCooldownBeforeRetrying() {
        val coordinator = SmokeVentilationCoordinator()
        val hw = makeHardware(smokePpm = 330, windowOpen = false, fanOn = false)
        val state = makeState(hardware = hw)

        val first = coordinator.onSensorUpdated(state, hw, elapsedMs = 1_000L)
        assertTrue(first.openWindow)
        assertTrue(first.turnOnFan)

        val withinCooldown = coordinator.onSensorUpdated(state, hw, elapsedMs = 2_000L)
        assertNull(withinCooldown.alertTitle)
        assertFalse(withinCooldown.openWindow)
        assertFalse(withinCooldown.turnOnFan)

        val stillWithinCooldown = coordinator.onSensorUpdated(state, hw, elapsedMs = 5_999L)
        assertFalse(stillWithinCooldown.openWindow)
        assertFalse(stillWithinCooldown.turnOnFan)

        val afterCooldown = coordinator.onSensorUpdated(state, hw, elapsedMs = 6_000L)
        assertNull(afterCooldown.alertTitle)
        assertTrue(afterCooldown.openWindow)
        assertTrue(afterCooldown.turnOnFan)
    }

    @Test
    fun preventsDuplicateAlertWhenSmokeEventsAlreadySyncedInCurrentEpisode() {
        val coordinator = SmokeVentilationCoordinator()
        coordinator.onSmokeEventsSynced(listOf("smoke_detected", "smoke_auto_vent"))
        assertTrue(coordinator.hasEventAlertInCurrentEpisode)

        val hw = makeHardware(smokePpm = 340, windowOpen = true, fanOn = true)
        val plan = coordinator.onSensorUpdated(makeState(hardware = hw), hw, elapsedMs = 1_000L)

        assertNull(plan.alertTitle)
        assertFalse(plan.hasCommands)
    }

    @Test
    fun hysteresisPreventsRearmAt270PpmAndRearmsBelow250Ppm() {
        val coordinator = SmokeVentilationCoordinator()

        val hwHigh = makeHardware(smokePpm = 340, windowOpen = true, fanOn = true)
        assertNotNull(coordinator.onSensorUpdated(makeState(hardware = hwHigh), hwHigh, 1_000L).alertTitle)

        // 270 ppm is still within [250..300) hysteresis band: should not re-arm
        val hwMid = makeHardware(smokePpm = 270, windowOpen = true, fanOn = true)
        assertNull(coordinator.onSensorUpdated(makeState(hardware = hwMid), hwMid, 2_000L).alertTitle)

        val hwSpikeBeforeRearm = makeHardware(smokePpm = 305, windowOpen = true, fanOn = true)
        assertNull(coordinator.onSensorUpdated(makeState(hardware = hwSpikeBeforeRearm), hwSpikeBeforeRearm, 3_000L).alertTitle)

        // 240 ppm < (300 - 50): re-arms episode
        val hwClear = makeHardware(smokePpm = 240, windowOpen = true, fanOn = true)
        assertNull(coordinator.onSensorUpdated(makeState(hardware = hwClear), hwClear, 4_000L).alertTitle)

        // Rising back to 305 ppm triggers a fresh alert episode
        val hwNewEpisode = makeHardware(smokePpm = 305, windowOpen = true, fanOn = true)
        val newPlan = coordinator.onSensorUpdated(makeState(hardware = hwNewEpisode), hwNewEpisode, 5_000L)
        assertNotNull(newPlan.alertTitle)
        assertTrue(newPlan.alertTitle!!.contains("305 ppm"))
    }

    @Test
    fun blocksClosingWindowAndTurningOffFanDuringSmokeHazardWhileAllowingOpening() {
        val coordinator = SmokeVentilationCoordinator()
        val hw = makeHardware(smokePpm = 340, smokeAlert = true, windowOpen = false, fanOn = false)
        val state = makeState(smokeAutoVentilation = true, hardware = hw)
        assertTrue(state.ventilationSafetyLocked)

        val closeWindow = coordinator.evaluateWindowCommand(state, targetOpen = false)
        assertTrue(closeWindow is ManualVentilationDecision.Rejected)
        assertEquals(SmokeVentilationCoordinator.SAFETY_LOCK_MESSAGE, (closeWindow as ManualVentilationDecision.Rejected).reason)

        val turnOffFan = coordinator.evaluateFanCommand(state, targetOn = false)
        assertTrue(turnOffFan is ManualVentilationDecision.Rejected)
        assertEquals(SmokeVentilationCoordinator.SAFETY_LOCK_MESSAGE, (turnOffFan as ManualVentilationDecision.Rejected).reason)

        assertEquals(ManualVentilationDecision.Allowed, coordinator.evaluateWindowCommand(state, targetOpen = true))
        assertEquals(ManualVentilationDecision.Allowed, coordinator.evaluateFanCommand(state, targetOn = true))
    }

    @Test
    fun clearAir120PpmAllowsManualControlInHomeAndBlocksInAwayMode() {
        val coordinator = SmokeVentilationCoordinator()
        val hw = makeHardware(smokePpm = 120, smokeAlert = false, windowOpen = true, fanOn = true)

        val homeState = makeState(mode = HomeMode.HOME, protecting = false, hardware = hw)
        assertEquals(ManualVentilationDecision.Allowed, coordinator.evaluateWindowCommand(homeState, targetOpen = false))
        assertEquals(ManualVentilationDecision.Allowed, coordinator.evaluateFanCommand(homeState, targetOn = false))

        val awayState = makeState(mode = HomeMode.AWAY, protecting = true, hardware = hw)
        val awayWindow = coordinator.evaluateWindowCommand(awayState, targetOpen = false)
        assertTrue(awayWindow is ManualVentilationDecision.Rejected)
        assertEquals(SmokeVentilationCoordinator.AWAY_LOCK_MESSAGE, (awayWindow as ManualVentilationDecision.Rejected).reason)

        val awayFan = coordinator.evaluateFanCommand(awayState, targetOn = false)
        assertTrue(awayFan is ManualVentilationDecision.Rejected)
        assertEquals(SmokeVentilationCoordinator.AWAY_LOCK_MESSAGE, (awayFan as ManualVentilationDecision.Rejected).reason)
    }

    @Test
    fun rejectsManualControlWhenHardwareLacksWindowOrFan() {
        val coordinator = SmokeVentilationCoordinator()
        val hw = makeHardware(smokePpm = 100, hasWindow = false, windowOpen = null, hasFan = false, fanOn = null)
        val state = makeState(hardware = hw)

        val windowDecision = coordinator.evaluateWindowCommand(state, targetOpen = true)
        assertTrue(windowDecision is ManualVentilationDecision.Rejected)
        assertEquals(SmokeVentilationCoordinator.MISSING_WINDOW_MESSAGE, (windowDecision as ManualVentilationDecision.Rejected).reason)

        val fanDecision = coordinator.evaluateFanCommand(state, targetOn = true)
        assertTrue(fanDecision is ManualVentilationDecision.Rejected)
        assertEquals(SmokeVentilationCoordinator.MISSING_FAN_MESSAGE, (fanDecision as ManualVentilationDecision.Rejected).reason)
    }

    @Test
    fun nullSmokePpmAndNullAlertNeverTriggerFalseAlarmAndResetClearsState() {
        val coordinator = SmokeVentilationCoordinator()
        val hwNoReading = makeHardware(smokePpm = null, smokeAlert = null, windowOpen = false, fanOn = false)
        val noAlertPlan = coordinator.onSensorUpdated(makeState(hardware = hwNoReading), hwNoReading, elapsedMs = 1_000L)
        assertNull(noAlertPlan.alertTitle)
        assertFalse(noAlertPlan.hasCommands)

        coordinator.onSmokeEventsSynced(listOf("smoke_detected"))
        assertTrue(coordinator.hasEventAlertInCurrentEpisode)
        coordinator.reset()
        assertFalse(coordinator.hasEventAlertInCurrentEpisode)
    }

    @Test
    fun disablesPhoneAlertWhenSmokeAlertsDisabledButStillRunsAutoVentilation() {
        val coordinator = SmokeVentilationCoordinator()
        val hw = makeHardware(smokePpm = 350, windowOpen = false, fanOn = false)
        val state = makeState(alertsEnabled = false, smokeAutoVentilation = true, hardware = hw)

        val plan = coordinator.onSensorUpdated(state, hw, elapsedMs = 1_000L)
        assertNull(plan.alertTitle)
        assertTrue(plan.openWindow)
        assertTrue(plan.turnOnFan)
    }

    @Test
    fun smokeAlertFlagWithoutPpmTriggersOnceAndRearmsWhenFlagClears() {
        val coordinator = SmokeVentilationCoordinator()
        val hwFlag = makeHardware(smokePpm = null, smokeAlert = true, windowOpen = false, fanOn = false)
        val state = makeState(hardware = hwFlag)

        val first = coordinator.onSensorUpdated(state, hwFlag, elapsedMs = 1_000L)
        assertNotNull(first.alertTitle)
        assertTrue(first.openWindow)
        assertTrue(first.turnOnFan)

        val second = coordinator.onSensorUpdated(state, hwFlag, elapsedMs = 2_000L)
        assertNull(second.alertTitle)

        val hwCleared = makeHardware(smokePpm = null, smokeAlert = false, windowOpen = true, fanOn = true)
        assertNull(coordinator.onSensorUpdated(makeState(hardware = hwCleared), hwCleared, elapsedMs = 3_000L).alertTitle)

        val third = coordinator.onSensorUpdated(state, hwFlag, elapsedMs = 7_000L)
        assertNotNull(third.alertTitle)
    }

    @Test
    fun disablesAutoVentilationAllowsManualCloseDuringSmokeHazardAndNeverAutoClosesOnClear() {
        val coordinator = SmokeVentilationCoordinator()
        val hwHazard = makeHardware(smokePpm = 340, smokeAlert = true, windowOpen = true, fanOn = true, smokeAutoEnabled = false)
        val state = makeState(smokeAutoVentilation = false, hardware = hwHazard)

        val hazardPlan = coordinator.onSensorUpdated(state, hwHazard, elapsedMs = 1_000L)
        assertNotNull(hazardPlan.alertTitle)
        assertFalse(hazardPlan.alertTitle!!.contains("Tự động mở"))
        assertFalse(hazardPlan.openWindow)
        assertFalse(hazardPlan.turnOnFan)

        assertEquals(ManualVentilationDecision.Allowed, coordinator.evaluateWindowCommand(state, targetOpen = false))
        assertEquals(ManualVentilationDecision.Allowed, coordinator.evaluateFanCommand(state, targetOn = false))

        // Dropping below threshold never emits auto-close/off commands
        val hwClear = makeHardware(smokePpm = 120, smokeAlert = false, windowOpen = true, fanOn = true, smokeAutoEnabled = true)
        val clearPlan = coordinator.onSensorUpdated(makeState(smokeAutoVentilation = true, hardware = hwClear), hwClear, elapsedMs = 2_000L)
        assertNull(clearPlan.alertTitle)
        assertFalse(clearPlan.openWindow)
        assertFalse(clearPlan.turnOnFan)
    }

    @Test
    fun unknownWindowAndFanStateDuringHazardStillDispatchesAutoVentilation() {
        val coordinator = SmokeVentilationCoordinator()
        val hw = makeHardware(smokePpm = 340, windowOpen = null, fanOn = null)
        val plan = coordinator.onSensorUpdated(makeState(hardware = hw), hw, elapsedMs = 1_000L)

        assertNotNull(plan.alertTitle)
        assertTrue(plan.openWindow)
        assertTrue(plan.turnOnFan)
        assertTrue(plan.hasCommands)
    }

    @Test
    fun preventsDuplicateEventAlertWhenSensorAlreadyEmittedInCurrentEpisodeAndRearmsOnClear() {
        val coordinator = SmokeVentilationCoordinator()
        val hwHazard = makeHardware(smokePpm = 340, windowOpen = true, fanOn = true)
        val plan = coordinator.onSensorUpdated(makeState(hardware = hwHazard), hwHazard, elapsedMs = 1_000L)

        assertNotNull(plan.alertTitle)
        coordinator.onSensorAlertEmitted()
        assertTrue(coordinator.hasSensorAlertInCurrentEpisode)
        assertTrue(coordinator.shouldSuppressSmokeHazardEventAlert("smoke_detected"))
        assertTrue(coordinator.shouldSuppressSmokeHazardEventAlert("smoke_auto_vent"))
        assertFalse(coordinator.shouldSuppressSmokeHazardEventAlert("smoke_cleared"))

        val hwClear = makeHardware(smokePpm = 240, smokeAlert = false, windowOpen = true, fanOn = true)
        assertNull(coordinator.onSensorUpdated(makeState(hardware = hwClear), hwClear, elapsedMs = 2_000L).alertTitle)
        assertFalse(coordinator.hasSensorAlertInCurrentEpisode)
        assertFalse(coordinator.shouldSuppressSmokeHazardEventAlert("smoke_detected"))
    }
}
