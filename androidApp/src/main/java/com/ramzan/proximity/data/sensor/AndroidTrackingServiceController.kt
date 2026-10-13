package com.ramzan.proximity.data.sensor

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.service.SensorTrackingService

class AndroidTrackingServiceController(context: Context) : TrackingServiceController {
    private val context = context.applicationContext

    override fun start(): DomainResult<Unit> = try {
        ContextCompat.startForegroundService(
            context,
            Intent(context, SensorTrackingService::class.java).setAction(SensorTrackingService.ACTION_START),
        )
        DomainResult.Success(Unit)
    } catch (error: RuntimeException) {
        // Background-start restrictions and missing foreground-service permissions surface here.
        DomainResult.Failure(DomainError.ForegroundStartFailed(error.message))
    }

    override fun stop() {
        context.stopService(Intent(context, SensorTrackingService::class.java))
    }
}
