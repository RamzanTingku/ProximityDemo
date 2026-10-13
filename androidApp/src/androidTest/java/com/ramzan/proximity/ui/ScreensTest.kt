package com.ramzan.proximity.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.GeoPoint
import com.ramzan.proximity.domain.model.NearbyDevice
import com.ramzan.proximity.domain.model.SystemCapabilities
import com.ramzan.proximity.domain.model.TagProblem
import com.ramzan.proximity.domain.model.TrackingState
import com.ramzan.proximity.domain.model.TrackingStatus
import com.ramzan.proximity.domain.model.UwbState
import com.ramzan.proximity.domain.model.UwbStatus
import com.ramzan.proximity.presentation.nfc.NfcHistoryItem
import com.ramzan.proximity.presentation.nfc.NfcMode
import com.ramzan.proximity.presentation.nfc.NfcUiState
import com.ramzan.proximity.presentation.sensors.DeviceFilter
import com.ramzan.proximity.presentation.sensors.SensorsUiState
import com.ramzan.proximity.presentation.uwb.UwbUiState
import com.ramzan.proximity.ui.nfc.NfcActions
import com.ramzan.proximity.ui.nfc.NfcContent
import com.ramzan.proximity.ui.sensors.SensorsActions
import com.ramzan.proximity.ui.sensors.SensorsContent
import com.ramzan.proximity.ui.theme.ProximityTheme
import com.ramzan.proximity.ui.uwb.UwbActions
import com.ramzan.proximity.ui.uwb.UwbContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val padding = PaddingValues()
    private val ready = SystemCapabilities(
        location = CapabilityStatus.READY,
        bluetooth = CapabilityStatus.READY,
        notifications = CapabilityStatus.READY,
    )

    private fun sensors(state: SensorsUiState, actions: SensorsActions = SensorsActions()) =
        compose.setContent { ProximityTheme { SensorsContent(state, 10_000L, actions, padding) } }

    @Test
    fun sensors_idle_showsStartButtonAndHint() {
        sensors(SensorsUiState(capabilities = ready))

        compose.onNodeWithText("Start tracking").assertIsDisplayed()
        compose.onNodeWithText("Start tracking to see where you are.").assertIsDisplayed()
        compose.onNodeWithTag("devices_empty").assertIsDisplayed()
    }

    @Test
    fun sensors_toggleClickInvokesCallback() {
        var clicks = 0
        sensors(SensorsUiState(capabilities = ready), SensorsActions(onToggleTracking = { clicks++ }))

        compose.onNodeWithTag("toggle_tracking").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun sensors_startingDisablesButton() {
        sensors(
            SensorsUiState(
                capabilities = ready,
                tracking = TrackingState(TrackingStatus.STARTING),
                isStarting = true,
            ),
        )
        compose.onNodeWithTag("toggle_tracking").assertIsNotEnabled()
    }

    @Test
    fun sensors_runningShowsLocationAndStopButton() {
        sensors(
            SensorsUiState(
                capabilities = ready,
                tracking = TrackingState(TrackingStatus.ACTIVE),
                location = GeoPoint(23.78206, 90.41605, 5f, 9_000L),
            ),
        )

        compose.onNodeWithText("Stop tracking").assertIsDisplayed()
        compose.onNodeWithTag("coordinates").assertIsDisplayed()
        compose.onNodeWithText("23.78206, 90.41605").assertIsDisplayed()
    }

    @Test
    fun sensors_errorBannerShown() {
        sensors(
            SensorsUiState(
                capabilities = ready,
                tracking = TrackingState(TrackingStatus.FAILED, DomainError.ScanFailed(6)),
            ),
        )
        compose.onNodeWithText("Bluetooth scanning was restarted too often", substring = true).assertIsDisplayed()
    }

    @Test
    fun sensors_deviceRowsAndNoMatchState() {
        val device = NearbyDevice("AA:BB", "Watch", -50, 9_000L)
        sensors(SensorsUiState(capabilities = ready, devices = listOf(device), totalDevices = 1))
        compose.onNodeWithTag("device_AA:BB").assertIsDisplayed()
    }

    @Test
    fun sensors_filteredToNothingExplainsWhy() {
        sensors(
            SensorsUiState(
                capabilities = ready,
                tracking = TrackingState(TrackingStatus.ACTIVE),
                totalDevices = 3,
                filter = DeviceFilter.NEAR,
            ),
        )
        compose.onNodeWithText("No matches").assertIsDisplayed()
    }

    @Test
    fun sensors_capabilityChipClickReportsCapability() {
        var clicked: String? = null
        sensors(
            SensorsUiState(capabilities = ready.copy(bluetooth = CapabilityStatus.DISABLED)),
            SensorsActions(onCapabilityClick = { clicked = it.name }),
        )
        compose.onNodeWithTag("chip_BLUETOOTH").performClick()
        assertEquals("BLUETOOTH", clicked)
    }

    @Test
    fun uwb_unsupportedShowsExplanation() {
        compose.setContent { ProximityTheme { UwbContent(UwbUiState(), UwbActions(), padding) } }
        compose.onNodeWithTag("uwb_unsupported").assertIsDisplayed()
    }

    @Test
    fun uwb_acceptDisabledForGarbageAndErrorShown() {
        compose.setContent {
            ProximityTheme {
                UwbContent(
                    UwbUiState(
                        capability = CapabilityStatus.READY,
                        input = "nonsense",
                        inputError = "This is not a Proximity Demo UWB code",
                    ),
                    UwbActions(),
                    padding,
                )
            }
        }
        compose.onNodeWithTag("accept_offer").assertIsNotEnabled()
        compose.onNodeWithText("This is not a Proximity Demo UWB code").assertIsDisplayed()
        compose.onNodeWithTag("create_offer").assertIsEnabled()
    }

    @Test
    fun uwb_typingForwardsInput() {
        var typed = ""
        compose.setContent {
            ProximityTheme {
                UwbContent(
                    UwbUiState(capability = CapabilityStatus.READY),
                    UwbActions(onInputChange = { typed = it }),
                    padding,
                )
            }
        }
        compose.onNodeWithTag("code_input").performTextInput("abc")
        assertEquals("abc", typed)
    }

    @Test
    fun uwb_rangingShowsDistance() {
        compose.setContent {
            ProximityTheme {
                UwbContent(
                    UwbUiState(
                        capability = CapabilityStatus.READY,
                        uwb = UwbState(UwbStatus.RANGING, distanceMeters = 1.5f),
                    ),
                    UwbActions(),
                    padding,
                )
            }
        }
        compose.onNodeWithText("1.50 m").assertIsDisplayed()
        compose.onNodeWithText("Ranging").assertIsDisplayed()
    }

    @Test
    fun uwb_stopAsksForConfirmation() {
        var stopped = 0
        compose.setContent {
            ProximityTheme {
                UwbContent(
                    UwbUiState(
                        capability = CapabilityStatus.READY,
                        uwb = UwbState(UwbStatus.RANGING, distanceMeters = 2f),
                    ),
                    UwbActions(onStop = { stopped++ }),
                    padding,
                )
            }
        }
        compose.onNodeWithTag("stop_session").performClick()
        assertEquals(0, stopped)
        compose.onNodeWithText("Stop").performClick()
        assertEquals(1, stopped)
    }

    @Test
    fun nfc_unsupportedBlocksWrite() {
        compose.setContent {
            ProximityTheme { NfcContent(NfcUiState(CapabilityStatus.UNSUPPORTED), NfcActions(), padding) }
        }
        compose.onNodeWithText("This device has no NFC hardware.").assertIsDisplayed()
        compose.onNodeWithTag("write_tag").assertIsNotEnabled()
    }

    @Test
    fun nfc_disabledOffersSettingsShortcut() {
        var opened = 0
        compose.setContent {
            ProximityTheme {
                NfcContent(NfcUiState(CapabilityStatus.DISABLED), NfcActions(onOpenSettings = { opened++ }), padding)
            }
        }
        compose.onNodeWithText("Turn on").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun nfc_waitingShowsCancel() {
        var cancelled = 0
        compose.setContent {
            ProximityTheme {
                NfcContent(
                    NfcUiState(CapabilityStatus.READY, NfcMode.WAITING_FOR_TAG),
                    NfcActions(onCancelWrite = { cancelled++ }),
                    padding,
                )
            }
        }
        compose.onNodeWithTag("cancel_write").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun nfc_historyListedAndClearable() {
        var cleared = 0
        compose.setContent {
            ProximityTheme {
                NfcContent(
                    NfcUiState(
                        CapabilityStatus.READY,
                        history = listOf(NfcHistoryItem("Hello tag", 1_000L, false)),
                    ),
                    NfcActions(onClearHistory = { cleared++ }),
                    padding,
                )
            }
        }
        compose.onNodeWithText("Hello tag").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()
        assertEquals(1, cleared)
    }
}
