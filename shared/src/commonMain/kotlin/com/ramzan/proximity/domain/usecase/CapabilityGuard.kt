package com.ramzan.proximity.domain.usecase

import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.CapabilityStatus
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.SystemCapabilities

/** Returns the reason [capability] cannot be used right now, or null when it is ready. */
fun SystemCapabilities.blockerFor(capability: Capability): DomainError? = when (this[capability]) {
    CapabilityStatus.READY -> null
    CapabilityStatus.PERMISSION_MISSING -> DomainError.PermissionMissing(capability)
    CapabilityStatus.DISABLED -> DomainError.ServiceDisabled(capability)
    CapabilityStatus.UNSUPPORTED -> DomainError.Unsupported(capability)
}
