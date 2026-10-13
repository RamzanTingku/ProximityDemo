package com.ramzan.proximity.domain.usecase

import com.ramzan.proximity.domain.codec.UwbExchangeCodec
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.ControleeAnswer
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.testing.FakeCapabilityRepository
import com.ramzan.proximity.testing.FakeUwbRepository
import com.ramzan.proximity.testing.allReady
import com.ramzan.proximity.testing.sampleOffer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UwbUseCasesTest {
    private val uwb = FakeUwbRepository()
    private val capabilities = FakeCapabilityRepository()

    private val create = CreateUwbOfferUseCase(uwb, capabilities)
    private val accept = AcceptUwbOfferUseCase(uwb, capabilities)
    private val startRanging = StartUwbRangingUseCase(uwb, capabilities)

    @Test
    fun createOfferReturnsADecodableCode() = runTest {
        val code = assertIs<DomainResult.Success<String>>(create()).value
        assertIs<DomainResult.Success<*>>(UwbExchangeCodec.decodeOffer(code))
    }

    @Test
    fun createOfferIsBlockedWhenUwbUnsupported() = runTest {
        capabilities.set(allReady.copy(uwb = CapabilityStatus.UNSUPPORTED))
        val failure = assertIs<DomainResult.Failure>(create())
        assertEquals(DomainError.Unsupported(Capability.UWB), failure.error)
    }

    @Test
    fun createOfferAsksForPermissionWhenMissing() = runTest {
        capabilities.set(allReady.copy(uwb = CapabilityStatus.PERMISSION_MISSING))
        val failure = assertIs<DomainResult.Failure>(create())
        assertEquals(DomainError.PermissionMissing(Capability.UWB), failure.error)
    }

    @Test
    fun createOfferPropagatesRepositoryFailure() = runTest {
        uwb.offerResult = DomainResult.Failure(DomainError.Unavailable(Capability.UWB))
        assertEquals(
            DomainError.Unavailable(Capability.UWB),
            assertIs<DomainResult.Failure>(create()).error,
        )
    }

    @Test
    fun acceptDecodesTheOfferAndReturnsAnAnswerCode() = runTest {
        val offer = sampleOffer(sessionId = 7)
        val answerCode = assertIs<DomainResult.Success<String>>(accept(UwbExchangeCodec.encode(offer))).value

        assertEquals(offer, uwb.joinedOffer)
        assertIs<DomainResult.Success<*>>(UwbExchangeCodec.decodeAnswer(answerCode))
    }

    @Test
    fun acceptRejectsGarbageWithoutTouchingTheRadio() = runTest {
        val failure = assertIs<DomainResult.Failure>(accept("not a code"))
        assertIs<DomainError.InvalidCode>(failure.error)
        assertNull(uwb.joinedOffer)
    }

    @Test
    fun acceptRejectsAnAnswerCodePastedByMistake() = runTest {
        val answer = UwbExchangeCodec.encode(ControleeAnswer(byteArrayOf(1, 2)))
        assertIs<DomainError.InvalidCode>(assertIs<DomainResult.Failure>(accept(answer)).error)
    }

    @Test
    fun validationErrorWinsOverMissingCapability() = runTest {
        capabilities.set(allReady.copy(uwb = CapabilityStatus.UNSUPPORTED))
        assertIs<DomainError.InvalidCode>(assertIs<DomainResult.Failure>(accept("junk")).error)
    }

    @Test
    fun startRangingDecodesTheAnswer() = runTest {
        val answer = ControleeAnswer(byteArrayOf(5, 6))
        assertIs<DomainResult.Success<*>>(startRanging(UwbExchangeCodec.encode(answer)))
        assertEquals(answer, uwb.startedAnswer)
    }

    @Test
    fun startRangingRejectsAnOfferCode() = runTest {
        val failure = assertIs<DomainResult.Failure>(startRanging(UwbExchangeCodec.encode(sampleOffer())))
        assertIs<DomainError.InvalidCode>(failure.error)
        assertNull(uwb.startedAnswer)
    }

    @Test
    fun startRangingPropagatesMissingOffer() = runTest {
        uwb.startResult = DomainResult.Failure(DomainError.NoActiveOffer)
        val code = UwbExchangeCodec.encode(ControleeAnswer(byteArrayOf(1)))
        assertEquals(DomainError.NoActiveOffer, assertIs<DomainResult.Failure>(startRanging(code)).error)
        assertNotNull(uwb.startedAnswer)
    }

    @Test
    fun stopDelegatesToTheRepository() {
        StopUwbRangingUseCase(uwb)()
        assertEquals(1, uwb.stopCalls)
    }
}
