package com.ramzan.proximity.domain.repository

import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.NfcReadEvent
import kotlinx.coroutines.flow.Flow

interface NfcRepository {
    /** Tags tapped while no write is pending. */
    val readEvents: Flow<NfcReadEvent>

    /** Suspends until the next tag is tapped, then writes [text] to it. Cancelling aborts the pending write. */
    suspend fun writeNextTag(text: String): DomainResult<Unit>
}
