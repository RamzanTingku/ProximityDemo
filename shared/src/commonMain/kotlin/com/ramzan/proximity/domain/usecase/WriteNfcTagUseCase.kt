package com.ramzan.proximity.domain.usecase

import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.repository.CapabilityRepository
import com.ramzan.proximity.domain.repository.NfcRepository
import kotlinx.coroutines.withTimeoutOrNull

class WriteNfcTagUseCase(
    private val nfc: NfcRepository,
    private val capabilities: CapabilityRepository,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    /** Waits for a tag tap. Cancel the calling coroutine to abort; gives up with [DomainError.Timeout]. */
    suspend operator fun invoke(text: String = DEMO_PAYLOAD): DomainResult<Unit> {
        capabilities.refresh()
        capabilities.capabilities.value.blockerFor(Capability.NFC)?.let {
            return DomainResult.Failure(it)
        }
        return withTimeoutOrNull(timeoutMs) { nfc.writeNextTag(text) }
            ?: DomainResult.Failure(DomainError.Timeout)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val DEMO_PAYLOAD = "Proximity Demo check-in"
    }
}
