package com.handball.stattracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.FieldPlayerGameStats
import com.handball.stattracker.model.GoalKeeperGameStats
import com.handball.stattracker.model.PlayerGame
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.repository.DemoDataSeed
import com.handball.stattracker.repository.HandballRepository
import com.handball.stattracker.repository.swapStandbyByCourtPlayer
import com.handball.stattracker.ui.screens.MobileCourtList
import com.handball.stattracker.ui.screens.SideBySideLineupAndBenchSection
import com.handball.stattracker.ui.screens.StartingSevenDialog
import com.handball.stattracker.ui.theme.HandballTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Wechselpaar Angriff/Abwehr (Demo-Spiel 1: #15 auf dem Feld, #20 als Wechselspieler). */
@OptIn(ExperimentalComposeUiApi::class)
class SwapPairScreenshotTest {
    private val outDir = File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }
    private val density = 2f

    private fun pairedRepo() = HandballRepository(persistLocally = false).apply {
        replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        setGameClockSeconds(1, 900)
        check(setSwapPartner(gameId = 1, playerIdA = 3, playerIdB = 13))
    }

    private fun shot(name: String, width: Int, height: Int, dark: Boolean = false, content: @Composable () -> Unit) {
        val scene = ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density)) {
            HandballTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(10.dp)) { content() }
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

    private fun participants(repo: HandballRepository): List<Pair<PlayerGame, Player>> {
        val s = repo.snapshot.value
        return s.playerGames.filter { it.gameId == 1 }.mapNotNull { pg ->
            s.players.find { it.playerId == pg.playerId && it.role != PlayerRole.COACH }?.let { pg to it }
        }.sortedWith(compareBy({ it.second.role != PlayerRole.GOALKEEPER }, { it.second.jerseyNumberHomeKit ?: 99 }))
    }

    private fun standbyMap(repo: HandballRepository, all: List<Pair<PlayerGame, Player>>) =
        repo.snapshot.value.swapStandbyByCourtPlayer(1).mapValues { (_, pg) -> all.first { it.first.playerGameId == pg.playerGameId } }

    @Test
    fun mobileList() {
        val repo = pairedRepo()
        val s = repo.snapshot.value
        val all = participants(repo)
        shot("wechselpaar-mobil", 400, 700) {
            MobileCourtList(
                onCourt = all.filter { it.first.isOnCourt },
                swapStandby = standbyMap(repo, all),
                positionName = { p -> s.positions.find { it.positionId == p.positionId }?.positionName?.substringBefore(" ") ?: p.role.label },
                fieldStats = { pg -> s.fieldPlayerStats.find { it.playerGameId == pg.playerGameId } ?: FieldPlayerGameStats(0, pg.playerGameId) },
                keeperStats = { pg -> s.goalKeeperStats.find { it.playerGameId == pg.playerGameId } ?: GoalKeeperGameStats(0, pg.playerGameId) },
                isEditMode = true,
                selectedPlayerId = 13,
                showFreeSlot = false,
                onSelect = {}, onOpenProfile = {}, onOpenFreeSlot = {}, onOpenStartingSeven = {}
            )
        }
    }

    @Test
    fun wideCourtView() {
        listOf(false to "wechselpaar-desktop-hell", true to "wechselpaar-desktop-dunkel").forEach { (dark, name) ->
            val repo = pairedRepo()
            val all = participants(repo)
            val standby = standbyMap(repo, all)
            val standbyIds = standby.values.map { it.first.playerGameId }.toSet()
            shot(name, 1100, 1500, dark) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SideBySideLineupAndBenchSection(
                        repository = repo,
                        snapshot = repo.snapshot.value,
                        game = repo.snapshot.value.games.first { it.gameId == 1 },
                        isSideBySide = true,
                        isEditMode = true,
                        autoSyncScore = true,
                        onCourtParticipants = all.filter { it.first.isOnCourt },
                        benchParticipants = all.filter { !it.first.isOnCourt && it.first.playerGameId !in standbyIds },
                        swapStandby = standby,
                        onTapPlayerForSubstitution = {},
                        showBench = true,
                        canFillFreeSlot = false,
                        onOpenFreeSlot = {},
                        onApplyStartingSeven = {},
                        onNavigateToPlayer = {},
                        onEnterEditAndManageSquad = {}
                    )
                }
            }
        }
    }

    @Test
    fun startingSevenDialogWithSwapSlot() {
        val repo = pairedRepo()
        // Startsieben mit #20 als Wechsel übernehmen → beim erneuten Öffnen vorausgewählt
        val rec = repo.recommendStartingSeven(1)
        val standbyId = rec.swapStandbyOptions.first().player.playerId
        repo.applyStartingSevenLineup(1, rec.chosenPlayerIds, rec.withSwapStandby(standbyId).swapPair)
        shot("startsieben-wechsel", 560, 1100) {
            StartingSevenDialog(
                repository = repo,
                gameId = 1,
                initialUseSeventhFieldPlayer = false,
                onDismiss = {},
                onApplied = {}
            )
        }
    }
}
