package com.ramzan.proximity.domain.model

enum class Capability {
    LOCATION,
    BLUETOOTH,
    NOTIFICATIONS,
    NFC,
    UWB,
}

enum class CapabilityStatus {
    READY,
    PERMISSION_MISSING,
    DISABLED,
    UNSUPPORTED,
}

data class SystemCapabilities(
    val location: CapabilityStatus = CapabilityStatus.PERMISSION_MISSING,
    val bluetooth: CapabilityStatus = CapabilityStatus.PERMISSION_MISSING,
    val notifications: CapabilityStatus = CapabilityStatus.PERMISSION_MISSING,
    val nfc: CapabilityStatus = CapabilityStatus.UNSUPPORTED,
    val uwb: CapabilityStatus = CapabilityStatus.UNSUPPORTED,
) {
    operator fun get(capability: Capability): CapabilityStatus = when (capability) {
        Capability.LOCATION -> location
        Capability.BLUETOOTH -> bluetooth
        Capability.NOTIFICATIONS -> notifications
        Capability.NFC -> nfc
        Capability.UWB -> uwb
    }
}
