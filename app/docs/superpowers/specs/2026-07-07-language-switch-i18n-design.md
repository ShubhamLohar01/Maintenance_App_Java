# Language Switch (i18n) — Design Spec

> **⚠️ SUPERSEDED (2026-07-08)** by
> [`2026-07-08-multilanguage-support-design.md`](2026-07-08-multilanguage-support-design.md).
> A re-brainstorm changed three material decisions: **per-user (reset on logout)**
> persistence instead of device-local, a **Compose `CompositionLocal` locale override**
> instead of AppCompat `setApplicationLocales`, and a **broad worker-facing scope**
> (incl. technician screens) instead of the 3-screen pilot. The draft translations
> below are still useful as source material. Kept for history — do not implement from this file.

**Date:** 2026-07-07
**Status:** Superseded by 2026-07-08 spec (see banner above)
**Scope:** Pilot slice — infrastructure + a core set of screens localized end-to-end in English / Hindi / Marathi.

## Goal

Let any user, in any role, switch the app UI language between **English**, **हिंदी (Hindi)**, and **मराठी (Marathi)** from Settings. The choice is a device-local preference; it does not touch the backend and does not translate user-entered data (machine names, notes, etc.).

## Why this is phased

The app is currently **not internationalized**: `stringResource(...)` is used nowhere, `res/values/strings.xml` holds ~30 strings (mostly the login screen), and there are 264+ hardcoded `Text("…")` strings across 39 screens. A working language switch therefore has two parts of very different size:

1. **Switch mechanism** (small) — the toggle, persistence, and per-app locale.
2. **String extraction + translation** (large) — every hardcoded string must be pulled into resources and translated before it will ever switch.

This spec delivers the full mechanism plus a **pilot slice** of screens translated end-to-end. It establishes the repeatable pattern; remaining screens are extracted in follow-up passes.

## Mechanism — AndroidX per-app locale

- **Dependency:** add `androidx.appcompat:appcompat` (not currently on the classpath — the app is pure-Compose on `ComponentActivity`). Required because `AppCompatDelegate.setApplicationLocales` backports per-app locales to `minSdk 24`; the framework `LocaleManager` alone is API 33+.
- **Switching:** `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi"))`. Android re-resolves resources and recreates the current activity — no manual `attachBaseContext`/`recreate` plumbing.
- **Current value (source of truth):** `AppCompatDelegate.getApplicationLocales()`. No separate prefs table.
- **`res/xml/locales_config.xml`** lists `en`, `hi`, `mr`; referenced from `<application android:localeConfig="@xml/locales_config">` in `AndroidManifest.xml`. This also surfaces the choice in Android 13+ **system** per-app language settings.
- **Pre-Android-13 persistence:** declare the appcompat auto-storage service in the manifest:
  ```xml
  <service
      android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
      android:enabled="false"
      android:exported="false">
      <meta-data
          android:name="autoStoreLocales"
          android:value="true" />
  </service>
  ```
- **MainActivity/Compose:** unchanged. Stays a `ComponentActivity`. A tiny `LocaleManager` helper wraps the `setApplicationLocales` call and language-tag ↔ label mapping.

## String resources

- `res/values/strings.xml` — English, the default and fallback for any untranslated key.
- `res/values-hi/strings.xml` — Hindi (drafted below).
- `res/values-mr/strings.xml` — Marathi (drafted below).
- Pilot screens convert hardcoded `Text("…")` → `stringResource(R.string.…)`. Runtime values use positional args (`%1$s`), matching the existing `notification_missed_stop_body`.
- Brand terms **FactoryOps** and **Candor Foods** are not translated.

## Settings control

A new **"Language / भाषा"** card in `SettingsScreen`, placed above Logout:

- Three options, each always rendered in its own script — **English · हिंदी · मराठी** — so they're recognizable regardless of the active language.
- Single-select (radio rows or a segmented control); the current language is checked.
- Tap → `SettingsViewModel` → `LocaleManager.setLanguage(tag)` → `AppCompatDelegate.setApplicationLocales(...)`.
- Available to every role because Settings is shared across roles.

## First launch / defaults

No app locale is set initially, so Android follows the **device** language; anything other than `hi`/`mr` falls back to English (`values/`). A device already in Hindi shows Hindi on the translated screens immediately.

## Pilot slice — translated end-to-end

| Screen | File |
|---|---|
| Login | `presentation/login/LoginScreen.kt` |
| Settings (+ the new switcher) | `presentation/settings/SettingsScreen.kt` |
| Operator home (machine list) | `presentation/machinelist/MachineListScreen.kt` |
| Shared actions/labels | across the above |

**In scope:** all static UI labels on those screens.
**Deferred (follow-up):** strings derived from enum `.label` properties and runtime formatting on the machine list — `MachineStatusFilter.label`, `MachineLiveStatus.label`, `"● SHUT DOWN"`, `"by <name>"`, `"Running <h>h <m>m"`, `"just now"`, group headers. These resolve outside `@Composable` scope and need a `Context`/refactor; they stay English in the pilot and are called out here so the gap is explicit.

## Out of scope (YAGNI)

- Backend involvement — language is device-local.
- Translating user-entered/dynamic data (machine names, notes, server error text).
- RTL layout work — Hindi and Marathi are left-to-right.
- The other 35 screens — later extraction passes using this same pattern.

## Draft translations for review

> **These are my drafts — please review, especially the factory/domain terms.** Marked keys are new; existing keys are already in `values/strings.xml`.

### Login
| key | English | हिंदी (draft) | मराठी (draft) |
|---|---|---|---|
| `login_subtitle` | Candor Foods | Candor Foods | Candor Foods |
| `label_username` | Username | उपयोगकर्ता नाम | वापरकर्तानाव |
| `label_password` | Password | पासवर्ड | पासवर्ड |
| `action_login` | Login | लॉग इन करें | लॉग इन करा |
| `cd_show_password` | Show password | पासवर्ड दिखाएँ | पासवर्ड दाखवा |
| `cd_hide_password` | Hide password | पासवर्ड छिपाएँ | पासवर्ड लपवा |

### Common actions
| key | English | हिंदी (draft) | मराठी (draft) |
|---|---|---|---|
| `action_logout` | Logout | लॉग आउट करें | लॉग आउट करा |
| `action_retry` | Retry | पुनः प्रयास करें | पुन्हा प्रयत्न करा |
| `action_back` | Back | पीछे | मागे |

### Settings
| key | English | हिंदी (draft) | मराठी (draft) |
|---|---|---|---|
| `title_settings` | Settings | सेटिंग्स | सेटिंग्ज |
| `settings_role` | Role | भूमिका | भूमिका |
| `settings_plant` | Plant | प्लांट | प्लांट |
| `settings_shift_schedule` | Shift Schedule | शिफ्ट अनुसूची | शिफ्ट वेळापत्रक |
| `settings_server` | Server | सर्वर | सर्व्हर |
| `settings_language` | Language | भाषा | भाषा |
| `logout_confirm_message` | Any unsynced data will be lost. Are you sure? | कोई भी अनसिंक डेटा खो जाएगा। क्या आप निश्चित हैं? | कोणताही सिंक न झालेला डेटा नष्ट होईल. तुम्हाला खात्री आहे का? |
| `language_english` | English | English | English |
| `language_hindi` | हिंदी | हिंदी | हिंदी |
| `language_marathi` | मराठी | मराठी | मराठी |

### Operator home (machine list)
| key | English | हिंदी (draft) | मराठी (draft) |
|---|---|---|---|
| `title_machines` | Machines | मशीनें | मशीन |
| `cd_refresh` | Refresh | रिफ्रेश करें | रिफ्रेश करा |
| `cd_filter_by_status` | Filter by status | स्थिति के अनुसार फ़िल्टर करें | स्थितीनुसार फिल्टर करा |
| `cd_add_machine` | Add new machine | नई मशीन जोड़ें | नवीन मशीन जोडा |
| `cd_clear_search` | Clear search | खोज साफ़ करें | शोध साफ करा |
| `machine_master` | Machine Master | मशीन मास्टर | मशीन मास्टर |
| `search_machines` | Search machines… | मशीनें खोजें… | मशीन शोधा… |
| `filter_all` | All | सभी | सर्व |
| `warehouse_both` | Both | दोनों | दोन्ही |
| `no_machines_match` | No machines match your filters. | आपके फ़िल्टर से कोई मशीन मेल नहीं खाती। | तुमच्या फिल्टरशी जुळणारी कोणतीही मशीन नाही. |
| `failed_load_machines` | Failed to load machines | मशीनें लोड करने में विफल | मशीन लोड करण्यात अयशस्वी |

## Files touched (pilot)

- `app/build.gradle.kts` — add `androidx.appcompat:appcompat`.
- `gradle/libs.versions.toml` — appcompat version + library alias.
- `AndroidManifest.xml` — `android:localeConfig` + auto-store service.
- `res/xml/locales_config.xml` — **new**, lists en/hi/mr.
- `res/values/strings.xml` — add pilot keys (English).
- `res/values-hi/strings.xml`, `res/values-mr/strings.xml` — **new**.
- `presentation/common/.../LocaleManager.kt` — **new**, thin wrapper + tag/label mapping.
- `presentation/settings/SettingsScreen.kt` + `SettingsViewModel.kt` — language card + handler.
- `presentation/login/LoginScreen.kt` — swap to `stringResource`.
- `presentation/machinelist/MachineListScreen.kt` — swap static labels to `stringResource`.

## Verification

- Switch to each language from Settings → the three pilot screens re-render in that language; unlisted screens stay English (expected).
- Kill and relaunch the app → language persists.
- Set the device system language to Hindi with the app never opened → pilot screens open in Hindi.
- Android 13+ → app appears with its language choice under Settings → Apps → FactoryOps → Language.

## Follow-up passes (not this spec)

Extract + translate remaining screens role-by-role (Technician, Supervisor, QC, Head, Admin) using the pattern established here, plus the deferred enum/runtime strings on the machine list.
