package com.ramzan.proximity.domain.model

enum class UwbStatus {
    IDLE,
    OFFER_READY,
    WAITING_FOR_PEER,
    RANGING,
    PEER_LOST,
    FAILED,
}

data class UwbState(
    val status: UwbStatus = UwbStatus.IDLE,
    val distanceMeters: Float? = null,
    val error: DomainError? = null,
)

class ControllerOffer(
    val sessionId: Int,
    val sessionKey: ByteArray,
    val address: ByteArray,
    val channel: Int,
    val preambleIndex: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is ControllerOffer &&
            sessionId == other.sessionId &&
            sessionKey.contentEquals(other.sessionKey) &&
            address.contentEquals(other.address) &&
            channel == other.channel &&
            preambleIndex == other.preambleIndex

    override fun hashCode(): Int {
        var result = sessionId
        result = 31 * result + sessionKey.contentHashCode()
        result = 31 * result + address.contentHashCode()
        result = 31 * result + channel
        result = 31 * result + preambleIndex
        return result
    }

    // Deliberately omits the session key so it never reaches logs.
    override fun toString(): String = "ControllerOffer(sessionId=$sessionId, channel=$channel, preamble=$preambleIndex)"
}

class ControleeAnswer(val address: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is ControleeAnswer && address.contentEquals(other.address)

    override fun hashCode(): Int = address.contentHashCode()

    override fun toString(): String = "ControleeAnswer(addressSize=${address.size})"
}

enum class UwbCodeKind {
    OFFER,
    ANSWER,
}
