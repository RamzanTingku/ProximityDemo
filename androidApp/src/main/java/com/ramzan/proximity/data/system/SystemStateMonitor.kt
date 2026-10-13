package com.ramzan.proximity.data.system

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.nfc.NfcAdapter
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.SystemCapabilities
import com.ramzan.proximity.domain.repository.CapabilityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SystemStateMonitor(context: Context) : CapabilityRepository {
    private val context = context.applicationContext
    private val state = MutableStateFlow(read())
    override val capabilities: StateFlow<SystemCapabilities> = state

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this.context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun refresh() {
        state.value = read()
    }

    private fun read() = SystemCapabilities(
        location = locationStatus(),
        bluetooth = bluetoothStatus(),
        notifications = notificationStatus(),
        nfc = nfcStatus(),
        uwb = uwbStatus(),
    )

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun locationStatus(): CapabilityStatus {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            return CapabilityStatus.PERMISSION_MISSING
        }
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return CapabilityStatus.UNSUPPORTED
        return if (LocationManagerCompat.isLocationEnabled(manager)) {
            CapabilityStatus.READY
        } else {
            CapabilityStatus.DISABLED
        }
    }

    private fun bluetoothStatus(): CapabilityStatus {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return CapabilityStatus.UNSUPPORTED
        }
        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (!granted(scanPermission)) return CapabilityStatus.PERMISSION_MISSING
        return if (adapter.isEnabled) CapabilityStatus.READY else CapabilityStatus.DISABLED
    }

    private fun notificationStatus(): CapabilityStatus = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(Manifest.permission.POST_NOTIFICATIONS) -> CapabilityStatus.PERMISSION_MISSING
        !NotificationManagerCompat.from(context).areNotificationsEnabled() -> CapabilityStatus.DISABLED
        else -> CapabilityStatus.READY
    }

    private fun nfcStatus(): CapabilityStatus {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return CapabilityStatus.UNSUPPORTED
        return if (adapter.isEnabled) CapabilityStatus.READY else CapabilityStatus.DISABLED
    }

    private fun uwbStatus(): CapabilityStatus = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB) -> CapabilityStatus.UNSUPPORTED
        !granted(UWB_RANGING_PERMISSION) -> CapabilityStatus.PERMISSION_MISSING
        else -> CapabilityStatus.READY
    }

    companion object {
        const val UWB_RANGING_PERMISSION = "android.permission.UWB_RANGING"
    }
}
