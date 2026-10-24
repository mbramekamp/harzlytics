package com.handball.stattracker.server

import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.repository.DemoDataSeed
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.DriverManager

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val serverDataDir = File(System.getProperty("user.home"), ".handball-stat-tracker-server").apply {
        if (!exists()) mkdirs()
    }
    val snapshotFile = File(serverDataDir, "server-snapshot.json")
    val sqliteDbFile = File(serverDataDir, "handball-server.sqlite")

    initServerSqliteSchema(sqliteDbFile)

    var currentSnapshot: DatabaseSnapshot = if (snapshotFile.exists()) {
        runCatching {
            json.decodeFromString<DatabaseSnapshot>(snapshotFile.readText(Charsets.UTF_8))
        }.getOrElse { DemoDataSeed.createDemoSnapshot() }
    } else {
        val seeded = DemoDataSeed.createDemoSnapshot()
        snapshotFile.writeText(json.encodeToString(seeded), Charsets.UTF_8)
        persistSnapshotToSqlite(sqliteDbFile, seeded)
        seeded
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    install(ContentNegotiation) {
        json(json)
    }

    routing {
        get("/api/health") {
            call.respond(mapOf("status" to "OK", "club" to currentSnapshot.ownClubName))
        }

        get("/api/sync") {
            call.respond(currentSnapshot)
        }

        post("/api/sync") {
            val incoming = call.receive<DatabaseSnapshot>()
            if (incoming.lastUpdatedEpochMillis >= currentSnapshot.lastUpdatedEpochMillis) {
                currentSnapshot = incoming
                snapshotFile.writeText(json.encodeToString(currentSnapshot), Charsets.UTF_8)
                persistSnapshotToSqlite(sqliteDbFile, currentSnapshot)
            }
            call.respond(currentSnapshot)
        }
    }
}

private fun initServerSqliteSchema(sqliteFile: File) {
    DriverManager.getConnection("jdbc:sqlite:${sqliteFile.absolutePath}").use { conn ->
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS POSITION (
                    position_id INTEGER PRIMARY KEY,
                    position_name TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS PLAYER (
                    player_id INTEGER PRIMARY KEY,
                    position_id INTEGER,
                    first_name TEXT NOT NULL,
                    last_name TEXT NOT NULL,
                    email TEXT NOT NULL,
                    role TEXT NOT NULL,
                    jersey_number_home_kit INTEGER,
                    jersey_number_away_kit INTEGER
                );
                CREATE TABLE IF NOT EXISTS SEASON (
                    season_id INTEGER PRIMARY KEY,
                    division TEXT NOT NULL,
                    start_of_season TEXT NOT NULL,
                    end_of_season TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS OPPONENT_TEAMS (
                    opponent_team_id INTEGER PRIMARY KEY,
                    opponent_name TEXT NOT NULL,
                    opponent_location TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS SEASON_OPPONENT (
                    season_id INTEGER NOT NULL,
                    opponent_team_id INTEGER NOT NULL,
                    PRIMARY KEY (season_id, opponent_team_id)
                );
                CREATE TABLE IF NOT EXISTS GAME (
                    game_id INTEGER PRIMARY KEY,
                    season_id INTEGER NOT NULL,
                    opponent_id INTEGER NOT NULL,
                    second_opponent_id INTEGER,
                    is_home_game INTEGER NOT NULL,
                    location TEXT NOT NULL,
                    game_type TEXT NOT NULL,
                    game_date TEXT NOT NULL,
                    home_team_halftime_score INTEGER NOT NULL,
                    away_team_halftime_score INTEGER NOT NULL,
                    home_team_score INTEGER NOT NULL,
                    away_team_score INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS PLAYER_GAME (
                    player_game_id INTEGER PRIMARY KEY,
                    game_id INTEGER NOT NULL,
                    player_id INTEGER NOT NULL,
                    two_minute_suspensions INTEGER NOT NULL,
                    yellow_card INTEGER NOT NULL,
                    red_card INTEGER NOT NULL,
                    blue_card INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS FIELD_PLAYER_GAMESTATS (
                    field_player_gamestats_id INTEGER PRIMARY KEY,
                    player_game_id INTEGER NOT NULL,
                    goals_scored INTEGER NOT NULL,
                    goales_attempted INTEGER NOT NULL,
                    penalties_scored INTEGER NOT NULL,
                    penalties_attempted INTEGER NOT NULL,
                    penalties_drawn INTEGER NOT NULL,
                    assists INTEGER NOT NULL,
                    steals INTEGER NOT NULL,
                    blocks INTEGER NOT NULL,
                    technical_mistakes INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS GOAL_KEEPER_GAMESTATS (
                    goal_keeper_gamestats_id INTEGER PRIMARY KEY,
                    player_game_id INTEGER NOT NULL,
                    saves INTEGER NOT NULL,
                    goales_conceded INTEGER NOT NULL,
                    penalties_saved INTEGER NOT NULL,
                    penalties_conceded INTEGER NOT NULL,
                    sucessful_fast_break_passes INTEGER NOT NULL,
                    attempted_fast_break_passes INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS TIMEOUT (
                    timeout_id INTEGER PRIMARY KEY,
                    timeout_reason TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS COACH_GAMESTATS (
                    coach_gamestats_id INTEGER PRIMARY KEY,
                    player_game_id INTEGER NOT NULL,
                    timeout_id INTEGER NOT NULL
                );
                """.trimIndent()
            )
        }
    }
}

private fun persistSnapshotToSqlite(sqliteFile: File, snapshot: DatabaseSnapshot) {
    runCatching {
        DriverManager.getConnection("jdbc:sqlite:${sqliteFile.absolutePath}").use { conn ->
            conn.autoCommit = false
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM COACH_GAMESTATS;")
                stmt.executeUpdate("DELETE FROM TIMEOUT;")
                stmt.executeUpdate("DELETE FROM GOAL_KEEPER_GAMESTATS;")
                stmt.executeUpdate("DELETE FROM FIELD_PLAYER_GAMESTATS;")
                stmt.executeUpdate("DELETE FROM PLAYER_GAME;")
                stmt.executeUpdate("DELETE FROM GAME;")
                stmt.executeUpdate("DELETE FROM SEASON_OPPONENT;")
                stmt.executeUpdate("DELETE FROM OPPONENT_TEAMS;")
                stmt.executeUpdate("DELETE FROM SEASON;")
                stmt.executeUpdate("DELETE FROM PLAYER;")
                stmt.executeUpdate("DELETE FROM POSITION;")
            }
            conn.prepareStatement("INSERT INTO POSITION VALUES (?, ?)").use { ps ->
                snapshot.positions.forEach {
                    ps.setInt(1, it.positionId)
                    ps.setString(2, it.positionName)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.prepareStatement("INSERT INTO PLAYER VALUES (?, ?, ?, ?, ?, ?, ?, ?)").use { ps ->
                snapshot.players.forEach {
                    val posId = it.positionId
                    val homeKit = it.jerseyNumberHomeKit
                    val awayKit = it.jerseyNumberAwayKit
                    ps.setInt(1, it.playerId)
                    if (posId != null) ps.setInt(2, posId) else ps.setNull(2, java.sql.Types.INTEGER)
                    ps.setString(3, it.firstName)
                    ps.setString(4, it.lastName)
                    ps.setString(5, it.email)
                    ps.setString(6, it.role.name)
                    if (homeKit != null) ps.setInt(7, homeKit) else ps.setNull(7, java.sql.Types.INTEGER)
                    if (awayKit != null) ps.setInt(8, awayKit) else ps.setNull(8, java.sql.Types.INTEGER)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        }
    }
}
