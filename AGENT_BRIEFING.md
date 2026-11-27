# AI Agent Briefing – Handball Stat Tracker (KMP)

Dieses Dokument dient als Übergabe-Briefing für KI-Coding-Assistenten, um in einer neuen Session ohne Informationsverlust an diesem Projekt weiterarbeiten zu können.

---

## 1. Projektziel & Ursprungsanforderungen

Auf Basis des vorhandenen ER-Datenmodells (`handball-stata-tracker-database-modell.mermaid`) und des skizzierten UI-Grundkonzepts (`Screenshot 2026-10-24 093637.png`) entsteht eine **Kotlin Multiplatform (KMP)** App mit **Compose Multiplatform**, die auf:
- **Desktop (JVM)**
- **iOS** (`iosArm64`, `iosSimulatorArm64`)
- **Android**

läuft. Nachdem das Statistik-, Aufstellungs- und Analyse-Kernsystem zunächst ohne Login aufgebaut wurde, stehen als nächste große Meilensteine die **Performance-basierte Start-7-Zusammenstellung** sowie der **Übergang zu User-Login & Authentifizierung** an.

---

## 2. Getroffene Architektur- & Design-Entscheidungen (inkl. bisheriger Umsetzungen)

1. **Datenhaltung & Synchronisation (Offline-First)**:
   - Jeder Client speichert Daten lokal (`PlatformStorage`: Desktop nutzt SQLDelight SQLite + JSON-Snapshot unter `~/.handball-stat-tracker/`, iOS `NSUserDefaults`, Android lokalen App-Storage).
   - Einziges Backend ist **Supabase** (PostgreSQL + PostgREST, später GoTrue Auth). Clients synchronisieren über `SupabaseSyncService`. Das frühere eigene Ktor-Backend (`:server`) wurde entfernt, ebenso das Web-Target (JS/Browser), da die App primär als Android-/Desktop-App am Spielfeldrand genutzt wird und Supabase Datenbank, API und Auth abdeckt.
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
  - `shared/src/commonMain/kotlin/com/handball/stattracker/repository/HandballRepository.kt` – StateFlow-Datenhaltung, CRUD, 7-on-Court Aufstellung inkl. `6+1` / `7+0` Regel-Check, 2-Klick-Wechsel, Spieluhr, Plus/Minus-Automatik, Spielprotokoll, 2-Punkte-Tabellenberechnung, Performance-Index, Positionsvergleich, Spieler-Ranking, Supabase-Sync
  - `shared/src/commonMain/kotlin/com/handball/stattracker/repository/DemoDataSeed.kt` – Initiale Demo-Daten (mit 7 aktiven Feld-/Torwartspielern, Bankspielern, `+/-`, Einsatzzeiten und realistischen Führungswechseln im Spielprotokoll)
  - `shared/src/commonMain/kotlin/com/handball/stattracker/storage/PlatformStorage.kt` (+ `desktopMain`, `iosMain`, `androidMain`)
  - `shared/src/commonTest/kotlin/com/handball/stattracker/HandballRepositoryTest.kt` – Unit-Tests (inkl. Tests für `6 Feld + 1 TW` vs. `7 Feld + 0 TW` und Verbot eines 2. Torwarts auf dem Feld)
- **`:composeApp`**:
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/HandballApp.kt` – Haupt-App-Shell, responsive Navbar & statische Titelzeile
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/theme/HandballTheme.kt` – Material 3 Sport-Dashboard-Theme (Light & Dark)
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/DashboardScreen.kt` – `[H]` Home-Dashboard
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/RosterScreen.kt` – Kader-Ansicht & Spieler-/Trainerprofil mit Reitern (*Übersicht & Spiele* sowie *Performance & Ranking*)
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/PlayerPerformanceTab.kt` – Performance-Graphen (Formkurve & 6-Achsen-Radar), Positionsvergleich & Spieler-Ranking
  - `ui/screens/SeasonScreen.kt` – Saison-Liste; Spiel-Dashboard aufgeteilt in `GameDashboard.kt` (Rahmen, Tabs, Trainer/Auszeiten), `GameScoreboard.kt` (Spielstand, Uhr), `GameLineupSection.kt` (Feld/Bank, Kader nominieren), `GameLogSection.kt` (Protokoll, Torverlauf), `GamePlayerCards.kt` + `GameCardExtras.kt` (Spielerkarten) (Spieluhr, Links: 7 Aktive Spieler vs. Rechts: Bank, 2-Klick-Wechsel mit `6+1`/`7+0`-Check, eigener Tab `Verlauf & Protokoll` mit `+3/0/-3` Torverlauf)
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/StandingsScreen.kt` – Ligatabelle
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/PlusCreationDialogs.kt` – `+`-Menü & Erstellungsdialoge

---

## 4. Wichtige Hinweise zur Build-Umgebung (Windows)

- Auf dem System ist **Java 26 (`jdk-26.0.1`)** installiert; daher wird **Gradle 9.8.2** genutzt und in den `build.gradle.kts`-Dateien mit `JvmTarget.JVM_21` kompiliert (ohne `foojay-resolver`, da dieser in älteren Versionen mit Gradle 9.8 inkompatibel war).
- **Wichtige Befehle**:
  - Build & Tests prüfen: `.\gradlew.bat :shared:compileKotlinDesktop :composeApp:compileKotlinDesktop :shared:desktopTest`
  - Desktop-App starten: `.\gradlew.bat :composeApp:run`

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

- [x] **Direkter Zugang zum eigenen Profil** (Branch `feat/own-profile-access`): Konten mit verknüpftem Kadereintrag (jede Rolle) haben auf dem Desktop den Eintrag "Profil" in der Seitenleiste. "Profil" ist ein eigener Bildschirm (`MainNavScreen.PROFILE`, `OwnProfileScreen` in `RosterScreen.kt`): nur der eigene Eintrag, nie die Kaderliste, kein Zurück-Pfeil; fällt die Verknüpfung weg, geht es zur Übersicht. Mobil: Spieler haben "Kader" und zusätzlich "Profil" als letzten Tab (Übersicht | Kader | Saison | Tabelle | Profil); Trainer/Admin behalten "Kader" und öffnen ihr Profil über "Mein Profil" im Konto-Dialog (nur mobil, Desktop hat die Seitenleiste). Symbol für "Profil" (Seitenleiste und Tab) ist die eigene Trikotnummer als Schild (`ui/ProfileNavIcon.kt`; gewählt gefüllt, sonst umrandet; ohne Nummer Personen-Symbol), damit es nicht mit dem Konto-Symbol der Kopfzeile verwechselt wird. Untere Leiste: 8 dp Randabstand, unter 360 dp Breite 4 dp und Beschriftung 12 sp, alle Beschriftungen einzeilig. Screenshots: `desktop-hell-eigenes-profil`, `desktop-hell-kader-mit-profil`, `dialog-trainer-konto-profil`, `mobil-schmal-*` (320 dp).
- [x] **Wechsel per Tipp** (2026-11-23): Im Bearbeitungsmodus öffnet ein Tipp auf die Kopfzeile einer Feld-Karte (bzw. die ganze Bank-Karte) die Wechsel-Auswahl (`ui/screens/SubstitutionPicker.kt`; Handy < 700 dp: Bottom Sheet auf Inhaltshöhe mit der Einzelaktion unten, schließt per Wischen; Tablet/Desktop: Pop-up; gleicher Listeninhalt): Feldspieler → passende Bank-Spieler + "Nur auf die Bank"; Bank-Spieler → Feld-Spieler zum Tauschen + "Aufs Feld (n/7)", wenn weniger als 7 spielen. Nur regelkonforme Partner (6+1 / 7+0, nie 2 TW, keine Gesperrten), sortiert nach gleicher Rolle → gleicher Position → Nummer (`HandballRepository.substitutionOptions`, getestet). Die alten Knöpfe (Einwechseln, Auswechseln für #, Auf Bank, Direkt aufs Feld) und das Wechsel-Banner sind weg. Ohne Bearbeiten öffnet der Tipp wie bisher das Profil. Screenshots `wechsel-feldspieler-mobil`, `wechsel-bankspieler-mobil`, `wechsel-feldspieler-desktop`.
- [x] **Vorbereitung, Wettbewerb und neue Formulare** (2026-11-24): Wettbewerb ist ein Enum `Competition` (Ligaspiel/Testspiel; gespeichert weiter als Text in `game_type`, unbekannte Werte = Ligaspiel, keine Schema-Änderung). Die Vorbereitung gehört zur Saison: `SeasonScope(seasonId, phase)` (`SEASON` = Ligaspiele, `PRESEASON` = Testspiele, `null` = beides) ersetzt die reine Saison-ID als Filter in Repository und UI; das Saison-Menü zeigt unter jeder Saison „Vorbereitung“, sobald es Testspiele gibt (`SeasonSelection.menuScopes`); im Sommer öffnet die App mit der Vorbereitung der kommenden Saison, sobald dort ein Testspiel gespielt wurde (`SeasonSelection.currentScope`). Die Tabelle zählt nur Ligaspiele. Formulare hinter dem „+“ in `ui/screens/CreationForm.kt` (`CreationFormFrame`: unter 700 dp Vollbild, sonst Dialog; Bausteine `SegmentedChoice`, `DropdownField`, `DateField` mit Kalender, `NumberField`). Neues Spiel (`NewGameForm.kt`) in zwei Schritten: Spieldaten, dann Aufgebot als LazyColumn (vorbelegt mit dem Aufgebot des letzten eigenen Spiels, `HandballRepository.suggestedSquad`; Zähler rot über 16 Spieler); Tore nur bei Fremdpaarungen; Spielort-Vorschlag ohne festen Hallennamen. Spieler-Formular: Trikotfelder „Nr. Heim“/„Nr. Auswärts“ gleich hoch, Position als Ausklappliste. Screenshots `formular-*`.
- [x] **Web-Version als PWA** (2026-11-23): Kotlin/Wasm-Target `wasmJs` in `:shared` und `:composeApp` (ersetzt das früher entfernte JS-Target). Einstieg `composeApp/src/wasmJsMain/.../main.kt` (`ComposeViewport`), Speicher `PlatformStorage.wasmJs.kt` (localStorage, Token unverschlüsselt wie im Browser üblich), PWA-Dateien in `composeApp/src/wasmJsMain/resources` (index.html mit Logo beim Laden und Hinweis bei zu altem Browser, `manifest.webmanifest`, Symbole aus dem Logo) und Service Worker aus `src/wasmJsMain/pwa/sw.template.js` (Gradle-Task `pwaServiceWorker`: Dateiliste + Versionskennung, Offline-Start, Updates komplett ab dem nächsten Öffnen; Supabase-Anfragen laufen nie über den Cache). Größe ca. 17 MB inkl. Source-Map. Veröffentlichung per `.github/workflows/web.yml` auf GitHub Pages bei Push auf `main` (Repo ist öffentlich, Entscheidung des Nutzers). Im Browser (Playwright/Chromium) geprüft: Start, Anmeldung Handy/Desktop, Offline-Neustart. **Live seit 2026-11-24** unter https://mbramekamp.github.io/harzlytics/ (Pages-Quelle „GitHub Actions“, `SUPABASE_URL`/`SUPABASE_PUBLIC_KEY` als Repo-Variables bzw. Secrets; Login gegen Supabase im Browser vom Nutzer bestätigt). Hinweis: neue Werte gelten erst ab dem nächsten Lauf, und der Service Worker liefert eine neue Version erst beim nächsten Öffnen. Nicht geprüft: iPhone/Safari, „Zum Startbildschirm“ auf echten Geräten; der Passwort-Reset-Link wird in der Web-App noch nicht verarbeitet.
- [x] **Kompakte Anzeigetafel auf dem Handy** (2026-11-23, `ui/screens/GameCompactScoreboard.kt`): Unter 700 dp ersetzt sie die große Anzeigetafel (~120 statt ~420 dp). Zeile 1: Zurück, Teams + Spielstand mittig, Bearbeiten (Stift) bzw. Fertig (Haken) + Menü ⋮ (Spielstand ändern, Spielstand & +/- automatisch, Spiel löschen mit Rückfrage). Zeile 2: Uhr, Halbzeitstand, Start/Pause; Tipp auf die Uhr öffnet ein Sheet zum Stellen (±10 s, ±1 Min, Halbzeit 30:00, Stand als HZ). Zeile 3 nur bei laufenden 2-Minuten-Strafen (Restzeit). Die ±1-Knöpfe am Spielstand entfallen mobil (Stand ergibt sich aus der Erfassung, Korrektur über „Spielstand ändern“). Startsieben steht mobil nur in der Liste „Auf dem Feld“. Tablet/Desktop unverändert. Bei 7 Feldspielern ohne TW zeigt die Aktionsleiste zusätzlich „Gegentor“ (Bernstein, gilt für die Mannschaft): Gegner +1, alle auf dem Feld −1 auf +/-, Protokolleintrag „Gegentor ins leere Tor“ (`HandballRepository.recordGoalConcededWithoutKeeper`, getestet; mit TW auf dem Feld wirkungslos). Screenshot `mobil-erfassung-ohne-torwart`.
- [x] **Handy-Erfassung „Variante B“** (2026-11-23, Entwurf: Artifact „Spielansicht B“): Unter 700 dp zeigt Tab „Aufstellung“ eine Zeile pro Spieler auf dem Feld (Nummer, Name, Position, Strafen, Tore/Würfe bzw. Paraden/Bälle, +/-) statt großer Karten (`ui/screens/GameMobileTracker.kt`). Im Bearbeitungsmodus: Zeile antippen = Spieler wählen (roter Streifen; Standard: erster Feldspieler), darunter fest die Aktionsleiste: Tor, Fehlwurf, 7m Tor, 7m vorbei, Assist, Techn. F., Steal, Block (TW: Parade, Gegentor, 7m gehalten, 7m Gegentor, TG-Pass, TG-Fehlpass) mit aktuellem Zählerstand, dazu Wechseln (Bottom Sheet, der Eingewechselte ist danach gewählt), 2 Min, Mehr (7m herausgeholt, Gelb, Rot, Blau) und Rückgängig. Die Kopfzeile der Leiste zeigt kurz, was erfasst wurde. Aktionen als reine Funktionen in `model/TrackerActions.kt` (`FieldAction`, `KeeperAction`, getestet); „7m vorbei“ zählt wie „7m Tor“ als Wurf. Rückgängig: Stapel der letzten 30 Stände pro Spiel im Speicher, `HandballRepository.restoreGameState` setzt nur dieses Spiel zurück, Uhr und Spielzeiten bleiben (getestet). Tablet/Desktop behalten die Karten. Ohne Bearbeiten öffnet eine Zeile das Profil. Screenshots `mobil-erfassung-feldspieler`, `mobil-erfassung-torwart`, `mobil-hell-spiel`. Nicht auf dem Handy getestet. Entscheidung des Nutzers: ein vergebener 7m zählt überall als Wurf; die Plus/Minus-Knöpfe der großen Karten (Tore, Würfe, 7m-Tore, 7m-Vers.) nutzen dafür `FieldAction.apply`/`revert` (getestet). Alte Spiele werden nicht umgerechnet. +/- ergibt sich nur aus Toren und Gegentoren, mobil gibt es bewusst kein manuelles +/-.
- [x] **Handy: nur die Spieler auf dem Feld** (2026-11-23): Unter 700 dp zeigt die Aufstellung keine Bank-Liste mehr (`showBench = isWideScreen` in `GameLineupSection.kt`); die Bank erscheint nur in der Wechsel-Auswahl. Stehen weniger als 7 auf dem Feld, steht im Bearbeitungsmodus unter den Karten "Freier Platz (n/7)" und öffnet die Auswahl der Bank-Spieler, die rein dürfen (`HandballRepository.courtEntryCandidates`, getestet; keine Gesperrten, nie 2 TW). Tablet/Desktop behalten Feld und Bank nebeneinander. Offen: Gelb/Rot für einen Spieler auf der Bank geht mobil nicht mehr direkt (Idee: lange drücken in der Auswahl). Screenshot `mobil-feld-freier-platz`.
- [x] **Wechselpaar Angriff/Abwehr („8. Mann“)** (2026-11-27, Stakeholder-Review): Zwei Feldspieler, die sich ständig zwischen Angriff und Abwehr abwechseln, bilden ein Paar (`PlayerGame.swapPartnerPlayerGameId`, gegenseitig gesetzt). Der Partner auf der Bank steht in der Feldansicht direkt unter seinem Feldspieler (Handy-Liste und Karten; Chip „⇅ Wechsel“), darf Statistik bekommen und fehlt in der Bank-Liste; Spielzeit und +/- zählen weiter nur für den, der wirklich auf dem Feld steht. Ein Tipp auf den ⇅-Chip tauscht die beiden als normalen Wechsel (`swapWithPartner` → `substitutePlayer`: Aufstellungsregel, Sperren, Protokoll, Rückgängig). Paar bilden/lösen in der Wechsel-Auswahl („Wechselpaar bilden“ → Partner wählen, „Wechselpaar lösen“; Partner steht in der Auswahl oben); im Startsieben-Dialog als 8. Platz „Wechsel“ (Spieler + „mit“ Partner, Vorschlag gleiche Position; ein bestehendes Paar ist vorausgewählt; Übernehmen ohne Wechsel löst alle Paare). Nur Feldspieler, ein Partner pro Spieler, mehrere Paare möglich; Disqualifikation beendet das Paar, Zeitstrafe blockiert den Tausch. Logik in `repository/SwapPairs.kt` (`swapPartnerOf`, `isSwapStandby`, `canRecordStats`, `swapStandbyByCourtPlayer`, `currentSwapPair`) und `HandballRepository` (`setSwapPartner`, `clearSwapPartner`, `swapWithPartner`, `swapPartnerCandidates`, `applyStartingSevenLineup(…, swapPair)`), Tests `SwapPairTest`. Sync über den Snapshot-JSON; `supabase_schema.sql` hat die Spalte `player_game.swap_partner_player_game_id` (+ `ALTER TABLE … ADD COLUMN IF NOT EXISTS`) und die RPC schreibt sie – vom Nutzer einzuspielen. Alte App-Versionen kennen das Feld nicht und verwerfen es beim nächsten Schreib-Sync. Nebenbei behoben: Bank-Karten hatten im Dunkelmodus dunkle Schrift (`contentColor`). Screenshots `wechselpaar-mobil`, `wechselpaar-desktop-hell/-dunkel`, `startsieben-wechsel`.
- [x] **Trainer-Rolle geprüft** (2026-11-27): `TrainerRoleRepositoryTest` – ein aktiver Trainer (`UserProfile.canEdit`) legt ein Spiel an, setzt die Startsieben, lässt die Uhr laufen, bucht ein Tor und wechselt; ein noch nicht freigegebener Trainer (`PENDING`) und ein Spieler ändern nichts. Mit echtem Supabase noch vom Nutzer zu prüfen (siehe §6.4).
- [x] **UI-Welle 3 "Profi-Look & Logo"** (Details in §6.0): Theme Harz-Rot/Graphit statt Lila, Schrift Barlow, flache Hülle ohne verschachtelte Rahmen, Übersicht als Anzeigetafel, deutsche Datumsangaben, Logo-Zeichen und -Wortmarke in App, Fenster- und Android-Symbol.
- [x] **UI-Überarbeitung Welle 1 & 2** (Details in §6): Lila/Weiß-Theme mit Dunkelmodus, Inhalts-Audit (ein Element = ein Zweck), Saison-Menü in der Kopfzeile, Sync-Symbol, Begriffe Kader/Staff/Aufgebot, Spieler-Profil-Tab mobil, Vereinsverwaltung (`ClubDialog`), Headless-Screenshot-Test.

### Nächste geplante Features (Priorität)
1. [x] **Performance-basierte Start 7 (Starting Seven Feature)**:
   - Dedizierte Funktion & Ansicht, die auf Grundlage der bisherigen Spieler-Performance (Performance-Index, Wurf-/Fangquoten, Plus/Minus, Formkurve der letzten Spiele) automatisch die beste **Start 7** (`TW`, `LA`, `RL`, `RM`, `RR`, `RA`, `KM` bzw. `7-Feldspieler`-Variante) zusammenstellt, begründet/vergleichbar macht und vor oder im Spiel direkt als Startaufstellung übernimmt.
   - Umgesetzt: `HandballRepository.recommendStartingSeven(gameId, useSeventhFieldPlayer)` (reine Funktion → `StartingSevenRecommendation` mit Platz, Wahl, Zweitplatziertem und `StartingSevenScoreBreakdown`), Gewichtung in `StartingSevenWeights` (45 % Saison-Performance-Index, 40 % Form der letzten 5 Spiele, 15 % Plus/Minus pro Spiel). Ein noch nicht angepfiffenes Zielspiel fließt nicht in die Bewertung ein. Gesperrte/zeitbestrafte Spieler werden ausgeschlossen; unbesetzbare Positionen übernimmt der beste freie Feldspieler (als Ersatzlösung markiert).
   - `applyStartingSevenLineup(gameId, playerIds)` übernimmt eine (ggf. manuell getauschte) Aufstellung nur bei gültiger `6+1` / `7+0` Formation und protokolliert sie als `LINEUP_SET` im `GAME_LOG`; `applyRecommendedStartingSevenToGame` nutzt beides.
   - UI: `ui/screens/StartingSevenDialog.kt` (Vorschau je Position mit Saison/Form/+/-, Tausch mit dem Zweitplatzierten, Umschalter `6+1` / `7+0`, „Übernehmen“), geöffnet über alle Startsieben-Buttons in `SeasonScreen.kt`. Neu angelegte Spiele (`addGame`) starten automatisch mit der empfohlenen Startsieben (Fallback: erste Nominierte je Position). Abgesichert durch Unit-Tests in `HandballRepositoryTest.kt`.
2. [x] **User-Login, Authentifizierung & Benutzer-/Rollenverwaltung (RBAC)** – *gemäß [`USER_FEATURE.md`](file:///C:/Users/matsb/harzlytics/USER_FEATURE.md)*:
   - **Stand:** `supabase_schema.sql` (Tabellen, `sync_handball_snapshot` RPC) und `SupabaseSyncService.kt` existieren bereits. Zugriff abgesichert: `anon` hat keinen Zugriff mehr, `authenticated` nur Lesezugriff; Schreiben ausschließlich über die `SECURITY DEFINER`-RPC mit User-JWT (`SupabaseAuthSession.kt`). Cloud-Sync ist bis zum Login-Flow inaktiv. Das SQL wurde am 2026-11-19 im Supabase SQL Editor ausgeführt (Live-Projekt ist abgesichert).
   - [x] **Phase 1 (Supabase Backend & PostgreSQL RLS)** – umgesetzt in `supabase_schema.sql` (Abschnitte 3a, 3, 4, 5); *muss im Supabase SQL Editor erneut ausgeführt werden (idempotent)*:
     - `public.app_role` Enum, `public.user_profiles` (`id`, `email`, `full_name`, `role`, `player_id`, `claim_player_id`, `status`) und `public.club_invitations`. **Bewusst ohne FK auf `player`**, da der Sync `player` bei jedem Lauf neu befüllt.
     - Trigger `handle_auth_user_change()` auf `auth.users`: Profil bei Signup (Standard `SPIELER` / `PENDING`), Einladungscode aus `raw_user_meta_data.invite_code` setzt Rolle/Spieler & `ACTIVE`; Kader-Verknüpfung per E-Mail erst nach E-Mail-Bestätigung und nur bei eindeutigem Treffer. **Rolle wird nie aus Signup-Metadaten übernommen.**
     - `guard_user_profile_update()`: Nicht-Admins dürfen nur `full_name` und `claim_player_id` ändern (kein Self-Upgrade).
     - Helfer `current_user_role()`, `current_player_id()`, `is_active_member()`, `is_admin()`, `is_coach_or_admin()` (nur für `ACTIVE`-Profile).
     - RLS: Fachtabellen nur lesbar für aktive Mitglieder; `user_profiles` eigenes Profil bzw. Admin alles; `club_invitations` nur Admin.
     - `sync_handball_snapshot`: nicht freigeschaltet → 403; `SPIELER` → reiner Lese-Sync; `TRAINER`/`ADMIN` → Schreib-Sync.
     - Erster Admin: nach eigener Registrierung `UPDATE public.user_profiles SET role = 'ADMIN', status = 'ACTIVE' WHERE email = '...';` im SQL Editor.
   - [x] **Phase 2 (KMP Shared Modul `:shared`)**: `model/AuthModels.kt`, `SupabaseAuthService` (Ktor, `/auth/v1/...` + Profil-Abfrage + `requestPlayerClaim`), `AuthRepository` (`StateFlow<AuthState>`, Session-Wiederherstellung auch offline, Token-Refresh), Token-Speicher über `PlatformStorage`, `SupabaseSyncService` mit Benutzer-JWT. Token-Speicher: `PlatformStorage.readSecret/writeSecret` – Android: AES-GCM-Schlüssel im Android Keystore (`AndroidSecretCipher`, **nicht auf dem Gerät/Emulator getestet**); Desktop: Windows DPAPI über PowerShell (**ungetestet**, fällt bei Fehler zurück) bzw. AES-GCM mit Schlüsseldatei (`DesktopSecretStore`, getestet); iOS: **noch unverschlüsselt** (NSUserDefaults, TODO Keychain, braucht einen Mac). Alte Klartext-Sessions werden beim ersten Start migriert und gelöscht. Android-Datenordner: `PlatformStorage.init(context)` (aufgerufen in `MainActivity`) legt alle lokalen Daten in `filesDir` ab statt im Cache-Ordner (`java.io.tmpdir`, vom System löschbar); Altdaten übernimmt `StorageMigration` (`shared/src/jvmSharedMain`, getestet; der Android-Aufruf selbst ist **nicht auf einem Gerät getestet**).
   - [x] **Phase 3 (Compose UI `:composeApp`)**: `LoginScreen` (Login, Registrierung mit optionalem Einladungscode, Passwort-Reset), `PendingApprovalScreen`, `ui/RoleGuard.kt` (`LocalUserRole`, `RoleGuard(vararg roles)`, `EditorOnly`, `rememberCanEdit()`; Standardrolle ohne Provider = SPIELER) und `screens/AccountDialog.kt` (Profil, Rollen-Badge, Kadereintrag beantragen/zurückziehen, Abmelden).
     - Spieler sehen alles, aber ohne Bearbeiten-Button, `+`-Menü, Löschen, Vereinsnamen-Edit und Mannschaftszuordnung. Zusätzlich verwirft `HandballRepository(canEdit = false)` jede Änderung (`mutate`), und die RPC lässt Spieler serverseitig nur lesen.
   - [x] **Phase 4 (Navigation & Header)**: Rollen-Badge und Konto-Dialog im Header (Desktop: Name + Badge klickbar; Mobil: Konto-Icon), `+`-Button in beiden Navigationsleisten nur für Trainer/Admin.
   - [x] **Admin-Bereich**: `AdminRepository` (+ `AdminApi`, `AdminRepositoryTest`) und `screens/AdminDialog.kt`, erreichbar über den Konto-Dialog (nur Rolle ADMIN): Konten freigeben (mit Rolle), sperren/reaktivieren, Rolle ändern, Kader-Anträge bestätigen/ablehnen, Verknüpfung lösen. Eigenes Konto ist geschützt (Rolle/Status nicht änderbar). Braucht keine Schema-Änderung (RLS erlaubt das ADMIN schon).
   - [ ] **Offen:** Einladungscodes in der App erzeugen (bisher per SQL in `club_invitations`; braucht einen kryptografisch sicheren Zufallsgenerator pro Plattform), iOS-Keychain.
3. [ ] **Weitere Analyse-Erweiterungen (Optional)**:
   - Interaktives Wurfbild & 9-Zonen-Tor-Matrix (*Shot Map & Goal Sectors*) für Feldspieler und Torhüter.
   - Gegner-Scouting-Profile & Team-Splits (1. vs. 2. Halbzeit, Heim vs. Auswärts).
   - Granulare Konfliktauflösung beim Backend-Sync & Android-SDK-Aktivierung.

---

## 6. Übergabe: Stand nach UI-Welle 3 (Profi-Look & Logo)

*Diesen Abschnitt zuerst lesen, wenn eine neue Session hier weiterarbeitet.*

### 6.0 Welle 3: Profi-Look & Logo (Branch `design/professional-ui-logo`)
- **Entscheidungen des Nutzers:** Markenfarbe = vertieftes Logo-Rot (`#C8102E`) statt Lila; Schrift Barlow (OFL) wird mitgeliefert.
- **Gefundene Schwachstellen und Lösung:** Rahmen in Rahmen (Seitenleiste, Kopfzeile, Inhalt je umrandet; Karten in Karten) → bündige Seitenleiste mit Haarlinie, Kopfzeile ohne Box, weiße Flächen auf grauem Grund, Listen mit Trennlinien statt Kärtchen. Lila Schleier auf allen Flächen → neutrale Flächen. Zahlen in Markenfarbe → Textfarbe (Farbe nur mit Bedeutung). ISO-Daten → `DisplayFormat.date` ("18.10.2026"). Sieben Eckradien → drei. Zentrierter Titel in leerer Leiste → links. Großbuchstaben-Labels ("AUF DEM FELD", "TORHÜTER", "HEIM") → Satzschreibung. "1. Platz" unter "Tabellenplatz" → "1.". Neues Spiel hatte fest "2026-10-24" und "Löwen-Arena" als Vorgabe → heute bzw. Halle des letzten Heimspiels.
- **Logo:** `ui/brand/HarzLogo.kt`: geneigter roter Block mit freigestelltem "H" (Neigung aus dem kursiven Original-Schriftzug) + Wortmarke HARZLYTICS in Barlow Semi Condensed Bold Italic. Eingesetzt in Desktop-Seitenleiste (Klick = Übersicht), mobiler Kopfzeile, Anmeldung, Fenster-Symbol (`Main.kt`), Android-Launcher (`androidMain/res`, adaptiv + Vektor für Android 7) und `docs/brand/harzlytics-mark.svg`. App-Name überall "Harzlytics".
- **Desktop-Seitenleiste** hat jetzt einen eigenen Eintrag "Übersicht" (vorher nur das "H").
- **Tests:** 69 Logik-Tests + 17 Screenshot-Tests (neu: `anmeldung-hell`/`anmeldung-dunkel`).
- **Auf dem Gerät prüfen:** Android-Symbol und Schrift auf dem Handy (hier kein Android-SDK); Dialog-Screenshots wirken headless grau, weil der Scrim mitgezeichnet wird.
- **Offen/Ideen:** Spielerkarten im Spiel (6.2 Punkt 2) sind weiterhin zahlenlastig; Diagrammfarben im Leistungs-Tab nutzen `primary` (Heim = Rot, Gast = Bernstein) und wurden nicht einzeln geprüft.

### 6.1 Stand
- **Alles implementiert und per Desktop-Tests/-Screenshots geprüft:** Login mit Rollen (Spieler read-only, Trainer/Admin bearbeiten), Admin-Benutzerverwaltung, verschlüsselte Token-Speicherung, Android-Datenordner `filesDir` mit Umzug, Sync-Fixes (keine verlorenen Eingaben), Lila/Weiß-Theme, Inhalts-Audit Welle 1 und 2.
- **Welle 2 enthielt:** Vereinsname und Teilnehmer-Auswahl wanderten aus Start/Tabelle in den `ClubDialog` (Konto-Dialog → "Verein verwalten", nur Trainer/Admin); der Knopf "Gegner" in der Tabelle ist weg (Gegner nur über `+`); Spielwerte in der Kader-Historie sind Wert-Paare (`GameValueRow`) ohne Nullwerte statt einer langen Textzeile; Profil-Kennzahlen zeigen "0× 2Min" nicht mehr.
- **Branches:** Arbeitsbranch `claude/repository-status-o99lyd`, `main` wird nur per Fast-Forward nachgezogen.
- **Konto ↔ Kadereintrag verknüpfen** (drei Wege): (1) automatisch per E-Mail: Trainer trägt die E-Mail beim Spieler ein, der Spieler registriert sich mit derselben Adresse und bestätigt sie → sofort aktiv und verknüpft (DB-Trigger `handle_auth_user_change`); (2) **Admin wählt beim Freigeben** im Benutzer-Dialog ("Kader: …", Vorschlag aus Antrag → E-Mail → Name, `AdminRepository.suggestPlayerId`, nur freie Spieler); (3) Antrag im Konto-Dialog, Bestätigung unter "Kader-Anträge". Ein Konto sieht die Kaderliste erst nach der Freischaltung (RLS, Datenschutz): deshalb wählt nicht der Spieler selbst bei der Registrierung. Einladungscodes für einen bestimmten Spieler gehen weiterhin nur per SQL.
- **Profil wird nachgeladen** (Rolle/Verknüpfung nach Admin-Änderung): beim Öffnen des Konto-Dialogs, beim Tippen auf das Wolken-Symbol und alle 5 Minuten (`onRefreshAccount` → `AuthRepository.reloadProfile`).
- **Tests:** 97 Logik-Tests (`:shared:desktopTest`, Stand 2026-11-27) + Screenshot-Tests (neu: `SwapPairScreenshotTest`); vorher 82 Logik-Tests + 34 Screenshot-Tests (`:composeApp:desktopTest`; `GameCardScreenshotTest`: Spielerkarten zu/auf `karte-*` und Wechsel-Auswahl `wechsel-*`).

### 6.2 Entscheidungen des Nutzers (2026-11-23 getroffen, nicht eigenmächtig ändern)
1. **Filter "Alle / Eigene Spiele / Liga-Spiele"** in der Saison-Liste bleibt.
2. **Spielerkarten in der Spielansicht:** Feldspieler behalten alle Zähler sichtbar (Tore, Würfe, 7m, Assists, Steals, Blocks, Tech. Fehler). Torhüter: Paraden, Gegentore, 7m gehalten, 7m-Gegentore sichtbar; TG-Pass/TG-Vers. hinter dem Aufklapp-Pfeil ("Mehr", `CardMoreToggle` in `ui/screens/GameCardExtras.kt`). Strafen: nur der 2Min-Chip ist immer sichtbar; Gelb/Rot/Blau (TW nur Rot) liegen hinter "Mehr", vergebene Karten erscheinen zugeklappt als kleines Abzeichen (`AwardedCardBadges`).
3. **Profil-Kennzahlen:** eine Kachel = ein Wert (`ui/screens/ProfileStatGrid.kt`, `profileStatTiles`); echte Paare (Tore / Würfe, 7-Meter, Paraden / Gegentore, Gegenstoßpässe) bleiben zusammen. Raster: Feldspieler 4 Spalten, TW/Staff 3, mobil 2.
4. **Staff** bleibt als eigene Gruppe in der Kaderliste.
5. Das "H"-Zeichen bleibt aus der mobilen Kopfzeile weg.

### 6.3 Nächster geplanter Schritt: UI Phase 3 "Bewegung" (noch nicht begonnen)
`AnimatedContent` zwischen den Haupt-Tabs (Überblenden + kurzes Schieben in Tab-Richtung), Liste → Detail (Kader, Spiel) nach vorn/zurück schieben, gleitender Indikator in der unteren Navigation, weiches Überblenden bei Hell/Dunkel (`animateColorAsState`), Dialoge blenden ein/skalieren, Zahlen (Spielstand) zählen kurz hoch. Auf dem Altgerät leicht halten (kurze Dauer, nur Verschieben/Überblenden, keine Layout-Animationen in `SeasonScreen`). Standbilder können Bewegung nicht prüfen: der Nutzer testet auf dem Handy. Die Navigation ist bisher ein `when(currentScreen)` in `MainContentRouter` (`HandballApp.kt`).

### 6.4 Auf dem Gerät des Nutzers zu prüfen (hier nicht ausführbar)
- **Wechselpaar + Trainer mit echtem Supabase** (2026-11-27): `supabase_schema.sql` im SQL-Editor einspielen; mit einem freigegebenen Trainer-Konto (Status ACTIVE) ein Spiel anlegen, Startsieben mit „Wechsel“ übernehmen, Uhr starten, für den Wechselspieler ein Tor buchen, ⇅ tippen, auf einem zweiten Gerät (Spieler-Konto) nach dem Sync prüfen. Ein Trainer, der „nichts bearbeiten kann“, ist fast immer noch nicht freigegeben (`PENDING`).
- Android-Build (`assembleDebug`) und alles in `androidMain` (`AndroidSecretCipher`, `PlatformStorage.init`, Datenumzug), Windows-DPAPI-Pfad (`WindowsDpapiProtector`), Migration der alten Klartext-Session, Flugmodus-/Offline-Test im Spiel, Login/Rollen/Admin-Dialog mit echtem Supabase.
- iOS: nicht gebaut (kein Xcode-Projekt, Token unverschlüsselt in `NSUserDefaults`, Keychain fehlt). Der Nutzer hat kein Mac; Weg wäre GitHub-Actions-Mac + Sideloadly, aktuell zurückgestellt (Android-Altgerät vorhanden).

### 6.5 Weiteres Backlog (Reihenfolge nach Nutzen)
1. Einladungscodes in der App erzeugen (bisher per SQL in `club_invitations`; braucht einen sicheren Zufallsgenerator pro Plattform).
2. Sync-Konflikte (Review-Finding #6): zwei Geräte offline, das mit der späteren Uhrzeit überschreibt das ganze Snapshot; bräuchte Zusammenführen pro Entität oder Serverzeit.
3. Release-APK mit eigenem Signaturschlüssel; Supabase-Einstellungen prüfen (E-Mail-Bestätigung, Passwort-Reset-Link).
4. ~~`SeasonScreen.kt` aufteilen~~ (erledigt 2026-11-23), README/Briefing von Windows-`file:///`-Links auf relative Links umstellen.
5. Optional: Wurfbild/Tor-Zonen, Gegner-Scouting, Halbzeit- und Heim/Auswärts-Auswertung.

### 6.6 Arbeitsweise, die sich bewährt hat
- Erst Audit/Plan mit Befunden und Empfehlung, dann umsetzen; subjektive Entscheidungen (Farben, Informationen streichen) vorher abstimmen.
- Nach jeder UI-Änderung `:composeApp:desktopTest` laufen lassen und die PNGs ansehen (so wurden u. a. eine kollabierte Tabellenspalte, überlappende Titel und doppelte Zurück-Pfeile gefunden).
- Neue Berechtigungs- oder Sync-Logik zuerst im Repository mit Test, dann in der UI.
