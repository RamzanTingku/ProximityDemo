package com.ramzan.proximity.ui.permissions

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.presentation.UiEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Performs the one-shot [UiEffect]s a ViewModel emits. */
@Composable
fun EffectHandler(
    effects: Flow<UiEffect>,
    snackbar: SnackbarHostState,
    onRequestPermissions: (Set<Capability>) -> Unit,
) {
    val context = LocalContext.current
    val request by rememberUpdatedState(onRequestPermissions)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowMessage -> launch {
                    val result = snackbar.showSnackbar(
                        message = effect.text,
                        actionLabel = effect.settings?.let { "Settings" },
                        duration = if (effect.settings != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) effect.settings?.let(context::openSettings)
                }
                is UiEffect.RequestPermissions -> request(effect.capabilities)
                is UiEffect.OpenSettings -> context.openSettings(effect.target)
                is UiEffect.Share -> context.shareText(effect.text)
            }
        }
    }
}
