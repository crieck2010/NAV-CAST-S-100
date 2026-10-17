package com.crieck2010.navcast.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MarineDarkColorScheme = darkColorScheme(
  primary = NeonCyan,
  onPrimary = NavyBackground,
  primaryContainer = NavySurfaceVariant,
  onPrimaryContainer = NeonCyan,
  secondary = OceanicBlue,
  onSecondary = NavyBackground,
  secondaryContainer = NavySurfaceHighlight,
  onSecondaryContainer = TextPrimary,
  tertiary = AmberAlert,
  onTertiary = NavyBackground,
  tertiaryContainer = Color(0xFF422E00),
  onTertiaryContainer = AmberAlert,
  background = NavyBackground,
  onBackground = TextPrimary,
  surface = NavySurface,
  onSurface = TextPrimary,
  surfaceVariant = NavySurfaceVariant,
  onSurfaceVariant = TextSecondary,
  error = CoralDanger,
  onError = TextPrimary,
  outline = GridLineColor,
  outlineVariant = Color(0xFF2C3E6B)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Marine ECDIS defaults to night/tactical dark mode
  dynamicColor: Boolean = false, // Preserve nautical high-contrast palette
  content: @Composable () -> Unit
) {
  val colorScheme = MarineDarkColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      window.statusBarColor = NavyBackground.toArgb()
      window.navigationBarColor = NavyBackground.toArgb()
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
      WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
