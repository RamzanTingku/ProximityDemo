package com.ramzan.proximity.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ProximitySignalTest {
    @Test
    fun bandBoundariesAreInclusiveOnTheStrongerSide() {
        assertEquals(ProximitySignal.NEAR, proximitySignal(-30))
        assertEquals(ProximitySignal.NEAR, proximitySignal(-60))
        assertEquals(ProximitySignal.MEDIUM, proximitySignal(-61))
        assertEquals(ProximitySignal.MEDIUM, proximitySignal(-75))
        assertEquals(ProximitySignal.FAR, proximitySignal(-76))
        assertEquals(ProximitySignal.FAR, proximitySignal(-120))
    }

    @Test
    fun deviceExposesItsSignalBand() {
        assertEquals(ProximitySignal.NEAR, NearbyDevice("a", null, -50).signal)
    }

    @Test
    fun trackingStateRunningOnlyWhileStartingOrActive() {
        assertEquals(false, TrackingState(TrackingStatus.STOPPED).isRunning)
        assertEquals(true, TrackingState(TrackingStatus.STARTING).isRunning)
        assertEquals(true, TrackingState(TrackingStatus.ACTIVE).isRunning)
        assertEquals(false, TrackingState(TrackingStatus.FAILED).isRunning)
    }
}
