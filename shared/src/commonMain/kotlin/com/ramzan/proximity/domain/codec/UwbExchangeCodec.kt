package com.ramzan.proximity.domain.codec

import com.ramzan.proximity.domain.model.ControleeAnswer
import com.ramzan.proximity.domain.model.ControllerOffer
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.UwbCodeKind
import kotlin.io.encoding.Base64

/**
 * Text format for the manual out-of-band UWB parameter exchange.
 *
 * Offer:  `uwb2|sessionId|base64(key)|base64(address)|channel|preamble`
 * Answer: `uwb2r|base64(address)`
 */
object UwbExchangeCodec {
    const val SESSION_KEY_SIZE = 16
    const val MAX_SESSION_ID = 65_535
    const val MAX_ADDRESS_SIZE = 8
    const val MAX_CHANNEL = 15
    const val MAX_PREAMBLE_INDEX = 32

    private const val OFFER_TAG = "uwb2"
    private const val ANSWER_TAG = "uwb2r"
    private const val SEPARATOR = '|'

    fun encode(offer: ControllerOffer): String = listOf(
        OFFER_TAG,
        offer.sessionId.toString(),
        Base64.encode(offer.sessionKey),
        Base64.encode(offer.address),
        offer.channel.toString(),
        offer.preambleIndex.toString(),
    ).joinToString(SEPARATOR.toString())

    fun encode(answer: ControleeAnswer): String =
        listOf(ANSWER_TAG, Base64.encode(answer.address)).joinToString(SEPARATOR.toString())

    fun detectKind(code: String): UwbCodeKind? = when (code.trim().substringBefore(SEPARATOR)) {
        OFFER_TAG -> UwbCodeKind.OFFER
        ANSWER_TAG -> UwbCodeKind.ANSWER
        else -> null
    }

    fun decodeOffer(code: String): DomainResult<ControllerOffer> {
        val parts = code.trim().split(SEPARATOR)
        if (parts.firstOrNull() != OFFER_TAG) return invalid("Not a controller offer")
        if (parts.size != 6) return invalid("Offer is incomplete; copy the whole code")

        val sessionId = parts[1].toIntOrNull()
        if (sessionId == null || sessionId !in 1..MAX_SESSION_ID) return invalid("Offer has an invalid session id")

        val key = decodeBase64(parts[2])
        if (key == null || key.size != SESSION_KEY_SIZE) return invalid("Offer has an invalid session key")

        val address = decodeAddress(parts[3]) ?: return invalid("Offer has an invalid device address")

        val channel = parts[4].toIntOrNull()
        if (channel == null || channel !in 0..MAX_CHANNEL) return invalid("Offer has an invalid channel")

        val preamble = parts[5].toIntOrNull()
        if (preamble == null || preamble !in 0..MAX_PREAMBLE_INDEX) return invalid("Offer has an invalid preamble")

        return DomainResult.Success(ControllerOffer(sessionId, key, address, channel, preamble))
    }

    fun decodeAnswer(code: String): DomainResult<ControleeAnswer> {
        val parts = code.trim().split(SEPARATOR)
        if (parts.firstOrNull() != ANSWER_TAG) return invalid("Not a responder answer")
        if (parts.size != 2) return invalid("Answer is incomplete; copy the whole code")
        val address = decodeAddress(parts[1]) ?: return invalid("Answer has an invalid device address")
        return DomainResult.Success(ControleeAnswer(address))
    }

    private fun decodeAddress(value: String): ByteArray? =
        decodeBase64(value)?.takeIf { it.isNotEmpty() && it.size <= MAX_ADDRESS_SIZE }

    private fun decodeBase64(value: String): ByteArray? =
        try {
            Base64.decode(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun invalid(reason: String): DomainResult.Failure =
        DomainResult.Failure(DomainError.InvalidCode(reason))
}
