package com.ramzan.proximity.domain.usecase

import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.TrackingStatus
import com.ramzan.proximity.testing.FakeCapabilityRepository
import com.ramzan.proximity.testing.FakeSensorRepository
import com.ramzan.proximity.testing.allReady
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TrackingUseCasesTest {
    private val sensors = FakeSensorRepository()
    private val capabilities = FakeCapabilityRepository()
    private val start = StartTrackingUseCase(sensors, capabilities)

    @Test
    fun startsWhenEverythingIsReady() = runTest {
        assertIs<DomainResult.Success<*>>(start())
        assertEquals(TrackingStatus.ACTIVE, sensors.trackingState.value.status)
    }

    @Test
    fun refreshesCapabilitiesBeforeDeciding() = runTest {
        start()
        assertEquals(1, capabilities.refreshCalls)
    }

    @Test
    fun missingLocationPermissionBlocksStart() = runTest {
        capabilities.set(allReady.copy(location = CapabilityStatus.PERMISSION_MISSING))
        val failure = assertIs<DomainResult.Failure>(start())
        assertEquals(DomainError.PermissionMissing(Capability.LOCATION), failure.error)
        assertEquals(0, sensors.startCalls)
    }

    @Test
    fun disabledLocationServicesBlockStart() = runTest {
        capabilities.set(allReady.copy(location = CapabilityStatus.DISABLED))
        val failure = assertIs<DomainResult.Failure>(start())
        assertEquals(DomainError.ServiceDisabled(Capability.LOCATION), failure.error)
    }

    @Test
    fun missingBluetoothAndNotificationsDoNotBlockStart() = runTest {
        capabilities.set(
            allReady.copy(
                bluetooth = CapabilityStatus.DISABLED,
                notifications = CapabilityStatus.PERMISSION_MISSING,
            ),
        )
        assertIs<DomainResult.Success<*>>(start())
        assertEquals(1, sensors.startCalls)
    }

    @Test
    fun startIsIdempotentWhileRunning() = runTest {
        start()
        start()
        assertEquals(1, sensors.startCalls)
    }

    @Test
    fun repositoryFailureIsPropagated() = runTest {
        sensors.startResult = DomainResult.Failure(DomainError.ForegroundStartFailed("denied"))
        val failure = assertIs<DomainResult.Failure>(start())
        assertEquals(DomainError.ForegroundStartFailed("denied"), failure.error)
    }

    @Test
    fun stopStopsTheRepositoryEvenWhenAlreadyStopped() = runTest {
        val stop = StopTrackingUseCase(sensors)
        stop()
        stop()
        assertEquals(2, sensors.stopCalls)
        assertEquals(TrackingStatus.STOPPED, sensors.trackingState.value.status)
    }
}
