package com.ramzan.proximity.domain.repository

import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.GeoPoint
import com.ramzan.proximity.domain.model.NearbyDevice
import com.ramzan.proximity.domain.model.TrackingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SensorRepository {
    val trackingState: StateFlow<TrackingState>
    val location: StateFlow<GeoPoint?>

    /** Hot stream of raw BLE advertisements; ranking and expiry are domain concerns. */
    val deviceSamples: Flow<NearbyDevice>

    suspend fun start(): DomainResult<Unit>
    fun stop()
}
