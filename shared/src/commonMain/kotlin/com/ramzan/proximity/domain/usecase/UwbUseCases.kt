package com.ramzan.proximity.domain.usecase

import com.ramzan.proximity.domain.codec.UwbExchangeCodec
import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.flatMap
import com.ramzan.proximity.domain.model.map
import com.ramzan.proximity.domain.repository.CapabilityRepository
import com.ramzan.proximity.domain.repository.UwbRepository

private fun CapabilityRepository.uwbBlocker(): DomainResult.Failure? {
    refresh()
    return capabilities.value.blockerFor(Capability.UWB)?.let { DomainResult.Failure(it) }
}

class CreateUwbOfferUseCase(
    private val uwb: UwbRepository,
    private val capabilities: CapabilityRepository,
) {
    /** @return the offer as a shareable code. */
    suspend operator fun invoke(): DomainResult<String> {
        capabilities.uwbBlocker()?.let { return it }
        return uwb.createControllerOffer().map { UwbExchangeCodec.encode(it) }
    }
}

class AcceptUwbOfferUseCase(
    private val uwb: UwbRepository,
    private val capabilities: CapabilityRepository,
) {
    /** @return the answer code the controller needs to start ranging. */
    suspend operator fun invoke(offerCode: String): DomainResult<String> {
        val offer = UwbExchangeCodec.decodeOffer(offerCode)
        if (offer is DomainResult.Failure) return offer
        capabilities.uwbBlocker()?.let { return it }
        return offer.flatMap { uwb.joinAsControlee(it) }.map { UwbExchangeCodec.encode(it) }
    }
}

class StartUwbRangingUseCase(
    private val uwb: UwbRepository,
    private val capabilities: CapabilityRepository,
) {
    suspend operator fun invoke(answerCode: String): DomainResult<Unit> {
        val answer = UwbExchangeCodec.decodeAnswer(answerCode)
        if (answer is DomainResult.Failure) return answer
        capabilities.uwbBlocker()?.let { return it }
        return answer.flatMap { uwb.startControllerRanging(it) }
    }
}

class StopUwbRangingUseCase(private val uwb: UwbRepository) {
    operator fun invoke() = uwb.stopRanging()
}
