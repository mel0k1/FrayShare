package com.frayshare.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0B0B)
val Paper = Color(0xFFF9F7F2)
val Chalk = Color(0xFFEDEAE4)
val Coral = Color(0xFFFFB4A6)
val CoralDeep = Color(0xFF9C4238)

val DarkScheme = darkColorScheme(
    primary = Chalk,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2B2A28),
    onPrimaryContainer = Chalk,
    inversePrimary = Color(0xFF33302C),
    secondary = Color(0xFFCFCCC4),
    onSecondary = Color(0xFF1B1A18),
    secondaryContainer = Color(0xFF33322F),
    onSecondaryContainer = Color(0xFFE4E1DA),
    tertiary = Coral,
    onTertiary = Color(0xFF5F1710),
    tertiaryContainer = Color(0xFF85291F),
    onTertiaryContainer = Color(0xFFFFDAD2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Ink,
    onBackground = Chalk,
    surface = Ink,
    onSurface = Chalk,
    surfaceVariant = Color(0xFF1E1D1B),
    onSurfaceVariant = Color(0xFFB8B5AD),
    surfaceDim = Color(0xFF0B0B0B),
    surfaceBright = Color(0xFF3A3835),
    surfaceContainerLowest = Color(0xFF060606),
    surfaceContainerLow = Color(0xFF141312),
    surfaceContainer = Color(0xFF191817),
    surfaceContainerHigh = Color(0xFF242321),
    surfaceContainerHighest = Color(0xFF2F2D2B),
    outline = Color(0xFF837F78),
    outlineVariant = Color(0xFF33302C),
    inverseSurface = Chalk,
    inverseOnSurface = Color(0xFF121110)
)

val LightScheme = lightColorScheme(
    primary = Color(0xFF33302C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E2DB),
    onPrimaryContainer = Color(0xFF191817),
    inversePrimary = Chalk,
    secondary = Color(0xFF615F5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E1DA),
    onSecondaryContainer = Color(0xFF1B1A18),
    tertiary = CoralDeep,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF410B05),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Paper,
    onBackground = Color(0xFF191817),
    surface = Paper,
    onSurface = Color(0xFF191817),
    surfaceVariant = Color(0xFFE7E3DB),
    onSurfaceVariant = Color(0xFF4B4945),
    surfaceDim = Color(0xFFDBD9D3),
    surfaceBright = Paper,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F1EC),
    surfaceContainer = Color(0xFFEDEBE4),
    surfaceContainerHigh = Color(0xFFE7E5DE),
    surfaceContainerHighest = Color(0xFFE2E0D8),
    outline = Color(0xFF7C7873),
    outlineVariant = Color(0xFFCCC8C0),
    inverseSurface = Color(0xFF2E2C2B),
    inverseOnSurface = Color(0xFFF1F0EB)
)
