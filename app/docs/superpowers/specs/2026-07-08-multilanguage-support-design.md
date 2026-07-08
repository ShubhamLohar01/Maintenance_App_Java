# Multilanguage Support (EN / HI / MR) — Design Spec

**Date:** 2026-07-08
**Status:** Approved design, pending implementation
**Supersedes:** [`2026-07-07-language-switch-i18n-design.md`](2026-07-07-language-switch-i18n-design.md)

## Goal

Let **any user, in any role**, switch the app UI language between **English**,
**हिन्दी (Hindi)** and **मराठी (Marathi)** from Settings. The switch is instant (no
restart / no visible flash). The choice is stored **per user** on the device and
**resets to English on logout**; it never touches the backend and never translates
user-entered data (machine names, notes, server text).

## Why this is phased

The app is **not internationalized today**: `stringResource(...)` is used **nowhere**,
`res/values/strings.xml` holds ~30 mostly-unused keys, and there are **647 hardcoded
`Text("…")`** calls across 37+ screens. A working switch therefore has two parts of
very different size:

1. **Switch mechanism** (small) — enum, per-user preference, root locale override, Settings picker, login/logout wiring.
2. **String extraction + translation** (large) — every in-scope hardcoded string pulled into resources and translated.

This spec delivers the full mechanism plus the **worker-facing screens** translated
end-to-end, establishing a repeatable pattern for later passes.

## Decisions (from this session's brainstorm)

| Decision | Choice |
|---|---|
| Languages | English (default/fallback), Hindi, Marathi — all Devanagari, **no RTL** |
| Mechanism | **Approach A** — Compose `CompositionLocal` locale override (no AppCompat) |
| Persistence | **Per-user**, keyed by userId, **reset to English on logout** |
| Scope | Worker-facing screens, **including technician** screens |
| Translations | I generate HI + MR drafts, flagged for native-speaker review |

Rationale for Approach A over AppCompat `setApplicationLocales`: the app is pure-Compose
on `ComponentActivity`, and `FactoryOpsTheme` already wraps content in a
`CompositionLocalProvider` — the override slots in naturally, gives an instant switch with
no activity recreate, and keeps per-user persistence simple (AppCompat's model is
device-global, so per-user would require a custom pref layer on top of it anyway).

## Architecture

### `AppLanguage` — `domain/model/AppLanguage.kt` (new)

Single source of truth for the supported languages.

```kotlin
enum class AppLanguage(val tag: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi",  "हिन्दी"),
    MARATHI("mr", "मराठी");

    companion object {
        val DEFAULT = ENGLISH
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: DEFAULT
    }
}
```

`displayName` is always rendered in its own script so each option is recognizable
regardless of the active language.

### `LanguagePreferences` — `data/local/LanguagePreferences.kt` (new)

Hilt `@Singleton` over plain `SharedPreferences` (file `"language_prefs"`), mirroring the
existing `OthersTagPreferences` pattern (not encrypted — the choice is not secret).

- State: `private val _current = MutableStateFlow(AppLanguage.DEFAULT)`,
  exposed as `currentLanguage: StateFlow<AppLanguage>`. This is the value the UI observes.
- Persisted per user under key `"lang_" + userId`.
- API:
  - `loadForUser(userId: String)` — read the stored tag for that user (default English) and push it to `_current`. Called on login.
  - `setLanguageForUser(userId: String, lang: AppLanguage)` — persist `"lang_<userId>"` and push to `_current`. Called from Settings.
  - `resetToDefault()` — set `_current = DEFAULT`. Does **not** erase the stored per-user value, so the next login for that user restores their choice. Called on logout.
  - `languageForUser(userId: String): AppLanguage` — a **read that does not touch `_current`**, for background workers building notifications.

### `ProvideLocalizedContext` — `presentation/common/ProvideLocalizedContext.kt` (new)

Root composable that overrides the context/config so every `stringResource` resolves the
active language and recomposes on change.

```kotlin
@Composable
fun ProvideLocalizedContext(language: AppLanguage, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val localized = remember(language, ctx) {
        val config = Configuration(ctx.resources.configuration).apply { setLocale(Locale(language.tag)) }
        ctx.createConfigurationContext(config) to config
    }
    CompositionLocalProvider(
        LocalContext        provides localized.first,
        LocalConfiguration  provides localized.second,
    ) { content() }
}
```

### `MainActivity` — wire the override at the root

`MainActivity` is already `@AndroidEntryPoint`, so inject the preference directly:

```kotlin
@Inject lateinit var languagePreferences: LanguagePreferences
// …
setContent {
    val lang by languagePreferences.currentLanguage.collectAsStateWithLifecycle()
    ProvideLocalizedContext(lang) {
        FactoryOpsTheme { AppNavGraph() }
    }
}
```

### Login / logout wiring

Hook the two transitions in the **auth repository** (the single source of truth for
session lifecycle) rather than scattering them across ViewModels:

- **On successful login** (once `userId` is known): `languagePreferences.loadForUser(userId)`.
- **On logout / auth clear** (`AuthRepository.logout()` / token clear): `languagePreferences.resetToDefault()`.

Exact call sites are confirmed against `AuthRepositoryImpl` during planning.

### Settings language picker

A new **"Language / भाषा"** card in `SettingsScreen`, above Logout:

- Three single-select rows (radio), each label in its own script — **English · हिन्दी · मराठी** — current language checked.
- On select → `SettingsViewModel.onLanguageSelected(lang)` → `languagePreferences.setLanguageForUser(currentUserId, lang)`, where `currentUserId` comes from the already-loaded `uiState.currentUser`.
- Available to every role, since Settings is shared.
- The whole app recomposes instantly in the new language.

### Notification localization

Worker notifications (e.g. **Missed Stop** in `FlagCheckerWorker`, breakdown poll in
`BreakdownPollWorker`) build strings from the app `Context`, which the Compose override
does **not** reach. Add a small helper:

```kotlin
fun Context.stringInLanguage(lang: AppLanguage, resId: Int, vararg args: Any): String {
    val config = Configuration(resources.configuration).apply { setLocale(Locale(lang.tag)) }
    return createConfigurationContext(config).getString(resId, *args)
}
```

Workers resolve the language via `AuthPreferences.getUserId()` →
`LanguagePreferences.languageForUser(userId)` (English if no user is logged in).

## Data flow

1. **App start** → `MainActivity` observes `LanguagePreferences.currentLanguage` (initial = English) → wraps the app in `ProvideLocalizedContext`.
2. **Login** → auth repo calls `loadForUser(userId)` → flow updates → root recomposes in that user's language.
3. **Settings → pick** → `setLanguageForUser(userId, lang)` → flow updates → instant flip.
4. **Logout** → `resetToDefault()` → English. Stored per-user value is retained for next login.

## Scope — screens translated in this pass

**In scope (worker-facing daily flow):**

| Area | Package |
|---|---|
| Login | `presentation/login` |
| Home router | `presentation/home` |
| Dashboard | `presentation/dashboard` |
| Breakdown flag | `presentation/breakdown` |
| Checklist (hub, checklist, breakdown form/list, preventive) | `presentation/checklist` |
| Machine detail | `presentation/machine` |
| Machine list (operator home) | `presentation/machinelist` |
| Transfer (form/records) | `presentation/transfer` |
| Technician (home, PM detail/execute, daily reading, ticket detail) | `presentation/technician` |
| Settings | `presentation/settings` |
| Sync status | `presentation/sync` |
| History | `presentation/history` |
| Shared widgets | `presentation/common/components` |

**Deferred (stay English, follow-up passes):** `admin`, `head`, `qc`, `supervisor`,
`schedule`, `machinemaster`, `user` (Manage Users).

## String extraction

- Add in-scope strings to `res/values/strings.xml` (English) with screen-grouped keys
  (`login_*`, `home_*`, `breakdown_*`, `checklist_*`, `machine_*`, `transfer_*`,
  `technician_*`, `settings_*`, `sync_*`, `history_*`, `common_*`). Reuse the ~30 existing
  keys where they match.
- Replace hardcoded `Text("…")` (and content descriptions, placeholders, dialog labels)
  with `stringResource(R.string.key)` in the in-scope screens only.
- Runtime values use positional args (`%1$s`), matching the existing
  `notification_missed_stop_body`. Use `plurals` where a count drives wording.
- Provide `res/values-hi/strings.xml` + `res/values-mr/strings.xml` (new) with a
  translation for every new key. Brand terms **FactoryOps** and **Candor Foods** are not
  translated. Reuse the HI/MR drafts from the superseded 2026-07-07 spec where keys match.

## Edge cases

- Absent/unknown stored language → English.
- Missing `hi`/`mr` key → Android falls back to `values/` (English) automatically.
- Rotation / dark-mode config change → the locale re-applies (derived from the flow each composition).
- Process death → `SharedPreferences` persists; the login screen shows English until `loadForUser` runs post-login.
- App locale is **in-app only** — it never changes the device locale.

## Out of scope (YAGNI)

- Backend involvement — language is device-local, per-user.
- Translating user-entered / dynamic data (machine names, notes, server error text).
- RTL layout — HI and MR are left-to-right.
- The deferred screens above — later passes using this same pattern.
- Following the **device** system language — first launch / pre-login is always English (per the reset-on-logout decision).

## Testing

- **Unit — `LanguagePreferences`:** per-user `set`/`load`, `resetToDefault` leaves stored
  value intact, `languageForUser` default fallback, persistence across instances (JUnit5 + MockK, matching existing tests).
- **Unit — `AppLanguage`:** `fromTag` mapping incl. null/unknown → `DEFAULT`.
- **Compose UI:** picking a language in Settings flips a representative screen's text (compose-ui-test already on the classpath).
- **Manual:** switch in Settings → worker screens flip; logout → English; re-login → choice restored; rotate → language holds; kill/relaunch + re-login → holds.

## Suggested build order (for the plan)

1. **Infra** — `AppLanguage`, `LanguagePreferences`, `ProvideLocalizedContext`, `MainActivity` wiring, login/logout hooks, Settings picker. *(Mechanism works immediately for any extracted key.)*
2. **Extract + translate** worker screens one at a time: login → home → dashboard → breakdown → checklist → machine → machinelist → transfer → technician → settings → sync → history → common.
3. **Notification localization** (`stringInLanguage` helper + worker wiring).

## Files touched

- `domain/model/AppLanguage.kt` — **new**.
- `data/local/LanguagePreferences.kt` — **new**.
- `presentation/common/ProvideLocalizedContext.kt` — **new**.
- `MainActivity.kt` — inject preference + wrap root.
- `data/repository/AuthRepositoryImpl.kt` — `loadForUser` / `resetToDefault` hooks.
- `presentation/settings/SettingsScreen.kt` + `SettingsViewModel.kt` — language card + handler.
- `res/values/strings.xml` — add in-scope keys (English).
- `res/values-hi/strings.xml`, `res/values-mr/strings.xml` — **new**.
- In-scope `presentation/**` screens — swap hardcoded strings → `stringResource`.
- `sync/FlagCheckerWorker.kt`, `sync/BreakdownPollWorker.kt` (+ helper) — localized notifications.
