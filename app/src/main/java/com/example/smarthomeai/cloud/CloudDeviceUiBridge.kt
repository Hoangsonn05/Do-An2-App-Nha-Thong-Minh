package com.example.smarthomeai.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

enum class GatewaySyncPhase {
    IDLE,
    WAITING_HOME,
    REGISTERING,
    SYNCING,
    OK,
    PAUSED_LAN_OFFLINE,
    BLOCKED,
    ERROR
}

data class GatewaySyncUiState(
    val phase: GatewaySyncPhase = GatewaySyncPhase.IDLE,
    val activeDeviceId: String? = null,
    val lastSyncAt: Long? = null,
    val warningMessage: String? = null,
    val lastError: CloudError? = null,
    val errorMessage: String? = null
)

/**
 * UI-facing bridge exposing Gateway-to-Cloud sync state and manual retry action
 * without leaking ViewModel, network Mutex, or Firebase SDK types.
 */
interface CloudDeviceUiBridge {
    val gatewaySync: StateFlow<GatewaySyncUiState>
    fun retrySync() = Unit
}

/**
 * Weakly-held registry connecting SmartHomeViewModel's CloudGatewaySync to Compose UI screens.
 */
object CloudDeviceUiBridgeRegistry {
    @Volatile
    private var activeRef: WeakReference<CloudDeviceUiBridge>? = null

    fun register(bridge: CloudDeviceUiBridge) {
        activeRef = WeakReference(bridge)
    }

    fun unregister(bridge: CloudDeviceUiBridge) {
        val current = activeRef?.get()
        if (current == null || current === bridge) {
            activeRef = null
        }
    }

    fun currentOrNull(): CloudDeviceUiBridge? = activeRef?.get()
}

@Composable
fun rememberActiveCloudDeviceBridge(): CloudDeviceUiBridge? =
    remember { CloudDeviceUiBridgeRegistry.currentOrNull() }
