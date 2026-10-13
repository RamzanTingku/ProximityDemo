package com.ramzan.proximity.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ramzan.proximity.domain.model.ProximitySignal

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696B),
    secondary = Color(0xFF4A6364),
    tertiary = Color(0xFF4B607C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CDADC),
    secondary = Color(0xFFB0CCCD),
    tertiary = Color(0xFFB3C8E8),
)

/** Status colours that must stay recognisable whatever the dynamic wallpaper palette is. */
data class StatusColors(
    val good: Color,
    val warning: Color,
    val bad: Color,
    val neutral: Color,
)

private val LightStatus = StatusColors(Color(0xFF2E7D32), Color(0xFFB26A00), Color(0xFFB3261E), Color(0xFF6F797A))
private val DarkStatus = StatusColors(Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFF2B8B5), Color(0xFF9AA5A6))

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

fun StatusColors.forSignal(signal: ProximitySignal): Color = when (signal) {
    ProximitySignal.NEAR -> good
    ProximitySignal.MEDIUM -> warning
    ProximitySignal.FAR -> neutral
}

@Composable
fun ProximityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus,
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
