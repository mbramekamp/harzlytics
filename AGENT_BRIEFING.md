# AI Agent Briefing – Handball Stat Tracker (KMP)

Dieses Dokument dient als Übergabe-Briefing für KI-Coding-Assistenten, um in einer neuen Session ohne Informationsverlust an diesem Projekt weiterarbeiten zu können.

---

## 1. Projektziel & Ursprungsanforderungen

Auf Basis des vorhandenen ER-Datenmodells (`handball-stata-tracker-database-modell.mermaid`) und des skizzierten UI-Grundkonzepts (`Screenshot 2026-10-24 093637.png`) entsteht eine **Kotlin Multiplatform (KMP)** App mit **Compose Multiplatform**, die auf:
- **Desktop (JVM)**
- **Web (JS Browser)**
- **iOS** (`iosArm64`, `iosSimulatorArm64`)
- **Android**

läuft. Von einer Login-Implementierung ist bewusst abgesehen.

---

## 2. Getroffene Architektur- & Design-Entscheidungen (aus `/grill-me`)

1. **Datenhaltung & Synchronisation (Offline-First)**:
   - Jeder Client speichert Daten lokal (`PlatformStorage`: Desktop nutzt SQLDelight SQLite + JSON-Snapshot unter `~/.handball-stat-tracker/`, Web nutzt Browser `localStorage`, iOS `NSUserDefaults`, Android lokalen App-Storage).
   - Zusätzlich existiert im selben Projekt ein eigenes **Ktor-Backend (`:server`)** mit SQLite-Datenbank (`~/.handball-stat-tracker-server/`), über das alle Clients via `GET/POST /api/sync` synchronisieren können.
2. **Datenmodell & Erweiterungen gegenüber dem Mermaid-Diagramm**:
   - **7 klassische Handball-Positionen** (`TW`, `LA`, `RL`, `RM`, `RR`, `RA`, `KM`) werden automatisch als Seed-Daten in `POSITION` angelegt; eigene Positionen können ergänzt werden.
   - **Trainer (`COACH`)**: Werden in derselben Tabelle `PLAYER` über `role = PlayerRole.COACH` gespeichert (`position_id`, `jersey_number_home_kit` und `jersey_number_away_kit` sind dafür `nullable`).
   - **Liga-Replika (`SEASON_OPPONENT` & `GAME`)**:
     - Neue Verknüpfungstabelle `SEASON_OPPONENT(season_id, opponent_team_id)`, damit Gegner beim Anlegen direkt einer oder mehreren Saisons zugewiesen werden können.
     - `GAME` wurde um `is_home_game` (`Boolean`, bestimmt Heim/Auswärts bei eigenen Spielen) und `second_opponent_id` (`Int?`, erlaubt das Eintragen von Spielen zwischen zwei Liga-Gegnern für eine vollständige Ligatabelle) erweitert.
   - **Eigener Vereinsname**: Konfigurierbar im Dashboard (`ownClubName` im `DatabaseSnapshot`, Standard: `"HC Handball Löwen"`).
3. **UI-Grundkonzept & Responsive Navigation**:
   - **Desktop / Web / Tablet (`maxWidth >= 700.dp`)**:
     - Links: Vertikale abgerundete `Navbar` mit `[H]`-Button oben, den Icons für **Kader**, **Saison**, **Tabelle** sowie dem **`+`-Button** und einem Dark/Light-Mode-Schalter.
     - Oben rechts: Abgerundete **statische Titelzeile (`Spieler/Saison/Spiel`)**, die den Namen des aktuell geöffneten Elements anzeigt (Spielername, Saison/Staffel oder Spielpaarung).
     - Unten rechts: Abgerundeter `Content`-Bereich.
   - **Mobile Devices (`maxWidth < 700.dp`)**:
     - Die `Navbar` wird am **unteren Bildschirmrand** (Bottom Navigation Bar) dargestellt, damit die Bedienung mit dem Daumen möglich bleibt.
   - **`[H]`-Button**: Öffnet einen eigenen **Home / Dashboard**-Screen mit Saisonzusammenfassung, letzten Spielen, Top-Scorern und Sync-Steuerung.
   - **`+`-Menü**: Bietet 5 Optionen:
     1. *Neues Spiel* (inkl. Spieltagskader-Schnellauswahl für `PLAYER_GAME` oder Liga-Fremdpaarung)
     2. *Neuen Spieler* (Feldspieler / Torwart mit Position & Heim-/Auswärtstrikot)
     3. *Neuen Gegner (mit Saison-Zuweisung)*
     4. *Neuer Trainer*
     5. *Neue Saison* (auch direkt im Saison-Screen anlegbar)
   - **Statistik-Erfassung & Bearbeitungsmodus pro Spiel**:
     - Spiele öffnen standardmäßig im **schreibgeschützten Ansichtsmodus** (`isEditMode = false`), in dem der Spielstand sowie die komplette Kaderliste mit allen Statistiken unbearbeitbar dargestellt werden.
     - Erst über den Button **„Bearbeitungsmodus“** (`isEditMode = true`) werden die schnellen `+/-` Buttons, die direkte Zahleneingabe, die Straf-Chips (`2Min`, `Gelb`, `Rot`, `Blau`), die Timeout-Verwaltung und die Spieltagskader-Nominierung freigeschaltet.
   - **Performance-Reiter, Positionsvergleich & Spieler-Ranking**:
     - Spieler- und Trainerprofile verfügen über den Reiter **„Performance & Ranking“** (`PlayerPerformanceTab.kt`) mit Handball-Performance-Index (`0–100 Pkt`), chronologischen Spielverlaufs-Graphen (inkl. gestrichelter Positions-Durchschnittslinie), 6-Achsen-Stärken-Radar, Einzelwert-Vergleich mit allen Spielern derselben Position sowie einem filter- und sortierbaren **Trainer-Spieler-Ranking** (inkl. Startsieben-Empfehlung je Position).

---

## 3. Modul- und Dateistruktur

- **`gradle/libs.versions.toml`**: Version Catalog (`Kotlin 2.4.21`, `Compose Multiplatform 1.12.2`, `SQLDelight 2.4.1`, `Ktor 3.6.0`, `Gradle 9.8.2`).
- **`:shared`**:
  - `shared/src/commonMain/sqldelight/com/handball/stattracker/db/HandballDatabase.sq` – SQLDelight-Tabellen & Queries
  - `shared/src/commonMain/kotlin/com/handball/stattracker/model/Models.kt` – Alle Entitäten, `DatabaseSnapshot`, `StandingsRow`, `PlayerAggregatedStats`, `PlayerGameHistoryEntry`, `PlayerGamePerformancePoint`, `PositionMetricComparison`, `PlayerRankingEntry`, `RadarMetricAxis`
  - `shared/src/commonMain/kotlin/com/handball/stattracker/repository/HandballRepository.kt` – StateFlow-Datenhaltung, CRUD, 2-Punkte-Tabellenberechnung, Quoten-Aggregation, Performance-Index, Positionsvergleich, Spieler-Ranking, Ktor-Sync
  - `shared/src/commonMain/kotlin/com/handball/stattracker/repository/DemoDataSeed.kt` – Initiale Demo-Daten (mit mindestens 2 Spielern auf allen 7 Positionen)
  - `shared/src/commonMain/kotlin/com/handball/stattracker/storage/PlatformStorage.kt` (+ `desktopMain`, `jsMain`, `iosMain`, `androidMain`)
  - `shared/src/commonTest/kotlin/com/handball/stattracker/HandballRepositoryTest.kt` – Unit-Tests
- **`:composeApp`**:
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/HandballApp.kt` – Haupt-App-Shell, responsive Navbar & statische Titelzeile
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/theme/HandballTheme.kt` – Material 3 Sport-Dashboard-Theme (Light & Dark)
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/DashboardScreen.kt` – `[H]` Home-Dashboard
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/RosterScreen.kt` – Kader-Ansicht & Spieler-/Trainerprofil mit Reitern (*Übersicht & Spiele* sowie *Performance & Ranking*)
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/PlayerPerformanceTab.kt` – Performance-Graphen (Formkurve & 6-Achsen-Radar), Positionsvergleich der Einzelwerte & Trainer-Spieler-Ranking
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/SeasonScreen.kt` – Saison-Ansicht & Spiel-Dashboard (Ansichtsmodus vs. expliziter Bearbeitungsmodus)
  - `composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/StandingsScreen.kt` – Ligatabelle (Liga-Replika)
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

## 5. Mögliche nächste Schritte für kommende Sessions

- **Gesamtwerte & Quoten pro Spiel anzeigen**:
  - In der Spiel-Detailansicht (`SeasonScreen.kt`) sollen die aggregierten Team-Gesamtwerte für das jeweilige Spiel (Gesamttore/-würfe, Team-Wurfquote %, 7m-Quote %, Torwart-Fangquote %, gesamte Assists, Steals, Blocks, Technische Fehler und 2-Minuten-Strafen) übersichtlich ausgewiesen werden.
- **Excel-Export für Saison-Daten & Einzelspiele**:
  - **Saison-Export (Excel)**: Export der gesamten Saisondaten (Spielübersicht, Ligatabelle, aggregierte Spieler- & Torwartstatistiken inkl. Quoten).
  - **Spiel-Export (Excel)**: Direkter Export eines einzelnen Spiels inkl. Endergebnis, Halbzeitstand, Team-Gesamtwerten/Quoten und den Einzelstatistiken des gesamten Spieltagskaders.
- Vollständige Konfliktauflösung auf Einzeldatensatz-Ebene (Granularer Entity-Merge statt Snapshot-Timestamp-Abgleich) zwischen `:shared` und `:server`.
- Aktivierung des Android-App-Plugins in `composeApp/build.gradle.kts`, sobald ein lokales Android SDK (`local.properties` / `ANDROID_HOME`) eingerichtet ist.
