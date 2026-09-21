package com.changewave.dungeon.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Palette del dungeon: fondo notturno, accenti ambra (torce) e rosso sangue. */
object DungeonColors {
    val Background = Color(0xFF0B0B10)
    val Surface = Color(0xFF14141C)
    val SurfaceVariant = Color(0xFF1E1E2A)
    val Amber = Color(0xFFE8B84B)
    val Blood = Color(0xFFC0392B)
    val Poison = Color(0xFF6AB04C)
    val Arcane = Color(0xFF7D5FFF)
    val Bone = Color(0xFFE6E1D3)
    val Stone = Color(0xFF6E6E80)
    val StoneDim = Color(0xFF32323F)
    val FloorLit = Color(0xFF8A8A9E)
    val FloorDim = Color(0xFF3A3A4A)
    val Gold = Color(0xFFF1C40F)
}

private val DarkScheme = darkColorScheme(
    primary = DungeonColors.Amber,
    onPrimary = Color(0xFF1B1405),
    secondary = DungeonColors.Arcane,
    background = DungeonColors.Background,
    onBackground = DungeonColors.Bone,
    surface = DungeonColors.Surface,
    onSurface = DungeonColors.Bone,
    surfaceVariant = DungeonColors.SurfaceVariant,
    error = DungeonColors.Blood,
)

private val DungeonTypography = Typography(
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
)

/** Il gioco usa sempre il tema scuro, anche se il sistema e' in modalita' chiara. */
@Composable
fun DungeonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = DungeonTypography, content = content)
}
