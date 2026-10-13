package com.ramzan.proximity.domain.usecase

import com.ramzan.proximity.core.Clock
import com.ramzan.proximity.domain.model.NearbyDevice
import com.ramzan.proximity.domain.model.TrackingStatus
import com.ramzan.proximity.domain.repository.SensorRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Turns raw advertisements into a ranked list: strongest first, capped, and devices
 * that stopped advertising expire. Stopping tracking clears the list.
 */
class ObserveNearbyDevicesUseCase(
    private val sensors: SensorRepository,
    private val clock: Clock,
    private val staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
    private val maxDevices: Int = DEFAULT_MAX_DEVICES,
    private val pruneIntervalMs: Long = DEFAULT_PRUNE_INTERVAL_MS,
) {
    private sealed interface Event {
        data class Sample(val device: NearbyDevice) : Event
        data object Tick : Event
        data object Cleared : Event
    }

    operator fun invoke(): Flow<List<NearbyDevice>> = flow {
        val devices = mutableMapOf<String, NearbyDevice>()
        val ticks = flow {
            while (true) {
                delay(pruneIntervalMs)
                emit(Event.Tick)
            }
        }
        val events = merge(
            sensors.deviceSamples.map { Event.Sample(it) },
            sensors.trackingState.map { it.status }.distinctUntilChanged()
                .map { if (it == TrackingStatus.STOPPED || it == TrackingStatus.FAILED) Event.Cleared else Event.Tick },
            ticks,
        )

        emit(emptyList())
        events.collect { event ->
            when (event) {
                is Event.Sample -> devices[event.device.id] = event.device.copy(lastSeenMs = clock.nowMs())
                Event.Cleared -> devices.clear()
                Event.Tick -> Unit
            }
            val now = clock.nowMs()
            devices.values.removeAll { now - it.lastSeenMs > staleAfterMs }
            if (devices.size > maxDevices) {
                devices.values.sortedByDescending { it.rssiDbm }
                    .drop(maxDevices)
                    .forEach { devices.remove(it.id) }
            }
            emit(devices.values.sortedWith(compareByDescending<NearbyDevice> { it.rssiDbm }.thenBy { it.id }))
        }
    }.distinctUntilChanged()

    companion object {
        const val DEFAULT_STALE_AFTER_MS = 10_000L
        const val DEFAULT_MAX_DEVICES = 20
        const val DEFAULT_PRUNE_INTERVAL_MS = 1_000L
    }
}
