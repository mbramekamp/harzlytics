package com.handball.stattracker.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
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
    fun mobileLightHome() = render("mobil-hell-home", 390, 800, 2f, dark = false, screen = MainNavScreen.HOME_DASHBOARD)

    @Test
    fun mobileDarkHome() = render("mobil-dunkel-home", 390, 800, 2f, dark = true, screen = MainNavScreen.HOME_DASHBOARD)

    @Test
    fun mobileLightRoster() = render("mobil-hell-kader", 390, 800, 2f, dark = false, screen = MainNavScreen.ROSTER)

    @Test
    fun mobilePlayerOwnProfile() = render(
        "mobil-hell-spieler-profil", 390, 800, 2f, dark = false, screen = MainNavScreen.ROSTER,
        role = UserRole.SPIELER, playerId = 4, ownPlayerId = 4
    )
}
