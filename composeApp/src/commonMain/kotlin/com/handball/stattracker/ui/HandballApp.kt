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
import com.handball.stattracker.model.AuthActionResult
import com.handball.stattracker.model.AuthState
import com.handball.stattracker.model.SyncState
import com.handball.stattracker.repository.SeasonSelection
import com.handball.stattracker.model.UserProfile
import com.handball.stattracker.model.UserRole
import com.handball.stattracker.repository.AdminRepository
import com.handball.stattracker.repository.AuthRepository
import kotlinx.coroutines.launch
import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.repository.HandballRepository
import com.handball.stattracker.ui.screens.*
import com.handball.stattracker.ui.theme.HandballTheme
import com.handball.stattracker.ui.theme.navigationItemColors

enum class MainNavScreen(val label: String) {
    HOME_DASHBOARD("Home"),
    ROSTER("Kader"),
    SEASON("Saison"),
    STANDINGS("Tabelle")
}

@Composable
fun HandballApp() {
    val auth = remember { AuthRepository() }
    val authState by auth.state.collectAsState()
    var darkTheme by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(auth) { auth.restore() }

    HandballTheme(darkTheme = darkTheme) {
        when (val state = authState) {
            AuthState.Loading -> AuthLoadingScreen()
            AuthState.NotConfigured -> NotConfiguredScreen()
            is AuthState.SignedOut -> LoginScreen(auth = auth, initialMessage = state.message)
            is AuthState.PendingApproval -> PendingApprovalScreen(auth = auth, state = state)
            is AuthState.SignedIn -> {
                val userId = state.session.userId
                val repository = remember(userId) {
                    HandballRepository(autoSync = true, canEdit = state.profile.canEdit)
                        .also { it.prepareForAccount(userId) }
                }
                val adminRepository = remember(userId) { auth.adminRepository(userId) }
                // Rolle kann sich ändern (Profil wird regelmäßig neu geladen)
                LaunchedEffect(repository, state.profile.canEdit) { repository.setCanEdit(state.profile.canEdit) }
                LaunchedEffect(userId) { repository.syncWithBackend() }
                LaunchedEffect(userId) { auth.keepSessionFresh() }

                CompositionLocalProvider(LocalUserRole provides state.profile.role) {
                    MainAppContent(
                        repository = repository,
                        darkTheme = darkTheme,
                        onToggleDarkTheme = { darkTheme = !darkTheme },
                        profile = state.profile,
                        email = state.session.email,
                        offline = state.offline,
                        onRequestClaim = { playerId -> auth.requestPlayerClaim(playerId) },
                        adminRepository = adminRepository,
                        onSignOut = { scope.launch { auth.signOut() } }
                    )
                }
            }
        }
    }
}

@Composable
internal fun MainAppContent(
    repository: HandballRepository,
    darkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    profile: UserProfile,
    email: String,
    offline: Boolean,
    onRequestClaim: suspend (Int?) -> AuthActionResult,
    adminRepository: AdminRepository?,
    onSignOut: () -> Unit,
    initialScreen: MainNavScreen = MainNavScreen.HOME_DASHBOARD,
    initialGameId: Int? = null,
    initialPlayerId: Int? = null
) {
    val snapshot by repository.snapshot.collectAsState()
    val canEdit = rememberCanEdit()
    var showAccount by remember { mutableStateOf(false) }
    var showAdmin by remember { mutableStateOf(false) }
    var showClub by remember { mutableStateOf(false) }

    var currentScreen by remember { mutableStateOf(initialScreen) }
    // Beim Start die aktuelle Saison; eine gewählte bleibt, solange es sie gibt
    var chosenSeasonId by remember { mutableStateOf<Int?>(null) }
    val currentSeasonId = remember(snapshot.seasons) {
        SeasonSelection.currentSeasonId(snapshot.seasons, SeasonSelection.today())
    }
    val selectedSeasonId = chosenSeasonId?.takeIf { id -> snapshot.seasons.any { it.seasonId == id } }
        ?: currentSeasonId ?: 1
    val syncState by repository.syncState.collectAsState()
    val syncScope = rememberCoroutineScope()
    val onSync: () -> Unit = { syncScope.launch { repository.syncWithBackend() } }
    var selectedPlayerId by remember { mutableStateOf(initialPlayerId) }
    // Spieler mit verknüpftem Kadereintrag: der zweite Tab unten ist ihr eigenes Profil statt der Kaderliste
    val ownPlayerId = profile.playerId?.takeIf { id -> snapshot.players.any { it.playerId == id } }
    val ownProfileTab = profile.role == UserRole.SPIELER && ownPlayerId != null
    var selectedGameId by remember { mutableStateOf(initialGameId) }
    val showSeasonPicker = currentScreen == MainNavScreen.HOME_DASHBOARD ||
        currentScreen == MainNavScreen.STANDINGS ||
        (currentScreen == MainNavScreen.SEASON && selectedGameId == null)
    var creationModal by remember { mutableStateOf(CreationModalType.NONE) }
    // Spieler dürfen nichts anlegen; die Buttons sind ohnehin ausgeblendet, dies ist die zweite Absicherung
    val openModal: (CreationModalType) -> Unit = { if (canEdit) creationModal = it }

    val staticHeaderTitle = if (ownProfileTab && currentScreen == MainNavScreen.ROSTER && selectedPlayerId == ownPlayerId) {
        "Profil"
    } else {
        headerTitle(currentScreen, selectedGameId)
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
                        selectedPlayerId = ownPlayerId ?: snapshot.players.firstOrNull()?.playerId
                    }
                }

                if (isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        VerticalRoundedNavbar(
                            currentScreen = currentScreen,
                            onSelectScreen = { screen ->
                                currentScreen = screen
                                if (screen != MainNavScreen.SEASON) {
                                    selectedGameId = null
                                }
                            },
                            onOpenPlusMenu = { openModal(CreationModalType.CHOOSER) }
                        )

                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            StaticTopHeaderBox(
                                title = staticHeaderTitle,
                                seasonPicker = if (showSeasonPicker) {
                                    { SeasonPicker(snapshot.seasons, selectedSeasonId, { chosenSeasonId = it }) }
                                } else null,
                                syncState = syncState,
                                onSync = onSync,
                                profile = profile,
                                email = email,
                                offline = offline,
                                onOpenAccount = { showAccount = true }
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
                                    onOpenModal = openModal
                                )
                            }
                        }
                    }
                } else {
                    // Mobile Ansicht: Native App Bar mit Status-Bar Insets & ergonomische Bottom Navigation
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        MobileTopHeaderBar(
                            title = staticHeaderTitle,
                            seasonPicker = if (showSeasonPicker) {
                                { SeasonPicker(snapshot.seasons, selectedSeasonId, { chosenSeasonId = it }, compact = true) }
                            } else null,
                            syncState = syncState,
                            onSync = onSync,
                            onOpenAccount = { showAccount = true }
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
                                onOpenModal = openModal
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
                            onOpenPlusMenu = { openModal(CreationModalType.CHOOSER) },
                            onOpenOwnProfile = if (ownProfileTab) {
                                {
                                    selectedPlayerId = ownPlayerId
                                    selectedGameId = null
                                    currentScreen = MainNavScreen.ROSTER
                                }
                            } else null
                        )
                    }
                }
            }

            if (showAccount) {
                AccountDialog(
                    profile = profile,
                    email = email,
                    offline = offline,
                    players = snapshot.players,
                    onRequestClaim = onRequestClaim,
                    onSignOut = { showAccount = false; onSignOut() },
                    darkTheme = darkTheme,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onOpenClub = if (canEdit) {
                        { showAccount = false; showClub = true }
                    } else null,
                    onOpenAdmin = if (profile.role == UserRole.ADMIN && adminRepository != null) {
                        { showAccount = false; showAdmin = true }
                    } else null,
                    onDismiss = { showAccount = false }
                )
            }

            if (showClub && canEdit) {
                ClubDialog(
                    repository = repository,
                    snapshot = snapshot,
                    selectedSeasonId = selectedSeasonId,
                    onDismiss = { showClub = false }
                )
            }

            if (showAdmin && adminRepository != null) {
                AdminDialog(
                    admin = adminRepository,
                    ownUserId = profile.id,
                    players = snapshot.players,
                    onDismiss = { showAdmin = false }
                )
            }

            PlusCreationModalHost(
                modalType = creationModal,
                repository = repository,
                snapshot = snapshot,
                defaultSeasonId = selectedSeasonId,
                onSelectModal = { creationModal = it },
                onDismiss = { creationModal = CreationModalType.NONE },
                onGameCreated = { newGame ->
                    chosenSeasonId = newGame.seasonId
                    selectedGameId = newGame.gameId
                    currentScreen = MainNavScreen.SEASON
                },
                onPlayerCreated = { newPerson ->
                    selectedPlayerId = newPerson.playerId
                    currentScreen = MainNavScreen.ROSTER
                },
                onSeasonCreated = { newSeason ->
                    chosenSeasonId = newSeason.seasonId
                    selectedGameId = null
                    currentScreen = MainNavScreen.SEASON
                }
            )
        }
    }
}

/** Titel der Kopfzeile = Name des Bereichs. Saison und Details zeigen das Saison-Menü bzw. die Ansicht selbst. */
private fun headerTitle(currentScreen: MainNavScreen, selectedGameId: Int?): String = when (currentScreen) {
    MainNavScreen.HOME_DASHBOARD -> "Übersicht"
    MainNavScreen.ROSTER -> "Kader"
    MainNavScreen.SEASON -> if (selectedGameId != null) "Spiel" else "Saison"
    MainNavScreen.STANDINGS -> "Tabelle"
}

@Composable
private fun MobileTopHeaderBar(
    title: String,
    seasonPicker: (@Composable () -> Unit)?,
    syncState: SyncState,
    onSync: () -> Unit,
    onOpenAccount: () -> Unit
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
                seasonPicker?.invoke()
            }

            SyncIndicator(syncState, onSync)
            IconButton(onClick = onOpenAccount) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Konto",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StaticTopHeaderBox(
    title: String,
    seasonPicker: (@Composable () -> Unit)?,
    syncState: SyncState,
    onSync: () -> Unit,
    profile: UserProfile,
    email: String,
    offline: Boolean,
    onOpenAccount: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(74.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Titel nimmt den Platz links vom Konto ein und überlappt es nie
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            seasonPicker?.invoke()
            SyncIndicator(syncState, onSync)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenAccount) {
                    Text(
                        text = profile.fullName.ifBlank { email },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    RoleBadge(profile.role, offline)
                }
            }
        }
    }
}

@Composable
private fun VerticalRoundedNavbar(
    currentScreen: MainNavScreen,
    onSelectScreen: (MainNavScreen) -> Unit,
    onOpenPlusMenu: () -> Unit
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

                EditorOnly {
                    FilledIconButton(
                        onClick = onOpenPlusMenu,
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Neu anlegen (+)",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
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
    onOpenPlusMenu: () -> Unit,
    /** Gesetzt für Spieler mit verknüpftem Kadereintrag: ersetzt den Kader-Tab durch das eigene Profil. */
    onOpenOwnProfile: (() -> Unit)?
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        NavigationBarItem(
            selected = currentScreen == MainNavScreen.HOME_DASHBOARD,
            colors = navigationItemColors(),
            onClick = { onSelectScreen(MainNavScreen.HOME_DASHBOARD) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Übersicht") },
            label = { Text("Übersicht") }
        )
        NavigationBarItem(
            selected = currentScreen == MainNavScreen.ROSTER,
            colors = navigationItemColors(),
            onClick = { if (onOpenOwnProfile != null) onOpenOwnProfile() else onSelectScreen(MainNavScreen.ROSTER) },
            icon = {
                Icon(
                    if (onOpenOwnProfile != null) Icons.Default.Person else Icons.Default.Groups,
                    contentDescription = if (onOpenOwnProfile != null) "Profil" else "Kader"
                )
            },
            label = { Text(if (onOpenOwnProfile != null) "Profil" else "Kader") }
        )

        // Prominenter Plus-Button (nur Trainer/Admin)
        EditorOnly {
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .wrapContentHeight(Alignment.CenterVertically),
                contentAlignment = Alignment.Center
            ) {
                FloatingActionButton(
                    onClick = onOpenPlusMenu,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
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
        }

        NavigationBarItem(
            selected = currentScreen == MainNavScreen.SEASON,
            colors = navigationItemColors(),
            onClick = { onSelectScreen(MainNavScreen.SEASON) },
            icon = { Icon(Icons.Default.SportsHandball, contentDescription = "Saison") },
            label = { Text("Saison") }
        )
        NavigationBarItem(
            selected = currentScreen == MainNavScreen.STANDINGS,
            colors = navigationItemColors(),
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
                onNavigateToPlayer = { onSelectPlayer(it) },
                onNavigateToGame = { onSelectGame(it) },
                onNavigateToStandings = onNavigateToStandings
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
                onSelectGame = onSelectGame,
                onNavigateToPlayer = { onSelectPlayer(it) }
            )
        }

        MainNavScreen.STANDINGS -> {
            StandingsScreen(
                repository = repository,
                snapshot = snapshot,
                isWideScreen = isWideScreen,
                selectedSeasonId = selectedSeasonId
            )
        }
    }
}
