package com.ramzan.proximity.domain.usecase

import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.TagProblem
import com.ramzan.proximity.testing.FakeCapabilityRepository
import com.ramzan.proximity.testing.FakeNfcRepository
import com.ramzan.proximity.testing.allReady
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WriteNfcTagUseCaseTest {
    private val nfc = FakeNfcRepository()
    private val capabilities = FakeCapabilityRepository()
    private val write = WriteNfcTagUseCase(nfc, capabilities, timeoutMs = 15_000)

    @Test
    fun writesTheDemoPayloadWhenATagIsTapped() = runTest {
        val result = async { write() }
        runCurrent()
        nfc.pendingWrite!!.complete(DomainResult.Success(Unit))

        assertIs<DomainResult.Success<*>>(result.await())
        assertEquals(WriteNfcTagUseCase.DEMO_PAYLOAD, nfc.writtenText)
    }

    @Test
    fun givesUpWithTimeoutWhenNoTagIsTapped() = runTest {
        val result = async { write() }
        runCurrent()
        advanceTimeBy(15_001)
        advanceUntilIdle()

        assertEquals(DomainError.Timeout, assertIs<DomainResult.Failure>(result.await()).error)
        assertTrue(nfc.writeCancelled)
    }

    @Test
    fun aTapJustBeforeTheTimeoutStillSucceeds() = runTest {
        val result = async { write() }
        runCurrent()
        advanceTimeBy(14_000)
        nfc.pendingWrite!!.complete(DomainResult.Success(Unit))

        assertIs<DomainResult.Success<*>>(result.await())
    }

    @Test
    fun cancellingTheCallerAbortsThePendingWrite() = runTest {
        val job = async { write() }
        runCurrent()
        job.cancel()
        advanceUntilIdle()

        assertTrue(nfc.writeCancelled)
    }

    @Test
    fun tagProblemsAreReportedToTheCaller() = runTest {
        TagProblem.entries.forEach { problem ->
            val fresh = FakeNfcRepository()
            val useCase = WriteNfcTagUseCase(fresh, capabilities)
            val result = async { useCase() }
            runCurrent()
            fresh.pendingWrite!!.complete(DomainResult.Failure(DomainError.Tag(problem)))
            assertEquals(DomainError.Tag(problem), assertIs<DomainResult.Failure>(result.await()).error)
        }
    }

    @Test
    fun disabledNfcFailsFastWithoutWaiting() = runTest {
        capabilities.set(allReady.copy(nfc = CapabilityStatus.DISABLED))
        val failure = assertIs<DomainResult.Failure>(write())
        assertEquals(DomainError.ServiceDisabled(Capability.NFC), failure.error)
        assertFalse(nfc.writeCancelled)
        assertEquals(null, nfc.pendingWrite)
    }

    @Test
    fun missingNfcHardwareFailsFast() = runTest {
        capabilities.set(allReady.copy(nfc = CapabilityStatus.UNSUPPORTED))
        val failure = assertIs<DomainResult.Failure>(write())
        assertEquals(DomainError.Unsupported(Capability.NFC), failure.error)
    }
}
