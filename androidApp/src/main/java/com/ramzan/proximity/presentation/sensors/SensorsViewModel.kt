package com.ramzan.proximity.presentation.sensors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.GeoPoint
import com.ramzan.proximity.domain.model.NearbyDevice
import com.ramzan.proximity.domain.model.ProximitySignal
import com.ramzan.proximity.domain.model.SystemCapabilities
import com.ramzan.proximity.domain.model.TrackingState
import com.ramzan.proximity.domain.model.TrackingStatus
import com.ramzan.proximity.domain.usecase.ObserveCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.ObserveLocationUseCase
import com.ramzan.proximity.domain.usecase.ObserveNearbyDevicesUseCase
import com.ramzan.proximity.domain.usecase.ObserveTrackingStateUseCase
import com.ramzan.proximity.domain.usecase.RefreshCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.StartTrackingUseCase
import com.ramzan.proximity.domain.usecase.StopTrackingUseCase
import com.ramzan.proximity.presentation.SettingsTarget
import com.ramzan.proximity.presentation.UiEffect
import com.ramzan.proximity.presentation.displayName
import com.ramzan.proximity.presentation.toEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DeviceFilter { ALL, NEAR }

data class SensorsUiState(
    val tracking: TrackingState = TrackingState(),
    val capabilities: SystemCapabilities = SystemCapabilities(),
    val location: GeoPoint? = null,
    val devices: List<NearbyDevice> = emptyList(),
    val totalDevices: Int = 0,
    val filter: DeviceFilter = DeviceFilter.ALL,
    val query: String = "",
    val isStarting: Boolean = false,
)

class SensorsViewModel(
    observeTracking: ObserveTrackingStateUseCase,
    observeLocation: ObserveLocationUseCase,
    observeDevices: ObserveNearbyDevicesUseCase,
    observeCapabilities: ObserveCapabilitiesUseCase,
    private val refreshCapabilities: RefreshCapabilitiesUseCase,
    private val startTracking: StartTrackingUseCase,
    private val stopTracking: StopTrackingUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val filter = MutableStateFlow(
        savedState.get<String>(KEY_FILTER)?.let { runCatching { DeviceFilter.valueOf(it) }.getOrNull() }
            ?: DeviceFilter.ALL,
    )
    private val query = savedState.getStateFlow(KEY_QUERY, "")
    private var startJob: Job? = null
    private var startAfterPermissions = false
    private val effectChannel = Channel<UiEffect>(Channel.BUFFERED)

    val effects: Flow<UiEffect> = effectChannel.receiveAsFlow()

    private val visibleDevices = combine(observeDevices(), filter, query) { all, filter, query ->
        Triple(all, filter, query)
    }

    val uiState: StateFlow<SensorsUiState> = combine(
        observeTracking(),
        observeLocation(),
        observeCapabilities(),
        visibleDevices,
    ) { tracking, location, capabilities, (all, filter, query) ->
        SensorsUiState(
            tracking = tracking,
            capabilities = capabilities,
            location = location,
            devices = all.filter { it.matches(filter, query) },
            totalDevices = all.size,
            filter = filter,
            query = query,
            isStarting = tracking.status == TrackingStatus.STARTING,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SensorsUiState())

    fun onToggleTracking() {
        if (uiState.value.tracking.isRunning) {
            stopTracking()
            return
        }
        if (startJob?.isActive == true) return

        refreshCapabilities()
        val missing = PERMISSION_CAPABILITIES.filter {
            uiState.value.capabilities[it] == CapabilityStatus.PERMISSION_MISSING
        }.toSet()
        if (missing.isNotEmpty()) {
            startAfterPermissions = true
            send(UiEffect.RequestPermissions(missing))
        } else {
            beginTracking()
        }
    }

    /** Called after the system permission dialog closes; continues the start the user asked for. */
    fun onPermissionsResult(permanentlyDenied: Set<Capability> = emptySet()) {
        refreshCapabilities()
        if (permanentlyDenied.isNotEmpty()) {
            val names = permanentlyDenied.joinToString { it.displayName }
            send(UiEffect.ShowMessage("$names permission is blocked. Enable it in Settings.", SettingsTarget.APP))
        }
        val shouldStart = startAfterPermissions
        startAfterPermissions = false
        if (shouldStart && Capability.LOCATION !in permanentlyDenied) beginTracking()
    }

    fun onCapabilityClicked(capability: Capability) {
        when (uiState.value.capabilities[capability]) {
            CapabilityStatus.PERMISSION_MISSING -> send(UiEffect.RequestPermissions(setOf(capability)))
            CapabilityStatus.DISABLED -> send(UiEffect.OpenSettings(capability.settingsTarget()))
            CapabilityStatus.UNSUPPORTED ->
                send(UiEffect.ShowMessage("${capability.displayName} is not supported on this device"))
            CapabilityStatus.READY -> send(UiEffect.ShowMessage("${capability.displayName} is ready"))
        }
    }

    fun onCapabilitiesRefreshRequested() = refreshCapabilities()

    fun onFilterChanged(value: DeviceFilter) {
        filter.value = value
        savedState[KEY_FILTER] = value.name
    }

    fun onQueryChanged(value: String) {
        savedState[KEY_QUERY] = value
    }

    private fun beginTracking() {
        if (startJob?.isActive == true) return
        startJob = viewModelScope.launch {
            val result = startTracking()
            if (result is DomainResult.Failure) send(result.error.toEffect())
        }
    }

    private fun send(effect: UiEffect) {
        effectChannel.trySend(effect)
    }

    private fun Capability.settingsTarget() = when (this) {
        Capability.LOCATION -> SettingsTarget.LOCATION
        Capability.BLUETOOTH -> SettingsTarget.BLUETOOTH
        Capability.NFC -> SettingsTarget.NFC
        Capability.NOTIFICATIONS -> SettingsTarget.NOTIFICATIONS
        Capability.UWB -> SettingsTarget.APP
    }

    companion object {
        private const val KEY_FILTER = "sensors.filter"
        private const val KEY_QUERY = "sensors.query"
        private const val STOP_TIMEOUT_MS = 5_000L
        private val PERMISSION_CAPABILITIES = listOf(Capability.LOCATION, Capability.BLUETOOTH, Capability.NOTIFICATIONS)

        internal fun NearbyDevice.matches(filter: DeviceFilter, query: String): Boolean {
            if (filter == DeviceFilter.NEAR && signal != ProximitySignal.NEAR) return false
            if (query.isBlank()) return true
            val needle = query.trim()
            return id.contains(needle, ignoreCase = true) || name?.contains(needle, ignoreCase = true) == true
        }
    }
}
