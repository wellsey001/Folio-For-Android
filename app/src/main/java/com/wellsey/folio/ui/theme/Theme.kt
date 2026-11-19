package com.wellsey.folio.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFFB2452E),
    secondary = Color(0xFF5B6478),
    tertiary = Color(0xFFB0672A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A0),
    secondary = Color(0xFFC0C6DC),
    tertiary = Color(0xFFFFB77C),
    background = Color(0xFF121412),
    surface = Color(0xFF121412),
)

@Composable
fun FolioTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    // Match the phone's wallpaper colours (Material You) on Android 12+.
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
