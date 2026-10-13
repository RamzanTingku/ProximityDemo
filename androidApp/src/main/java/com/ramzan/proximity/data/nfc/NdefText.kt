package com.ramzan.proximity.data.nfc

/**
 * Decodes the payload of an NFC Forum "Text" record: a status byte (bit 7 = UTF-16,
 * bits 0-5 = language code length), the language code, then the text.
 */
internal fun decodeTextPayload(payload: ByteArray): String? {
    if (payload.isEmpty()) return null
    val status = payload[0].toInt()
    val languageLength = status and 0x3f
    if (languageLength + 1 > payload.size) return null
    val charset = if (status and 0x80 == 0) Charsets.UTF_8 else Charsets.UTF_16
    return String(payload, languageLength + 1, payload.size - languageLength - 1, charset)
}
