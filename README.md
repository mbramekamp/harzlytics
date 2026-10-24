# Handball Stat Tracker (Kotlin Multiplatform)

Eine **Kotlin Multiplatform (KMP)** & **Compose Multiplatform** Anwendung zur Erfassung und Auswertung von Handball-Spiel- und Kaderstatistiken für **Desktop (JVM)**, **Web (Browser)**, **iOS** und **Android** mit **Offline-First-Speicherung** und **Ktor-Backend-Synchronisation**.

---

## 1. Projektstruktur

- **`:shared`**:
  - **Datenmodell**: [Models.kt](file:///C:/Users/matsb/Desktop/Programmieren/Handball/shared/src/commonMain/kotlin/com/handball/stattracker/model/Models.kt) & SQLDelight-Schema [HandballDatabase.sq](file:///C:/Users/matsb/Desktop/Programmieren/Handball/shared/src/commonMain/sqldelight/com/handball/stattracker/db/HandballDatabase.sq) basierend auf [handball-stata-tracker-database-modell.mermaid](file:///C:/Users/matsb/Desktop/Programmieren/Handball/handball-stata-tracker-database-modell.mermaid).
  - **Offline-First Persistenz**: [PlatformStorage.kt](file:///C:/Users/matsb/Desktop/Programmieren/Handball/shared/src/commonMain/kotlin/com/handball/stattracker/storage/PlatformStorage.kt) (Desktop: SQLite + Datei im User-Home, Web: Browser `localStorage`, iOS: `NSUserDefaults`, Android: App-Storage).
  - **Repository & Berechnungslogik**: [HandballRepository.kt](file:///C:/Users/matsb/Desktop/Programmieren/Handball/shared/src/commonMain/kotlin/com/handball/stattracker/repository/HandballRepository.kt) (Offizielle 2-Punkte-Ligatabelle, Quotenberechnung, Spielhistorie, Ktor-Sync) sowie [DemoDataSeed.kt](file:///C:/Users/matsb/Desktop/Programmieren/Handball/shared/src/commonMain/kotlin/com/handball/stattracker/repository/DemoDataSeed.kt).
- **`:composeApp`**:
  - **App-Shell & Adaptive Navigation**: [HandballApp.kt](file:///C:/Users/matsb/Desktop/Programmieren/Handball/composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/HandballApp.kt) setzt das UI-Grundkonzept ([Screenshot 2026-10-24 093637.png](file:///C:/Users/matsb/Desktop/Programmieren/Handball/Screenshot%202026-10-24%20093637.png)) um:
    - Auf **Desktop / Web / Tablet**: Linke vertikale abgerundete Navbar (mit `[H]`-Button oben, **Kader**, **Saison**, **Tabelle** und **`+`-Button**), oben rechts die statische Titelzeile (`Spieler/Saison/Spiel`) und darunter der abgerundete `Content`-Bereich.
    - Auf **Mobile (iOS / Android)**: Untere Navigation Bar zur optimalen Daumen-Bedienung.
  - **Screens**:
    - [`DashboardScreen`](file:///C:/Users/matsb/Desktop/Programmieren/Handball/composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/DashboardScreen.kt) (`[H]` Home-Screen)
    - [`RosterScreen`](file:///C:/Users/matsb/Desktop/Programmieren/Handball/composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/RosterScreen.kt) (Gruppierter Kader & Spieler-/Trainerprofil mit Spielhistorie)
    - [`SeasonScreen`](file:///C:/Users/matsb/Desktop/Programmieren/Handball/composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/SeasonScreen.kt) (Saisonübersicht & interaktives Spiel-Statistik-Dashboard mit `+/-` Buttons)
    - [`StandingsScreen`](file:///C:/Users/matsb/Desktop/Programmieren/Handball/composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/StandingsScreen.kt) (Handball-Ligatabelle / Liga-Replika)
    - [`PlusCreationDialogs`](file:///C:/Users/matsb/Desktop/Programmieren/Handball/composeApp/src/commonMain/kotlin/com/handball/stattracker/ui/screens/PlusCreationDialogs.kt) (`+`-Menü: Neues Spiel, Neuen Spieler, Neuen Gegner mit Saison-Zuweisung, Neuer Trainer, Neue Saison)
- **`:server`**:
  - Ktor-Backend ([Application.kt](file:///C:/Users/matsb/Desktop/Programmieren/Handball/server/src/main/kotlin/com/handball/stattracker/server/Application.kt)) mit lokaler SQLite-Persistenz und `/api/sync`-Endpunkt.

---

## 2. Starten der Anwendung

### Desktop (JVM) starten
```bash
./gradlew :composeApp:run
```

### Web (Browser) starten
```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
```

### Ktor Offline-First Sync-Backend starten (optional, Port 8080)
```bash
./gradlew :server:run
```

### Tests ausführen
```bash
./gradlew :shared:desktopTest
```
