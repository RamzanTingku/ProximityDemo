package com.ramzan.proximity.presentation

import com.ramzan.proximity.domain.model.Capability

enum class SettingsTarget {
    APP,
    LOCATION,
    BLUETOOTH,
    NFC,
    NOTIFICATIONS,
}

/** One-shot events a ViewModel asks the UI to perform. */
sealed interface UiEffect {
    /** [settings] adds a "Settings" action to the snackbar that opens the matching system screen. */
    data class ShowMessage(val text: String, val settings: SettingsTarget? = null) : UiEffect
    data class RequestPermissions(val capabilities: Set<Capability>) : UiEffect
    data class OpenSettings(val target: SettingsTarget) : UiEffect
    data class Share(val text: String) : UiEffect
}
