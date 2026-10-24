package com.handball.stattracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.repository.HandballRepository
import com.handball.stattracker.ui.screens.*
import com.handball.stattracker.ui.theme.HandballTheme

enum class MainNavScreen(val label: String) {
    HOME_DASHBOARD("Home"),
    ROSTER("Kader"),
    SEASON("Saison"),
    STANDINGS("Tabelle")
}

@Composable
fun HandballApp(
    repository: HandballRepository = remember { HandballRepository() }
) {
    var darkTheme by remember { mutableStateOf(false) }
    val snapshot by repository.snapshot.collectAsState()

    var currentScreen by remember { mutableStateOf(MainNavScreen.ROSTER) }
    var selectedSeasonId by remember(snapshot.seasons) {
        mutableStateOf(snapshot.seasons.firstOrNull()?.seasonId ?: 1)
    }
    var selectedPlayerId by remember {
        mutableStateOf<Int?>(snapshot.players.firstOrNull()?.playerId)
    }
    var selectedGameId by remember { mutableStateOf<Int?>(null) }
    var creationModal by remember { mutableStateOf(CreationModalType.NONE) }

    // Statische Titelzeile ('Spieler/Saison/Spiel' aus dem UI-Grundkonzept):
    // Zeigt den Namen des aktuell geöffneten Elements (Spielername, Saison oder Spielpaarung).
    val staticHeaderTitle = remember(currentScreen, selectedPlayerId, selectedSeasonId, selectedGameId, snapshot) {
        computeHeaderTitle(
            currentScreen = currentScreen,
            selectedPlayerId = selectedPlayerId,
            selectedSeasonId = selectedSeasonId,
            selectedGameId = selectedGameId,
            snapshot = snapshot,
            repository = repository
        )
    }

    HandballTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 700.dp

                if (isWideScreen) {
                    // Desktop / Web / Tablet Layout exakt wie in der UI-Skizze:
                    // Links vertikale abgerundete Navbar (mit [H] oben), rechts oben statische Titel-Box, darunter Content-Box
                    Row(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        VerticalRoundedNavbar(
                            currentScreen = currentScreen,
                            darkTheme = darkTheme,
                            onSelectScreen = { screen ->
                                currentScreen = screen
                                if (screen != MainNavScreen.SEASON) {
                                    selectedGameId = null
                                }
                            },
                            onOpenPlusMenu = { creationModal = CreationModalType.CHOOSER },
                            onToggleDarkTheme = { darkTheme = !darkTheme }
                        )

                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            StaticTopHeaderBox(
                                title = staticHeaderTitle,
                                subtitle = snapshot.ownClubName
                            )

                            Surface(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                tonalElevation = 1.dp
                            ) {
                                MainContentRouter(
                                    currentScreen = currentScreen,
                                    repository = repository,
                                    snapshot = snapshot,
                                    isWideScreen = true,
                                    selectedSeasonId = selectedSeasonId,
                                    selectedPlayerId = selectedPlayerId,
                                    selectedGameId = selectedGameId,
                                    onSelectSeason = { selectedSeasonId = it },
                                    onSelectPlayer = { playerId ->
                                        selectedPlayerId = playerId
                                        currentScreen = MainNavScreen.ROSTER
                                    },
                                    onSelectGame = { gameId ->
                                        selectedGameId = gameId
                                        if (gameId != null) {
                                            currentScreen = MainNavScreen.SEASON
                                        }
                                    },
                                    onNavigateToStandings = { currentScreen = MainNavScreen.STANDINGS },
                                    onOpenModal = { creationModal = it }
                                )
                            }
                        }
                    }
                } else {
                    // Mobile Layout (iOS / Android Hochformat):
                    // Navbar am unteren Bildschirmrand für optimale Daumen-Bedienung
                    Column(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StaticTopHeaderBox(
                            title = staticHeaderTitle,
                            subtitle = snapshot.ownClubName
                        )

                        Surface(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            tonalElevation = 1.dp
                        ) {
                            MainContentRouter(
                                currentScreen = currentScreen,
                                repository = repository,
                                snapshot = snapshot,
                                isWideScreen = false,
                                selectedSeasonId = selectedSeasonId,
                                selectedPlayerId = selectedPlayerId,
                                selectedGameId = selectedGameId,
                                onSelectSeason = { selectedSeasonId = it },
                                onSelectPlayer = { playerId ->
                                    selectedPlayerId = playerId
                                    currentScreen = MainNavScreen.ROSTER
                                },
                                onSelectGame = { gameId ->
                                    selectedGameId = gameId
                                    if (gameId != null) {
                                        currentScreen = MainNavScreen.SEASON
                                    }
                                },
                                onNavigateToStandings = { currentScreen = MainNavScreen.STANDINGS },
                                onOpenModal = { creationModal = it }
                            )
                        }

                        BottomMobileNavbar(
                            currentScreen = currentScreen,
                            darkTheme = darkTheme,
                            onSelectScreen = { screen ->
                                currentScreen = screen
                                if (screen != MainNavScreen.SEASON) {
                                    selectedGameId = null
                                }
                            },
                            onOpenPlusMenu = { creationModal = CreationModalType.CHOOSER },
                            onToggleDarkTheme = { darkTheme = !darkTheme }
                        )
                    }
                }
            }

            // Plus-Menü & Erstellungs-Dialoge
            PlusCreationModalHost(
                modalType = creationModal,
                repository = repository,
                snapshot = snapshot,
                defaultSeasonId = selectedSeasonId,
                onSelectModal = { creationModal = it },
                onDismiss = { creationModal = CreationModalType.NONE },
                onGameCreated = { newGame ->
                    selectedSeasonId = newGame.seasonId
                    selectedGameId = newGame.gameId
                    currentScreen = MainNavScreen.SEASON
                },
                onPlayerCreated = { newPerson ->
                    selectedPlayerId = newPerson.playerId
                    currentScreen = MainNavScreen.ROSTER
                },
                onSeasonCreated = { newSeason ->
                    selectedSeasonId = newSeason.seasonId
                    selectedGameId = null
                    currentScreen = MainNavScreen.SEASON
                }
            )
        }
    }
}

private fun computeHeaderTitle(
    currentScreen: MainNavScreen,
    selectedPlayerId: Int?,
    selectedSeasonId: Int,
    selectedGameId: Int?,
    snapshot: DatabaseSnapshot,
    repository: HandballRepository
): String {
    val activeSeason = snapshot.seasons.find { it.seasonId == selectedSeasonId } ?: snapshot.seasons.firstOrNull()
    return when (currentScreen) {
        MainNavScreen.HOME_DASHBOARD -> {
            "${snapshot.ownClubName} • ${activeSeason?.division ?: "Übersicht"}"
        }
        MainNavScreen.ROSTER -> {
            val player = snapshot.players.find { it.playerId == selectedPlayerId }
            if (player != null) {
                val pos = snapshot.positions.find { it.positionId == player.positionId }?.positionName ?: player.role.label
                val nr = player.jerseyNumberHomeKit?.let { "#$it • " } ?: ""
                "$nr${player.fullName} ($pos)"
            } else {
                "Kader – ${snapshot.ownClubName}"
            }
        }
        MainNavScreen.SEASON -> {
            val game = snapshot.games.find { it.gameId == selectedGameId }
            if (game != null) {
                "${repository.formatGameMatchup(game, snapshot)} (${game.homeTeamScore}:${game.awayTeamScore})"
            } else {
                activeSeason?.division ?: "Saison"
            }
        }
        MainNavScreen.STANDINGS -> {
            "Tabelle – ${activeSeason?.division ?: "Liga"}"
        }
    }
}

@Composable
private fun StaticTopHeaderBox(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(74.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VerticalRoundedNavbar(
    currentScreen: MainNavScreen,
    darkTheme: Boolean,
    onSelectScreen: (MainNavScreen) -> Unit,
    onOpenPlusMenu: () -> Unit,
    onToggleDarkTheme: () -> Unit
) {
    Surface(
        modifier = Modifier.width(88.dp).fillMaxHeight(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Oben links: [H]-Box für Home / Dashboard gemäß UI-Grundkonzept
                val isHomeSelected = currentScreen == MainNavScreen.HOME_DASHBOARD
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isHomeSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .size(56.dp)
                        .clickable { onSelectScreen(MainNavScreen.HOME_DASHBOARD) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "H",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isHomeSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 6.dp))

                // Screen-Icons: Kader, Saison, Tabelle
                NavbarIconItem(
                    label = "Kader",
                    icon = Icons.Default.Groups,
                    selected = currentScreen == MainNavScreen.ROSTER,
                    onClick = { onSelectScreen(MainNavScreen.ROSTER) }
                )

                NavbarIconItem(
                    label = "Saison",
                    icon = Icons.Default.SportsHandball,
                    selected = currentScreen == MainNavScreen.SEASON,
                    onClick = { onSelectScreen(MainNavScreen.SEASON) }
                )

                NavbarIconItem(
                    label = "Tabelle",
                    icon = Icons.Default.FormatListNumbered,
                    selected = currentScreen == MainNavScreen.STANDINGS,
                    onClick = { onSelectScreen(MainNavScreen.STANDINGS) }
                )

                Spacer(Modifier.height(4.dp))

                // Plus Icon in der Navbar
                FilledIconButton(
                    onClick = onOpenPlusMenu,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Neu anlegen (+)",
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }

            // Unten: Dark-/Light-Mode Umschalter
            IconButton(onClick = onToggleDarkTheme) {
                Icon(
                    imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Design wechseln"
                )
            }
        }
    }
}

@Composable
private fun NavbarIconItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun BottomMobileNavbar(
    currentScreen: MainNavScreen,
    darkTheme: Boolean,
    onSelectScreen: (MainNavScreen) -> Unit,
    onOpenPlusMenu: () -> Unit,
    onToggleDarkTheme: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [H] Home Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (currentScreen == MainNavScreen.HOME_DASHBOARD) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onSelectScreen(MainNavScreen.HOME_DASHBOARD) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "H",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (currentScreen == MainNavScreen.HOME_DASHBOARD) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }

            IconButton(onClick = { onSelectScreen(MainNavScreen.ROSTER) }) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = "Kader",
                    tint = if (currentScreen == MainNavScreen.ROSTER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { onSelectScreen(MainNavScreen.SEASON) }) {
                Icon(
                    imageVector = Icons.Default.SportsHandball,
                    contentDescription = "Saison",
                    tint = if (currentScreen == MainNavScreen.SEASON) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { onSelectScreen(MainNavScreen.STANDINGS) }) {
                Icon(
                    imageVector = Icons.Default.FormatListNumbered,
                    contentDescription = "Tabelle",
                    tint = if (currentScreen == MainNavScreen.STANDINGS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledIconButton(
                onClick = onOpenPlusMenu,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Plus Menü",
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }

            IconButton(onClick = onToggleDarkTheme) {
                Icon(
                    imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Theme"
                )
            }
        }
    }
}

@Composable
private fun MainContentRouter(
    currentScreen: MainNavScreen,
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    isWideScreen: Boolean,
    selectedSeasonId: Int,
    selectedPlayerId: Int?,
    selectedGameId: Int?,
    onSelectSeason: (Int) -> Unit,
    onSelectPlayer: (Int?) -> Unit,
    onSelectGame: (Int?) -> Unit,
    onNavigateToStandings: () -> Unit,
    onOpenModal: (CreationModalType) -> Unit
) {
    when (currentScreen) {
        MainNavScreen.HOME_DASHBOARD -> {
            DashboardScreen(
                repository = repository,
                snapshot = snapshot,
                selectedSeasonId = selectedSeasonId,
                onSelectSeason = onSelectSeason,
                onNavigateToPlayer = { onSelectPlayer(it) },
                onNavigateToGame = { onSelectGame(it) },
                onNavigateToStandings = onNavigateToStandings,
                onOpenPlusMenu = { onOpenModal(CreationModalType.CHOOSER) }
            )
        }

        MainNavScreen.ROSTER -> {
            RosterScreen(
                repository = repository,
                snapshot = snapshot,
                selectedPlayerId = selectedPlayerId,
                isWideScreen = isWideScreen,
                onSelectPlayer = onSelectPlayer,
                onNavigateToGame = { onSelectGame(it) },
                onOpenNewPlayerDialog = { onOpenModal(CreationModalType.NEW_PLAYER) },
                onOpenNewTrainerDialog = { onOpenModal(CreationModalType.NEW_TRAINER) }
            )
        }

        MainNavScreen.SEASON -> {
            SeasonScreen(
                repository = repository,
                snapshot = snapshot,
                selectedSeasonId = selectedSeasonId,
                selectedGameId = selectedGameId,
                onSelectSeason = onSelectSeason,
                onSelectGame = onSelectGame,
                onNavigateToPlayer = { onSelectPlayer(it) },
                onOpenNewSeasonDialog = { onOpenModal(CreationModalType.NEW_SEASON) },
                onOpenNewGameDialog = { onOpenModal(CreationModalType.NEW_GAME) }
            )
        }

        MainNavScreen.STANDINGS -> {
            StandingsScreen(
                repository = repository,
                snapshot = snapshot,
                selectedSeasonId = selectedSeasonId,
                onSelectSeason = onSelectSeason,
                onOpenNewOpponentDialog = { onOpenModal(CreationModalType.NEW_OPPONENT) },
                onOpenNewGameDialog = { onOpenModal(CreationModalType.NEW_GAME) }
            )
        }
    }
}
