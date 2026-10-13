package com.ramzan.proximity.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ramzan.proximity.MainActivity
import com.ramzan.proximity.data.sensor.TrackingServiceHost
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.repository.SensorRepository
import org.koin.android.ext.android.inject

/** Keeps the process alive with a visible notification; the sensors live in [SensorRepository]. */
class SensorTrackingService : Service() {
    private val host: TrackingServiceHost by inject()
    private val sensors: SensorRepository by inject()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            sensors.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification(), foregroundServiceTypes())
        } catch (error: RuntimeException) {
            host.onServiceFailed(DomainError.ForegroundStartFailed(error.message))
            stopSelf()
            return START_NOT_STICKY
        }
        host.onServiceStarted()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        host.onServiceStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun foregroundServiceTypes(): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        return types
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Proximity Demo")
        .setContentText("Location and nearby-device tracking are active")
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            PendingIntent.getService(
                this,
                1,
                Intent(this, SensorTrackingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Proximity tracking", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val ACTION_START = "com.ramzan.proximity.action.START_TRACKING"
        const val ACTION_STOP = "com.ramzan.proximity.action.STOP_TRACKING"
        private const val CHANNEL_ID = "proximity_tracking"
        private const val NOTIFICATION_ID = 1
    }
}
