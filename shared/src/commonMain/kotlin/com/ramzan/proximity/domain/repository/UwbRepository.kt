package com.ramzan.proximity.domain.repository

import com.ramzan.proximity.domain.model.ControleeAnswer
import com.ramzan.proximity.domain.model.ControllerOffer
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.UwbState
import kotlinx.coroutines.flow.StateFlow

interface UwbRepository {
    val state: StateFlow<UwbState>

    suspend fun createControllerOffer(): DomainResult<ControllerOffer>

    /** Joins as responder and starts ranging immediately; the returned answer must reach the controller. */
    suspend fun joinAsControlee(offer: ControllerOffer): DomainResult<ControleeAnswer>

    suspend fun startControllerRanging(answer: ControleeAnswer): DomainResult<Unit>

    fun stopRanging()
}
