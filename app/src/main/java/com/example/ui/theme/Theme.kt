package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SightYellow,
    secondary = SightGreen,
    tertiary = SightRed,
    background = SightBlack,
    surface = SightSurface,
    onPrimary = SightBlack,
    onSecondary = SightBlack,
    onTertiary = SightWhite,
    onBackground = SightWhite,
    onSurface = SightWhite,
    outline = SightBorder
)

private val LightColorScheme = darkColorScheme( // Force dark theme for high-contrast accessibility
    primary = SightYellow,
    secondary = SightGreen,
    tertiary = SightRed,
    background = SightBlack,
    surface = SightSurface,
    onPrimary = SightBlack,
    onSecondary = SightBlack,
    onTertiary = SightWhite,
    onBackground = SightWhite,
    onSurface = SightWhite,
    outline = SightBorder
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark mode for premium high contrast
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve accessibility contrast
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
