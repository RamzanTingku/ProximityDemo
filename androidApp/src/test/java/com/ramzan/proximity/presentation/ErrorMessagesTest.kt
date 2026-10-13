package com.ramzan.proximity.presentation

import com.ramzan.proximity.domain.model.Capability
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.TagProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {
    @Test
    fun `every error has a non blank message`() {
        val errors = listOf(
            DomainError.PermissionMissing(Capability.LOCATION),
            DomainError.PermissionMissing(Capability.BLUETOOTH),
            DomainError.PermissionMissing(Capability.UWB),
            DomainError.PermissionMissing(Capability.NOTIFICATIONS),
            DomainError.ServiceDisabled(Capability.NFC),
            DomainError.Unsupported(Capability.UWB),
            DomainError.ScanFailed(6),
            DomainError.ScanFailed(2),
            DomainError.ForegroundStartFailed(null),
            DomainError.InvalidCode("bad"),
            DomainError.Unavailable(Capability.LOCATION),
            DomainError.SessionFailed("x"),
            DomainError.NoActiveOffer,
            DomainError.Timeout,
            DomainError.Unknown(null),
        ) + TagProblem.entries.map { DomainError.Tag(it) }

        errors.forEach { assertFalse("$it", it.toMessage().isBlank()) }
    }

    @Test
    fun `scan throttling gets a specific hint`() {
        assertTrue(DomainError.ScanFailed(6).toMessage().contains("30 seconds"))
    }

    @Test
    fun `settings shortcuts match the cause`() {
        assertEquals(SettingsTarget.APP, DomainError.PermissionMissing(Capability.LOCATION).settingsTarget())
        assertEquals(SettingsTarget.BLUETOOTH, DomainError.ServiceDisabled(Capability.BLUETOOTH).settingsTarget())
        assertEquals(SettingsTarget.NFC, DomainError.ServiceDisabled(Capability.NFC).settingsTarget())
        assertNull(DomainError.Timeout.settingsTarget())
    }

    @Test
    fun `toEffect carries message and target`() {
        val effect = DomainError.ServiceDisabled(Capability.LOCATION).toEffect()
        assertEquals(SettingsTarget.LOCATION, effect.settings)
        assertTrue(effect.text.contains("Location"))
    }
}
