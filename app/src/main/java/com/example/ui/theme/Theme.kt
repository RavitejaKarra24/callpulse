package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = PulseBlueDark,
        onPrimary = Color(0xFF003258),
        primaryContainer = PulseBlueContainerDark,
        onPrimaryContainer = Color(0xFFD6E4FF),
        secondary = PulseTealDark,
        onSecondary = Color(0xFF003731),
        secondaryContainer = PulseTealContainerDark,
        onSecondaryContainer = Color(0xFFB2DFDB),
        tertiary = PulseAmberDark,
        onTertiary = Color(0xFF3E2723),
        tertiaryContainer = PulseAmberContainerDark,
        onTertiaryContainer = Color(0xFFFFE0B2),
        background = PulseBackgroundDark,
        onBackground = PulseOnSurfaceDark,
        surface = PulseSurfaceDark,
        onSurface = PulseOnSurfaceDark,
        surfaceVariant = Color(0xFF2A2D33),
        onSurfaceVariant = PulseOnSurfaceVariantDark,
        outline = PulseOutlineDark
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PulseBlue,
        onPrimary = Color.White,
        primaryContainer = PulseBlueContainer,
        onPrimaryContainer = Color(0xFF001B3D),
        secondary = PulseTeal,
        onSecondary = Color.White,
        secondaryContainer = PulseTealContainer,
        onSecondaryContainer = Color(0xFF00201C),
        tertiary = PulseAmber,
        onTertiary = Color.White,
        tertiaryContainer = PulseAmberContainer,
        onTertiaryContainer = Color(0xFF2D1600),
        background = PulseBackground,
        onBackground = PulseOnSurface,
        surface = PulseSurface,
        onSurface = PulseOnSurface,
        surfaceVariant = Color(0xFFE8ECF2),
        onSurfaceVariant = PulseOnSurfaceVariant,
        outline = PulseOutline
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Prefer branded palette for a consistent product look; dynamic still optional
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
