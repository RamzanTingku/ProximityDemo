package com.ramzan.proximity.testing

import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.ControleeAnswer
import com.ramzan.proximity.domain.model.ControllerOffer
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.GeoPoint
import com.ramzan.proximity.domain.model.NearbyDevice
import com.ramzan.proximity.domain.model.NfcReadEvent
import com.ramzan.proximity.domain.model.SystemCapabilities
import com.ramzan.proximity.domain.model.TrackingState
import com.ramzan.proximity.domain.model.TrackingStatus
import com.ramzan.proximity.domain.model.UwbState
import com.ramzan.proximity.domain.repository.CapabilityRepository
import com.ramzan.proximity.domain.repository.NfcRepository
import com.ramzan.proximity.domain.repository.SensorRepository
import com.ramzan.proximity.domain.repository.UwbRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val allReady = SystemCapabilities(
    location = CapabilityStatus.READY,
    bluetooth = CapabilityStatus.READY,
    notifications = CapabilityStatus.READY,
    nfc = CapabilityStatus.READY,
    uwb = CapabilityStatus.READY,
)

class FakeCapabilityRepository(initial: SystemCapabilities = allReady) : CapabilityRepository {
    private val state = MutableStateFlow(initial)
    override val capabilities: StateFlow<SystemCapabilities> = state
    var refreshCalls = 0
        private set

    fun set(value: SystemCapabilities) {
        state.value = value
    }

    override fun refresh() {
        refreshCalls++
    }
}

class FakeSensorRepository : SensorRepository {
    override val trackingState = MutableStateFlow(TrackingState())
    override val location = MutableStateFlow<GeoPoint?>(null)
    val samples = MutableSharedFlow<NearbyDevice>(extraBufferCapacity = 64)
    override val deviceSamples: Flow<NearbyDevice> = samples

    var startResult: DomainResult<Unit> = DomainResult.Success(Unit)
    var startCalls = 0
        private set
    var stopCalls = 0
        private set

    override suspend fun start(): DomainResult<Unit> {
        startCalls++
        if (startResult is DomainResult.Success) trackingState.value = TrackingState(TrackingStatus.ACTIVE)
        return startResult
    }

    override fun stop() {
        stopCalls++
        trackingState.value = TrackingState()
    }
}

class FakeUwbRepository : UwbRepository {
    override val state = MutableStateFlow(UwbState())

    var offerResult: DomainResult<ControllerOffer> = DomainResult.Success(sampleOffer())
    var joinResult: DomainResult<ControleeAnswer> = DomainResult.Success(ControleeAnswer(byteArrayOf(0x0A, 0x0B)))
    var startResult: DomainResult<Unit> = DomainResult.Success(Unit)
    var joinedOffer: ControllerOffer? = null
        private set
    var startedAnswer: ControleeAnswer? = null
        private set
    var stopCalls = 0
        private set

    override suspend fun createControllerOffer() = offerResult

    override suspend fun joinAsControlee(offer: ControllerOffer): DomainResult<ControleeAnswer> {
        joinedOffer = offer
        return joinResult
    }

    override suspend fun startControllerRanging(answer: ControleeAnswer): DomainResult<Unit> {
        startedAnswer = answer
        return startResult
    }

    override fun stopRanging() {
        stopCalls++
    }
}

class FakeNfcRepository : NfcRepository {
    val reads = MutableSharedFlow<NfcReadEvent>(extraBufferCapacity = 16)
    override val readEvents: Flow<NfcReadEvent> = reads

    var pendingWrite: CompletableDeferred<DomainResult<Unit>>? = null
        private set
    var writtenText: String? = null
        private set
    var writeCancelled = false
        private set

    override suspend fun writeNextTag(text: String): DomainResult<Unit> {
        val deferred = CompletableDeferred<DomainResult<Unit>>()
        pendingWrite = deferred
        writtenText = text
        try {
            return deferred.await()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            writeCancelled = true
            throw cancellation
        }
    }
}

fun sampleOffer(
    sessionId: Int = 42,
    key: ByteArray = ByteArray(16) { it.toByte() },
    address: ByteArray = byteArrayOf(0x01, 0x02),
    channel: Int = 9,
    preamble: Int = 10,
) = ControllerOffer(sessionId, key, address, channel, preamble)
