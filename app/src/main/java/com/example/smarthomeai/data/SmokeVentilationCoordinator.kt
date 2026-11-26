package com.example.smarthomeai.data

/** Plan returned by [SmokeVentilationCoordinator] after processing a sensor snapshot. */
data class AutoVentilationPlan(
    val alertTitle: String? = null,
    val openWindow: Boolean = false,
    val turnOnFan: Boolean = false
) {
    val hasCommands: Boolean get() = openWindow || turnOnFan
}

/** Validation outcome for manual window or fan control requests. */
sealed class ManualVentilationDecision {
    data object Allowed : ManualVentilationDecision()
    data class Rejected(val reason: String) : ManualVentilationDecision()
}

/**
 * Pure Kotlin coordinator for MQ-2 smoke alerts, dual-layer auto-ventilation
 * (window + fan), event deduplication, and manual safety interlocks.
 */
class SmokeVentilationCoordinator {
    companion object {
        const val AUTO_DISPATCH_COOLDOWN_MS = 5_000L
        const val SAFETY_LOCK_MESSAGE = "Đang có khói vượt ngưỡng: giữ cửa sổ và quạt mở để thoát khói."
        const val AWAY_LOCK_MESSAGE = "Chuyển về Ở nhà trước khi điều khiển cửa sổ và quạt."
        const val MISSING_WINDOW_MESSAGE = "ESP32 chưa kết nối phần cứng cửa sổ tự động."
        const val MISSING_FAN_MESSAGE = "ESP32 chưa kết nối phần cứng quạt thông gió."
    }

    private val safetyGate = SmokeSafetyGate()
    private var lastAutoDispatchMs: Long = 0L
    private var hasAutoDispatched: Boolean = false
    private var eventAlertedInCurrentEpisode: Boolean = false
    private var flagAlertedInCurrentEpisode: Boolean = false

    val hasEventAlertInCurrentEpisode: Boolean
        get() = eventAlertedInCurrentEpisode

    fun onSensorUpdated(
        state: SmartHomeState,
        incomingHardware: HardwareStatus?,
        elapsedMs: Long
    ): AutoVentilationPlan {
        val limit = state.smokeLimit.coerceIn(MIN_SMOKE_LIMIT_PPM, MAX_SMOKE_LIMIT_PPM)
        val ppm = incomingHardware?.smokePpm?.takeIf { it in 0..MAX_SMOKE_PPM }
        val rearmThreshold = (limit - SMOKE_HYSTERESIS_PPM).coerceAtLeast(0)
        val flagHazard = incomingHardware?.smokeAlert == true

        if (!flagHazard && ppm != null && ppm < rearmThreshold) {
            eventAlertedInCurrentEpisode = false
            flagAlertedInCurrentEpisode = false
            hasAutoDispatched = false
            lastAutoDispatchMs = 0L
        } else if (!flagHazard && ppm == null && incomingHardware?.smokeAlert == false) {
            flagAlertedInCurrentEpisode = false
            if (!safetyGate.isActive) {
                eventAlertedInCurrentEpisode = false
                hasAutoDispatched = false
                lastAutoDispatchMs = 0L
            }
        }

        val gateAlert = safetyGate.evaluate(
            enabled = state.smokeAlertsEnabled,
            smokePpm = ppm,
            limitPpm = limit
        )

        val hazard = (ppm != null && ppm >= limit) || flagHazard
        val effectiveAuto = incomingHardware?.smokeAutoEnabled ?: state.effectiveSmokeAuto
        val hasVentHardware = incomingHardware?.hasWindow == true || incomingHardware?.hasFan == true

        val rawTriggered = when {
            !state.smokeAlertsEnabled -> false
            gateAlert != null -> {
                flagAlertedInCurrentEpisode = true
                true
            }
            flagHazard && !flagAlertedInCurrentEpisode && !safetyGate.isActive -> {
                flagAlertedInCurrentEpisode = true
                true
            }
            else -> false
        }

        val alertTitle = if (rawTriggered && !eventAlertedInCurrentEpisode) {
            val ppmText = ppm?.let { " · $it ppm" } ?: ""
            val autoSuffix = if (effectiveAuto && hasVentHardware) " · Tự động mở cửa sổ & bật quạt" else ""
            "Cảnh báo khói vượt ngưỡng$ppmText$autoSuffix"
        } else {
            null
        }

        if (!hazard || !effectiveAuto) {
            return AutoVentilationPlan(alertTitle = alertTitle)
        }

        val needsWindowOpen = incomingHardware?.hasWindow == true && incomingHardware.windowOpen == false
        val needsFanOn = incomingHardware?.hasFan == true && incomingHardware.fanOn == false
        val cooldownReady = !hasAutoDispatched || (elapsedMs - lastAutoDispatchMs >= AUTO_DISPATCH_COOLDOWN_MS)

        val shouldOpenWindow = needsWindowOpen && cooldownReady
        val shouldTurnOnFan = needsFanOn && cooldownReady

        if (shouldOpenWindow || shouldTurnOnFan) {
            hasAutoDispatched = true
            lastAutoDispatchMs = elapsedMs
        }

        return AutoVentilationPlan(
            alertTitle = alertTitle,
            openWindow = shouldOpenWindow,
            turnOnFan = shouldTurnOnFan
        )
    }

    fun onSmokeEventsSynced(eventKinds: Collection<String>) {
        if (eventKinds.any { it == "smoke_detected" || it == "smoke_auto_vent" }) {
            eventAlertedInCurrentEpisode = true
        }
    }

    fun evaluateWindowCommand(state: SmartHomeState, targetOpen: Boolean): ManualVentilationDecision {
        val hw = state.hardware
        if (hw?.hasWindow != true) {
            return ManualVentilationDecision.Rejected(MISSING_WINDOW_MESSAGE)
        }
        val limit = state.smokeLimit.coerceIn(MIN_SMOKE_LIMIT_PPM, MAX_SMOKE_LIMIT_PPM)
        val hazard = hw.smokeAlert == true || (hw.smokePpm != null && hw.smokePpm >= limit)
        val safetyLocked = hazard && (hw.smokeAutoEnabled ?: state.smokeAutoVentilation)
        if (!targetOpen && safetyLocked) {
            return ManualVentilationDecision.Rejected(SAFETY_LOCK_MESSAGE)
        }
        if (!hazard && (state.mode == HomeMode.AWAY || state.security?.protecting == true)) {
            return ManualVentilationDecision.Rejected(AWAY_LOCK_MESSAGE)
        }
        return ManualVentilationDecision.Allowed
    }

    fun evaluateFanCommand(state: SmartHomeState, targetOn: Boolean): ManualVentilationDecision {
        val hw = state.hardware
        if (hw?.hasFan != true) {
            return ManualVentilationDecision.Rejected(MISSING_FAN_MESSAGE)
        }
        val limit = state.smokeLimit.coerceIn(MIN_SMOKE_LIMIT_PPM, MAX_SMOKE_LIMIT_PPM)
        val hazard = hw.smokeAlert == true || (hw.smokePpm != null && hw.smokePpm >= limit)
        val safetyLocked = hazard && (hw.smokeAutoEnabled ?: state.smokeAutoVentilation)
        if (!targetOn && safetyLocked) {
            return ManualVentilationDecision.Rejected(SAFETY_LOCK_MESSAGE)
        }
        if (!hazard && (state.mode == HomeMode.AWAY || state.security?.protecting == true)) {
            return ManualVentilationDecision.Rejected(AWAY_LOCK_MESSAGE)
        }
        return ManualVentilationDecision.Allowed
    }

    fun reset() {
        safetyGate.reset()
        lastAutoDispatchMs = 0L
        hasAutoDispatched = false
        eventAlertedInCurrentEpisode = false
        flagAlertedInCurrentEpisode = false
    }
}
