package com.handball.stattracker.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.AuthActionResult
import com.handball.stattracker.model.UserProfile
import com.handball.stattracker.model.UserRole
import com.handball.stattracker.repository.DemoDataSeed
import com.handball.stattracker.repository.HandballRepository
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Zeichnet die Hauptbildschirme ohne Fenster als PNG nach `composeApp/build/screenshots/`,
 * damit Theme und Layout ohne Gerät beurteilt werden können. Kein Vergleich gegen Referenzbilder,
 * der Test prüft nur, dass das Zeichnen klappt und die Bilder nicht leer sind.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {
    private val outDir = File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }

    private val profile = UserProfile(id = "u1", email = "trainer@club.de", fullName = "Tina Trainer", role = UserRole.ADMIN, status = "ACTIVE")

    private fun render(
        name: String, width: Int, height: Int, density: Float, dark: Boolean, screen: MainNavScreen,
        gameId: Int? = null, role: UserRole = UserRole.ADMIN, playerId: Int? = null, ownPlayerId: Int? = null
    ) {
        val repo = HandballRepository(persistLocally = false, canEdit = role != UserRole.SPIELER)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        // width/height sind in dp; die Szene misst in Pixeln
        val scene = ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalUserRole provides role) {
                MainAppContent(
                    repository = repo,
                    darkTheme = dark,
                    onToggleDarkTheme = {},
                    profile = profile.copy(role = role, playerId = ownPlayerId),
                    email = profile.email,
                    offline = false,
                    onRequestClaim = { AuthActionResult.Success },
                    adminRepository = null,
                    onSignOut = {},
                    initialScreen = screen,
                    initialGameId = gameId,
                    initialPlayerId = playerId
                )
            }
        }
        try {
            // mehrere Frames, damit LaunchedEffects und Layout-Folgen durchlaufen
            var image = scene.render(0)
            for (i in 1..4) image = scene.render(i * 100_000_000L)
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            val file = File(outDir, "$name.png").also { it.writeBytes(png) }
            assertTrue(file.length() > 5_000, "$name ist (fast) leer: ${file.length()} Bytes")
        } finally {
            scene.close()
        }
    }

    @Test
    fun desktopLightHome() = render("desktop-hell-home", 1180, 780, 1f, dark = false, screen = MainNavScreen.HOME_DASHBOARD)

    @Test
    fun desktopDarkHome() = render("desktop-dunkel-home", 1180, 780, 1f, dark = true, screen = MainNavScreen.HOME_DASHBOARD)

    @Test
    fun desktopLightRoster() = render("desktop-hell-kader", 1180, 780, 1f, dark = false, screen = MainNavScreen.ROSTER)

    @Test
    fun desktopLightStandings() = render("desktop-hell-tabelle", 1180, 780, 1f, dark = false, screen = MainNavScreen.STANDINGS)

    @Test
    fun desktopLightSeason() = render("desktop-hell-saison", 1180, 780, 1f, dark = false, screen = MainNavScreen.SEASON)

    @Test
    fun desktopLightGame() = render("desktop-hell-spiel", 1180, 1000, 1f, dark = false, screen = MainNavScreen.SEASON, gameId = DemoDataSeed.createDemoSnapshot().games.first { it.isOwnTeamGame }.gameId)

    @Test
    fun mobileLightGame() = render("mobil-hell-spiel", 390, 800, 2f, dark = false, screen = MainNavScreen.SEASON, gameId = DemoDataSeed.createDemoSnapshot().games.first { it.isOwnTeamGame }.gameId)

    @Test
    fun mobileLightHome() = render("mobil-hell-home", 390, 800, 2f, dark = false, screen = MainNavScreen.HOME_DASHBOARD)

    @Test
    fun mobileDarkHome() = render("mobil-dunkel-home", 390, 800, 2f, dark = true, screen = MainNavScreen.HOME_DASHBOARD)

    @Test
    fun mobileLightRoster() = render("mobil-hell-kader", 390, 800, 2f, dark = false, screen = MainNavScreen.ROSTER)

    @Test
    fun mobilePlayerOwnProfile() = render(
        "mobil-hell-spieler-profil", 390, 800, 2f, dark = false, screen = MainNavScreen.PROFILE,
        role = UserRole.SPIELER, playerId = 4, ownPlayerId = 4
    )

    /** Schmales Handy (320 dp): fünf Tabs mit Trikotnummer-Symbol, nichts klebt am Rand. */
    @Test
    fun mobileNarrowPlayerNav() = render(
        "mobil-schmal-spieler-profil", 320, 568, 2f, dark = false, screen = MainNavScreen.PROFILE,
        role = UserRole.SPIELER, ownPlayerId = 4
    )

    @Test
    fun mobileNarrowPlayerNavDark() = render(
        "mobil-schmal-spieler-kader-dunkel", 320, 568, 2f, dark = true, screen = MainNavScreen.ROSTER,
        role = UserRole.SPIELER, ownPlayerId = 4
    )

    @Test
    fun mobileNarrowCoachNav() = render(
        "mobil-schmal-trainer-kader", 320, 568, 2f, dark = false, screen = MainNavScreen.ROSTER,
        role = UserRole.TRAINER, ownPlayerId = 9
    )

    /** Trainer mit eigenem Kadereintrag: "Profil" in der Seitenleiste, nur der eigene Eintrag (keine Kaderliste). */
    @Test
    fun desktopCoachOwnProfile() = render(
        "desktop-hell-eigenes-profil", 1180, 780, 1f, dark = false, screen = MainNavScreen.PROFILE,
        role = UserRole.TRAINER, playerId = 9, ownPlayerId = 9
    )

    /** Trainer mit eigenem Kadereintrag in der Kaderliste: "Kader" markiert, "Profil" nicht. */
    @Test
    fun desktopCoachOtherPlayer() = render(
        "desktop-hell-kader-mit-profil", 1180, 780, 1f, dark = false, screen = MainNavScreen.ROSTER,
        role = UserRole.TRAINER, playerId = 4, ownPlayerId = 9
    )

    @Test
    fun desktopRosterGameHistory() = render(
        "desktop-hell-kader-spiele", 1180, 1100, 1f, dark = false, screen = MainNavScreen.ROSTER, playerId = 15
    )

    /** Inhalt des Vereins-Dialogs (der Dialog selbst braucht ein Fenster). */
    @Test
    fun clubManagementContent() {
        val repo = HandballRepository(persistLocally = false, canEdit = true)
        val snapshot = DemoDataSeed.createDemoSnapshot()
        repo.replaceSnapshot(snapshot)
        val scene = ImageComposeScene(420, 300, Density(1f)) {
            com.handball.stattracker.ui.theme.HandballTheme(darkTheme = false) {
                androidx.compose.material3.Surface {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) {
                        com.handball.stattracker.ui.screens.ClubManagementContent(repo, snapshot, snapshot.seasons.first().seasonId)
                    }
                }
            }
        }
        try {
            var image = scene.render(0)
            for (i in 1..3) image = scene.render(i * 100_000_000L)
            val file = File(outDir, "desktop-hell-verein.png").also {
                it.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
            assertTrue(file.length() > 3_000)
        } finally {
            scene.close()
        }
    }

    /** Anmeldung mit Logo (ohne Supabase: die Maske selbst braucht keine Verbindung). */
    @Test
    fun loginLight() = renderDialog("anmeldung-hell", 900, 720) {
        com.handball.stattracker.ui.screens.LoginScreen(
            auth = com.handball.stattracker.repository.AuthRepository(api = null),
            initialMessage = null
        )
    }

    @Test
    fun loginDark() = renderDialog("anmeldung-dunkel", 900, 720, dark = true) {
        com.handball.stattracker.ui.screens.LoginScreen(
            auth = com.handball.stattracker.repository.AuthRepository(api = null),
            initialMessage = null
        )
    }

    /** Zeichnet einen Dialog (so, wie ihn der Nutzer sieht) auf neutralem Hintergrund. */
    private fun renderDialog(name: String, width: Int, height: Int, dark: Boolean = false, dialog: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            com.handball.stattracker.ui.theme.HandballTheme(darkTheme = dark) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize())
                }
                dialog()
            }
        }
        try {
            var image = scene.render(0)
            for (i in 1..5) image = scene.render(i * 100_000_000L)
            val file = File(outDir, "$name.png").also { it.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
            assertTrue(file.length() > 3_000, "$name leer")
        } finally {
            scene.close()
        }
    }

    private fun adminRepo(): HandballRepository =
        HandballRepository(persistLocally = false, canEdit = true).also { it.replaceSnapshot(DemoDataSeed.createDemoSnapshot()) }

    @Test
    fun adminAccountDialog() {
        val repo = adminRepo()
        renderDialog("dialog-admin-konto", 700, 560) {
            CompositionLocalProvider(LocalUserRole provides UserRole.ADMIN) {
                com.handball.stattracker.ui.screens.AccountDialog(
                    profile = profile, email = profile.email, offline = false,
                    players = repo.snapshot.value.players,
                    onRequestClaim = { AuthActionResult.Success },
                    onSignOut = {}, darkTheme = false, onToggleDarkTheme = {},
                    onOpenClub = {}, onOpenAdmin = {}, onDismiss = {}
                )
            }
        }
    }

    /** Mobil, Trainer mit verknüpftem Kadereintrag: Zugang zum eigenen Profil im Konto-Dialog. */
    @Test
    fun coachAccountDialogWithOwnProfile() {
        val repo = adminRepo()
        renderDialog("dialog-trainer-konto-profil", 390, 560) {
            CompositionLocalProvider(LocalUserRole provides UserRole.TRAINER) {
                com.handball.stattracker.ui.screens.AccountDialog(
                    profile = profile.copy(role = UserRole.TRAINER, playerId = 9), email = profile.email, offline = false,
                    players = repo.snapshot.value.players,
                    onRequestClaim = { AuthActionResult.Success },
                    onSignOut = {}, darkTheme = false, onToggleDarkTheme = {},
                    onOpenClub = {}, onOpenOwnProfile = {}, onDismiss = {}
                )
            }
        }
    }

    @Test
    fun adminClubDialog() {
        val repo = adminRepo()
        val snapshot = repo.snapshot.value
        renderDialog("dialog-admin-verein", 700, 560) {
            CompositionLocalProvider(LocalUserRole provides UserRole.ADMIN) {
                com.handball.stattracker.ui.screens.ClubDialog(repo, snapshot, snapshot.seasons.first().seasonId, onDismiss = {})
            }
        }
    }

    @Test
    fun adminUserDialog() {
        val users = listOf(
            profile,
            UserProfile(id = "p1", email = "j.knorr@club.de", fullName = "Juri Knorr Schmidt", status = "PENDING"),
            UserProfile(id = "p2", email = "nina@club.de", fullName = "Nina Neu", status = "PENDING"),
            UserProfile(id = "c1", email = "max@club.de", fullName = "Max Muster", status = "ACTIVE", claimPlayerId = 4),
            UserProfile(id = "l1", email = "lena@club.de", fullName = "Lena Lang", role = UserRole.TRAINER, status = "ACTIVE", playerId = 9),
            UserProfile(id = "s1", email = "sven@club.de", fullName = "Sven Gesperrt", status = "SUSPENDED")
        )
        val api = object : com.handball.stattracker.repository.AdminApi {
            override suspend fun listProfiles(accessToken: String) = users
            override suspend fun patchProfile(accessToken: String, userId: String, patch: kotlinx.serialization.json.JsonObject) {}
        }
        val admin = com.handball.stattracker.repository.AdminRepository(api, ownUserId = profile.id) { "token" }
        val repo = adminRepo()
        renderDialog("dialog-admin-benutzer", 760, 760) {
            com.handball.stattracker.ui.screens.AdminDialog(admin, profile.id, repo.snapshot.value.players, onDismiss = {})
        }
    }
}
