package com.blacksingularities.openmail.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Interfejs AMOLED jak w OpenPDF: czysta czerń, biel jako akcent, czerwień dla usuwania. */
object Palette {
    val Ink = Color(0xFF000000)
    val Card = Color(0xFF0D0D10)
    val CardHigh = Color(0xFF1B1B21)
    val CardTop = Color(0xFF26262E)
    val Line = Color(0x1FFFFFFF)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x66FFFFFF)
    val Danger = Color(0xFFFF3B47)
    val Star = Color(0xFFFFC34D)
    val Ok = Color(0xFF5CE1A6)
    val Archive = Color(0xFF6FA8FF)
    val Unread = Color(0xFF6FA8FF)
}

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Palette.Ink,
    secondary = Color(0xFFE0E0E0),
    onSecondary = Palette.Ink,
    background = Palette.Ink,
    onBackground = Color.White,
    surface = Palette.Ink,
    onSurface = Color.White,
    surfaceVariant = Palette.CardHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Card,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.CardHigh,
    surfaceContainerHighest = Palette.CardHigh,
    outline = Color(0x33FFFFFF),
    outlineVariant = Color(0x1FFFFFFF),
    error = Palette.Danger,
)

private val base = Typography()
private val typography = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
)

@Composable
fun OpenMailTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
