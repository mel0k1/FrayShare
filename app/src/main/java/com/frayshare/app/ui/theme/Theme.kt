package com.frayshare.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@Composable
fun FrayTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = FrayTypography,
        shapes = FrayShapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
