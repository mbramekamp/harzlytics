package com.handball.stattracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handball.stattracker.resources.Res
import com.handball.stattracker.resources.barlow_bold
import com.handball.stattracker.resources.barlow_medium
import com.handball.stattracker.resources.barlow_regular
import com.handball.stattracker.resources.barlow_semibold
import com.handball.stattracker.resources.barlow_semicondensed_bold
import com.handball.stattracker.resources.barlow_semicondensed_bolditalic
import com.handball.stattracker.resources.barlow_semicondensed_semibold
import org.jetbrains.compose.resources.Font

/*
 * Farbkonzept "Harz-Rot auf Graphit/Weiß" (abgeleitet aus dem Logo, gedämpft für ruhige Flächen).
 *  - primary   = Harz-Rot: nur Marke, Auswahl und Hauptaktionen. Zahlen bleiben in Textfarbe.
 *  - secondary = Bernstein ("Harz" = Haftmittel): Gegenfarbe für Gast-Serien, Gelbe Karten, 2-Min, Auszeiten.
 *  - secondaryContainer = neutrales Schiefergrau: Tonal-Buttons, gewählte Chips (kein Farbschleier).
 *  - tertiary  = Grün: "gut/positiv" (Rang 1, Plus-Werte, Tore, Paraden).
 *  - Flächen: kühles Grau als Hintergrund, Karten/Dialoge weiß; surfaceTint transparent,
 *    damit Elevation keine Farbe in die Flächen mischt.
 * Alle Text-Farbpaare erreichen mindestens WCAG-Kontrast 4,5:1.
 */
val HarzRed = Color(0xFFC8102E)
val HarzInk = Color(0xFF171A1F)

private val LightSportColorScheme = lightColorScheme(
    primary = HarzRed,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCE8EA),
    onPrimaryContainer = Color(0xFF5C0A17),
    inversePrimary = Color(0xFFFF8593),
    secondary = Color(0xFFA84E00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9ECF0),
    onSecondaryContainer = Color(0xFF1B1F26),
    tertiary = Color(0xFF146C2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5F0DD),
    onTertiaryContainer = Color(0xFF072711),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE1DC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF3F4F6),
    onBackground = HarzInk,
    surface = Color(0xFFFFFFFF),
    onSurface = HarzInk,
    surfaceVariant = Color(0xFFEEF0F3),
    onSurfaceVariant = Color(0xFF545B67),
    inverseSurface = Color(0xFF262A31),
    inverseOnSurface = Color(0xFFF1F2F4),
    outline = Color(0xFF8A909B),
    outlineVariant = Color(0xFFDDE0E5),
    surfaceTint = Color.Transparent,
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE4E6EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    // Karten (Material-Standard) liegen weiß auf dem grauen Hintergrund
    surfaceContainerHighest = Color(0xFFFFFFFF)
)

private val DarkSportColorScheme = darkColorScheme(
    primary = Color(0xFFFF5468),
    onPrimary = Color(0xFF3B0009),
    primaryContainer = Color(0xFF5E0E1A),
    onPrimaryContainer = Color(0xFFFFD9DC),
    inversePrimary = HarzRed,
    secondary = Color(0xFFFFB870),
    onSecondary = Color(0xFF3F1F00),
    secondaryContainer = Color(0xFF2A2F37),
    onSecondaryContainer = Color(0xFFE4E7EC),
    tertiary = Color(0xFF6DD58C),
    onTertiary = Color(0xFF0A3818),
    tertiaryContainer = Color(0xFF0F4220),
    onTertiaryContainer = Color(0xFFC4EED0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1114),
    onBackground = Color(0xFFE4E7EC),
    surface = Color(0xFF181B20),
    onSurface = Color(0xFFE4E7EC),
    surfaceVariant = Color(0xFF23272E),
    onSurfaceVariant = Color(0xFFA9B0BB),
    inverseSurface = Color(0xFFE4E7EC),
    inverseOnSurface = Color(0xFF1B1F26),
    outline = Color(0xFF6E7581),
    outlineVariant = Color(0xFF2E333B),
    surfaceTint = Color.Transparent,
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2E35),
    surfaceDim = Color(0xFF0F1114),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF14171B),
    surfaceContainer = Color(0xFF1C2026),
    surfaceContainerHigh = Color(0xFF20242A),
    surfaceContainerHighest = Color(0xFF181B20)
)

/** Drei Radien mit klarer Hierarchie: Chips/Badges 6, Zeilen/Felder 10, Karten/Dialoge 14. */
val HandballShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

/** Schriften: Barlow für Text, Barlow Semi Condensed für Titel und Zahlen, kursiv nur für Spielstände und Logo. */
class HarzFonts(val text: FontFamily, val display: FontFamily, val score: FontFamily)

val LocalHarzFonts = staticCompositionLocalOf { HarzFonts(FontFamily.Default, FontFamily.Default, FontFamily.Default) }

@Composable
private fun rememberHarzFonts(): HarzFonts {
    val text = FontFamily(
        Font(Res.font.barlow_regular, FontWeight.Normal),
        Font(Res.font.barlow_medium, FontWeight.Medium),
        Font(Res.font.barlow_semibold, FontWeight.SemiBold),
        Font(Res.font.barlow_bold, FontWeight.Bold)
    )
    val display = FontFamily(
        Font(Res.font.barlow_semicondensed_semibold, FontWeight.SemiBold),
        Font(Res.font.barlow_semicondensed_bold, FontWeight.Bold),
        Font(Res.font.barlow_semicondensed_bolditalic, FontWeight.Bold, FontStyle.Italic)
    )
    val score = FontFamily(
        Font(Res.font.barlow_semicondensed_bolditalic, FontWeight.Bold, FontStyle.Italic)
    )
    return HarzFonts(text, display, score)
}

private const val TABULAR = "tnum"

private fun harzTypography(fonts: HarzFonts): Typography {
    val base = Typography()
    fun TextStyle.text(spacing: Float) = copy(fontFamily = fonts.text, letterSpacing = spacing.sp, fontFeatureSettings = TABULAR)
    fun TextStyle.display(weight: FontWeight) = copy(fontFamily = fonts.display, fontWeight = weight, letterSpacing = 0.sp, fontFeatureSettings = TABULAR)
    return Typography(
        displayLarge = base.displayLarge.display(FontWeight.Bold),
        displayMedium = base.displayMedium.display(FontWeight.Bold),
        displaySmall = base.displaySmall.display(FontWeight.Bold),
        headlineLarge = base.headlineLarge.display(FontWeight.Bold),
        headlineMedium = base.headlineMedium.display(FontWeight.Bold),
        headlineSmall = base.headlineSmall.display(FontWeight.SemiBold),
        titleLarge = base.titleLarge.display(FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp).display(FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 15.sp).display(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.text(0.1f),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp).text(0.1f),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp).text(0.1f),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold).text(0.1f),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp).text(0.2f),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp).text(0.2f)
    )
}

/** Spielstand-Stil (kursiv, verdichtet): das einzige kursive Element neben dem Logo. */
@Composable
fun scoreStyle(size: androidx.compose.ui.unit.TextUnit): TextStyle = TextStyle(
    fontFamily = LocalHarzFonts.current.score,
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic,
    fontSize = size,
    lineHeight = size * 1.05f,
    fontFeatureSettings = TABULAR
)

@Composable
fun HandballTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val fonts = rememberHarzFonts()
    CompositionLocalProvider(LocalHarzFonts provides fonts) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkSportColorScheme else LightSportColorScheme,
            shapes = HandballShapes,
            typography = harzTypography(fonts),
            content = content
        )
    }
}
