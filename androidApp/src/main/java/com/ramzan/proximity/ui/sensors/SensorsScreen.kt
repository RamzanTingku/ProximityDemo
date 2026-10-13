package com.ramzan.proximity.ui.sensors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.GeoPoint
import com.ramzan.proximity.domain.model.NearbyDevice
import com.ramzan.proximity.domain.model.TrackingStatus
import com.ramzan.proximity.presentation.displayName
import com.ramzan.proximity.presentation.sensors.DeviceFilter
import com.ramzan.proximity.presentation.sensors.SensorsUiState
import com.ramzan.proximity.presentation.sensors.SensorsViewModel
import com.ramzan.proximity.presentation.toMessage
import com.ramzan.proximity.ui.components.BannerCard
import com.ramzan.proximity.ui.components.EmptyState
import com.ramzan.proximity.ui.components.PulsingDot
import com.ramzan.proximity.ui.components.SectionCard
import com.ramzan.proximity.ui.components.rememberNowMs
import com.ramzan.proximity.ui.formatAgo
import com.ramzan.proximity.ui.formatCoordinates
import com.ramzan.proximity.ui.permissions.EffectHandler
import com.ramzan.proximity.ui.permissions.copyToClipboard
import com.ramzan.proximity.ui.permissions.openInMaps
import com.ramzan.proximity.ui.permissions.rememberPermissionRequester
import com.ramzan.proximity.ui.rssiStrength
import com.ramzan.proximity.ui.theme.LocalStatusColors
import com.ramzan.proximity.ui.theme.forSignal
import kotlinx.coroutines.launch

class SensorsActions(
    val onToggleTracking: () -> Unit = {},
    val onCapabilityClick: (Capability) -> Unit = {},
    val onFilterChange: (DeviceFilter) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onCopyLocation: (GeoPoint) -> Unit = {},
    val onOpenMaps: (GeoPoint) -> Unit = {},
    val onCopyDevice: (NearbyDevice) -> Unit = {},
)

@Composable
fun SensorsScreen(
    viewModel: SensorsViewModel,
    snackbar: SnackbarHostState,
    contentPadding: PaddingValues,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by rememberNowMs()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val requestPermissions = rememberPermissionRequester(viewModel::onPermissionsResult)
    EffectHandler(viewModel.effects, snackbar, requestPermissions)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onCapabilitiesRefreshRequested() }

    val actions = SensorsActions(
        onToggleTracking = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.onToggleTracking()
        },
        onCapabilityClick = viewModel::onCapabilityClicked,
        onFilterChange = viewModel::onFilterChanged,
        onQueryChange = viewModel::onQueryChanged,
        onCopyLocation = {
            context.copyToClipboard("Location", formatCoordinates(it.latitude, it.longitude))
            scope.launch { snackbar.showSnackbar("Coordinates copied") }
        },
        onOpenMaps = {
            if (!context.openInMaps(it.latitude, it.longitude)) {
                scope.launch { snackbar.showSnackbar("No maps app installed") }
            }
        },
        onCopyDevice = {
            context.copyToClipboard("Device address", it.id)
            scope.launch { snackbar.showSnackbar("Address ${it.id} copied") }
        },
    )
    SensorsContent(state, now, actions, contentPadding)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SensorsContent(
    state: SensorsUiState,
    nowMs: Long,
    actions: SensorsActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("sensors_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "control") { ControlCard(state, actions) }
        state.tracking.error?.let { error ->
            item(key = "error") { BannerCard(error.toMessage()) }
        }
        item(key = "location") { LocationCard(state, nowMs, actions) }
        item(key = "devices_header") { DevicesHeader(state, actions) }
        if (state.devices.isEmpty()) {
            item(key = "devices_empty") { DevicesEmptyState(state) }
        } else {
            items(state.devices, key = { it.id }) { device ->
                DeviceRow(device, nowMs, { actions.onCopyDevice(device) }, Modifier.animateItem())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlCard(state: SensorsUiState, actions: SensorsActions) {
    val running = state.tracking.isRunning
    val colors = LocalStatusColors.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PulsingDot(active = state.tracking.status == TrackingStatus.ACTIVE, activeColor = colors.good)
            Text(
                when (state.tracking.status) {
                    TrackingStatus.STOPPED -> "Tracking is off"
                    TrackingStatus.STARTING -> "Starting…"
                    TrackingStatus.ACTIVE -> "Tracking location and nearby devices"
                    TrackingStatus.FAILED -> "Tracking stopped"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Capability.LOCATION, Capability.BLUETOOTH, Capability.NOTIFICATIONS).forEach {
                CapabilityChip(it, state.capabilities[it]) { actions.onCapabilityClick(it) }
            }
        }
        Button(
            onClick = actions.onToggleTracking,
            enabled = !state.isStarting,
            modifier = Modifier.fillMaxWidth().testTag("toggle_tracking"),
            colors = if (running) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            if (state.isStarting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    state.isStarting -> "Starting…"
                    running -> "Stop tracking"
                    else -> "Start tracking"
                },
            )
        }
    }
}

@Composable
private fun CapabilityChip(capability: Capability, status: CapabilityStatus, onClick: () -> Unit) {
    val colors = LocalStatusColors.current
    val (label, tint) = when (status) {
        CapabilityStatus.READY -> "Ready" to colors.good
        CapabilityStatus.PERMISSION_MISSING -> "Allow" to colors.warning
        CapabilityStatus.DISABLED -> "Turn on" to colors.bad
        CapabilityStatus.UNSUPPORTED -> "Unsupported" to colors.neutral
    }
    val icon = when (capability) {
        Capability.LOCATION -> Icons.Default.LocationOn
        Capability.BLUETOOTH -> Icons.Default.Bluetooth
        else -> Icons.Default.Notifications
    }
    AssistChip(
        onClick = onClick,
        label = { Text("${capability.displayName} · $label") },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (status == CapabilityStatus.READY) {
            { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        colors = AssistChipDefaults.assistChipColors(),
        modifier = Modifier.testTag("chip_${capability.name}"),
    )
}

@Composable
private fun LocationCard(state: SensorsUiState, nowMs: Long, actions: SensorsActions) {
    SectionCard(title = "Your location") {
        val location = state.location
        if (location == null) {
            Text(
                if (state.tracking.isRunning) "Waiting for a GPS fix…" else "Start tracking to see where you are.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.tracking.isRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Text(
                formatCoordinates(location.latitude, location.longitude),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("coordinates"),
            )
            val accuracy = location.accuracyMeters?.let { "±${it.toInt()} m · " }.orEmpty()
            Text(
                "$accuracy${formatAgo(nowMs, location.timestampMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.onCopyLocation(location) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                OutlinedButton(onClick = { actions.onOpenMaps(location) }) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in Maps")
                }
            }
        }
    }
}

@Composable
private fun DevicesHeader(state: SensorsUiState, actions: SensorsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val count = if (state.devices.size == state.totalDevices) "${state.totalDevices}" else "${state.devices.size} of ${state.totalDevices}"
        Text("Nearby devices ($count)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("device_search"),
            placeholder = { Text("Search by name or address") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { actions.onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.filter == DeviceFilter.ALL,
                onClick = { actions.onFilterChange(DeviceFilter.ALL) },
                label = { Text("All") },
                modifier = Modifier.testTag("filter_all"),
            )
            FilterChip(
                selected = state.filter == DeviceFilter.NEAR,
                onClick = { actions.onFilterChange(DeviceFilter.NEAR) },
                label = { Text("Near only") },
                modifier = Modifier.testTag("filter_near"),
            )
        }
    }
}

@Composable
private fun DevicesEmptyState(state: SensorsUiState) {
    val (title, message) = when {
        state.totalDevices > 0 -> "No matches" to "Try a different search or show all devices."
        !state.tracking.isRunning -> "Not scanning" to "Start tracking to look for nearby Bluetooth devices."
        state.capabilities.bluetooth != CapabilityStatus.READY ->
            "Bluetooth unavailable" to "Allow and turn on Bluetooth to discover devices."
        else -> "Scanning…" to "No devices found yet. Move closer to a Bluetooth device."
    }
    EmptyState(Icons.Default.Bluetooth, title, message, Modifier.testTag("devices_empty"))
}

@Composable
private fun DeviceRow(device: NearbyDevice, nowMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val tint by animateColorAsState(colors.forSignal(device.signal), label = "signalColor")
    val strength by animateFloatAsState(rssiStrength(device.rssiDbm), label = "strength")
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().testTag("device_${device.id}")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.name ?: "Unknown device", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${device.id} · ${formatAgo(nowMs, device.lastSeenMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${device.rssiDbm} dBm", style = MaterialTheme.typography.titleSmall, color = tint)
                    Text(
                        device.signal.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { strength },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Signal strength" },
                color = tint,
            )
        }
    }
}
