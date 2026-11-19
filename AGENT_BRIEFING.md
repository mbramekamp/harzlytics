# AI Agent Briefing – Handball Stat Tracker (KMP)

Dieses Dokument dient als Übergabe-Briefing für KI-Coding-Assistenten, um in einer neuen Session ohne Informationsverlust an diesem Projekt weiterarbeiten zu können.

---

## 1. Projektziel & Ursprungsanforderungen

Auf Basis des vorhandenen ER-Datenmodells (`handball-stata-tracker-database-modell.mermaid`) und des skizzierten UI-Grundkonzepts (`Screenshot 2026-10-24 093637.png`) entsteht eine **Kotlin Multiplatform (KMP)** App mit **Compose Multiplatform**, die auf:
- **Desktop (JVM)**
- **Web (JS Browser)**
- **iOS** (`iosArm64`, `iosSimulatorArm64`)
- **Android**

läuft. Nachdem das Statistik-, Aufstellungs- und Analyse-Kernsystem zunächst ohne Login aufgebaut wurde, stehen als nächste große Meilensteine die **Performance-basierte Start-7-Zusammenstellung** sowie der **Übergang zu User-Login & Authentifizierung** an.

---

## 2. Getroffene Architektur- & Design-Entscheidungen (inkl. bisheriger Umsetzungen)

1. **Datenhaltung & Synchronisation (Offline-First)**:
   - Jeder Client speichert Daten lokal (`PlatformStorage`: Desktop nutzt SQLDelight SQLite + JSON-Snapshot unter `~/.handball-stat-tracker/`, Web nutzt Browser `localStorage`, iOS `NSUserDefaults`, Android lokalen App-Storage).
   - Zusätzlich existiert im selben Projekt ein eigenes **Ktor-Backend (`:server`)** mit SQLite-Datenbank (`~/.handball-stat-tracker-server/`), über das alle Clients via `GET/POST /api/sync` synchronisieren können.
2. **Datenmodell & Erweiterungen gegenüber dem Mermaid-Diagramm**:
   - **7 klassische Handball-Positionen** (`TW`, `LA`, `RL`, `RM`, `RR`, `RA`, `KM`) werden automatisch als Seed-Daten in `POSITION` angelegt; eigene Positionen können ergänzt werden.
   - **Trainer (`COACH`)**: Werden in derselben Tabelle `PLAYER` über `role = PlayerRole.COACH` gespeichert (`position_id`, `jersey_number_home_kit` und `jersey_number_away_kit` sind dafür `nullable`).
   - **Liga-Replika (`SEASON_OPPONENT` & `GAME`)**:
     - Verknüpfungstabelle `SEASON_OPPONENT(season_id, opponent_team_id)`, damit Gegner beim Anlegen direkt einer oder mehreren Saisons zugewiesen werden können.
     - `GAME` wurde um `is_home_game` (`Boolean`, bestimmt Heim/Auswärts bei eigenen Spielen), `second_opponent_id` (`Int?`, erlaubt das Eintragen von Spielen zwischen zwei Liga-Gegnern für eine vollständige Ligatabelle) und `elapsed_seconds` (`Int`, Spieluhr 00:00–60:00) erweitert.
   - **Aktive 7-on-Court Aufstellung, Plus/Minus (`+/-`), Einsatzzeit & Spielprotokoll (`PLAYER_GAME` & `GAME_LOG`)**:
     - `PLAYER_GAME` speichert `is_on_court` (`Boolean`, max. 7 aktive Spieler auf dem Feld), `plus_minus` (`Int`, automatische Tordifferenz während der Spieler auf dem Feld steht), `seconds_played` (`Int`, Einsatzzeit) und `active_suspension_until_seconds` (`Int?`, 2-Minuten-Straf-Countdown).
     - `GAME_LOG` (`GameLogEntry`) protokolliert chronologisch alle Spielerwechsel (Ein-/Auswechslung), Startaufstellungen, Tore, 7m-Tore, Fehlwürfe, Paraden, Gegentore, 2-Minuten-Strafen, Karten und Auszeiten inkl. Spieluhrzeit und Spielstand.
   - **Eigener Vereinsname**: Konfigurierbar im Dashboard (`ownClubName` im `DatabaseSnapshot`, Standard: `"HC Handball Löwen"`).
3. **UI-Grundkonzept, Aufstellungs-Logik & jüngste Umsetzungen**:
   - **Desktop / Web / Tablet (`maxWidth >= 700.dp`)**:
     - Links: Vertikale abgerundete `Navbar` mit `[H]`-Button oben, den Icons für **Kader**, **Saison**, **Tabelle** sowie dem **`+`-Button** und einem Dark/Light-Mode-Schalter.
     - Oben rechts: Abgerundete **statische Titelzeile**, die kontextbezogen den Namen des aktuell geöffneten Elements anzeigt (ohne redundante Vereinsnamen-Dopplung).
     - Unten rechts: Abgerundeter `Content`-Bereich.
   - **Mobile Devices (`maxWidth < 700.dp`)**:
     - Die `Navbar` wird am **unteren Bildschirmrand** (Bottom Navigation Bar) dargestellt.
   - **Side-by-Side Bearbeitungs-UI im Spiel (`SeasonScreen.kt`)**:
     - **Links (`AUF DEM FELD (X/7)`)**: Die bis zu 7 aktiven Spieler auf dem Feld. Nur für aktive Feld-/Torwartspieler auf der linken Seite sind die `+/-`-Statistik-Stepper im Bearbeitungsmodus freigeschaltet.
     - **Rechts (`BANK (Y)`)**: Die Auswechselspieler auf der Bank im inaktiven Zustand (Statistiken gesperrt).
     - **Positions-Badges statt redundanter Rollen-Tabs**: Jede Spielerkarte zeigt die genaue Position (`TW`, `LA`, `RL`, `RM`, `RR`, `RA`, `KM`) direkt als Badge an; auf separate Feldspieler-/Torwart-Tabs wurde bewusst verzichtet.
     - **2-Klick-Wechsel-Workflow**: Klick rechts auf einen Bank-Spieler wählt ihn zur Einwechslung aus; Klick links auf einen gültigen Tauschpartner auf dem Feld führt den Wechsel durch und protokolliert ihn im `GAME_LOG`.
   - **Handball-Regelprüfung für die 7 Aktiven (`6 Feld + 1 TW` oder `7 Feld + 0 TW`)**:
     - In `HandballRepository.kt` (`isValidActiveCourtLineup` und `canSubstituteOrEnterCourt`) wird strikt geprüft, dass unter den maximal 7 aktiven Spielern auf dem Feld **höchstens 1 Torwart (`TW`)** und **bis zu 7 Feldspieler** stehen dürfen (d. h. entweder klassisch **6 Feldspieler + 1 Torwart** oder **7 Feldspieler / Empty Net**, niemals 2 Torhüter gleichzeitig).
     - Dies wird sowohl beim 2-Klick-Wechsel (`substitutePlayer`), bei der direkten Aufnahme aufs Feld als auch bei der Kader-Nominierung (`togglePlayerInGame`) durchgesetzt und durch Unit-Tests in `HandballRepositoryTest.kt` abgesichert.
   - **Eigener Tab „Verlauf & Protokoll“ & Torverlauf als `+3 / 0 / -3` Ausschlag-Graph**:
     - Torverlauf und chronologische Spiel-Logs sind in einen eigenen Tab **„Verlauf & Protokoll“** ausgelagert.
     - Das Diagramm `ScoreProgressionMiniChartCard` steigt **nicht** als kumulativer Summengraph an, sondern schwingt um eine horizontale **`0`-Mittellinie** im festen Bereich **`+3` (oben, Heim)** bis **`-3` (unten, Gast)** aus (umschaltbar zwischen *Tordifferenz* und *Tor-Lauf*).
   - **Professionelles, entschlacktes UI (Entfernung redundanter Labels & Erklärtexte)**:
     - Sämtliche Screens (`HandballApp.kt`, `DashboardScreen.kt`, `StandingsScreen.kt`, `PlusCreationDialogs.kt`, `RosterScreen.kt`, `PlayerPerformanceTab.kt`, `SeasonScreen.kt`) wurden von überflüssigen Anleitungstexten, redundanten Status-Badges (`FELD`/`BANK` innerhalb der ohnehin getrennten Spalten) und technischen Datenbank-Bezeichnungen in Klammern (`PLAYER_GAME`, `COACH_GAMESTATS`, `SEASON_OPPONENT`, `POSITION` etc.) befreit, sodass die UI für sich selbst spricht.

---

## 3. Modul- und Dateistruktur

- **`gradle/libs.versions.toml`**: Version Catalog (`Kotlin 2.4.21`, `Compose Multiplatform 1.12.2`, `SQLDelight 2.4.1`, `Ktor 3.6.0`, `Gradle 9.8.2`).
- **`USER_FEATURE.md`**: Vollständiges Feature-Design für rollenbasierte Benutzeranbindung & Zugriffsberechtigung (RBAC: Spieler, Trainer, Admin), Supabase GoTrue Auth, PostgreSQL RLS Policies, Snapshot-Sync Absicherung & KMP Compose UI Integration.
- **`:shared`**:
  - `shared/src/commonMain/sqldelight/com/handball/stattracker/db/HandballDatabase.sq` – SQLDelight-Tabellen & Queries (inkl. `GAME_LOG` & Aufstellungs-/Plus-Minus-Feldern)
  - `shared/src/commonMain/kotlin/com/handball/stattracker/model/Models.kt` – Alle Entitäten, `DatabaseSnapshot`, `GameLogEntry`, `GameLogEventType`, `StandingsRow`, `PlayerAggregatedStats`, `PlayerGameHistoryEntry`, `PlayerGamePerformancePoint`, `PositionMetricComparison`, `PlayerRankingEntry`, `RadarMetricAxis`
  - `shared/src/commonMain/kotlin/com/handball/stattracker/repository/HandballRepository.kt` – StateFlow-Datenhaltung, CRUD, 7-on-Court Aufstellung inkl. `6+1` / `7+0` Regel-Check, 2-Klick-Wechsel, Spieluhr, Plus/Minus-Automatik, Spielprotokoll, 2-Punkte-Tabellenberechnung, Performance-Index, Positionsvergleich, Spieler-Ranking, Ktor-Sync
  - `shared/src/commonMain/kotlin/com/handball/stattracker/repository/DemoDataSeed.kt` – Initiale Demo-Daten (mit 7 aktiven Feld-/Torwartspielern, Bankspielern, `+/-`, Einsatzzeiten und realistischen Führungswechseln im Spielprotokoll)
  - `shared/src/commonMain/kotlin/com/handball/stattracker/storage/PlatformStorage.kt` (+ `desktopMain`, `jsMain`, `iosMain`, `androidMain`)
  - `shared/src/commonTest/kotlin/com/handball/stattracker/HandballRepositoryTest.kt` – Unit-Tests (inkl. Tests für `6 Feld + 1 TW` vs. `7 Feld + 0 TW` und Verbot eines 2. Torwarts auf dem Feld)
- **`:composeApp`**:
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/HandballApp.kt` – Haupt-App-Shell, responsive Navbar & statische Titelzeile
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/theme/HandballTheme.kt` – Material 3 Sport-Dashboard-Theme (Light & Dark)
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/DashboardScreen.kt` – `[H]` Home-Dashboard
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/RosterScreen.kt` – Kader-Ansicht & Spieler-/Trainerprofil mit Reitern (*Übersicht & Spiele* sowie *Performance & Ranking*)
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/PlayerPerformanceTab.kt` – Performance-Graphen (Formkurve & 6-Achsen-Radar), Positionsvergleich & Spieler-Ranking
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/SeasonScreen.kt` – Saison-Ansicht & Spiel-Dashboard (Spieluhr, Links: 7 Aktive Spieler vs. Rechts: Bank, 2-Klick-Wechsel mit `6+1`/`7+0`-Check, eigener Tab `Verlauf & Protokoll` mit `+3/0/-3` Torverlauf)
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/StandingsScreen.kt` – Ligatabelle
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/PlusCreationDialogs.kt` – `+`-Menü & Erstellungsdialoge
- **`:server`**:
  - `server/src/main/kotlin/com/handball/stattracker/server/Application.kt` – Ktor-Server (Port 8080) mit SQLite-Persistenz & `/api/sync`

---

## 4. Wichtige Hinweise zur Build-Umgebung (Windows)

- Auf dem System ist **Java 26 (`jdk-26.0.1`)** installiert; daher wird **Gradle 9.8.2** genutzt und in den `build.gradle.kts`-Dateien mit `JvmTarget.JVM_21` kompiliert (ohne `foojay-resolver`, da dieser in älteren Versionen mit Gradle 9.8 inkompatibel war).
- **Wichtige Befehle**:
  - Build & Tests prüfen: `.\gradlew.bat :shared:compileKotlinDesktop :composeApp:compileKotlinDesktop :server:compileKotlin :shared:desktopTest`
  - Desktop-App starten: `.\gradlew.bat :composeApp:run`
  - Web-App starten: `.\gradlew.bat :composeApp:jsBrowserDevelopmentRun`
  - Ktor-Backend starten: `.\gradlew.bat :server:run`

---

## 5. Erledigte Meilensteine & Nächste Schritte für kommende Sessions

### Bereits umgesetzt
- [x] **Gesamtwerte & Quoten pro Spiel anzeigen**
- [x] **Excel-Export für Saison-Daten & Einzelspiele**
- [x] **Aktive 7-on-Court Aufstellung, 2-Klick-Spielerwechsel, Spieluhr (inkl. 2-Min-Countdown), Spielprotokoll (`GAME_LOG`) & Plus/Minus (`+/-`) Tracking**
- [x] **Side-by-Side Bearbeitungs-UI**: Links die 7 aktiven Spieler (`AUF DEM FELD`), rechts die Spieler auf der `BANK`, Positionsanzeige direkt in der Spielerkarte (ohne redundante Feldspieler/Torwart-Tabs), eigener Tab `Verlauf & Protokoll`.
- [x] **Handball-Formations-Check (`6 Feld + 1 TW` oder `7 Feld + 0 TW`)**: Softwareseitige Prüfung in Repository & UI, dass niemals 2 Torhüter gleichzeitig auf dem Feld stehen.
- [x] **Torverlauf-Ausschlag (`+3 / 0 / -3`)**: Darstellung des Torverlaufs als Ausschlag um die Nulllinie statt als steigender Graph.
- [x] **UI-Bereinigung & Professionalisierung**: Entfernung redundanter Labels, erklärender Hilfstexte und technischer Datenbank-Begriffe in der gesamten App.
- [x] **Mobile-Ergonomie (Android) & Supabase Cloud-Sync** (Snapshot-Sync via `SupabaseSyncService.kt`, Konfiguration über `.env` / `SupabaseConfig.kt`).

### Nächste geplante Features (Priorität)
1. [x] **Performance-basierte Start 7 (Starting Seven Feature)**:
   - Dedizierte Funktion & Ansicht, die auf Grundlage der bisherigen Spieler-Performance (Performance-Index, Wurf-/Fangquoten, Plus/Minus, Formkurve der letzten Spiele) automatisch die beste **Start 7** (`TW`, `LA`, `RL`, `RM`, `RR`, `RA`, `KM` bzw. `7-Feldspieler`-Variante) zusammenstellt, begründet/vergleichbar macht und vor oder im Spiel direkt als Startaufstellung übernimmt.
   - Umgesetzt: `HandballRepository.recommendStartingSeven(gameId, useSeventhFieldPlayer)` (reine Funktion → `StartingSevenRecommendation` mit Platz, Wahl, Zweitplatziertem und `StartingSevenScoreBreakdown`), Gewichtung in `StartingSevenWeights` (45 % Saison-Performance-Index, 40 % Form der letzten 5 Spiele, 15 % Plus/Minus pro Spiel). Ein noch nicht angepfiffenes Zielspiel fließt nicht in die Bewertung ein. Gesperrte/zeitbestrafte Spieler werden ausgeschlossen; unbesetzbare Positionen übernimmt der beste freie Feldspieler (als Ersatzlösung markiert).
   - `applyStartingSevenLineup(gameId, playerIds)` übernimmt eine (ggf. manuell getauschte) Aufstellung nur bei gültiger `6+1` / `7+0` Formation und protokolliert sie als `LINEUP_SET` im `GAME_LOG`; `applyRecommendedStartingSevenToGame` nutzt beides.
   - UI: `ui/screens/StartingSevenDialog.kt` (Vorschau je Position mit Saison/Form/+/-, Tausch mit dem Zweitplatzierten, Umschalter `6+1` / `7+0`, „Übernehmen“), geöffnet über alle Startsieben-Buttons in `SeasonScreen.kt`. Neu angelegte Spiele (`addGame`) starten automatisch mit der empfohlenen Startsieben (Fallback: erste Nominierte je Position). Abgesichert durch Unit-Tests in `HandballRepositoryTest.kt`.
2. [ ] **User-Login, Authentifizierung & Benutzer-/Rollenverwaltung (RBAC)** – *gemäß [`USER_FEATURE.md`](file:///C:/Users/matsb/harzlytics/USER_FEATURE.md)*:
   - **Stand:** `supabase_schema.sql` (Tabellen, `sync_handball_snapshot` RPC) und `SupabaseSyncService.kt` existieren bereits. Zugriff abgesichert: `anon` hat keinen Zugriff mehr, `authenticated` nur Lesezugriff; Schreiben ausschließlich über die `SECURITY DEFINER`-RPC mit User-JWT (`SupabaseAuthSession.kt`). Cloud-Sync ist bis zum Login-Flow inaktiv. Das SQL wurde am 2026-11-19 im Supabase SQL Editor ausgeführt (Live-Projekt ist abgesichert).
   - [x] **Phase 1 (Supabase Backend & PostgreSQL RLS)** – umgesetzt in `supabase_schema.sql` (Abschnitte 3a, 3, 4, 5); *muss im Supabase SQL Editor erneut ausgeführt werden (idempotent)*:
     - `public.app_role` Enum, `public.user_profiles` (`id`, `email`, `full_name`, `role`, `player_id`, `claim_player_id`, `status`) und `public.club_invitations`. **Bewusst ohne FK auf `player`**, da der Sync `player` bei jedem Lauf neu befüllt.
     - Trigger `handle_auth_user_change()` auf `auth.users`: Profil bei Signup (Standard `SPIELER` / `PENDING`), Einladungscode aus `raw_user_meta_data.invite_code` setzt Rolle/Spieler & `ACTIVE`; Kader-Verknüpfung per E-Mail erst nach E-Mail-Bestätigung und nur bei eindeutigem Treffer. **Rolle wird nie aus Signup-Metadaten übernommen.**
     - `guard_user_profile_update()`: Nicht-Admins dürfen nur `full_name` und `claim_player_id` ändern (kein Self-Upgrade).
     - Helfer `current_user_role()`, `current_player_id()`, `is_active_member()`, `is_admin()`, `is_coach_or_admin()` (nur für `ACTIVE`-Profile).
     - RLS: Fachtabellen nur lesbar für aktive Mitglieder; `user_profiles` eigenes Profil bzw. Admin alles; `club_invitations` nur Admin.
     - `sync_handball_snapshot`: nicht freigeschaltet → 403; `SPIELER` → reiner Lese-Sync; `TRAINER`/`ADMIN` → Schreib-Sync.
     - Erster Admin: nach eigener Registrierung `UPDATE public.user_profiles SET role = 'ADMIN', status = 'ACTIVE' WHERE email = '...';` im SQL Editor.
   - [ ] **Phase 2 (KMP Shared Modul `:shared`)**:
     - Auth-Datenmodelle (`UserRole`, `UserProfile`, `AuthTokens`, `AuthState`) in `model/AuthModels.kt` anlegen.
     - `SupabaseAuthService` mit Ktor Client (`/auth/v1/signup`, `/auth/v1/token`, `/auth/v1/logout`) implementieren.
     - `AuthRepository` mit `StateFlow<AuthState>` und automatischer Session-Wiederherstellung aufbauen.
     - Multiplatform Token Storage in `PlatformStorage` absichern (Desktop Keyring/File, Web localStorage, Android EncryptedSharedPreferences, iOS Keychain).
     - `SupabaseSyncService` mit dynamischem `Authorization: Bearer <access_token>` Header ausstatten.
   - [ ] **Phase 3 (Compose Multiplatform UI `:composeApp`)**:
     - Auth Screens (`LoginScreen.kt`, `RegisterScreen.kt`, `ForgotPasswordDialog.kt`) entwickeln.
     - Profil-Claiming-Dialog zur Verknüpfung von Spieler-Accounts mit vorhandenen Kadereinträgen implementieren.
     - Wiederverwendbaren `RoleGuard(allowedRoles = ...)` Composable zur deklarativen Absicherung von Aktionen und UI-Elementen bereitstellen.
     - Side-by-Side Live-Ticker (`SeasonScreen.kt`), Kader-Bearbeitung (`RosterScreen.kt`) und `+`-Erstellungsmenü (`PlusCreationDialogs.kt`) rollenspezifisch schützen (Read-Only für Spieler, Full-Access für Trainer/Admin).
   - [ ] **Phase 4 (Navigation & Header-Integration)**:
     - Aktives Benutzerprofil samt Rollen-Badge (`[Spieler]`, `[Trainer]`, `[Admin]`) im `StaticTopHeaderBox` (`HandballApp.kt`) anzeigen.
     - Account-Menü mit Logout & Profilverwaltung integrieren.
     - Navigation dynamisch an die Rolle anpassen.
3. [ ] **Weitere Analyse-Erweiterungen (Optional)**:
   - Interaktives Wurfbild & 9-Zonen-Tor-Matrix (*Shot Map & Goal Sectors*) für Feldspieler und Torhüter.
   - Gegner-Scouting-Profile & Team-Splits (1. vs. 2. Halbzeit, Heim vs. Auswärts).
   - Granulare Konfliktauflösung beim Backend-Sync & Android-SDK-Aktivierung.
