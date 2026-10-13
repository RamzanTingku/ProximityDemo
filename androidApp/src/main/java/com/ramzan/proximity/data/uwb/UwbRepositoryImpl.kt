package com.ramzan.proximity.data.uwb

import android.content.Context
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbClientSessionScope
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbControllerSessionScope
import androidx.core.uwb.UwbDevice
import androidx.core.uwb.UwbManager
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.ControleeAnswer
import com.ramzan.proximity.domain.model.ControllerOffer
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.UwbState
import com.ramzan.proximity.domain.model.UwbStatus
import com.ramzan.proximity.domain.repository.UwbRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom

class UwbRepositoryImpl(
    context: Context,
    private val scope: CoroutineScope,
) : UwbRepository {
    private val context = context.applicationContext
    private val random = SecureRandom()
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(UwbState())
    private var controllerSession: UwbControllerSessionScope? = null
    private var pendingOffer: ControllerOffer? = null
    private var rangingJob: Job? = null

    override val state: StateFlow<UwbState> = mutableState

    override suspend fun createControllerOffer(): DomainResult<ControllerOffer> = mutex.withLock {
        guarded {
            clearSession()
            val session = availableManager().controllerSessionScope()
            val offer = ControllerOffer(
                sessionId = random.nextInt(MAX_SESSION_ID) + 1,
                sessionKey = ByteArray(SESSION_KEY_SIZE).also(random::nextBytes),
                address = session.localAddress.address,
                channel = session.uwbComplexChannel.channel,
                preambleIndex = session.uwbComplexChannel.preambleIndex,
            )
            controllerSession = session
            pendingOffer = offer
            mutableState.value = UwbState(UwbStatus.OFFER_READY)
            offer
        }
    }

    override suspend fun joinAsControlee(offer: ControllerOffer): DomainResult<ControleeAnswer> = mutex.withLock {
        guarded {
            clearSession()
            val session = availableManager().controleeSessionScope()
            startRanging(
                session = session,
                offer = offer,
                channel = UwbComplexChannel(offer.channel, offer.preambleIndex),
                peerAddress = offer.address,
            )
            ControleeAnswer(session.localAddress.address)
        }
    }

    override suspend fun startControllerRanging(answer: ControleeAnswer): DomainResult<Unit> = mutex.withLock {
        val session = controllerSession
        val offer = pendingOffer
        if (session == null || offer == null) return@withLock DomainResult.Failure(DomainError.NoActiveOffer)
        guarded {
            startRanging(session, offer, session.uwbComplexChannel, answer.address)
        }
    }

    override fun stopRanging() {
        clearSession()
        mutableState.value = UwbState()
    }

    private fun clearSession() {
        rangingJob?.cancel()
        rangingJob = null
        controllerSession = null
        pendingOffer = null
    }

    private fun startRanging(
        session: UwbClientSessionScope,
        offer: ControllerOffer,
        channel: UwbComplexChannel,
        peerAddress: ByteArray,
    ) {
        val parameters = RangingParameters(
            uwbConfigType = RangingParameters.CONFIG_PROVISIONED_UNICAST_DS_TWR,
            sessionId = offer.sessionId,
            subSessionId = 0,
            sessionKeyInfo = offer.sessionKey,
            subSessionKeyInfo = null,
            complexChannel = channel,
            peerDevices = listOf(UwbDevice.createForAddress(peerAddress)),
            updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC,
        )
        mutableState.value = UwbState(UwbStatus.WAITING_FOR_PEER)
        rangingJob?.cancel()
        rangingJob = scope.launch {
            try {
                session.prepareSession(parameters).collect { result -> mutableState.value = result.toState() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.value = UwbState(UwbStatus.FAILED, error = DomainError.SessionFailed(error.message))
            }
        }
    }

    private fun RangingResult.toState(): UwbState = when (this) {
        is RangingResult.RangingResultPosition ->
            UwbState(UwbStatus.RANGING, distanceMeters = position.distance?.value)
        is RangingResult.RangingResultInitialized -> UwbState(UwbStatus.WAITING_FOR_PEER)
        is RangingResult.RangingResultPeerDisconnected -> UwbState(UwbStatus.PEER_LOST)
        is RangingResult.RangingResultFailure ->
            UwbState(UwbStatus.FAILED, error = DomainError.SessionFailed("reason $reason"))
    }

    private suspend fun availableManager(): UwbManager {
        val manager = UwbManager.createInstance(context)
        if (!manager.isAvailable()) throw UwbUnavailableException()
        return manager
    }

    private suspend fun <T> guarded(block: suspend () -> T): DomainResult<T> = try {
        DomainResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: UwbUnavailableException) {
        DomainResult.Failure(DomainError.Unavailable(Capability.UWB))
    } catch (_: SecurityException) {
        DomainResult.Failure(DomainError.PermissionMissing(Capability.UWB))
    } catch (error: Exception) {
        DomainResult.Failure(DomainError.SessionFailed(error.message))
    }

    private class UwbUnavailableException : Exception()

    private companion object {
        const val SESSION_KEY_SIZE = 16
        const val MAX_SESSION_ID = 65_535
    }
}
