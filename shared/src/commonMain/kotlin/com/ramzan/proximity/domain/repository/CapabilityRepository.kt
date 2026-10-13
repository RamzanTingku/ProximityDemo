package com.ramzan.proximity.domain.repository

import com.ramzan.proximity.domain.model.SystemCapabilities
import kotlinx.coroutines.flow.StateFlow

interface CapabilityRepository {
    /** Live permission and system-toggle status (Bluetooth, location services, NFC...). */
    val capabilities: StateFlow<SystemCapabilities>

    /** Re-reads permissions, which have no change broadcast. */
    fun refresh()
}
