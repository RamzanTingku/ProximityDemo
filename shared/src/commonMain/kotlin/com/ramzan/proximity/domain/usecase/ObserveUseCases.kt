package com.ramzan.proximity.domain.usecase

import com.ramzan.proximity.domain.model.GeoPoint
import com.ramzan.proximity.domain.model.NfcReadEvent
import com.ramzan.proximity.domain.model.SystemCapabilities
import com.ramzan.proximity.domain.model.TrackingState
import com.ramzan.proximity.domain.model.UwbState
import com.ramzan.proximity.domain.repository.CapabilityRepository
import com.ramzan.proximity.domain.repository.NfcRepository
import com.ramzan.proximity.domain.repository.SensorRepository
import com.ramzan.proximity.domain.repository.UwbRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ObserveCapabilitiesUseCase(private val repository: CapabilityRepository) {
    operator fun invoke(): StateFlow<SystemCapabilities> = repository.capabilities
}

class RefreshCapabilitiesUseCase(private val repository: CapabilityRepository) {
    operator fun invoke() = repository.refresh()
}

class ObserveTrackingStateUseCase(private val repository: SensorRepository) {
    operator fun invoke(): StateFlow<TrackingState> = repository.trackingState
}

class ObserveLocationUseCase(private val repository: SensorRepository) {
    operator fun invoke(): StateFlow<GeoPoint?> = repository.location
}

class ObserveUwbStateUseCase(private val repository: UwbRepository) {
    operator fun invoke(): StateFlow<UwbState> = repository.state
}

class ObserveNfcReadsUseCase(private val repository: NfcRepository) {
    operator fun invoke(): Flow<NfcReadEvent> = repository.readEvents
}
