package com.ramzan.proximity.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60 * SECOND_MS
private const val HOUR_MS = 60 * MINUTE_MS

/** "just now", "12s ago", "3m ago", "2h ago". Clock skew (future times) reads as "just now". */
fun formatAgo(nowMs: Long, thenMs: Long): String {
    val delta = (nowMs - thenMs).coerceAtLeast(0)
    return when {
        delta < 2 * SECOND_MS -> "just now"
        delta < MINUTE_MS -> "${delta / SECOND_MS}s ago"
        delta < HOUR_MS -> "${delta / MINUTE_MS}m ago"
        else -> "${delta / HOUR_MS}h ago"
    }
}

fun formatCoordinates(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

fun formatDistance(meters: Float): String = String.format(Locale.US, "%.2f m", meters)

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

fun formatClockTime(timestampMs: Long): String = timeFormatter.format(Instant.ofEpochMilli(timestampMs))

/** Maps an RSSI reading onto 0..1 for a strength bar (-100 dBm empty, -40 dBm full). */
fun rssiStrength(rssiDbm: Int): Float = ((rssiDbm + 100) / 60f).coerceIn(0f, 1f)
