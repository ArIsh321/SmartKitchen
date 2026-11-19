package com.arise.smartkitchenai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = Sage40,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = Terracotta40,
    onTertiary = Color.White,
    tertiaryContainer = TerracottaContainer,
    onTertiaryContainer = OnTerracottaContainer,
    background = WarmBackground,
    onBackground = Ink,
    surface = WarmBackground,
    onSurface = Ink,
    surfaceVariant = WarmSurfaceHigh,
    onSurfaceVariant = InkVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = WarmSurfaceLow,
    surfaceContainer = WarmSurface,
    surfaceContainerHigh = WarmSurfaceHigh,
    // Filled cards use this: white cards on the warm background, as in the design.
    surfaceContainerHighest = Color.White,
    outline = Outline,
    outlineVariant = Outline,
)

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = OnGreenContainer,
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = GreenContainer,
    secondary = Sage80,
    secondaryContainer = SageContainerDark,
    onSecondaryContainer = SageContainer,
    tertiary = Terracotta80,
    onTertiary = OnTerracottaContainer,
    tertiaryContainer = TerracottaContainerDark,
    onTertiaryContainer = TerracottaContainer,
    background = DarkBackground,
    surface = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
)

/** SmartKitchen brand theme. Dynamic (wallpaper) colours are off so the brand look is consistent. */
@Composable
fun SmartKitchenAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
