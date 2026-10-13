package com.ramzan.proximity.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.ramzan.proximity.core.Clock
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.GeoPoint
import com.ramzan.proximity.domain.model.NearbyDevice
import com.ramzan.proximity.domain.usecase.ObserveCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.ObserveLocationUseCase
import com.ramzan.proximity.domain.usecase.ObserveNearbyDevicesUseCase
import com.ramzan.proximity.domain.usecase.ObserveTrackingStateUseCase
import com.ramzan.proximity.domain.usecase.RefreshCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.StartTrackingUseCase
import com.ramzan.proximity.domain.usecase.StopTrackingUseCase
import com.ramzan.proximity.presentation.sensors.DeviceFilter
import com.ramzan.proximity.presentation.sensors.SensorsViewModel
import com.ramzan.proximity.testing.FakeCapabilityRepository
import com.ramzan.proximity.testing.FakeSensorRepository
import com.ramzan.proximity.testing.MainDispatcherRule
import com.ramzan.proximity.testing.allReady
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val sensors = FakeSensorRepository()
    private val capabilities = FakeCapabilityRepository()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = SensorsViewModel(
        observeTracking = ObserveTrackingStateUseCase(sensors),
        observeLocation = ObserveLocationUseCase(sensors),
        observeDevices = ObserveNearbyDevicesUseCase(sensors, Clock { 1_000L }),
        observeCapabilities = ObserveCapabilitiesUseCase(capabilities),
        refreshCapabilities = RefreshCapabilitiesUseCase(capabilities),
        startTracking = StartTrackingUseCase(sensors, capabilities),
        stopTracking = StopTrackingUseCase(sensors),
        savedState = handle,
    )

    private fun kotlinx.coroutines.test.TestScope.keepActive(vm: SensorsViewModel) {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Test
    fun `toggle starts tracking when everything is granted`() = runTest {
        val vm = viewModel().also { keepActive(it) }

        vm.onToggleTracking()

        assertEquals(1, sensors.startCalls)
        assertTrue(vm.uiState.value.tracking.isRunning)
    }

    @Test
    fun `toggle while running stops tracking`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        vm.onToggleTracking()

        vm.onToggleTracking()

        assertEquals(1, sensors.stopCalls)
        assertFalse(vm.uiState.value.tracking.isRunning)
    }

    @Test
    fun `missing permissions request them first and start afterwards`() = runTest {
        capabilities.set(allReady.copy(location = CapabilityStatus.PERMISSION_MISSING))
        val vm = viewModel().also { keepActive(it) }

        vm.effects.test {
            vm.onToggleTracking()
            assertEquals(UiEffect.RequestPermissions(setOf(Capability.LOCATION)), awaitItem())
            assertEquals(0, sensors.startCalls)

            capabilities.set(allReady)
            vm.onPermissionsResult()
            assertEquals(1, sensors.startCalls)
            expectNoEvents()
        }
    }

    @Test
    fun `permanently denied location explains how to fix it and does not start`() = runTest {
        capabilities.set(allReady.copy(location = CapabilityStatus.PERMISSION_MISSING))
        val vm = viewModel().also { keepActive(it) }

        vm.effects.test {
            vm.onToggleTracking()
            awaitItem()
            vm.onPermissionsResult(setOf(Capability.LOCATION))

            val message = awaitItem() as UiEffect.ShowMessage
            assertEquals(SettingsTarget.APP, message.settings)
            assertEquals(0, sensors.startCalls)
        }
    }

    @Test
    fun `chip permission result does not start tracking`() = runTest {
        capabilities.set(allReady.copy(bluetooth = CapabilityStatus.PERMISSION_MISSING))
        val vm = viewModel().also { keepActive(it) }

        vm.onCapabilityClicked(Capability.BLUETOOTH)
        vm.onPermissionsResult()

        assertEquals(0, sensors.startCalls)
    }

    @Test
    fun `start failure is surfaced as a message with a settings shortcut`() = runTest {
        sensors.startResult = DomainResult.Failure(DomainError.ServiceDisabled(Capability.LOCATION))
        val vm = viewModel().also { keepActive(it) }

        vm.effects.test {
            vm.onToggleTracking()
            val message = awaitItem() as UiEffect.ShowMessage
            assertEquals(SettingsTarget.LOCATION, message.settings)
        }
    }

    @Test
    fun `disabled capability chip opens its settings screen`() = runTest {
        capabilities.set(allReady.copy(bluetooth = CapabilityStatus.DISABLED))
        val vm = viewModel().also { keepActive(it) }

        vm.effects.test {
            vm.onCapabilityClicked(Capability.BLUETOOTH)
            assertEquals(UiEffect.OpenSettings(SettingsTarget.BLUETOOTH), awaitItem())
        }
    }

    @Test
    fun `location updates reach the ui state`() = runTest {
        val vm = viewModel().also { keepActive(it) }
        assertNull(vm.uiState.value.location)

        sensors.location.value = GeoPoint(1.0, 2.0)

        assertEquals(GeoPoint(1.0, 2.0), vm.uiState.value.location)
    }

    @Test
    fun `query and filter narrow the device list and survive recreation`() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(handle).also { keepActive(it) }
        vm.onToggleTracking()
        sensors.samples.emit(NearbyDevice("AA:01", "Watch", -50, 1_000L))
        sensors.samples.emit(NearbyDevice("AA:02", "Speaker", -80, 1_000L))
        assertEquals(2, vm.uiState.value.devices.size)

        vm.onQueryChanged("watch")
        assertEquals(listOf("AA:01"), vm.uiState.value.devices.map { it.id })
        assertEquals(2, vm.uiState.value.totalDevices)

        vm.onQueryChanged("")
        vm.onFilterChanged(DeviceFilter.NEAR)
        assertEquals(listOf("AA:01"), vm.uiState.value.devices.map { it.id })

        val restored = viewModel(handle).also { keepActive(it) }
        assertEquals(DeviceFilter.NEAR, restored.uiState.value.filter)
    }

    @Test
    fun `refresh request reaches the repository`() = runTest {
        val vm = viewModel()
        val before = capabilities.refreshCalls

        vm.onCapabilitiesRefreshRequested()

        assertEquals(before + 1, capabilities.refreshCalls)
    }
}
