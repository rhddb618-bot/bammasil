package com.example.pleoscamping.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** 다크 색 구성 하나뿐. 라이트 테마·dynamicColor는 없다 (SPEC_DARK 3절) */
private val PleosColorScheme = darkColorScheme(
  primary = PleosAccentSolid,
  onPrimary = PleosOnAccent,
  primaryContainer = PleosAccentSoft,
  onPrimaryContainer = PleosFg,
  secondary = PleosFg,
  onSecondary = PleosBg,
  background = PleosBg,
  onBackground = PleosFg,
  surface = PleosCard,
  onSurface = PleosFg,
  surfaceVariant = PleosRaised,
  onSurfaceVariant = PleosMuted,
  outline = PleosLine,
  outlineVariant = PleosLine,
  error = PleosDanger
)

/** 모든 컴포넌트 기본 모양은 직각 (SPEC_UI 2장) */
private val PleosShapes = Shapes(
  extraSmall = RoundedCornerShape(0.dp),
  small = RoundedCornerShape(0.dp),
  medium = RoundedCornerShape(0.dp),
  large = RoundedCornerShape(0.dp),
  extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun PleosCampingTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = PleosColorScheme,
    typography = Typography,
    shapes = PleosShapes,
    content = content
  )
}
