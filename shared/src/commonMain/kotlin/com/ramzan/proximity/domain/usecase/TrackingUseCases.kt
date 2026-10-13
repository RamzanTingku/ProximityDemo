package com.ramzan.proximity.domain.usecase

import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.repository.CapabilityRepository
import com.ramzan.proximity.domain.repository.SensorRepository

class StartTrackingUseCase(
    private val sensors: SensorRepository,
    private val capabilities: CapabilityRepository,
) {
    /**
     * Location is mandatory. Bluetooth and notifications are optional: tracking still starts
     * and the UI surfaces what is missing.
     */
    suspend operator fun invoke(): DomainResult<Unit> {
        capabilities.refresh()
        capabilities.capabilities.value.blockerFor(Capability.LOCATION)?.let {
            return DomainResult.Failure(it)
        }
        if (sensors.trackingState.value.isRunning) return DomainResult.Success(Unit)
        return sensors.start()
    }
}

class StopTrackingUseCase(private val sensors: SensorRepository) {
    operator fun invoke() = sensors.stop()
}
