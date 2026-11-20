# Harzlytics – Handball Stat Tracker (KMP)

Kotlin Multiplatform + Compose Multiplatform app for tracking handball game, lineup and player statistics.
Targets: Android, Desktop (JVM), iOS. Offline-first storage with sync to Supabase (the only backend; the Ktor `:server` and the web/JS target were removed).

For the full history, design decisions and roadmap, read `AGENT_BRIEFING.md`. The RBAC/auth design is in `USER_FEATURE.md`.

## Modules
- `:shared`: models (`model/Models.kt`), SQLDelight schema (`commonMain/sqldelight/.../HandballDatabase.sq`), core logic in `repository/HandballRepository.kt` (StateFlow state, lineup rules, stats, rankings, sync), `repository/SupabaseSyncService.kt`, `config/SupabaseConfig.kt`, `storage/PlatformStorage*.kt` (one actual per platform), tests in `commonTest/.../HandballRepositoryTest.kt`.
- `:composeApp`: UI. `ui/HandballApp.kt` is the shell (side navbar ≥700dp, bottom bar on mobile). Screens are in `ui/screens/`.
- `supabase_schema.sql`: Supabase tables, RLS and the `sync_handball_snapshot` RPC. The user applies it manually in the Supabase SQL editor, so never run it against the live project.

## Commands (Windows, Java 26, Gradle 9.8.2, JvmTarget 21)
- Full check: `.\gradlew.bat :shared:compileKotlinDesktop :composeApp:compileKotlinDesktop :shared:desktopTest`
- Tests only: `.\gradlew.bat :shared:desktopTest`
- Desktop app: `.\gradlew.bat :composeApp:run`
- Screenshots ohne Fenster: `.\gradlew.bat :composeApp:desktopTest` rendert die Hauptscreens (Desktop/Mobil, hell/dunkel) nach `composeApp/build/screenshots/` (`ScreenshotTest`). Nach UI-Änderungen ansehen.

## Conventions
- Theme: Lila (leichter Magenta-Einschlag) auf Weiß, Dunkelmodus bleibt. `secondary` ist bewusst Bernstein (Gegenfarbe: Gast-Serien, Gelbe Karten, Auszeiten), `tertiary` Grün = "gut/positiv". Farben nur in `ui/theme/HandballTheme.kt` ändern, Text-Paare mindestens WCAG 4,5:1. Gemeinsame Komponentenfarben in `ui/theme/ComponentColors.kt`.
- All UI text is **German**. Keep the UI clean: no explanatory help text, no redundant badges, no DB table names in labels.
- Handball lineup rule: at most 7 on court, either 6 field + 1 TW or 7 field + 0 TW, never 2 TW at once. It is enforced in `isValidActiveCourtLineup` / `canSubstituteOrEnterCourt`. Changes to lineup logic need tests.
- Coaches are stored in `PLAYER` with `role = PlayerRole.COACH`.
- Put new logic in `HandballRepository` (or a new class in `:shared`) as pure, testable functions, with UI in `:composeApp`.
- `SeasonScreen.kt` (~3.6k lines) and `HandballRepository.kt` (~2k lines) are large. Put new UI in new files instead of growing them, and read them in targeted ranges.
- Secrets live in `.env` (gitignored): `SUPABASE_URL`, `SUPABASE_PUBLIC_KEY`/`SUPABASE_ANON_KEY`. Never commit keys, and never use `sb_secret_` keys client-side.

## Workflow
- After changes in `:shared`, run `:shared:desktopTest`. Before you finish, run the full check.
- When a roadmap item ships, tick it in `AGENT_BRIEFING.md` §5.
