package com.handball.stattracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.handball.stattracker.model.SeasonScope
import com.handball.stattracker.repository.DemoDataSeed
import com.handball.stattracker.repository.HandballRepository
import com.handball.stattracker.ui.screens.CreationModalType
import com.handball.stattracker.ui.screens.PlusCreationModalHost
import com.handball.stattracker.ui.theme.HandballTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Formulare hinter dem "+": Handy als Vollbild, PC als Dialog; neues Spiel auch Schritt 2 (Aufgebot). */
@OptIn(ExperimentalComposeUiApi::class)
class CreationFormScreenshotTest {
    private val outDir = File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }

    /** [tapAt] in dp: optionaler Tipp (z. B. auf "Weiter"), danach wird ein zweites Bild gespeichert. */
    private fun render(name: String, modal: CreationModalType, width: Int, height: Int, density: Float, tapAt: Pair<Int, Int>? = null) {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        val scene = ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density)) {
            HandballTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                PlusCreationModalHost(
                    modalType = modal,
                    repository = repo,
                    snapshot = repo.snapshot.value,
                    defaultScope = SeasonScope(1),
                    onSelectModal = {}, onDismiss = {}, onGameCreated = {}, onPlayerCreated = {}, onSeasonCreated = {}
                )
            }
        }
        try {
            fun save(file: String, start: Long) {
                var image = scene.render(start)
                for (i in 1..4) image = scene.render(start + i * 100_000_000L)
                val f = File(outDir, "$file.png").also { it.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
                assertTrue(f.length() > 5_000, "$file ist (fast) leer")
            }
            save(name, 0)
            if (tapAt != null) {
                val pos = Offset(tapAt.first * density, tapAt.second * density)
                scene.sendPointerEvent(PointerEventType.Press, pos)
                scene.sendPointerEvent(PointerEventType.Release, pos)
                save("$name-schritt2", 1_000_000_000L)
            }
        } finally {
            scene.close()
        }
    }

    // "Weiter" sitzt oben rechts in der Kopfzeile (60 dp hoch)
    @Test
    fun newGameMobile() = render("formular-spiel-mobil", CreationModalType.NEW_GAME, 390, 800, 2f, tapAt = 390 - 60 to 30)

    @Test
    fun newGameDesktop() = render("formular-spiel-desktop", CreationModalType.NEW_GAME, 1180, 800, 1f)

    @Test
    fun newPlayerMobile() = render("formular-spieler-mobil", CreationModalType.NEW_PLAYER, 390, 800, 2f)

    @Test
    fun newSeasonDesktop() = render("formular-saison-desktop", CreationModalType.NEW_SEASON, 1180, 800, 1f)
}
