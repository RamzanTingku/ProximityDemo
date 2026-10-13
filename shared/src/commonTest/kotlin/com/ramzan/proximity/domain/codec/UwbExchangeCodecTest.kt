package com.ramzan.proximity.domain.codec

import com.ramzan.proximity.domain.model.ControleeAnswer
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.UwbCodeKind
import com.ramzan.proximity.testing.sampleOffer
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UwbExchangeCodecTest {
    private val validKey = Base64.encode(ByteArray(16) { it.toByte() })
    private val validAddress = Base64.encode(byteArrayOf(1, 2))

    private fun assertInvalid(result: DomainResult<*>) {
        val failure = assertIs<DomainResult.Failure>(result)
        assertIs<DomainError.InvalidCode>(failure.error)
    }

    @Test
    fun offerRoundTrips() {
        val offer = sampleOffer()
        val decoded = UwbExchangeCodec.decodeOffer(UwbExchangeCodec.encode(offer))
        assertEquals(offer, assertIs<DomainResult.Success<*>>(decoded).value)
    }

    @Test
    fun answerRoundTrips() {
        val answer = ControleeAnswer(byteArrayOf(9, 8))
        val decoded = UwbExchangeCodec.decodeAnswer(UwbExchangeCodec.encode(answer))
        assertEquals(answer, assertIs<DomainResult.Success<*>>(decoded).value)
    }

    @Test
    fun surroundingWhitespaceFromPastingIsIgnored() {
        val code = "  \n" + UwbExchangeCodec.encode(sampleOffer()) + "\n "
        assertIs<DomainResult.Success<*>>(UwbExchangeCodec.decodeOffer(code))
    }

    @Test
    fun emptyAndBlankCodesAreInvalid() {
        assertInvalid(UwbExchangeCodec.decodeOffer(""))
        assertInvalid(UwbExchangeCodec.decodeOffer("   "))
        assertInvalid(UwbExchangeCodec.decodeAnswer(""))
    }

    @Test
    fun wrongVersionOrKindIsRejected() {
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb1|42|$validKey|$validAddress|9|10"))
        assertInvalid(UwbExchangeCodec.decodeOffer(UwbExchangeCodec.encode(ControleeAnswer(byteArrayOf(1)))))
        assertInvalid(UwbExchangeCodec.decodeAnswer(UwbExchangeCodec.encode(sampleOffer())))
    }

    @Test
    fun truncatedOfferIsRejected() {
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|42|$validKey|$validAddress|9"))
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|42|$validKey|$validAddress|9|10|extra"))
    }

    @Test
    fun badBase64IsRejected() {
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|42|!!!notbase64|$validAddress|9|10"))
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|42|$validKey|!!!|9|10"))
        assertInvalid(UwbExchangeCodec.decodeAnswer("uwb2r|!!!"))
    }

    @Test
    fun sessionKeyMustBeExactly16Bytes() {
        val shortKey = Base64.encode(ByteArray(15))
        val longKey = Base64.encode(ByteArray(17))
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|42|$shortKey|$validAddress|9|10"))
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|42|$longKey|$validAddress|9|10"))
    }

    @Test
    fun numericFieldsAreRangeChecked() {
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|abc|$validKey|$validAddress|9|10"))
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|0|$validKey|$validAddress|9|10"))
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|65536|$validKey|$validAddress|9|10"))
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|42|$validKey|$validAddress|-1|10"))
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|42|$validKey|$validAddress|16|10"))
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|42|$validKey|$validAddress|9|33"))
        assertIs<DomainResult.Success<*>>(UwbExchangeCodec.decodeOffer("uwb2|65535|$validKey|$validAddress|0|0"))
    }

    @Test
    fun addressMustBeNonEmptyAndAtMostEightBytes() {
        assertInvalid(UwbExchangeCodec.decodeOffer("uwb2|42|$validKey||9|10"))
        assertInvalid(UwbExchangeCodec.decodeAnswer("uwb2r|"))
        assertInvalid(UwbExchangeCodec.decodeAnswer("uwb2r|${Base64.encode(ByteArray(9))}"))
    }

    @Test
    fun detectKindIdentifiesCodeTypeWithoutFullValidation() {
        assertEquals(UwbCodeKind.OFFER, UwbExchangeCodec.detectKind("uwb2|anything"))
        assertEquals(UwbCodeKind.ANSWER, UwbExchangeCodec.detectKind(" uwb2r|anything"))
        assertNull(UwbExchangeCodec.detectKind("hello"))
        assertNull(UwbExchangeCodec.detectKind(""))
    }
}
