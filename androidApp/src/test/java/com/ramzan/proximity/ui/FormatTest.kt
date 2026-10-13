package com.ramzan.proximity.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `formatAgo buckets`() {
        assertEquals("just now", formatAgo(10_000, 10_000))
        assertEquals("just now", formatAgo(10_000, 20_000)) // clock skew
        assertEquals("5s ago", formatAgo(15_000, 10_000))
        assertEquals("2m ago", formatAgo(130_000, 10_000))
        assertEquals("3h ago", formatAgo(3 * 3_600_000L + 5, 5))
    }

    @Test
    fun `coordinates use dot decimals regardless of locale`() {
        assertEquals("23.78206, 90.41605", formatCoordinates(23.782061, 90.416049))
        assertEquals("-33.86000, 151.20000", formatCoordinates(-33.86, 151.2))
    }

    @Test
    fun `distance has two decimals`() = assertEquals("1.50 m", formatDistance(1.5f))

    @Test
    fun `rssi strength is clamped`() {
        assertEquals(0f, rssiStrength(-120), 0.001f)
        assertEquals(1f, rssiStrength(-20), 0.001f)
        assertEquals(0.5f, rssiStrength(-70), 0.001f)
    }
}
