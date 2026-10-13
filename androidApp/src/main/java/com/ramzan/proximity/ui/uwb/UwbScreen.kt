package com.ramzan.proximity.ui.uwb

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramzan.proximity.domain.model.UwbCodeKind
import com.ramzan.proximity.domain.model.UwbStatus
import com.ramzan.proximity.presentation.toMessage
import com.ramzan.proximity.presentation.uwb.UwbStep
import com.ramzan.proximity.presentation.uwb.UwbUiState
import com.ramzan.proximity.presentation.uwb.UwbViewModel
import com.ramzan.proximity.ui.components.BannerCard
import com.ramzan.proximity.ui.components.EmptyState
import com.ramzan.proximity.ui.components.SectionCard
import com.ramzan.proximity.ui.formatDistance
import com.ramzan.proximity.ui.permissions.EffectHandler
import com.ramzan.proximity.ui.permissions.copyToClipboard
import com.ramzan.proximity.ui.permissions.readClipboardText
import com.ramzan.proximity.ui.permissions.rememberPermissionRequester
import com.ramzan.proximity.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

class UwbActions(
    val onCreateOffer: () -> Unit = {},
    val onAcceptOffer: () -> Unit = {},
    val onStartRanging: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onInputChange: (String) -> Unit = {},
    val onCopyOwnCode: () -> Unit = {},
    val onShareOwnCode: () -> Unit = {},
    val onPaste: () -> Unit = {},
)

@Composable
fun UwbScreen(viewModel: UwbViewModel, snackbar: SnackbarHostState, contentPadding: PaddingValues) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val requestPermissions = rememberPermissionRequester { viewModel.onPermissionResult(it) }
    EffectHandler(viewModel.effects, snackbar, requestPermissions)

    val actions = UwbActions(
        onCreateOffer = viewModel::onCreateOffer,
        onAcceptOffer = viewModel::onAcceptOffer,
        onStartRanging = viewModel::onStartRanging,
        onStop = viewModel::onStop,
        onInputChange = viewModel::onInputChanged,
        onCopyOwnCode = {
            context.copyToClipboard("UWB code", state.ownCode)
            scope.launch { snackbar.showSnackbar("Code copied") }
        },
        onShareOwnCode = viewModel::onShare,
        onPaste = {
            val text = context.readClipboardText()?.trim()
            if (text.isNullOrEmpty()) {
                scope.launch { snackbar.showSnackbar("Clipboard is empty") }
            } else {
                viewModel.onInputChanged(text)
            }
        },
    )
    UwbContent(state, actions, contentPadding)
}

@Composable
fun UwbContent(
    state: UwbUiState,
    actions: UwbActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.isSupported) {
            SectionCard {
                EmptyState(
                    Icons.Default.SettingsInputAntenna,
                    "UWB isn't available",
                    "Ultra-wideband ranging needs Android 14+ and UWB hardware on both devices. " +
                        "Emulators don't have it.",
                    Modifier.testTag("uwb_unsupported"),
                )
            }
            return@Column
        }
        if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        StepHeader(state.step)
        state.uwb.error?.let { BannerCard(it.toMessage()) }
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "uwbStep",
        ) { step ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    UwbStep.START -> StartStep(state, actions)
                    UwbStep.EXCHANGE -> ExchangeStep(state, actions)
                    UwbStep.RANGING -> RangingStep(state, actions)
                }
            }
        }
    }
}

@Composable
private fun StepHeader(current: UwbStep) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(UwbStep.START to "1 Start", UwbStep.EXCHANGE to "2 Exchange", UwbStep.RANGING to "3 Range")
            .forEach { (step, label) ->
                val active = step.ordinal <= current.ordinal
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
    }
}

@Composable
private fun StartStep(state: UwbUiState, actions: UwbActions) {
    SectionCard(title = "I'm starting the session") {
        Text(
            "Create an offer, send it to the other phone, then paste the answer it sends back.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = actions.onCreateOffer,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().testTag("create_offer"),
        ) { Text("Create offer") }
    }
    SectionCard(title = "I received an offer") {
        CodeInput(state, actions, label = "Paste the offer here")
        Button(
            onClick = actions.onAcceptOffer,
            enabled = state.canAcceptOffer,
            modifier = Modifier.fillMaxWidth().testTag("accept_offer"),
        ) { Text("Accept offer") }
    }
}

@Composable
private fun ExchangeStep(state: UwbUiState, actions: UwbActions) {
    OwnCodeCard(state, actions, "Send this offer to the other phone")
    SectionCard(title = "Paste the answer") {
        CodeInput(state, actions, label = "Paste the answer here")
        Button(
            onClick = actions.onStartRanging,
            enabled = state.canStartRanging,
            modifier = Modifier.fillMaxWidth().testTag("start_ranging"),
        ) { Text("Start ranging") }
    }
    TextButton(onClick = actions.onStop, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
}

@Composable
private fun RangingStep(state: UwbUiState, actions: UwbActions) {
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    val colors = LocalStatusColors.current
    val status = state.uwb.status
    val (statusText, statusColor) = when (status) {
        UwbStatus.WAITING_FOR_PEER -> "Waiting for the other phone…" to colors.warning
        UwbStatus.RANGING -> "Ranging" to colors.good
        UwbStatus.PEER_LOST -> "Lost contact with the other phone" to colors.warning
        UwbStatus.FAILED -> "Session failed" to colors.bad
        else -> "Starting…" to colors.neutral
    }
    val distance = state.uwb.distanceMeters
    val progress by animateFloatAsState(
        targetValue = distance?.let { (1f - it / MAX_GAUGE_METERS).coerceIn(0.02f, 1f) } ?: 0f,
        label = "gauge",
    )
    SectionCard {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (distance == null && status != UwbStatus.FAILED) {
                CircularProgressIndicator(Modifier.size(160.dp), strokeWidth = 8.dp)
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(160.dp),
                    strokeWidth = 8.dp,
                    color = statusColor,
                )
            }
            Text(
                distance?.let(::formatDistance) ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.testTag("distance"),
            )
        }
        Text(
            statusText,
            color = statusColor,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (state.ownCodeKind == UwbCodeKind.ANSWER) {
        OwnCodeCard(state, actions, "Send this answer back to the other phone")
    }
    OutlinedButton(
        onClick = { confirmStop = true },
        modifier = Modifier.fillMaxWidth().testTag("stop_session"),
    ) { Text("Stop session") }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop ranging?") },
            text = { Text("The session ends and you'll need to exchange new codes to start again.") },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; actions.onStop() }) { Text("Stop") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun OwnCodeCard(state: UwbUiState, actions: UwbActions, title: String) {
    SectionCard(title = title) {
        OutlinedTextField(
            value = state.ownCode,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().testTag("own_code"),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 4,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.onCopyOwnCode) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            OutlinedButton(onClick = actions.onShareOwnCode) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        }
    }
}

@Composable
private fun CodeInput(state: UwbUiState, actions: UwbActions, label: String) {
    OutlinedTextField(
        value = state.input,
        onValueChange = actions.onInputChange,
        modifier = Modifier.fillMaxWidth().testTag("code_input"),
        label = { Text(label) },
        isError = state.inputError != null,
        supportingText = state.inputError?.let { { Text(it) } },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        maxLines = 4,
        trailingIcon = {
            if (state.input.isEmpty()) {
                IconButton(onClick = actions.onPaste) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                }
            } else {
                IconButton(onClick = { actions.onInputChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
    )
}

private const val MAX_GAUGE_METERS = 10f
