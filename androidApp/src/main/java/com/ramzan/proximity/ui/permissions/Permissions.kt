package com.ramzan.proximity.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ramzan.proximity.data.system.SystemStateMonitor
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.presentation.SettingsTarget

fun Capability.runtimePermissions(): List<String> = when (this) {
    Capability.LOCATION -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    Capability.BLUETOOTH ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyList()
        }
    Capability.NOTIFICATIONS ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    Capability.UWB ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            listOf(SystemStateMonitor.UWB_RANGING_PERMISSION)
        } else {
            emptyList()
        }
    Capability.NFC -> emptyList()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Returns a function that asks for the runtime permissions behind the given capabilities.
 * [onResult] receives the capabilities the user blocked for good ("Don't ask again"), which can
 * only be fixed from system settings.
 */
@Composable
fun rememberPermissionRequester(onResult: (permanentlyDenied: Set<Capability>) -> Unit): (Set<Capability>) -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var requested by remember { mutableStateOf<Set<Capability>>(emptySet()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val activity = context.findActivity()
        val blocked = requested.filterTo(mutableSetOf()) { capability ->
            capability.runtimePermissions().any { permission ->
                !context.isGranted(permission) &&
                    activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
        }
        currentOnResult(blocked)
    }

    return { capabilities ->
        val missing = capabilities.flatMap { it.runtimePermissions() }.distinct().filterNot(context::isGranted)
        requested = capabilities
        if (missing.isEmpty()) currentOnResult(emptySet()) else launcher.launch(missing.toTypedArray())
    }
}

fun Context.openSettings(target: SettingsTarget) {
    val intent = when (target) {
        SettingsTarget.APP -> appSettingsIntent()
        SettingsTarget.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        SettingsTarget.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        SettingsTarget.NFC -> Intent(Settings.ACTION_NFC_SETTINGS)
        SettingsTarget.NOTIFICATIONS ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Some OEM builds lack individual settings screens; app details always exist.
        startActivity(appSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun Context.appSettingsIntent() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
