package com.arise.smartkitchenai.ui.theme

import androidx.compose.ui.graphics.Color

// SmartKitchen brand: herb green + terracotta on a warm off-white.

// Light
val Green40 = Color(0xFF46704F)
val GreenContainer = Color(0xFFDCEADF)
val OnGreenContainer = Color(0xFF0F2A17)
val Sage40 = Color(0xFF5E6B60)
val SageContainer = Color(0xFFE4EAE3)
val OnSageContainer = Color(0xFF1A231C)
val Terracotta40 = Color(0xFFB85A35)
val TerracottaContainer = Color(0xFFFBE3D8)
val OnTerracottaContainer = Color(0xFF4A1B08)
val WarmBackground = Color(0xFFFBF9F5)
val WarmSurfaceLow = Color(0xFFF5F2EC)
val WarmSurface = Color(0xFFF0ECE5)
val WarmSurfaceHigh = Color(0xFFEAE6DE)
val Ink = Color(0xFF1C1F1C)
val InkVariant = Color(0xFF5B615B)
val Outline = Color(0xFFD6D2CA)

// Dark
val Green80 = Color(0xFFA4D1AE)
val GreenContainerDark = Color(0xFF2E5737)
val Sage80 = Color(0xFFC2CCC3)
val SageContainerDark = Color(0xFF3F4A41)
val Terracotta80 = Color(0xFFF2B497)
val TerracottaContainerDark = Color(0xFF7A3517)
val DarkBackground = Color(0xFF141714)
val DarkSurface = Color(0xFF1E221E)
val DarkSurfaceHigh = Color(0xFF282C28)
val DarkSurfaceHighest = Color(0xFF323632)

// Chart marks (validated: lightness band, chroma floor, >= 3:1 contrast vs the card surface).
// Revenue and waste are always drawn in separate single-series charts, never side by side.
val ChartGreenLight = Color(0xFF3D8A55)
val ChartTerracottaLight = Color(0xFFC0562B)
val ChartGreenDark = Color(0xFF55A06D)
val ChartTerracottaDark = Color(0xFFD46E45)

/** Positive change (revenue up, costs down). */
val GoodGreen = Color(0xFF2E7D45)
/** Negative change (revenue down, costs up). */
val BadTerracotta = Color(0xFFB85A35)
