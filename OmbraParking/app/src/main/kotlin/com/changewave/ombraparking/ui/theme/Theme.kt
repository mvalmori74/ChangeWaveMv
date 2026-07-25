package com.changewave.ombraparking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colori con cui l'app racconta la luce: giallo = sole, blu = ombra. */
object ShadeColors {
    val sun = Color(0xFFFFC94D)
    val dappled = Color(0xFF9BD17C)
    val shade = Color(0xFF6C8CD5)
    val night = Color(0xFF3C4763)

    /** Riempimento delle aree in ombra su mappa e in AR. */
    val shadowFill = Color(0x59122043)
    val shadowFillAr = Color(0x662B3E6E)
    val buildingStroke = Color(0xFF8A97B8)
    val buildingFill = Color(0x33566B99)
}

private val DarkScheme = darkColorScheme(
    primary = ShadeColors.sun,
    onPrimary = Color(0xFF2A1F00),
    secondary = ShadeColors.shade,
    onSecondary = Color(0xFF06122E),
    background = Color(0xFF0F1420),
    onBackground = Color(0xFFE7EAF3),
    surface = Color(0xFF182031),
    onSurface = Color(0xFFE7EAF3),
    surfaceVariant = Color(0xFF232C41),
    onSurfaceVariant = Color(0xFFBFC7DC),
    error = Color(0xFFFF8A80),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFB07800),
    onPrimary = Color.White,
    secondary = Color(0xFF31508F),
    onSecondary = Color.White,
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF141A26),
    surface = Color.White,
    onSurface = Color(0xFF141A26),
    surfaceVariant = Color(0xFFE4E8F2),
    onSurfaceVariant = Color(0xFF444E66),
)

@Composable
fun OmbraParkingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
