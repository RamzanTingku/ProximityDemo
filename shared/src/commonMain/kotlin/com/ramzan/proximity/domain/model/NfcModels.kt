package com.ramzan.proximity.domain.model

/** Result of a tag the user tapped while no write was pending. */
sealed interface NfcReadEvent {
    val timestampMs: Long

    data class Text(val text: String, override val timestampMs: Long) : NfcReadEvent
    data class NoTextRecord(override val timestampMs: Long) : NfcReadEvent
    data class Failed(val error: DomainError, override val timestampMs: Long) : NfcReadEvent
}
