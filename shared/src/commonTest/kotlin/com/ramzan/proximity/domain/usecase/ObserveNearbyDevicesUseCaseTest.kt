package com.ramzan.proximity.domain.usecase

import com.ramzan.proximity.core.Clock
import com.ramzan.proximity.domain.model.NearbyDevice
import com.ramzan.proximity.domain.model.TrackingState
import com.ramzan.proximity.domain.model.TrackingStatus
import com.ramzan.proximity.testing.FakeSensorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveNearbyDevicesUseCaseTest {
    private val sensors = FakeSensorRepository()

    private class Harness(val latest: () -> List<NearbyDevice>)

    private fun TestScope.observe(
        maxDevices: Int = 20,
        staleAfterMs: Long = 10_000,
    ): Harness {
        val useCase = ObserveNearbyDevicesUseCase(
            sensors = sensors,
            clock = Clock { currentTime },
            staleAfterMs = staleAfterMs,
            maxDevices = maxDevices,
        )
        var latest = emptyList<NearbyDevice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase().collect { latest = it }
        }
        return Harness { latest }
    }

    private suspend fun emit(id: String, rssi: Int, name: String? = null) =
        sensors.samples.emit(NearbyDevice(id, name, rssi))

    @Test
    fun startsEmpty() = runTest {
        assertTrue(observe().latest().isEmpty())
    }

    @Test
    fun ranksStrongestSignalFirst() = runTest {
        val devices = observe()
        emit("weak", -90)
        emit("strong", -40)
        emit("mid", -65)

        assertEquals(listOf("strong", "mid", "weak"), devices.latest().map { it.id })
    }

    @Test
    fun repeatedAdvertisementUpdatesInsteadOfDuplicating() = runTest {
        val devices = observe()
        emit("a", -80, "Beacon")
        emit("a", -50, "Beacon")

        assertEquals(1, devices.latest().size)
        assertEquals(-50, devices.latest().single().rssiDbm)
    }

    @Test
    fun keepsOnlyTheStrongestDevicesWhenOverTheCap() = runTest {
        val devices = observe(maxDevices = 3)
        (1..6).forEach { emit("d$it", -40 - it * 10) }

        assertEquals(listOf("d1", "d2", "d3"), devices.latest().map { it.id })
    }

    @Test
    fun devicesThatStopAdvertisingExpire() = runTest {
        val devices = observe(staleAfterMs = 5_000)
        emit("a", -50)
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(1, devices.latest().size)

        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(devices.latest().isEmpty())
    }

    @Test
    fun aFreshAdvertisementKeepsTheDeviceAlive() = runTest {
        val devices = observe(staleAfterMs = 5_000)
        emit("a", -50)
        advanceTimeBy(4_000)
        emit("a", -52)
        advanceTimeBy(4_000)
        runCurrent()

        assertEquals(1, devices.latest().size)
    }

    @Test
    fun stoppingTrackingClearsTheList() = runTest {
        sensors.trackingState.value = TrackingState(TrackingStatus.ACTIVE)
        val devices = observe()
        emit("a", -50)
        assertEquals(1, devices.latest().size)

        sensors.trackingState.value = TrackingState(TrackingStatus.STOPPED)

        assertTrue(devices.latest().isEmpty())
    }

    @Test
    fun equalSignalsAreOrderedByIdForStableUi() = runTest {
        val devices = observe()
        emit("b", -60)
        emit("a", -60)

        assertEquals(listOf("a", "b"), devices.latest().map { it.id })
    }
}
