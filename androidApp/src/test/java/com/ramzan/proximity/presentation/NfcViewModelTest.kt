package com.ramzan.proximity.presentation

import app.cash.turbine.test
import com.ramzan.proximity.core.Clock
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.NfcReadEvent
import com.ramzan.proximity.domain.model.TagProblem
import com.ramzan.proximity.domain.usecase.ObserveCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.ObserveNfcReadsUseCase
import com.ramzan.proximity.domain.usecase.RefreshCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.WriteNfcTagUseCase
import com.ramzan.proximity.presentation.nfc.NfcMode
import com.ramzan.proximity.presentation.nfc.NfcViewModel
import com.ramzan.proximity.testing.FakeCapabilityRepository
import com.ramzan.proximity.testing.FakeNfcRepository
import com.ramzan.proximity.testing.MainDispatcherRule
import com.ramzan.proximity.testing.allReady
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NfcViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val nfc = FakeNfcRepository()
    private val capabilities = FakeCapabilityRepository()

    private fun viewModel() = NfcViewModel(
        observeReads = ObserveNfcReadsUseCase(nfc),
        observeCapabilities = ObserveCapabilitiesUseCase(capabilities),
        refreshCapabilities = RefreshCapabilitiesUseCase(capabilities),
        writeTag = WriteNfcTagUseCase(nfc, capabilities),
        clock = Clock { 5_000L },
    )

    private fun kotlinx.coroutines.test.TestScope.keepActive(vm: NfcViewModel) {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Test
    fun `reads are added to history newest first`() = runTest {
        val vm = viewModel().also { keepActive(it) }

        nfc.reads.emit(NfcReadEvent.Text("one", 1))
        nfc.reads.emit(NfcReadEvent.Text("two", 2))

        assertEquals(listOf("two", "one"), vm.uiState.value.history.map { it.message })
    }

    @Test
    fun `read failures and empty tags are recorded distinctly`() = runTest {
        val vm = viewModel().also { keepActive(it) }

        nfc.reads.emit(NfcReadEvent.NoTextRecord(1))
        nfc.reads.emit(NfcReadEvent.Failed(DomainError.Tag(TagProblem.LOST), 2))

        val (failure, empty) = vm.uiState.value.history
        assertTrue(failure.isError)
        assertFalse(empty.isError)
    }

    @Test
    fun `history is capped`() = runTest {
        val vm = viewModel().also { keepActive(it) }

        repeat(NfcViewModel.MAX_HISTORY + 5) { nfc.reads.emit(NfcReadEvent.Text("tag $it", it.toLong())) }

        assertEquals(NfcViewModel.MAX_HISTORY, vm.uiState.value.history.size)
        assertEquals("tag ${NfcViewModel.MAX_HISTORY + 4}", vm.uiState.value.history.first().message)
    }

    @Test
    fun `clear empties history`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        nfc.reads.emit(NfcReadEvent.Text("one", 1))

        vm.onClearHistory()

        assertTrue(vm.uiState.value.history.isEmpty())
    }

    @Test
    fun `write waits for a tag then records success`() = runTest {
        val vm = viewModel().also { keepActive(it) }

        vm.effects.test {
            vm.onWriteTag()
            assertEquals(NfcMode.WAITING_FOR_TAG, vm.uiState.value.mode)
            assertFalse(vm.uiState.value.canWrite)

            nfc.pendingWrite!!.complete(DomainResult.Success(Unit))

            assertEquals(UiEffect.ShowMessage("Tag written"), awaitItem())
        }
        assertEquals(NfcMode.IDLE, vm.uiState.value.mode)
        assertFalse(vm.uiState.value.history.single().isError)
    }

    @Test
    fun `second write request while waiting is ignored`() = runTest {
        val vm = viewModel().also { keepActive(it) }

        vm.onWriteTag()
        val first = nfc.pendingWrite
        vm.onWriteTag()

        assertTrue(first === nfc.pendingWrite)
    }

    @Test
    fun `cancelling returns to idle and cancels the write`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        vm.effects.test {
            vm.onWriteTag()

            vm.onCancelWrite()

            assertEquals(UiEffect.ShowMessage("Write cancelled"), awaitItem())
        }
        assertTrue(nfc.writeCancelled)
        assertEquals(NfcMode.IDLE, vm.uiState.value.mode)
    }

    @Test
    fun `write failure is recorded as an error`() = runTest {
        val vm = viewModel().also { keepActive(it) }

        vm.onWriteTag()
        nfc.pendingWrite!!.complete(DomainResult.Failure(DomainError.Tag(TagProblem.READ_ONLY)))

        assertTrue(vm.uiState.value.history.single().isError)
        assertEquals(NfcMode.IDLE, vm.uiState.value.mode)
    }

    @Test
    fun `write is blocked and explained when nfc is off`() = runTest {
        capabilities.set(allReady.copy(nfc = CapabilityStatus.DISABLED))
        val vm = viewModel().also { keepActive(it) }
        assertFalse(vm.uiState.value.canWrite)

        vm.effects.test {
            vm.onWriteTag()
            val message = awaitItem() as UiEffect.ShowMessage
            assertEquals(SettingsTarget.NFC, message.settings)
        }
        assertEquals(NfcMode.IDLE, vm.uiState.value.mode)
    }

    @Test
    fun `open settings emits the nfc settings effect`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onOpenNfcSettings()
            assertEquals(UiEffect.OpenSettings(SettingsTarget.NFC), awaitItem())
        }
    }
}
