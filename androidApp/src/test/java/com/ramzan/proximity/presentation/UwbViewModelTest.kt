package com.ramzan.proximity.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
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
import com.ramzan.proximity.presentation.uwb.UwbStep
import com.ramzan.proximity.presentation.uwb.UwbViewModel
import com.ramzan.proximity.testing.FakeCapabilityRepository
import com.ramzan.proximity.testing.FakeUwbRepository
import com.ramzan.proximity.testing.MainDispatcherRule
import com.ramzan.proximity.testing.allReady
import com.ramzan.proximity.testing.sampleOffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UwbViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val uwb = FakeUwbRepository()
    private val capabilities = FakeCapabilityRepository()

    private fun viewModel() = UwbViewModel(
        observeUwb = ObserveUwbStateUseCase(uwb),
        observeCapabilities = ObserveCapabilitiesUseCase(capabilities),
        refreshCapabilities = RefreshCapabilitiesUseCase(capabilities),
        createOffer = CreateUwbOfferUseCase(uwb, capabilities),
        acceptOffer = AcceptUwbOfferUseCase(uwb, capabilities),
        startRanging = StartUwbRangingUseCase(uwb, capabilities),
        stopRanging = StopUwbRangingUseCase(uwb),
        savedState = SavedStateHandle(),
    )

    private fun kotlinx.coroutines.test.TestScope.keepActive(vm: UwbViewModel) {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private val offerCode get() = UwbExchangeCodec.encode(sampleOffer())

    @Test
    fun `starts on the first step`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        assertEquals(UwbStep.START, vm.uiState.value.step)
        assertTrue(vm.uiState.value.isSupported)
    }

    @Test
    fun `unsupported hardware is reported`() = runTest {
        capabilities.set(allReady.copy(uwb = CapabilityStatus.UNSUPPORTED))
        val vm = viewModel().also { keepActive(it) }
        assertFalse(vm.uiState.value.isSupported)
    }

    @Test
    fun `creating an offer moves to exchange with a shareable code`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        uwb.state.value = UwbState(UwbStatus.OFFER_READY)

        vm.onCreateOffer()

        val state = vm.uiState.value
        assertEquals(UwbStep.EXCHANGE, state.step)
        assertEquals(UwbCodeKind.OFFER, state.ownCodeKind)
        assertEquals(UwbCodeKind.OFFER, UwbExchangeCodec.detectKind(state.ownCode))
        assertFalse(state.isBusy)
    }

    @Test
    fun `garbage input is flagged and cannot be accepted`() = runTest {
        val vm = viewModel().also { keepActive(it) }

        vm.onInputChanged("hello")

        val state = vm.uiState.value
        assertNotNull(state.inputError)
        assertFalse(state.canAcceptOffer)
        assertFalse(state.canStartRanging)
    }

    @Test
    fun `blank input shows no error`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        vm.onInputChanged("   ")
        assertNull(vm.uiState.value.inputError)
        assertFalse(vm.uiState.value.canAcceptOffer)
    }

    @Test
    fun `valid offer can be accepted and produces an answer code`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        vm.onInputChanged(offerCode)
        assertTrue(vm.uiState.value.canAcceptOffer)

        vm.onAcceptOffer()

        assertNotNull(uwb.joinedOffer)
        assertEquals(UwbCodeKind.ANSWER, vm.uiState.value.ownCodeKind)
    }

    @Test
    fun `an offer pasted where an answer is expected cannot start ranging`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        vm.onInputChanged(offerCode)
        assertFalse(vm.uiState.value.canStartRanging)
    }

    @Test
    fun `missing permission asks for it and retries the action`() = runTest {
        capabilities.set(allReady.copy(uwb = CapabilityStatus.PERMISSION_MISSING))
        val vm = viewModel().also { keepActive(it) }

        vm.effects.test {
            vm.onCreateOffer()
            assertEquals(UiEffect.RequestPermissions(setOf(Capability.UWB)), awaitItem())
            assertEquals("", vm.uiState.value.ownCode)

            capabilities.set(allReady)
            vm.onPermissionResult()
            assertTrue(vm.uiState.value.ownCode.isNotEmpty())
        }
    }

    @Test
    fun `permanently denied permission shows a settings message and does not retry`() = runTest {
        capabilities.set(allReady.copy(uwb = CapabilityStatus.PERMISSION_MISSING))
        val vm = viewModel().also { keepActive(it) }

        vm.effects.test {
            vm.onCreateOffer()
            awaitItem()
            vm.onPermissionResult(setOf(Capability.UWB))
            assertEquals(SettingsTarget.APP, (awaitItem() as UiEffect.ShowMessage).settings)
        }
        assertEquals("", vm.uiState.value.ownCode)
    }

    @Test
    fun `failure is reported as a message`() = runTest {
        uwb.offerResult = DomainResult.Failure(DomainError.SessionFailed("boom"))
        val vm = viewModel().also { keepActive(it) }

        vm.effects.test {
            vm.onCreateOffer()
            assertTrue(awaitItem() is UiEffect.ShowMessage)
        }
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `stop clears the session and the input`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        vm.onCreateOffer()
        vm.onInputChanged("something")

        vm.onStop()

        assertEquals(1, uwb.stopCalls)
        assertEquals("", vm.uiState.value.ownCode)
        assertEquals("", vm.uiState.value.input)
    }

    @Test
    fun `share emits the own code only when there is one`() = runTest {
        val vm = viewModel().also { keepActive(it) }

        vm.effects.test {
            vm.onShare()
            expectNoEvents()
            vm.onCreateOffer()
            vm.onShare()
            assertTrue(awaitItem() is UiEffect.Share)
        }
    }

    @Test
    fun `ranging state selects the ranging step`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        uwb.state.value = UwbState(UwbStatus.RANGING, distanceMeters = 1.5f)
        assertEquals(UwbStep.RANGING, vm.uiState.value.step)
    }
}
