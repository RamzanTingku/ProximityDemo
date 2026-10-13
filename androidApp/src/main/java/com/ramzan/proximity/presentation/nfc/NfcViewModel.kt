package com.ramzan.proximity.presentation.nfc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzan.proximity.core.Clock
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.NfcReadEvent
import com.ramzan.proximity.domain.usecase.ObserveCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.ObserveNfcReadsUseCase
import com.ramzan.proximity.domain.usecase.RefreshCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.WriteNfcTagUseCase
import com.ramzan.proximity.presentation.SettingsTarget
import com.ramzan.proximity.presentation.UiEffect
import com.ramzan.proximity.presentation.toEffect
import com.ramzan.proximity.presentation.toMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

enum class NfcMode { IDLE, WAITING_FOR_TAG }

data class NfcHistoryItem(
    val message: String,
    val timestampMs: Long,
    val isError: Boolean,
)

data class NfcUiState(
    val availability: CapabilityStatus = CapabilityStatus.UNSUPPORTED,
    val mode: NfcMode = NfcMode.IDLE,
    val history: List<NfcHistoryItem> = emptyList(),
) {
    val canWrite: Boolean
        get() = availability == CapabilityStatus.READY && mode == NfcMode.IDLE
}

class NfcViewModel(
    observeReads: ObserveNfcReadsUseCase,
    observeCapabilities: ObserveCapabilitiesUseCase,
    private val refreshCapabilities: RefreshCapabilitiesUseCase,
    private val writeTag: WriteNfcTagUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val mode = MutableStateFlow(NfcMode.IDLE)
    private val history = MutableStateFlow<List<NfcHistoryItem>>(emptyList())
    private val effectChannel = Channel<UiEffect>(Channel.BUFFERED)
    private var writeJob: Job? = null

    val effects: Flow<UiEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<NfcUiState> = combine(
        observeCapabilities().map { it.nfc },
        mode,
        history,
    ) { availability, mode, history -> NfcUiState(availability, mode, history) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NfcUiState())

    init {
        viewModelScope.launch {
            observeReads().collect { record(it.toHistoryItem()) }
        }
    }

    fun onWriteTag() {
        if (writeJob?.isActive == true) return
        refreshCapabilities()
        writeJob = viewModelScope.launch {
            mode.value = NfcMode.WAITING_FOR_TAG
            try {
                when (val result = writeTag()) {
                    is DomainResult.Success -> {
                        record(NfcHistoryItem("Wrote demo text to the tag", clock.nowMs(), isError = false))
                        effectChannel.trySend(UiEffect.ShowMessage("Tag written"))
                    }
                    is DomainResult.Failure -> {
                        record(NfcHistoryItem(result.error.toMessage(), clock.nowMs(), isError = true))
                        effectChannel.trySend(result.error.toEffect())
                    }
                }
            } catch (cancellation: CancellationException) {
                effectChannel.trySend(UiEffect.ShowMessage("Write cancelled"))
                throw cancellation
            } finally {
                mode.value = NfcMode.IDLE
            }
        }
    }

    fun onCancelWrite() {
        writeJob?.cancel()
    }

    fun onOpenNfcSettings() {
        effectChannel.trySend(UiEffect.OpenSettings(SettingsTarget.NFC))
    }

    fun onResumed() = refreshCapabilities()

    fun onClearHistory() {
        history.value = emptyList()
    }

    private fun record(item: NfcHistoryItem) {
        history.value = (listOf(item) + history.value).take(MAX_HISTORY)
    }

    private fun NfcReadEvent.toHistoryItem(): NfcHistoryItem = when (this) {
        is NfcReadEvent.Text -> NfcHistoryItem(text, timestampMs, isError = false)
        is NfcReadEvent.NoTextRecord ->
            NfcHistoryItem("Tag read, but it has no text record", timestampMs, isError = false)
        is NfcReadEvent.Failed -> NfcHistoryItem(error.toMessage(), timestampMs, isError = true)
    }

    companion object {
        const val MAX_HISTORY = 10
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
