package com.ramzan.proximity.presentation

import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.TagProblem

val Capability.displayName: String
    get() = when (this) {
        Capability.LOCATION -> "Location"
        Capability.BLUETOOTH -> "Bluetooth"
        Capability.NOTIFICATIONS -> "Notifications"
        Capability.NFC -> "NFC"
        Capability.UWB -> "UWB"
    }

private const val SCAN_FAILED_TOO_FREQUENTLY = 6

fun DomainError.toMessage(): String = when (this) {
    is DomainError.PermissionMissing -> when (capability) {
        Capability.LOCATION -> "Location permission is required"
        Capability.BLUETOOTH, Capability.UWB -> "Nearby devices permission is required"
        Capability.NOTIFICATIONS -> "Notification permission is needed to show the tracking notification"
        Capability.NFC -> "NFC permission is required"
    }
    is DomainError.ServiceDisabled -> "${capability.displayName} is turned off. Turn it on to continue"
    is DomainError.Unsupported -> "${capability.displayName} is not supported on this device"
    is DomainError.ScanFailed ->
        if (code == SCAN_FAILED_TOO_FREQUENTLY) {
            "Bluetooth scanning was restarted too often. Wait about 30 seconds and try again"
        } else {
            "Bluetooth scan failed (code $code)"
        }
    is DomainError.ForegroundStartFailed ->
        "Could not start background tracking${reason?.let { ": $it" }.orEmpty()}"
    is DomainError.InvalidCode -> reason
    is DomainError.Unavailable -> "${capability.displayName} is currently unavailable"
    is DomainError.SessionFailed -> "UWB session failed${reason?.let { ": $it" }.orEmpty()}"
    DomainError.NoActiveOffer -> "Create an offer first, then paste the other device's answer"
    DomainError.Timeout -> "No tag detected. Hold a tag against the back of the phone and try again"
    is DomainError.Tag -> when (problem) {
        TagProblem.NOT_NDEF -> "This tag does not support NDEF"
        TagProblem.READ_ONLY -> "This tag is read-only"
        TagProblem.TOO_SMALL -> "This tag does not have enough space"
        TagProblem.LOST -> "The tag moved away too soon. Keep it steady and try again"
        TagProblem.IO_FAILURE -> "Could not communicate with the tag"
        TagProblem.FORMAT_FAILED -> "Could not format the tag"
    }
    is DomainError.Unknown -> message ?: "Something went wrong"
}

/** The system screen that can resolve this error, if any. */
fun DomainError.settingsTarget(): SettingsTarget? = when (this) {
    is DomainError.PermissionMissing -> SettingsTarget.APP
    is DomainError.ServiceDisabled -> when (capability) {
        Capability.LOCATION -> SettingsTarget.LOCATION
        Capability.BLUETOOTH -> SettingsTarget.BLUETOOTH
        Capability.NFC -> SettingsTarget.NFC
        Capability.NOTIFICATIONS -> SettingsTarget.NOTIFICATIONS
        Capability.UWB -> null
    }
    else -> null
}

fun DomainError.toEffect(): UiEffect.ShowMessage = UiEffect.ShowMessage(toMessage(), settingsTarget())
