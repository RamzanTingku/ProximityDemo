package com.ramzan.proximity.domain.model

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val timestampMs: Long = 0L,
)

data class NearbyDevice(
    val id: String,
    val name: String?,
    val rssiDbm: Int,
    val lastSeenMs: Long = 0L,
) {
    val signal: ProximitySignal
        get() = proximitySignal(rssiDbm)
}

enum class ProximitySignal {
    NEAR,
    MEDIUM,
    FAR,
}

fun proximitySignal(rssiDbm: Int): ProximitySignal = when {
    rssiDbm >= NEAR_RSSI_DBM -> ProximitySignal.NEAR
    rssiDbm >= MEDIUM_RSSI_DBM -> ProximitySignal.MEDIUM
    else -> ProximitySignal.FAR
}

private const val NEAR_RSSI_DBM = -60
private const val MEDIUM_RSSI_DBM = -75

enum class TrackingStatus {
    STOPPED,
    STARTING,
    ACTIVE,
    FAILED,
}

/** [error] on an ACTIVE state is a non-fatal warning, e.g. BLE failed while location still works. */
data class TrackingState(
    val status: TrackingStatus = TrackingStatus.STOPPED,
    val error: DomainError? = null,
) {
    val isRunning: Boolean
        get() = status == TrackingStatus.STARTING || status == TrackingStatus.ACTIVE
}
