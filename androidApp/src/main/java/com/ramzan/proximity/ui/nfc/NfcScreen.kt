package com.ramzan.proximity.ui.nfc

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.presentation.nfc.NfcHistoryItem
import com.ramzan.proximity.presentation.nfc.NfcMode
import com.ramzan.proximity.presentation.nfc.NfcUiState
import com.ramzan.proximity.presentation.nfc.NfcViewModel
import com.ramzan.proximity.ui.components.BannerCard
import com.ramzan.proximity.ui.components.EmptyState
import com.ramzan.proximity.ui.components.SectionCard
import com.ramzan.proximity.ui.formatClockTime
import com.ramzan.proximity.ui.permissions.EffectHandler
import com.ramzan.proximity.ui.theme.LocalStatusColors

class NfcActions(
    val onWrite: () -> Unit = {},
    val onCancelWrite: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onClearHistory: () -> Unit = {},
)

@Composable
fun NfcScreen(viewModel: NfcViewModel, snackbar: SnackbarHostState, contentPadding: PaddingValues) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EffectHandler(viewModel.effects, snackbar, onRequestPermissions = {})
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    NfcContent(
        state,
        NfcActions(
            onWrite = viewModel::onWriteTag,
            onCancelWrite = viewModel::onCancelWrite,
            onOpenSettings = viewModel::onOpenNfcSettings,
            onClearHistory = viewModel::onClearHistory,
        ),
        contentPadding,
    )
}

@Composable
fun NfcContent(
    state: NfcUiState,
    actions: NfcActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("nfc_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "availability") {
            when (state.availability) {
                CapabilityStatus.UNSUPPORTED -> BannerCard("This device has no NFC hardware.")
                CapabilityStatus.DISABLED ->
                    BannerCard("NFC is turned off.", actionLabel = "Turn on", onAction = actions.onOpenSettings)
                else -> Unit
            }
        }
        item(key = "actions") { ActionsCard(state, actions) }
        item(key = "history_header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.history.isNotEmpty()) TextButton(onClick = actions.onClearHistory) { Text("Clear") }
            }
        }
        if (state.history.isEmpty()) {
            item(key = "history_empty") {
                EmptyState(
                    Icons.Default.Nfc,
                    "Nothing yet",
                    "Tap a tag to read it, or write the demo text to a blank one.",
                    Modifier.testTag("history_empty"),
                )
            }
        } else {
            items(state.history, key = { "${it.timestampMs}-${it.message}" }) { HistoryRow(it) }
        }
    }
}

@Composable
private fun ActionsCard(state: NfcUiState, actions: NfcActions) {
    SectionCard {
        if (state.mode == NfcMode.WAITING_FOR_TAG) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Ripple() }
            Text(
                "Hold a writable tag against the back of the phone…",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = actions.onCancelWrite,
                modifier = Modifier.fillMaxWidth().testTag("cancel_write"),
            ) { Text("Cancel") }
        } else {
            Text(
                "Hold any NFC tag against the back of the phone to read it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = actions.onWrite,
                enabled = state.canWrite,
                modifier = Modifier.fillMaxWidth().testTag("write_tag"),
            ) { Text("Write demo tag") }
        }
    }
}

@Composable
private fun Ripple() {
    val transition = rememberInfiniteTransition(label = "ripple")
    val spread by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "spread",
    )
    val color = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(72.dp)
                .scale(spread)
                .alpha((1.6f - spread).coerceIn(0f, 1f) * 0.5f)
                .background(color, androidx.compose.foundation.shape.CircleShape),
        )
        Icon(Icons.Default.Nfc, contentDescription = null, tint = color, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun HistoryRow(item: NfcHistoryItem) {
    val colors = LocalStatusColors.current
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (item.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                contentDescription = if (item.isError) "Error" else "Success",
                tint = if (item.isError) colors.bad else colors.good,
            )
            Spacer(Modifier.width(12.dp))
            Text(item.message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(8.dp))
            Text(
                formatClockTime(item.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
