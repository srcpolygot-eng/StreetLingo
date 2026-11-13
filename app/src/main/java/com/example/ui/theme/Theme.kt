package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
  primary = CobaltSky,
  onPrimary = Color.White,
  primaryContainer = SkyMist,
  onPrimaryContainer = DeepCobalt,
  secondary = Terracotta,
  onSecondary = Color.White,
  secondaryContainer = TerracottaContainer,
  onSecondaryContainer = OnTerracottaContainer,
  tertiary = SunlitGold,
  onTertiary = InkCharcoal,
  tertiaryContainer = GoldContainer,
  onTertiaryContainer = OnGoldContainer,
  background = ParchmentCream,
  onBackground = InkCharcoal,
  surface = ParchmentSurface,
  onSurface = InkCharcoal,
  surfaceVariant = ParchmentVariant,
  onSurfaceVariant = InkMuted,
  outline = OutlineWarm,
  error = Color(0xFFB3261E),
  onError = Color.White,
)

private val DarkColors = darkColorScheme(
  primary = LanternCobalt,
  onPrimary = TwilightInk,
  primaryContainer = DeepCobalt,
  onPrimaryContainer = SkyMist,
  secondary = LanternCoral,
  onSecondary = TwilightInk,
  secondaryContainer = Color(0xFF5D2114),
  onSecondaryContainer = TerracottaContainer,
  tertiary = LanternGold,
  onTertiary = TwilightInk,
  tertiaryContainer = Color(0xFF533909),
  onTertiaryContainer = GoldContainer,
  background = TwilightInk,
  onBackground = CreamInk,
  surface = TwilightSurface,
  onSurface = CreamInk,
  surfaceVariant = TwilightCard,
  onSurfaceVariant = MutedCreamInk,
  outline = Color(0xFF3E4861),
)

val StreetLingoShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun StreetLingoTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColors else LightColors
  MaterialTheme(
    colorScheme = colorScheme,
    typography = StreetLingoTypography,
    shapes = StreetLingoShapes,
    content = content,
  )
}
