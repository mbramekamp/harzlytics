package com.handball.stattracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handball.stattracker.config.SupabaseConfig
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
    repository: HandballRepository = remember { HandballRepository(autoSync = true) }
) {
    LaunchedEffect(repository) {
        if (SupabaseConfig.hasConfiguredKeys() && SupabaseConfig.resolvedProjectUrl().isNotBlank()) {
            repository.syncWithBackend()
        }
    }
    var darkTheme by remember { mutableStateOf(false) }
    val snapshot by repository.snapshot.collectAsState()

    var currentScreen by remember { mutableStateOf(MainNavScreen.HOME_DASHBOARD) }
    var selectedSeasonId by remember(snapshot.seasons) {
        mutableStateOf(snapshot.seasons.firstOrNull()?.seasonId ?: 1)
    }
    var selectedPlayerId by remember { mutableStateOf<Int?>(null) }
    var selectedGameId by remember { mutableStateOf<Int?>(null) }
    var creationModal by remember { mutableStateOf(CreationModalType.NONE) }

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

                // Auf Desktop/Tablet: Falls kein Spieler gewählt ist, ersten vorwählen für Master-Detail
                LaunchedEffect(isWideScreen, snapshot.players) {
                    if (isWideScreen && selectedPlayerId == null && snapshot.players.isNotEmpty()) {
                        selectedPlayerId = snapshot.players.firstOrNull()?.playerId
                    }
                }

                if (isWideScreen) {
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
                    // Mobile Ansicht: Native App Bar mit Status-Bar Insets & ergonomische Bottom Navigation
                    val showMobileBack = (currentScreen == MainNavScreen.ROSTER && selectedPlayerId != null) ||
                            (currentScreen == MainNavScreen.SEASON && selectedGameId != null)

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        MobileTopHeaderBar(
                            title = staticHeaderTitle,
                            subtitle = snapshot.ownClubName,
                            showBackButton = showMobileBack,
                            onBack = {
                                if (currentScreen == MainNavScreen.ROSTER) selectedPlayerId = null
                                if (currentScreen == MainNavScreen.SEASON) selectedGameId = null
                            },
                            darkTheme = darkTheme,
                            onToggleDarkTheme = { darkTheme = !darkTheme }
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
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
                            onSelectScreen = { screen ->
                                currentScreen = screen
                                if (screen != MainNavScreen.SEASON) {
                                    selectedGameId = null
                                }
                            },
                            onOpenPlusMenu = { creationModal = CreationModalType.CHOOSER }
                        )
                    }
                }
            }

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
            activeSeason?.division ?: "Übersicht"
        }
        MainNavScreen.ROSTER -> {
            val player = snapshot.players.find { it.playerId == selectedPlayerId }
            if (player != null) {
                val pos = snapshot.positions.find { it.positionId == player.positionId }?.positionName ?: player.role.label
                val nr = player.jerseyNumberHomeKit?.let { "#$it • " } ?: ""
                "$nr${player.fullName} ($pos)"
            } else {
                "Kader (${snapshot.players.size})"
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
            "Tabelle • ${activeSeason?.division ?: "Liga"}"
        }
    }
}

@Composable
private fun MobileTopHeaderBar(
    title: String,
    subtitle: String,
    showBackButton: Boolean,
    onBack: () -> Unit,
    darkTheme: Boolean,
    onToggleDarkTheme: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(38.dp)
                        .padding(start = 4.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "H",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleDarkTheme) {
                Icon(
                    imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Theme wechseln",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    onSelectScreen: (MainNavScreen) -> Unit,
    onOpenPlusMenu: () -> Unit
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        NavigationBarItem(
            selected = currentScreen == MainNavScreen.HOME_DASHBOARD,
            onClick = { onSelectScreen(MainNavScreen.HOME_DASHBOARD) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentScreen == MainNavScreen.ROSTER,
            onClick = { onSelectScreen(MainNavScreen.ROSTER) },
            icon = { Icon(Icons.Default.Groups, contentDescription = "Kader") },
            label = { Text("Kader") }
        )

        // Prominenter Plus-Button
        Box(
            modifier = Modifier
                .weight(0.9f)
                .wrapContentHeight(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = onOpenPlusMenu,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                elevation = FloatingActionButtonDefaults.elevation(2.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Neu anlegen",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        NavigationBarItem(
            selected = currentScreen == MainNavScreen.SEASON,
            onClick = { onSelectScreen(MainNavScreen.SEASON) },
            icon = { Icon(Icons.Default.SportsHandball, contentDescription = "Saison") },
            label = { Text("Saison") }
        )
        NavigationBarItem(
            selected = currentScreen == MainNavScreen.STANDINGS,
            onClick = { onSelectScreen(MainNavScreen.STANDINGS) },
            icon = { Icon(Icons.Default.FormatListNumbered, contentDescription = "Tabelle") },
            label = { Text("Tabelle") }
        )
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
                isWideScreen = isWideScreen,
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
                isWideScreen = isWideScreen,
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
                isWideScreen = isWideScreen,
                selectedSeasonId = selectedSeasonId,
                onSelectSeason = onSelectSeason,
                onOpenNewOpponentDialog = { onOpenModal(CreationModalType.NEW_OPPONENT) },
                onOpenNewGameDialog = { onOpenModal(CreationModalType.NEW_GAME) }
            )
        }
    }
}
