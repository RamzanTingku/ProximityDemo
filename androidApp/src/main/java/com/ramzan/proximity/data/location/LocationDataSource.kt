package com.ramzan.proximity.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainException
import com.ramzan.proximity.domain.model.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationDataSource(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    /** Emits a fix as soon as one is known; fails with [DomainException] if updates cannot be started. */
    @SuppressLint("MissingPermission")
    fun updates(): Flow<GeoPoint> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toGeoPoint()) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { close(DomainException(DomainError.Unavailable(Capability.LOCATION))) }
            client.lastLocation.addOnSuccessListener { location -> location?.let { trySend(it.toGeoPoint()) } }
        } catch (_: SecurityException) {
            close(DomainException(DomainError.PermissionMissing(Capability.LOCATION)))
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun Location.toGeoPoint() = GeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        timestampMs = time,
    )

    private companion object {
        const val INTERVAL_MS = 5_000L
        const val MIN_INTERVAL_MS = 2_000L
    }
}
