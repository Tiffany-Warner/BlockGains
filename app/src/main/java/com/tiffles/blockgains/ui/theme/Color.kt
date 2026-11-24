package com.tiffles.blockgains.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * "Cozy coffee" brand palette, used unless the user turns on wallpaper colors (Android 12+).
 * Generated with Material color utilities, not hand-picked: SchemeTonalSpot (2021 spec) from
 * seed #C8925A, with the primary palette's chroma at 28 instead of 36 so the browns stay muted.
 * Light surfaces use a warmer cream: neutral palette at the hue/chroma of #FDF3E4 and every surface
 * role 2 tones lower (surface 96, not 98), except surfaceDim (87).
 * Every text/background pair the app uses meets WCAG AA (4.5:1). See ui-guidelines → Color.
 */

internal val BrandLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF7E562A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBD),
    onPrimaryContainer = Color(0xFF633F15),
    inversePrimary = Color(0xFFF2BC88),
    secondary = Color(0xFF725A42),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEDDBE),
    onSecondaryContainer = Color(0xFF59422C),
    tertiary = Color(0xFF57633A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDBE8B5),
    onTertiaryContainer = Color(0xFF404B25),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFCF2E3),
    onBackground = Color(0xFF211A14),
    surface = Color(0xFFFCF2E3),
    onSurface = Color(0xFF211A14),
    surfaceVariant = Color(0xFFF2DFD0),
    onSurfaceVariant = Color(0xFF50453A),
    surfaceTint = Color(0xFF7E562A),
    inverseSurface = Color(0xFF372F28),
    inverseOnSurface = Color(0xFFFDEEE3),
    outline = Color(0xFF837568),
    outlineVariant = Color(0xFFD5C3B5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFCF2E3),
    surfaceDim = Color(0xFFE2D9CA),
    surfaceContainerLowest = Color(0xFFFFF8F1),
    surfaceContainerLow = Color(0xFFF6EDDE),
    surfaceContainer = Color(0xFFF1E7D8),
    surfaceContainerHigh = Color(0xFFEBE1D3),
    surfaceContainerHighest = Color(0xFFE5DCCD),
)

internal val BrandDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFF2BC88),
    onPrimary = Color(0xFF492901),
    primaryContainer = Color(0xFF633F15),
    onPrimaryContainer = Color(0xFFFFDCBD),
    inversePrimary = Color(0xFF7E562A),
    secondary = Color(0xFFE1C1A3),
    onSecondary = Color(0xFF402C18),
    secondaryContainer = Color(0xFF59422C),
    onSecondaryContainer = Color(0xFFFEDDBE),
    tertiary = Color(0xFFBFCC9B),
    onTertiary = Color(0xFF2A3410),
    tertiaryContainer = Color(0xFF404B25),
    onTertiaryContainer = Color(0xFFDBE8B5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF19120C),
    onBackground = Color(0xFFEEE0D5),
    surface = Color(0xFF19120C),
    onSurface = Color(0xFFEEE0D5),
    surfaceVariant = Color(0xFF50453A),
    onSurfaceVariant = Color(0xFFD5C3B5),
    surfaceTint = Color(0xFFF2BC88),
    inverseSurface = Color(0xFFEEE0D5),
    inverseOnSurface = Color(0xFF372F28),
    outline = Color(0xFF9D8E81),
    outlineVariant = Color(0xFF50453A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF403830),
    surfaceDim = Color(0xFF19120C),
    surfaceContainerLowest = Color(0xFF130D07),
    surfaceContainerLow = Color(0xFF211A14),
    surfaceContainer = Color(0xFF261E18),
    surfaceContainerHigh = Color(0xFF302921),
    surfaceContainerHighest = Color(0xFF3C332C),
)

/**
 * Extended colors for amounts: gain = sage, loss = terracotta, each with a container. Not
 * primary/error, so a loss never reads as an app error. Containers and on-colors come from each
 * color's own tonal palette (container tone 90 light / 30 dark).
 */
@Immutable
data class GainLossColors(
    val gain: Color,
    val onGain: Color,
    val gainContainer: Color,
    val onGainContainer: Color,
    val loss: Color,
    val onLoss: Color,
    val lossContainer: Color,
    val onLossContainer: Color,
)

/** Light gain/loss are tone 40 of #3F7A4A / #B3412E: the given tones fell below 4.5:1 on cards. */
internal val LightGainLoss = GainLossColors(
    gain = Color(0xFF2F6A3C),
    onGain = Color(0xFFFFFFFF),
    gainContainer = Color(0xFFB2F2B7),
    onGainContainer = Color(0xFF002109),
    loss = Color(0xFFA73826),
    onLoss = Color(0xFFFFFFFF),
    lossContainer = Color(0xFFFFDAD4),
    onLossContainer = Color(0xFF3F0300),
)

internal val DarkGainLoss = GainLossColors(
    gain = Color(0xFF8FC79A),
    onGain = Color(0xFF00391A),
    gainContainer = Color(0xFF1B502E),
    onGainContainer = Color(0xFFB6F0C1),
    loss = Color(0xFFF2A08E),
    onLoss = Color(0xFF542015),
    lossContainer = Color(0xFF713629),
    onLossContainer = Color(0xFFFFDAD3),
)
