package com.handball.stattracker.ui

import com.handball.stattracker.model.Competition
import com.handball.stattracker.model.SeasonPhase
import com.handball.stattracker.model.SeasonScope
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handball.stattracker.model.AuthActionResult
import com.handball.stattracker.model.AuthState
import com.handball.stattracker.model.SyncState
import com.handball.stattracker.repository.SeasonSelection
import com.handball.stattracker.model.UserProfile
import com.handball.stattracker.model.UserRole
import com.handball.stattracker.repository.AdminRepository
import com.handball.stattracker.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.repository.HandballRepository
import com.handball.stattracker.ui.screens.*
import com.handball.stattracker.ui.brand.HarzMark
import com.handball.stattracker.ui.theme.HandballTheme
import com.handball.stattracker.ui.theme.navigationItemColors

enum class MainNavScreen(val label: String) {
    HOME_DASHBOARD("Home"),
    ROSTER("Kader"),
    SEASON("Saison"),
    STANDINGS("Tabelle"),
    /** Nur der eigene Kadereintrag, ohne Kaderliste. */
    PROFILE("Profil")
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
                        onRefreshAccount = { auth.reloadProfile() },
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
    /** Lädt das Profil neu (z. B. nach Freigabe/Verknüpfung durch einen Admin). */
    onRefreshAccount: suspend () -> Unit = {},
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
    // Auswahl = Saison oder deren Vorbereitung (SeasonScope)
    var chosenScope by remember { mutableStateOf<SeasonScope?>(null) }
    val currentScope = remember(snapshot.seasons, snapshot.games) {
        SeasonSelection.currentScope(snapshot.seasons, snapshot.games, SeasonSelection.today())
    }
    val menuScopes = remember(snapshot.seasons, snapshot.games) {
        SeasonSelection.menuScopes(snapshot.seasons, snapshot.games)
    }
    val selectedScope = chosenScope?.takeIf { scope -> snapshot.seasons.any { it.seasonId == scope.seasonId } }
        ?: currentScope ?: SeasonScope(1)
    val selectedSeasonId = selectedScope.seasonId
    val syncState by repository.syncState.collectAsState()
    val syncScope = rememberCoroutineScope()
    // Wolken-Symbol = alles aktualisieren: Daten und Konto
    val onSync: () -> Unit = {
        syncScope.launch { onRefreshAccount() }
        syncScope.launch { repository.syncWithBackend() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60 * 1000L)
            onRefreshAccount()
        }
    }
    LaunchedEffect(showAccount) { if (showAccount) onRefreshAccount() }
    var selectedPlayerId by remember { mutableStateOf(initialPlayerId) }
    // Konto mit verknüpftem Kadereintrag: eigenes Profil direkt erreichbar
    // (Desktop: Eintrag in der Seitenleiste; mobil: Spieler-Tab bzw. Konto-Dialog)
    val ownPlayerId = profile.playerId?.takeIf { id -> snapshot.players.any { it.playerId == id } }
    // Mobil: Spieler bekommen einen eigenen Tab "Profil" (ohne +-Knopf ist Platz); Trainer/Admin über den Konto-Dialog
    val ownProfileTab = profile.role == UserRole.SPIELER && ownPlayerId != null
    val ownJerseyNumber = snapshot.players.find { it.playerId == ownPlayerId }?.jerseyNumberHomeKit
    var selectedGameId by remember { mutableStateOf(initialGameId) }
    // Breite des Layouts, auch für die Dialoge außerhalb von BoxWithConstraints
    var isWideScreenLayout by remember { mutableStateOf(false) }
    val openOwnProfile: (() -> Unit)? = ownPlayerId?.let {
        {
            selectedGameId = null
            currentScreen = MainNavScreen.PROFILE
        }
    }
    // Verknüpfung weg (z. B. vom Admin gelöst oder Eintrag gelöscht): Profil gibt es nicht mehr
    LaunchedEffect(ownPlayerId, currentScreen) {
        if (currentScreen == MainNavScreen.PROFILE && ownPlayerId == null) currentScreen = MainNavScreen.HOME_DASHBOARD
    }
    val ownProfileOpen = currentScreen == MainNavScreen.PROFILE
    val showSeasonPicker = currentScreen == MainNavScreen.HOME_DASHBOARD ||
        currentScreen == MainNavScreen.STANDINGS ||
        (currentScreen == MainNavScreen.SEASON && selectedGameId == null)
    var creationModal by remember { mutableStateOf(CreationModalType.NONE) }
    // Spieler dürfen nichts anlegen; die Buttons sind ohnehin ausgeblendet, dies ist die zweite Absicherung
    val openModal: (CreationModalType) -> Unit = { if (canEdit) creationModal = it }

    val staticHeaderTitle = headerTitle(currentScreen, selectedGameId)

    HandballTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 700.dp
                val narrowPhone = maxWidth < 360.dp
                SideEffect { isWideScreenLayout = isWideScreen }

                // Auf Desktop/Tablet: Falls kein Spieler gewählt ist, ersten vorwählen für Master-Detail
                LaunchedEffect(isWideScreen, snapshot.players) {
                    if (isWideScreen && selectedPlayerId == null && snapshot.players.isNotEmpty()) {
                        selectedPlayerId = snapshot.players.firstOrNull()?.playerId
                    }
                }

                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        SideNavigationRail(
                            currentScreen = currentScreen,
                            ownProfileOpen = ownProfileOpen,
                            onOpenOwnProfile = openOwnProfile,
                            ownJerseyNumber = ownJerseyNumber,
                            onSelectScreen = { screen ->
                                currentScreen = screen
                                if (screen != MainNavScreen.SEASON) {
                                    selectedGameId = null
                                }
                            },
                            onOpenPlusMenu = { openModal(CreationModalType.CHOOSER) }
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            DesktopHeader(
                                title = staticHeaderTitle,
                                seasonPicker = if (showSeasonPicker) {
                                    { SeasonPicker(snapshot.seasons, menuScopes, selectedScope, { chosenScope = it }) }
                                } else null,
                                syncState = syncState,
                                onSync = onSync,
                                profile = profile,
                                email = email,
                                offline = offline,
                                onOpenAccount = { showAccount = true }
                            )

                            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                                MainContentRouter(
                                    currentScreen = currentScreen,
                                    repository = repository,
                                    snapshot = snapshot,
                                    isWideScreen = true,
                                    selectedScope = selectedScope,
                                    selectedPlayerId = selectedPlayerId,
                                    selectedGameId = selectedGameId,
                                    ownPlayerId = ownPlayerId,
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
                                { SeasonPicker(snapshot.seasons, menuScopes, selectedScope, { chosenScope = it }, compact = true) }
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
                                selectedScope = selectedScope,
                                selectedPlayerId = selectedPlayerId,
                                selectedGameId = selectedGameId,
                                ownPlayerId = ownPlayerId,
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
                            onOpenOwnProfile = if (ownProfileTab) openOwnProfile else null,
                            ownJerseyNumber = ownJerseyNumber,
                            narrow = narrowPhone
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
                    // Desktop hat den Eintrag in der Seitenleiste, mobile Spieler den Tab: nur sonst hier anbieten
                    onOpenOwnProfile = openOwnProfile?.takeIf { !isWideScreenLayout && !ownProfileTab }?.let { open ->
                        { showAccount = false; open() }
                    },
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
                defaultScope = selectedScope,
                onSelectModal = { creationModal = it },
                onDismiss = { creationModal = CreationModalType.NONE },
                onGameCreated = { newGame ->
                    chosenScope = SeasonScope(
                        newGame.seasonId,
                        if (newGame.competition == Competition.FRIENDLY) SeasonPhase.PRESEASON else SeasonPhase.SEASON
                    )
                    selectedGameId = newGame.gameId
                    currentScreen = MainNavScreen.SEASON
                },
                onPlayerCreated = { newPerson ->
                    selectedPlayerId = newPerson.playerId
                    currentScreen = MainNavScreen.ROSTER
                },
                onSeasonCreated = { newSeason ->
                    chosenScope = SeasonScope(newSeason.seasonId)
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
    MainNavScreen.PROFILE -> "Profil"
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
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarzMark(height = 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
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

/** Kopfzeile Desktop: liegt ohne Rahmen auf dem Hintergrund, Titel links, Werkzeuge rechts. */
@Composable
private fun DesktopHeader(
    title: String,
    seasonPicker: (@Composable () -> Unit)?,
    syncState: SyncState,
    onSync: () -> Unit,
    profile: UserProfile,
    email: String,
    offline: Boolean,
    onOpenAccount: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(start = 28.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Titel nimmt den Platz links vom Konto ein und überlappt es nie
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        seasonPicker?.invoke()
        Spacer(Modifier.width(8.dp))
        SyncIndicator(syncState, onSync)
        Spacer(Modifier.width(4.dp))
        TextButton(
            onClick = onOpenAccount,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
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

/** Seitenleiste Desktop: bündig am Rand, Logo oben, Anlegen-Knopf als Hauptaktion, dann die Bereiche. */
@Composable
private fun SideNavigationRail(
    currentScreen: MainNavScreen,
    /** Eigener Kadereintrag ist gerade offen: dann ist "Profil" statt "Kader" markiert. */
    ownProfileOpen: Boolean,
    /** Gesetzt für Konten mit verknüpftem Kadereintrag. */
    onOpenOwnProfile: (() -> Unit)?,
    ownJerseyNumber: Int?,
    onSelectScreen: (MainNavScreen) -> Unit,
    onOpenPlusMenu: () -> Unit
) {
    Surface(
        modifier = Modifier.width(88.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 20.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HarzMark(
                height = 30.dp,
                modifier = Modifier.clickable { onSelectScreen(MainNavScreen.HOME_DASHBOARD) }
            )
            Spacer(Modifier.height(16.dp))

            EditorOnly {
                FilledIconButton(
                    onClick = onOpenPlusMenu,
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Neu anlegen")
                }
            }
            Spacer(Modifier.height(12.dp))

            NavbarIconItem("Übersicht", Icons.Default.Home, currentScreen == MainNavScreen.HOME_DASHBOARD) {
                onSelectScreen(MainNavScreen.HOME_DASHBOARD)
            }
            NavbarIconItem("Kader", Icons.Default.Groups, currentScreen == MainNavScreen.ROSTER) {
                onSelectScreen(MainNavScreen.ROSTER)
            }
            NavbarIconItem("Saison", Icons.Default.SportsHandball, currentScreen == MainNavScreen.SEASON) {
                onSelectScreen(MainNavScreen.SEASON)
            }
            NavbarIconItem("Tabelle", Icons.Default.FormatListNumbered, currentScreen == MainNavScreen.STANDINGS) {
                onSelectScreen(MainNavScreen.STANDINGS)
            }
            if (onOpenOwnProfile != null) {
                NavbarIconItem("Profil", ownProfileOpen, onOpenOwnProfile) { ProfileNavIcon(ownJerseyNumber, ownProfileOpen) }
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
) = NavbarIconItem(label, selected, onClick) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
}

@Composable
private fun NavbarIconItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                content = icon
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BottomMobileNavbar(
    currentScreen: MainNavScreen,
    onSelectScreen: (MainNavScreen) -> Unit,
    onOpenPlusMenu: () -> Unit,
    /** Gesetzt für Spieler mit verknüpftem Kadereintrag: zusätzlicher Tab "Profil" am Ende. */
    onOpenOwnProfile: (() -> Unit)?,
    ownJerseyNumber: Int?,
    /** Schmales Handy (< 360 dp): weniger Randabstand und kleinere Beschriftung, damit "Übersicht" passt. */
    narrow: Boolean
) {
    val labelStyle = if (narrow) MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
        else MaterialTheme.typography.labelMedium
    val tabLabel: @Composable (String) -> Unit = { Text(it, style = labelStyle, maxLines = 1, softWrap = false) }
    // Fläche bis zum Rand, Tabs mit etwas Abstand davon ("Übersicht" klebte sonst am Rand)
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (narrow) 4.dp else 8.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = currentScreen == MainNavScreen.HOME_DASHBOARD,
            colors = navigationItemColors(),
            onClick = { onSelectScreen(MainNavScreen.HOME_DASHBOARD) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Übersicht") },
            label = { tabLabel("Übersicht") }
        )
        NavigationBarItem(
            selected = currentScreen == MainNavScreen.ROSTER,
            colors = navigationItemColors(),
            onClick = { onSelectScreen(MainNavScreen.ROSTER) },
            icon = { Icon(Icons.Default.Groups, contentDescription = "Kader") },
            label = { tabLabel("Kader") }
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
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.size(48.dp)
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
            label = { tabLabel("Saison") }
        )
        NavigationBarItem(
            selected = currentScreen == MainNavScreen.STANDINGS,
            colors = navigationItemColors(),
            onClick = { onSelectScreen(MainNavScreen.STANDINGS) },
            icon = { Icon(Icons.Default.FormatListNumbered, contentDescription = "Tabelle") },
            label = { tabLabel("Tabelle") }
        )
        if (onOpenOwnProfile != null) {
            NavigationBarItem(
                selected = currentScreen == MainNavScreen.PROFILE,
                colors = navigationItemColors(),
                onClick = onOpenOwnProfile,
                icon = { ProfileNavIcon(ownJerseyNumber, currentScreen == MainNavScreen.PROFILE) },
                label = { tabLabel("Profil") }
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
    selectedScope: SeasonScope,
    selectedPlayerId: Int?,
    selectedGameId: Int?,
    ownPlayerId: Int?,
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
                selectedScope = selectedScope,
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
                selectedScope = selectedScope,
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
                selectedSeasonId = selectedScope.seasonId
            )
        }

        MainNavScreen.PROFILE -> {
            OwnProfileScreen(
                repository = repository,
                snapshot = snapshot,
                playerId = ownPlayerId,
                isWideScreen = isWideScreen,
                onSelectPlayer = { onSelectPlayer(it) },
                onNavigateToGame = { onSelectGame(it) }
            )
        }
    }
}
