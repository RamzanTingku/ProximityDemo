package com.ramzan.proximity.data.sensor

import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult

/** Starts and stops the foreground service that keeps tracking alive in the background. */
interface TrackingServiceController {
    fun start(): DomainResult<Unit>
    fun stop()
}

/** Callbacks from the foreground service back into the repository that owns the sensors. */
interface TrackingServiceHost {
    fun onServiceStarted()
    fun onServiceFailed(error: DomainError)
    fun onServiceStopped()
}
