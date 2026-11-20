package com.handball.stattracker

import com.handball.stattracker.model.AuthActionResult
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.model.UserProfile
import com.handball.stattracker.model.UserRole
import com.handball.stattracker.repository.AdminApi
import com.handball.stattracker.repository.AdminRepository
import com.handball.stattracker.repository.AuthRejectedException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAdminApi(var profiles: List<UserProfile>) : AdminApi {
    var offline = false
    var forbidden = false
    val patches = mutableListOf<Pair<String, JsonObject>>()

    override suspend fun listProfiles(accessToken: String): List<UserProfile> {
        if (offline) throw IllegalStateException("offline")
        if (forbidden) throw AuthRejectedException("Keine Berechtigung.")
        return profiles
    }

    override suspend fun patchProfile(accessToken: String, userId: String, patch: JsonObject) {
        if (offline) throw IllegalStateException("offline")
        patches += userId to patch
    }
}

class AdminRepositoryTest {

    private val admin = UserProfile(id = "admin", email = "admin@club.de", fullName = "Ada Admin", role = UserRole.ADMIN, status = "ACTIVE")
    private val pending = UserProfile(id = "p1", email = "neu@club.de", fullName = "Nina Neu", status = "PENDING")
    private val claimer = UserProfile(id = "c1", email = "max@club.de", fullName = "Max M", status = "ACTIVE", claimPlayerId = 4)
    private val linked = UserProfile(id = "l1", email = "lena@club.de", fullName = "Lena L", status = "ACTIVE", playerId = 9)
    private val suspended = UserProfile(id = "s1", email = "weg@club.de", fullName = "Sven S", status = "SUSPENDED")

    private fun repo(api: FakeAdminApi) = AdminRepository(api, ownUserId = "admin") { "token" }
    private fun api() = FakeAdminApi(listOf(admin, pending, claimer, linked, suspended))

    @Test
    fun reloadLoadsProfilesAndGroupsThemIntoSections() = runBlocking<Unit> {
        val repo = repo(api())
        assertIs<AuthActionResult.Success>(repo.reload())

        val sections = AdminRepository.sections(repo.state.value.profiles)
        assertEquals(listOf("p1"), sections.pending.map { it.id })
        assertEquals(listOf("c1"), sections.claims.map { it.id })
        assertEquals(listOf("admin", "l1", "c1"), sections.members.map { it.id })
        assertEquals(listOf("s1"), sections.suspended.map { it.id })
    }

    @Test
    fun approveActivatesAccountWithChosenRole() = runBlocking<Unit> {
        val api = api(); val repo = repo(api); repo.reload()

        assertIs<AuthActionResult.Success>(repo.approve("p1", UserRole.TRAINER))

        val patch = api.patches.single().second
        assertEquals("ACTIVE", patch["status"]!!.jsonPrimitive.content)
        assertEquals("TRAINER", patch["role"]!!.jsonPrimitive.content)
        val updated = repo.state.value.profiles.first { it.id == "p1" }
        assertEquals(UserRole.TRAINER, updated.role)
        assertTrue(updated.isActive)
    }

    @Test
    fun adminCannotChangeOwnRoleOrStatus() = runBlocking<Unit> {
        val api = api(); val repo = repo(api); repo.reload()

        assertIs<AuthActionResult.Error>(repo.suspend("admin"))
        assertIs<AuthActionResult.Error>(repo.setRole("admin", UserRole.SPIELER))
        assertIs<AuthActionResult.Error>(repo.approve("admin", UserRole.SPIELER))
        assertTrue(api.patches.isEmpty(), "Es darf kein Server-Aufruf stattfinden")
    }

    @Test
    fun confirmClaimMovesClaimToPlayerLink() = runBlocking<Unit> {
        val api = api(); val repo = repo(api); repo.reload()

        assertIs<AuthActionResult.Success>(repo.confirmClaim("c1"))

        val patch = api.patches.single().second
        assertEquals(4, patch["player_id"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, patch["claim_player_id"])
        val updated = repo.state.value.profiles.first { it.id == "c1" }
        assertEquals(4, updated.playerId)
        assertNull(updated.claimPlayerId)
    }

    @Test
    fun confirmClaimIsRefusedWhenPlayerAlreadyLinkedToAnotherAccount() = runBlocking<Unit> {
        val api = FakeAdminApi(listOf(admin, linked, claimer.copy(claimPlayerId = 9)))
        val repo = repo(api); repo.reload()

        assertIs<AuthActionResult.Error>(repo.confirmClaim("c1"))
        assertTrue(api.patches.isEmpty())
    }

    @Test
    fun rejectClaimClearsRequestOnly() = runBlocking<Unit> {
        val api = api(); val repo = repo(api); repo.reload()

        assertIs<AuthActionResult.Success>(repo.rejectClaim("c1"))

        assertEquals(setOf("claim_player_id"), api.patches.single().second.keys)
        assertNull(repo.state.value.profiles.first { it.id == "c1" }.claimPlayerId)
    }

    @Test
    fun networkAndPermissionErrorsAreReportedWithoutChangingLocalState() = runBlocking<Unit> {
        val api = api(); val repo = repo(api); repo.reload()

        api.offline = true
        assertIs<AuthActionResult.Error>(repo.approve("p1", UserRole.SPIELER))
        assertEquals("PENDING", repo.state.value.profiles.first { it.id == "p1" }.status)

        api.offline = false; api.forbidden = true
        assertIs<AuthActionResult.Error>(repo.reload())
        assertEquals("Keine Berechtigung.", repo.state.value.error)
    }

    @Test
    fun withoutSessionNothingIsSent() = runBlocking<Unit> {
        val api = api()
        val repo = AdminRepository(api, ownUserId = "admin") { null }
        assertIs<AuthActionResult.Error>(repo.reload())
        assertIs<AuthActionResult.Error>(repo.approve("p1", UserRole.SPIELER))
        assertTrue(api.patches.isEmpty())
    }

    // --- Verknüpfung beim Freigeben

    private fun player(id: Int, first: String, last: String, email: String = "", role: PlayerRole = PlayerRole.FIELD_PLAYER) =
        Player(playerId = id, positionId = 1, firstName = first, lastName = last, email = email, role = role)

    private val roster = listOf(
        player(4, "Johannes", "Golla-Richter"),
        player(9, "Lena", "Lang"),
        player(15, "Juri", "Knorr-Schmidt", email = "juri@club.de"),
        player(30, "Tom", "Trainer", role = PlayerRole.COACH)
    )

    @Test
    fun approveCanLinkThePlayerInOneStepAndClearsTheClaim() = runBlocking<Unit> {
        val api = api(); val repo = repo(api); repo.reload()

        assertIs<AuthActionResult.Success>(repo.approve("c1", UserRole.SPIELER, playerId = 4))

        val patch = api.patches.single().second
        assertEquals(4, patch["player_id"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, patch["claim_player_id"])
        assertEquals("ACTIVE", patch["status"]!!.jsonPrimitive.content)
        val updated = repo.state.value.profiles.first { it.id == "c1" }
        assertEquals(4, updated.playerId)
        assertNull(updated.claimPlayerId)
    }

    @Test
    fun approveWithoutPlayerLeavesTheLinkFieldsAlone() = runBlocking<Unit> {
        val api = api(); val repo = repo(api); repo.reload()
        repo.approve("p1", UserRole.SPIELER)
        assertEquals(setOf("status", "role"), api.patches.single().second.keys)
    }

    @Test
    fun approveRefusesAPlayerThatBelongsToAnotherAccount() = runBlocking<Unit> {
        val api = api(); val repo = repo(api); repo.reload()
        // Spieler 9 gehört schon zu "l1"
        assertIs<AuthActionResult.Error>(repo.approve("p1", UserRole.SPIELER, playerId = 9))
        assertTrue(api.patches.isEmpty())
        assertEquals("PENDING", repo.state.value.profiles.first { it.id == "p1" }.status)
    }

    @Test
    fun availablePlayersExcludeCoachesAndAlreadyLinked() {
        val profiles = listOf(linked.copy(playerId = 9))
        assertEquals(listOf(4, 15), AdminRepository.availablePlayers(roster, profiles).map { it.playerId })
    }

    @Test
    fun suggestionFindsTheNameInAnyOrderAndSpelling() {
        val p = pending.copy(fullName = "knorr schmidt, JURI")
        assertEquals(15, AdminRepository.suggestPlayerId(p, roster, listOf(p)))
        val umlaut = pending.copy(fullName = "Johannes Golla Richter")
        assertEquals(4, AdminRepository.suggestPlayerId(umlaut, roster, listOf(umlaut)))
        val oe = player(5, "Julian", "Köster-Lang")
        assertEquals(5, AdminRepository.suggestPlayerId(pending.copy(fullName = "Julian Koester Lang"), roster + oe, listOf(pending)))
    }

    @Test
    fun suggestionPrefersTheRequestedPlayerThenTheEmail() {
        val claimed = pending.copy(fullName = "Jemand Anders", claimPlayerId = 4)
        assertEquals(4, AdminRepository.suggestPlayerId(claimed, roster, listOf(claimed)))
        val byMail = pending.copy(fullName = "Jemand Anders", email = "JURI@club.de")
        assertEquals(15, AdminRepository.suggestPlayerId(byMail, roster, listOf(byMail)))
    }

    @Test
    fun suggestionIsEmptyWhenUnclearOrTaken() {
        // keine Übereinstimmung
        assertNull(AdminRepository.suggestPlayerId(pending, roster, listOf(pending)))
        // mehrdeutig: zwei Spieler gleichen Namens
        val twins = roster + player(99, "Lena", "Lang")
        assertNull(AdminRepository.suggestPlayerId(pending.copy(fullName = "Lena Lang"), twins, listOf(pending)))
        // schon vergeben
        val owner = linked.copy(playerId = 9)
        assertNull(AdminRepository.suggestPlayerId(pending.copy(fullName = "Lena Lang"), roster, listOf(pending, owner)))
        // Trainer werden nie vorgeschlagen
        assertNull(AdminRepository.suggestPlayerId(pending.copy(fullName = "Tom Trainer"), roster, listOf(pending)))
    }
}
