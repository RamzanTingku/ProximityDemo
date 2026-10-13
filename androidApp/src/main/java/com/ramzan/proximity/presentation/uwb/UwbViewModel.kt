package com.ramzan.proximity.presentation.uwb

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzan.proximity.domain.codec.UwbExchangeCodec
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.UwbCodeKind
import com.ramzan.proximity.domain.model.UwbState
import com.ramzan.proximity.domain.model.UwbStatus
import com.ramzan.proximity.domain.usecase.AcceptUwbOfferUseCase
import com.ramzan.proximity.domain.usecase.CreateUwbOfferUseCase
import com.ramzan.proximity.domain.usecase.ObserveCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.ObserveUwbStateUseCase
import com.ramzan.proximity.domain.usecase.RefreshCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.StartUwbRangingUseCase
import com.ramzan.proximity.domain.usecase.StopUwbRangingUseCase
import com.ramzan.proximity.presentation.SettingsTarget
import com.ramzan.proximity.presentation.UiEffect
import com.ramzan.proximity.presentation.toEffect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UwbStep { START, EXCHANGE, RANGING }

data class UwbUiState(
    val capability: CapabilityStatus = CapabilityStatus.UNSUPPORTED,
    val uwb: UwbState = UwbState(),
    /** The code this device wants the other one to receive: an offer or an answer. */
    val ownCode: String = "",
    val ownCodeKind: UwbCodeKind? = null,
    val input: String = "",
    val inputKind: UwbCodeKind? = null,
    val inputError: String? = null,
    val isBusy: Boolean = false,
) {
    val step: UwbStep
        get() = when {
            uwb.status == UwbStatus.IDLE || uwb.status == UwbStatus.OFFER_READY ->
                if (ownCode.isEmpty()) UwbStep.START else UwbStep.EXCHANGE
            else -> UwbStep.RANGING
        }

    val canAcceptOffer: Boolean
        get() = !isBusy && inputKind == UwbCodeKind.OFFER && inputError == null
    val canStartRanging: Boolean
        get() = !isBusy && inputKind == UwbCodeKind.ANSWER && inputError == null
    val isSupported: Boolean
        get() = capability != CapabilityStatus.UNSUPPORTED
}

class UwbViewModel(
    observeUwb: ObserveUwbStateUseCase,
    observeCapabilities: ObserveCapabilitiesUseCase,
    private val refreshCapabilities: RefreshCapabilitiesUseCase,
    private val createOffer: CreateUwbOfferUseCase,
    private val acceptOffer: AcceptUwbOfferUseCase,
    private val startRanging: StartUwbRangingUseCase,
    private val stopRanging: StopUwbRangingUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private enum class PendingAction { CREATE_OFFER, ACCEPT_OFFER, START_RANGING }

    private class Local(
        val ownCode: String = "",
        val ownCodeKind: UwbCodeKind? = null,
        val isBusy: Boolean = false,
    )

    private val local = MutableStateFlow(Local())
    private val input = savedState.getStateFlow(KEY_INPUT, "")
    private val effectChannel = Channel<UiEffect>(Channel.BUFFERED)
    private var pending: PendingAction? = null
    private var running: PendingAction? = null

    val effects: Flow<UiEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<UwbUiState> = combine(
        observeUwb(),
        observeCapabilities().map { it.uwb },
        local,
        input,
    ) { uwb, capability, local, input ->
        val kind = UwbExchangeCodec.detectKind(input)
        UwbUiState(
            capability = capability,
            uwb = uwb,
            ownCode = local.ownCode,
            ownCodeKind = local.ownCodeKind,
            input = input,
            inputKind = kind,
            inputError = validate(input, kind),
            isBusy = local.isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UwbUiState())

    fun onInputChanged(value: String) {
        savedState[KEY_INPUT] = value
    }

    fun onCreateOffer() = run(PendingAction.CREATE_OFFER) {
        createOffer().onCode(UwbCodeKind.OFFER)
    }

    fun onAcceptOffer() = run(PendingAction.ACCEPT_OFFER) {
        acceptOffer(input.value).onCode(UwbCodeKind.ANSWER)
    }

    fun onStartRanging() = run(PendingAction.START_RANGING) {
        when (val result = startRanging(input.value)) {
            is DomainResult.Failure -> reportFailure(result.error)
            is DomainResult.Success -> Unit
        }
    }

    fun onStop() {
        stopRanging()
        local.value = Local()
        savedState[KEY_INPUT] = ""
    }

    fun onShare() {
        val code = local.value.ownCode
        if (code.isNotEmpty()) effectChannel.trySend(UiEffect.Share(code))
    }

    /** Called after the permission dialog; retries the action that needed it. */
    fun onPermissionResult(permanentlyDenied: Set<Capability> = emptySet()) {
        refreshCapabilities()
        if (Capability.UWB in permanentlyDenied) {
            pending = null
            effectChannel.trySend(
                UiEffect.ShowMessage("UWB permission is blocked. Enable it in Settings.", SettingsTarget.APP),
            )
            return
        }
        when (pending.also { pending = null }) {
            PendingAction.CREATE_OFFER -> onCreateOffer()
            PendingAction.ACCEPT_OFFER -> onAcceptOffer()
            PendingAction.START_RANGING -> onStartRanging()
            null -> Unit
        }
    }

    private fun run(action: PendingAction, block: suspend () -> Unit) {
        if (local.value.isBusy) return
        local.value = local.value.busy(true)
        pending = null
        running = action
        viewModelScope.launch {
            try {
                block()
            } finally {
                local.value = local.value.busy(false)
            }
        }
    }

    private fun DomainResult<String>.onCode(kind: UwbCodeKind) {
        when (this) {
            is DomainResult.Success -> {
                local.value = Local(ownCode = value, ownCodeKind = kind, isBusy = true)
            }
            is DomainResult.Failure -> reportFailure(error)
        }
    }

    private fun reportFailure(error: DomainError) {
        if (error is DomainError.PermissionMissing) {
            pending = running
            effectChannel.trySend(UiEffect.RequestPermissions(setOf(Capability.UWB)))
        } else {
            effectChannel.trySend(error.toEffect())
        }
    }

    private fun validate(value: String, kind: UwbCodeKind?): String? {
        if (value.isBlank()) return null
        val result = when (kind) {
            UwbCodeKind.OFFER -> UwbExchangeCodec.decodeOffer(value)
            UwbCodeKind.ANSWER -> UwbExchangeCodec.decodeAnswer(value)
            null -> return "This is not a Proximity Demo UWB code"
        }
        return (result as? DomainResult.Failure)?.error?.let { (it as? DomainError.InvalidCode)?.reason }
    }

    private fun Local.busy(value: Boolean) = Local(ownCode, ownCodeKind, value)

    private companion object {
        const val KEY_INPUT = "uwb.input"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
