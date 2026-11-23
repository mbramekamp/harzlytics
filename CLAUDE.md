# Harzlytics – Handball Stat Tracker (KMP)

Kotlin Multiplatform + Compose Multiplatform app for tracking handball game, lineup and player statistics.
Targets: Android, Desktop (JVM), iOS. Offline-first storage with sync to Supabase (the only backend; the Ktor `:server` and the web/JS target were removed). Primary use: an Android phone or desktop at the sideline during a game.

Full history, decisions, roadmap and the **hand-over state** (what is done, what is open, how to resume): `AGENT_BRIEFING.md` §5 and §6. RBAC/auth design: `USER_FEATURE.md`. **Start a new session by reading §6 of the briefing.**

## Modules
- `:shared`
  - `model/` (`Models.kt`, `AuthModels.kt`, `SyncState.kt`), SQLDelight schema in `commonMain/sqldelight/.../HandballDatabase.sq`
  - `repository/`: `HandballRepository.kt` (StateFlow state, lineup rules, stats, rankings, sync, `canEdit` read-only mode), `SupabaseSyncService.kt`, `AuthRepository.kt` + `SupabaseAuthService.kt` (login, session, player claim), `AdminRepository.kt` (user management), `SeasonSelection.kt` (current season), `DefaultClubData.kt`
  - `config/SupabaseConfig.kt`, `storage/PlatformStorage*.kt` (one actual per platform; `readSecret/writeSecret` for tokens), `desktopMain/.../DesktopSecretStore.kt`, `androidMain/.../AndroidSecretCipher.kt`, `jvmSharedMain/.../StorageMigration.kt` (compiled into androidMain and desktopTest)
  - tests in `commonTest` (+ `desktopTest` for the JVM-only storage classes); demo data lives only in `commonTest/.../DemoDataSeed.kt`
- `:composeApp`: UI. `ui/HandballApp.kt` is the shell (side navbar ≥700dp, bottom bar on mobile; header with season menu, sync icon, account). Screens in `ui/screens/`: Dashboard, Roster, Season (+ game in `SeasonScreen.kt`), Standings, `LoginScreen`, `AccountDialog`, `AdminDialog` (users), `ClubDialog` (club name, season participants), `PlusCreationDialogs` (only place to create things), `StartingSevenDialog`. Shared UI: `ui/RoleGuard.kt`, `ui/SeasonPicker.kt`, `ui/SyncIndicator.kt`, `ui/ProfileNavIcon.kt` (shirt-number icon for "Profil"), `ui/theme/`.
- `supabase_schema.sql`: Supabase tables, RLS and the `sync_handball_snapshot` RPC. The user applies it manually in the Supabase SQL editor, so never run it against the live project.

## Commands (Windows, Java 26, Gradle 9.8.2, JvmTarget 21)
- Full check: `.\gradlew.bat :shared:compileKotlinDesktop :composeApp:compileKotlinDesktop :shared:desktopTest :composeApp:desktopTest`
- Desktop app: `.\gradlew.bat :composeApp:run`; Android APK: `.\gradlew.bat :composeApp:assembleDebug` (needs `.env` with the Supabase keys at build time)
- `:composeApp:desktopTest` renders the main screens headless (desktop/mobile, light/dark, player profile, account/club/user dialogs) to `composeApp/build/screenshots/` (`ScreenshotTest`). **Look at the PNGs after every UI change.** Pass `width`/`height` in dp (the helper multiplies by density).
- `.\gradlew.bat :composeApp:updateDocScreenshots` re-renders and copies the README images to `docs/screenshots/` (commit them after UI changes).
- Cloud sessions (Linux): Gradle works, but there is **no Android SDK** (installing it needs license acceptance, which the sandbox blocks), so Android code (`androidMain`, `MainActivity`) is not compiled there. A Kotlin incremental-compile bug sometimes appears after changes in `:shared` ("FqNames can't be derived"): `rm -rf composeApp/build/kotlin composeApp/build/classes` and rebuild. Maven Central may answer 429; just retry.

## Conventions
- All UI text is **German**. UI content: as little as possible, as much as necessary. One element = one purpose; nothing duplicated (titles, cards, chips, buttons); no technical terms, URLs or keys visible to users; no explanatory help text, no DB table names.
- Terms: **Kader = Torhüter + Feldspieler**; coaches are **Staff** (stored in `PLAYER` with `role = COACH`); the nominated game roster is **Aufgebot**.
- Navigation rules: season is chosen only in the header menu (default: current season via `SeasonSelection`); new game/season/player/opponent only through the `+` menu (trainer/admin); club name and season participants in `ClubDialog`; users in `AdminDialog` (admin); sync state only as the cloud icon (`SyncState`); theme switch only in the account dialog; "Profil" (`MainNavScreen.PROFILE`) shows only the own linked roster entry, never the Kader list: desktop side navbar for every linked account, extra last mobile tab for Spieler (Kader stays), "Mein Profil" in the account dialog for linked Trainer/Admin on mobile.
- Account ↔ roster link: automatic by e-mail, or the admin picks the roster entry when approving (`AdminDialog`, suggestion via `AdminRepository.suggestPlayerId`), or the player requests it in the account dialog. Unapproved accounts cannot read the roster (RLS), so they cannot choose themselves. The profile is reloaded on opening the account dialog, on tapping the cloud icon and every 5 minutes.
- Roles: `SPIELER` read-only, `TRAINER`/`ADMIN` edit. Guard UI with `EditorOnly`/`RoleGuard`; the repository additionally ignores changes when `canEdit = false`; the server enforces it in the RPC.
- Theme "Harz red on graphite/white" (from the logo), dark mode kept. `primary` = Harz red `#C8102E`, used only for brand, selection and main actions; numbers and data stay in text colour; panels are white on a cool grey background (no tinted surfaces, `surfaceTint` transparent). `secondaryContainer` is neutral slate (tonal buttons, chips). Three radii: 6 (chips/badges), 10 (rows/fields), 14 (cards/dialogs). Fonts: Barlow (text), Barlow Semi Condensed (titles, numbers), bold italic only for scores and the wordmark (`scoreStyle()`); fonts live in `composeResources/font` (OFL, `docs/BARLOW_OFL.txt`). Logo: `ui/brand/HarzLogo.kt` (`HarzMark`, `HarzLockup`, `harzMarkVector()`), also the desktop window icon, the Android launcher icon (`androidMain/res`) and `docs/brand/harzlytics-mark.svg`. Dates are shown German via `DisplayFormat.date` (stored as ISO). `secondary` is deliberately amber (opposing series, yellow cards, 2-min, timeouts, draws via `amberContainer()`), `tertiary` green = "good". Change colors only in `ui/theme/HandballTheme.kt`, keep text pairs at WCAG ≥ 4.5:1. Shared component colors in `ui/theme/ComponentColors.kt`.
- Handball lineup rule: at most 7 on court, either 6 field + 1 TW or 7 field + 0 TW, never 2 TW at once (`isValidActiveCourtLineup` / `canSubstituteOrEnterCourt`). Lineup changes need tests.
- Put new logic in `HandballRepository` (or a new class in `:shared`) as pure, testable functions, with UI in `:composeApp`. `SeasonScreen.kt` (~3.7k lines) and `HandballRepository.kt` (~2.2k lines) are large: put new UI in new files and read them in targeted ranges.
- Secrets live in `.env` (gitignored): `SUPABASE_URL`, `SUPABASE_PUBLIC_KEY`/`SUPABASE_ANON_KEY`. Never commit keys, never use `sb_secret_` keys client-side.

## Workflow
- After changes in `:shared`, run `:shared:desktopTest`. Before finishing, run the full check above and look at the screenshots for UI changes.
- When a roadmap item ships, tick it in `AGENT_BRIEFING.md` §5 and keep §6 current.
- Git: develop on the session branch; `main` is brought up to date by fast-forward only when the user asks. Commit trailers as given by the session.
