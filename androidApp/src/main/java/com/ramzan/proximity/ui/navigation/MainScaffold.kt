package com.ramzan.proximity.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramzan.proximity.presentation.nfc.NfcViewModel
import com.ramzan.proximity.presentation.sensors.SensorsViewModel
import com.ramzan.proximity.presentation.uwb.UwbViewModel
import com.ramzan.proximity.ui.nfc.NfcScreen
import com.ramzan.proximity.ui.sensors.SensorsScreen
import com.ramzan.proximity.ui.uwb.UwbScreen
import org.koin.androidx.compose.koinViewModel

enum class AppTab(val label: String, val icon: ImageVector) {
    SENSORS("Sensors", Icons.Default.Sensors),
    UWB("UWB", Icons.Default.SettingsInputAntenna),
    NFC("NFC", Icons.Default.Nfc),
}

@Composable
fun MainScaffold() {
    val sensorsViewModel = koinViewModel<SensorsViewModel>()
    val uwbViewModel = koinViewModel<UwbViewModel>()
    val nfcViewModel = koinViewModel<NfcViewModel>()
    val sensorsState by sensorsViewModel.uiState.collectAsStateWithLifecycle()

    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = {
                            if (tab == AppTab.SENSORS && sensorsState.tracking.isRunning) {
                                BadgedBox(badge = { Badge() }) { Icon(tab.icon, contentDescription = null) }
                            } else {
                                Icon(tab.icon, contentDescription = null)
                            }
                        },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("tab_${tab.name}"),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding()) {
            Crossfade(targetState = AppTab.entries[selected], label = "tabs") { tab ->
                when (tab) {
                    AppTab.SENSORS -> SensorsScreen(sensorsViewModel, snackbar, padding)
                    AppTab.UWB -> UwbScreen(uwbViewModel, snackbar, padding)
                    AppTab.NFC -> NfcScreen(nfcViewModel, snackbar, padding)
                }
            }
        }
    }
}
