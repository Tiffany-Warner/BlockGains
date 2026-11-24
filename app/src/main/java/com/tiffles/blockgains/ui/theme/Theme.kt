package com.tiffles.blockgains.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.tiffles.blockgains.domain.model.ThemeMode

private val LocalGainLossColors = staticCompositionLocalOf { LightGainLoss }

/** Gain/loss colors for amounts (ui-guidelines → Color). They stay the same with wallpaper colors. */
val MaterialTheme.gainLoss: GainLossColors
    @Composable @ReadOnlyComposable get() = LocalGainLossColors.current

/** App theme: the cozy brand scheme, or wallpaper colors on Android 12+ when the user turns them on. */
@Composable
fun BlockGainsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> BrandDarkScheme
        else -> BrandLightScheme
    }
    CompositionLocalProvider(LocalGainLossColors provides if (dark) DarkGainLoss else LightGainLoss) {
        MaterialTheme(colorScheme = colorScheme, typography = BlockGainsTypography, content = content)
    }
}
