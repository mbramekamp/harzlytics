package com.handball.stattracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Farbkonzept: Lila (mit leichtem Magenta-Einschlag) auf Weiß.
 *  - primary   = Lila: Marke, Navigation, Hauptaktionen, Heim-Serien in Diagrammen
 *  - secondary = Bernstein: bewusste Gegenfarbe zu Lila. Gast-Serien in Diagrammen, Gelbe Karten, Auszeiten.
 *                Ein zweites Lila würde Heim und Gast kaum unterscheidbar machen.
 *  - tertiary  = Grün: "gut/positiv" (Rang 1, Plus-Werte, Tore). Bewusst NICHT lila, damit die Bedeutung bleibt.
 *  - error     = Material-Standard-Rot.
 * Alle Text-Farbpaare erreichen mindestens WCAG-Kontrast 4,5:1.
 */
private val LightSportColorScheme = lightColorScheme(
    primary = Color(0xFF9A2DBE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DBFF),
    onPrimaryContainer = Color(0xFF3D094E),
    secondary = Color(0xFFB85805),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2DBFA),
    onSecondaryContainer = Color(0xFF390849),
    tertiary = Color(0xFF146C2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4EED0),
    onTertiaryContainer = Color(0xFF072711),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1E1820),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1820),
    surfaceVariant = Color(0xFFF3E9F7),
    onSurfaceVariant = Color(0xFF534757),
    outline = Color(0xFF7D6E81),
    surfaceTint = Color(0xFF9A2DBE),
    outlineVariant = Color(0xFFDECDE4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF7FF),
    surfaceContainer = Color(0xFFFBF4FE),
    surfaceContainerHigh = Color(0xFFF9F0FC),
    surfaceContainerHighest = Color(0xFFF6EBFA)
)
private val DarkSportColorScheme = darkColorScheme(
    primary = Color(0xFFEDB8FF),
    onPrimary = Color(0xFF400953),
    primaryContainer = Color(0xFF651B7E),
    onPrimaryContainer = Color(0xFFF2CCFF),
    secondary = Color(0xFFFFBE85),
    onSecondary = Color(0xFF421F00),
    secondaryContainer = Color(0xFF512460),
    onSecondaryContainer = Color(0xFFF2CCFF),
    tertiary = Color(0xFF6DD58C),
    onTertiary = Color(0xFF0A3818),
    tertiaryContainer = Color(0xFF0F5223),
    onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF150C18),
    onBackground = Color(0xFFE7E2E9),
    surface = Color(0xFF1F1322),
    onSurface = Color(0xFFE7E2E9),
    surfaceVariant = Color(0xFF302235),
    onSurfaceVariant = Color(0xFFCAC0CE),
    outline = Color(0xFF988B9C),
    surfaceTint = Color(0xFFEDB8FF),
    outlineVariant = Color(0xFF4D3D52),
    surfaceContainerLowest = Color(0xFF120A15),
    surfaceContainerLow = Color(0xFF1A101E),
    surfaceContainer = Color(0xFF231627),
    surfaceContainerHigh = Color(0xFF2B1D30),
    surfaceContainerHighest = Color(0xFF332438)
)

val HandballShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp)
)

@Composable
fun HandballTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkSportColorScheme else LightSportColorScheme,
        shapes = HandballShapes,
        content = content
    )
}
