package com.ramzan.proximity.data

import com.ramzan.proximity.data.nfc.decodeTextPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NdefTextTest {
    private fun payload(text: String, lang: String = "en", utf16: Boolean = false): ByteArray {
        val status = (if (utf16) 0x80 else 0) or lang.length
        val body = text.toByteArray(if (utf16) Charsets.UTF_16BE else Charsets.UTF_8)
        return byteArrayOf(status.toByte()) + lang.toByteArray() + body
    }

    @Test
    fun `decodes utf8 text`() = assertEquals("hello", decodeTextPayload(payload("hello")))

    @Test
    fun `decodes utf8 with non-ascii`() = assertEquals("héllo ✓", decodeTextPayload(payload("héllo ✓")))

    @Test
    fun `decodes utf16 text`() {
        val bytes = byteArrayOf(0x82.toByte(), 'e'.code.toByte(), 'n'.code.toByte()) +
            "hi".toByteArray(Charsets.UTF_16BE)
        assertEquals("hi", decodeTextPayload(bytes))
    }

    @Test
    fun `empty payload is rejected`() = assertNull(decodeTextPayload(ByteArray(0)))

    @Test
    fun `language length beyond payload is rejected`() =
        assertNull(decodeTextPayload(byteArrayOf(0x3f, 'e'.code.toByte())))

    @Test
    fun `empty text is allowed`() = assertEquals("", decodeTextPayload(payload("")))
}
