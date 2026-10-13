package com.ramzan.proximity.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DomainResultTest {
    @Test
    fun mapTransformsSuccessOnly() {
        assertEquals(DomainResult.Success(4), DomainResult.Success(2).map { it * 2 })
        val failure: DomainResult<Int> = DomainResult.Failure(DomainError.Timeout)
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun flatMapShortCircuitsOnFailure() {
        var called = false
        val failure: DomainResult<Int> = DomainResult.Failure(DomainError.Timeout)
        failure.flatMap { called = true; DomainResult.Success(it) }
        assertEquals(false, called)
    }

    @Test
    fun accessorsReturnNullForTheOtherCase() {
        assertNull(DomainResult.Success(1).errorOrNull())
        assertNull(DomainResult.Failure(DomainError.Timeout).getOrNull())
        assertEquals(1, DomainResult.Success(1).getOrNull())
    }

    @Test
    fun offerToStringNeverLeaksTheSessionKey() {
        val text = ControllerOffer(1, ByteArray(16) { 7 }, byteArrayOf(1), 9, 10).toString()
        assertEquals(false, text.contains("7, 7"))
    }
}
