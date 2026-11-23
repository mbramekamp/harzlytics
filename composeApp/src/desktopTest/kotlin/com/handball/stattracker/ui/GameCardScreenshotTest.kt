package com.handball.stattracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.FieldPlayerGameStats
import com.handball.stattracker.model.GoalKeeperGameStats
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.PlayerGame
import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.ui.screens.FieldPlayerStatTrackerCard
import com.handball.stattracker.ui.screens.GoalKeeperStatTrackerCard
import com.handball.stattracker.ui.screens.SubstitutionPicker
import com.handball.stattracker.ui.screens.SideBySideLineupAndBenchSection
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.handball.stattracker.repository.DemoDataSeed
import com.handball.stattracker.repository.HandballRepository
import com.handball.stattracker.ui.theme.HandballTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Spielerkarten im Bearbeitungsmodus, zugeklappt und nach einem Tipp auf "Mehr" (oben rechts).
 */
@OptIn(ExperimentalComposeUiApi::class)
class GameCardScreenshotTest {
    private val outDir = File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }
    private val density = 2f
    private val width = 400
    private val pad = 12

    private val fieldPlayer = Player(playerId = 1, firstName = "Lena", lastName = "Krause", jerseyNumberHomeKit = 7)
    private val keeper = Player(playerId = 2, firstName = "Mia", lastName = "Hoffmann", role = PlayerRole.GOALKEEPER, jerseyNumberHomeKit = 12)

    private fun render(name: String, height: Int, card: @Composable () -> Unit) {
        val scene = ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density)) {
            HandballTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(pad.dp)) { card() }
            }
        }
        try {
            fun save(file: String, start: Long) {
                var image = scene.render(start)
                for (i in 1..4) image = scene.render(start + i * 100_000_000L)
                val f = File(outDir, "$file.png").also { it.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
                assertTrue(f.length() > 5_000, "$file ist (fast) leer")
            }
            save("$name-zu", 0)
            // Aufklapp-Pfeil: rechts oben in der Kopfzeile (Kartenrand 12 dp, Knopf 48 dp)
            val toggle = Offset((width - pad - 12 - 24) * density, (pad + 12 + 24) * density)
            scene.sendPointerEvent(PointerEventType.Press, toggle)
            scene.sendPointerEvent(PointerEventType.Release, toggle)
            scene.sendPointerEvent(PointerEventType.Move, Offset(0f, 0f))
            save("$name-auf", 1_000_000_000L)
        } finally {
            scene.close()
        }
    }

    @Test
    fun fieldPlayerCard() = render("karte-feldspieler", 470) {
        FieldPlayerStatTrackerCard(
            player = fieldPlayer,
            positionName = "RL",
            playerGame = PlayerGame(1, 1, 1, twoMinuteSuspensions = 1, yellowCard = true, isOnCourt = true, plusMinus = 2, secondsPlayed = 1260),
            stats = FieldPlayerGameStats(1, 1, goalsScored = 4, goalesAttempted = 6, assists = 2, steals = 1),
            gameElapsedSeconds = 1500,
            isWideScreen = false,
            isEditMode = true,
            canSubstitute = true,
            onOpenProfile = {},
            onSubstitutionClick = {},
            onAdjustPlusMinus = {},
            onUpdateStats = {},
            onUpdateDisciplinary = { _, _, _, _ -> }
        )
    }

    @Test
    fun goalkeeperCard() = render("karte-torhueter", 470) {
        GoalKeeperStatTrackerCard(
            keeper = keeper,
            positionName = "TW",
            playerGame = PlayerGame(2, 1, 2, isOnCourt = true, plusMinus = 2, secondsPlayed = 1500),
            stats = GoalKeeperGameStats(1, 2, saves = 9, goalesConceded = 11, penaltiesSaved = 1, sucessfulFastBreakPasses = 2, attemptedFastBreakPasses = 3),
            gameElapsedSeconds = 1500,
            isWideScreen = false,
            isEditMode = true,
            canSubstitute = true,
            onOpenProfile = {},
            onSubstitutionClick = {},
            onAdjustPlusMinus = {},
            onUpdateStats = {},
            onUpdateDisciplinary = { _, _, _, _ -> }
        )
    }

    /** Wechsel-Auswahl (Demo-Spiel 1): mobil als Bottom Sheet für Feld- und Bank-Spieler, breit als Pop-up. */
    @Test
    fun substitutionPicker() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        val snapshot = repo.snapshot.value
        listOf(
            Triple(3, "wechsel-feldspieler-mobil", true),
            Triple(13, "wechsel-bankspieler-mobil", true),
            Triple(3, "wechsel-feldspieler-desktop", false)
        ).forEach { (playerId, name, asSheet) ->
            val scene = ImageComposeScene((width * density).toInt(), (800 * density).toInt(), Density(density)) {
                HandballTheme(darkTheme = false) {
                    SubstitutionPicker(
                        asBottomSheet = asSheet,
                        tappedPlayer = snapshot.players.first { it.playerId == playerId },
                        options = repo.substitutionOptions(1, playerId)!!,
                        snapshot = snapshot,
                        gameId = 1,
                        onCourtCount = 7,
                        onPickPartner = {},
                        onActAlone = {},
                        onDismiss = {}
                    )
                }
            }
            try {
                var image = scene.render(0)
                for (i in 1..4) image = scene.render(i * 100_000_000L)
                val f = File(outDir, "$name.png").also { it.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
                assertTrue(f.length() > 5_000, "$name ist (fast) leer")
            } finally {
                scene.close()
            }
        }
    }

    /** Handy im Bearbeitungsmodus: nur die Spieler auf dem Feld, ein Platz frei (Demo-Spiel 1, #15 auf der Bank). */
    @Test
    fun mobileLineupWithFreeSlot() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        repo.movePlayerToBench(gameId = 1, playerOutId = 3)
        val snapshot = repo.snapshot.value
        val game = snapshot.games.first { it.gameId == 1 }
        val participants = snapshot.playerGames.filter { it.gameId == 1 }.mapNotNull { pg ->
            snapshot.players.find { it.playerId == pg.playerId && it.role != PlayerRole.COACH }?.let { pg to it }
        }.sortedWith(compareBy({ it.second.role != PlayerRole.GOALKEEPER }, { it.second.jerseyNumberHomeKit ?: 99 }))
        val scene = ImageComposeScene((width * density).toInt(), (3800 * density).toInt(), Density(density)) {
            HandballTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(10.dp)) {
                    androidx.compose.foundation.layout.Column {
                        SideBySideLineupAndBenchSection(
                            repository = repo,
                            snapshot = snapshot,
                            game = game,
                            isSideBySide = false,
                            isEditMode = true,
                            autoSyncScore = true,
                            onCourtParticipants = participants.filter { it.first.isOnCourt },
                            benchParticipants = participants.filter { !it.first.isOnCourt },
                            onTapPlayerForSubstitution = {},
                            showBench = false,
                            canFillFreeSlot = repo.courtEntryCandidates(1).isNotEmpty(),
                            onOpenFreeSlot = {},
                            onApplyStartingSeven = {},
                            onNavigateToPlayer = {},
                            onEnterEditAndManageSquad = {}
                        )
                    }
                }
            }
        }
        try {
            var image = scene.render(0)
            for (i in 1..4) image = scene.render(i * 100_000_000L)
            val f = File(outDir, "mobil-feld-freier-platz.png").also { it.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
            assertTrue(f.length() > 5_000)
        } finally {
            scene.close()
        }
    }
}
