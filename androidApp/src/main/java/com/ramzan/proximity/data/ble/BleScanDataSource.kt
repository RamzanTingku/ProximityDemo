package com.ramzan.proximity.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainException
import com.ramzan.proximity.domain.model.NearbyDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BleScanDataSource(context: Context) {
    private val context = context.applicationContext

    /** Fails with [DomainException] when Bluetooth is off, permission is missing or the scan is rejected. */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<NearbyDevice> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            close(DomainException(DomainError.ServiceDisabled(Capability.BLUETOOTH)))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toDevice())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toDevice()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(DomainException(DomainError.ScanFailed(errorCode)))
            }
        }

        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        } catch (_: SecurityException) {
            close(DomainException(DomainError.PermissionMissing(Capability.BLUETOOTH)))
        } catch (_: IllegalStateException) {
            close(DomainException(DomainError.ServiceDisabled(Capability.BLUETOOTH)))
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
                // Bluetooth may already be off or permission revoked; nothing left to stop.
            }
        }
    }

    // scanRecord.deviceName avoids needing BLUETOOTH_CONNECT just to read a name.
    private fun ScanResult.toDevice() = NearbyDevice(
        id = device.address,
        name = scanRecord?.deviceName,
        rssiDbm = rssi,
    )
}
