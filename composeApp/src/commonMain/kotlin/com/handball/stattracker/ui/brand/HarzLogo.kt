package com.handball.stattracker.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handball.stattracker.ui.theme.HarzRed
import com.handball.stattracker.ui.theme.scoreStyle

/*
 * Logo "Harzlytics": aus dem ursprünglichen roten Schriftzug-Banner abgeleitet.
 *  - Zeichen: ein um 12° geneigter Block in Harz-Rot mit freigestelltem, ebenso geneigtem "H".
 *    Die Neigung übernimmt die Dynamik der kursiven Original-Lettern, ohne Neon-Rot und ohne Vollflächen-Banner.
 *  - Wortmarke: HARZLYTICS in Barlow Semi Condensed Bold Italic, Textfarbe (nicht rot), damit das Zeichen trägt.
 * Seitenverhältnis Zeichen: 110 × 100.
 */
private const val MARK_W = 110f
private const val MARK_H = 100f
private const val SHEAR = 0.2f // ≈ 11,3°

/** Neigt einen Punkt im ungeneigten 90×100-Raster nach rechts (oben weiter rechts als unten). */
private fun sx(x: Float, y: Float) = x + SHEAR * (MARK_H - y)

private fun ImageVector.Builder.slantedRect(left: Float, top: Float, right: Float, bottom: Float, color: Color) {
    path(fill = SolidColor(color)) {
        moveTo(sx(left, top), top)
        lineTo(sx(right, top), top)
        lineTo(sx(right, bottom), bottom)
        lineTo(sx(left, bottom), bottom)
        close()
    }
}

/** Zeichen als Vektor: auch als Fenster-Symbol (Desktop) nutzbar. */
fun harzMarkVector(block: Color = HarzRed, letter: Color = Color.White): ImageVector =
    ImageVector.Builder(
        name = "HarzMark",
        defaultWidth = MARK_W.dp,
        defaultHeight = MARK_H.dp,
        viewportWidth = MARK_W,
        viewportHeight = MARK_H
    ).apply {
        slantedRect(0f, 0f, 90f, 100f, block)
        // "H": zwei Stämme und ein Querbalken, mittig im Block
        slantedRect(24f, 22f, 39f, 78f, letter)
        slantedRect(51f, 22f, 66f, 78f, letter)
        slantedRect(38f, 44f, 52f, 56f, letter)
    }.build()

private val DefaultMark = harzMarkVector()

/** Nur das Zeichen (Seitenleiste, Kopfzeile, App-Symbol). */
@Composable
fun HarzMark(height: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = rememberVectorPainter(DefaultMark),
        contentDescription = "Harzlytics",
        modifier = modifier.height(height).width(height * (MARK_W / MARK_H))
    )
}

/** Zeichen + Wortmarke (Anmeldung, breite Kopfzeilen). */
@Composable
fun HarzLockup(markHeight: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(markHeight * 0.32f)
    ) {
        HarzMark(markHeight)
        Text(
            text = "HARZLYTICS",
            style = scoreStyle((markHeight.value * 0.78f).sp).copy(letterSpacing = (markHeight.value * 0.02f).sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
