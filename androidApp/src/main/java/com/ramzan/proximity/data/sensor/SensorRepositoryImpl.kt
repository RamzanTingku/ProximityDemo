package com.ramzan.proximity.data.sensor

import com.ramzan.proximity.data.ble.BleScanDataSource
import com.ramzan.proximity.data.location.LocationDataSource
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.GeoPoint
import com.ramzan.proximity.domain.model.NearbyDevice
import com.ramzan.proximity.domain.model.TrackingState
import com.ramzan.proximity.domain.model.TrackingStatus
import com.ramzan.proximity.domain.model.toDomainError
import com.ramzan.proximity.domain.repository.CapabilityRepository
import com.ramzan.proximity.domain.repository.SensorRepository
import com.ramzan.proximity.domain.usecase.blockerFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the location and BLE collection. The foreground service only keeps the process alive and
 * calls back through [TrackingServiceHost]; Bluetooth and location toggles are followed live so
 * scanning resumes by itself when the user turns them back on.
 */
class SensorRepositoryImpl(
    private val scope: CoroutineScope,
    private val locationSource: LocationDataSource,
    private val bleSource: BleScanDataSource,
    private val capabilityRepository: CapabilityRepository,
    private val serviceController: TrackingServiceController,
) : SensorRepository, TrackingServiceHost {
    private val state = MutableStateFlow(TrackingState())
    private val lastLocation = MutableStateFlow<GeoPoint?>(null)
    private val samples = MutableSharedFlow<NearbyDevice>(
        extraBufferCapacity = SAMPLE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var sessionJob: Job? = null

    override val trackingState: StateFlow<TrackingState> = state
    override val location: StateFlow<GeoPoint?> = lastLocation
    override val deviceSamples: Flow<NearbyDevice> = samples

    override suspend fun start(): DomainResult<Unit> {
        if (state.value.isRunning) return DomainResult.Success(Unit)
        state.value = TrackingState(TrackingStatus.STARTING)
        val result = serviceController.start()
        if (result is DomainResult.Failure) state.value = TrackingState(TrackingStatus.FAILED, result.error)
        return result
    }

    override fun stop() {
        endSession()
        serviceController.stop()
        state.value = TrackingState()
    }

    override fun onServiceStarted() {
        if (sessionJob?.isActive == true) return
        sessionJob = scope.launch { runSession() }
    }

    override fun onServiceFailed(error: DomainError) {
        endSession()
        serviceController.stop()
        state.value = TrackingState(TrackingStatus.FAILED, error)
    }

    override fun onServiceStopped() {
        endSession()
        if (state.value.status != TrackingStatus.FAILED) state.value = TrackingState()
    }

    private fun endSession() {
        sessionJob?.cancel()
        sessionJob = null
    }

    private suspend fun runSession() = coroutineScope {
        val locationIssue = MutableStateFlow<DomainError?>(null)
        val bleIssue = MutableStateFlow<DomainError?>(null)
        state.value = TrackingState(TrackingStatus.ACTIVE)

        launch {
            combine(locationIssue, bleIssue) { locationError, bleError -> bleError ?: locationError }
                .distinctUntilChanged()
                .collect { issue -> state.update { if (it.status == TrackingStatus.ACTIVE) it.copy(error = issue) else it } }
        }
        launch {
            capabilityRepository.capabilities.map { it.location }.distinctUntilChanged().collectLatest { status ->
                locationIssue.value = capabilityRepository.capabilities.value.blockerFor(Capability.LOCATION)
                if (status == CapabilityStatus.READY) {
                    locationSource.updates()
                        .catch { locationIssue.value = it.toDomainError() }
                        .collect { lastLocation.value = it }
                }
            }
        }
        launch {
            capabilityRepository.capabilities.map { it.bluetooth }.distinctUntilChanged().collectLatest { status ->
                bleIssue.value = capabilityRepository.capabilities.value.blockerFor(Capability.BLUETOOTH)
                if (status == CapabilityStatus.READY) {
                    bleSource.scan()
                        .catch { bleIssue.value = it.toDomainError() }
                        .collect { samples.emit(it) }
                }
            }
        }
    }

    private companion object {
        const val SAMPLE_BUFFER = 128
    }
}
