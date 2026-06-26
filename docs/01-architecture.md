# Architecture Overview

FactoryOps is an **offline-first Android app** for shop-floor maintenance and production tracking at Candor Foods. It is built in Kotlin on the Jetpack stack with **MVVM + Clean Architecture**: presentation (Compose + ViewModel), domain (pure Kotlin use cases + repository contracts), and data (Room + Retrofit + Mock APIs).

This document is the high-level map. For per-component detail, see the index in [README.md](README.md).

---

## Stack (locked in Phase 1)

| Layer            | Library / version                                         |
|------------------|-----------------------------------------------------------|
| Language         | Kotlin 2.0.21                                             |
| Build            | AGP 8.6.1, KSP 2.0.21-1.0.27, Gradle 8.9                  |
| UI               | Jetpack Compose (BOM 2024.09.03), Material 3              |
| DI               | Hilt 2.52                                                 |
| Networking       | Retrofit 2.11.0                                           |
| Persistence      | Room 2.6.1, DataStore (preferences)                       |
| Background work  | WorkManager 2.9.1 (+ HiltWorker)                          |
| Imaging          | Coil 2.7.0, CameraX 1.3.4                                 |
| Date/time        | kotlinx-datetime 0.6.1                                    |
| Notifications    | FCM (placeholder google-services.json), Local channels    |

Build command: `./gradlew.bat :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

Mock-vs-real API switch: `BuildConfig.USE_MOCK_API` (currently `true` for all phases through 2C-4). Flip to `false` in Phase 3 to wire FastAPI backend.

---

## Module / package layout

```
com.candorfoods.factoryops
├── FactoryOpsApp.kt              # @HiltAndroidApp, WorkManager init
├── MainActivity.kt               # Single-activity host, sets Compose tree
│
├── data/
│   ├── local/                    # Room: AppDatabase (v4), entities, DAOs + DataStore (AuthPreferences)
│   ├── remote/                   # Retrofit APIs, DTOs, Mock implementations + MockSeedData
│   └── repository/               # Repository impls bridging APIs ↔ DAOs ↔ SyncQueue
│
├── di/
│   ├── DatabaseModule.kt         # Room + DAO providers
│   ├── NetworkModule.kt          # Retrofit + Mock/Real API selection
│   └── RepositoryModule.kt       # Repository binding
│
├── domain/
│   ├── model/                    # Pure Kotlin domain types + enums + Result<T>
│   ├── repository/               # Repository interfaces
│   └── usecase/                  # Single-purpose use cases (auth / energy / machine / breakdown / pm / mtmachine / sync)
│
├── navigation/
│   ├── NavGraph.kt               # Compose-NavHost wiring
│   └── Screen.kt                 # Sealed routes
│
├── notification/
│   ├── FcmService.kt             # FirebaseMessagingService
│   └── NotificationHelper.kt     # Channels (BREAKDOWN, SYNC, FLAG, PM_ESCALATION) + show* methods
│
├── presentation/
│   ├── common/                   # Theme (FactoryTheme.colors/Spacing/Type/Theme) + reusable components
│   ├── login/                    # LoginScreen + ViewModel
│   ├── home/                     # HomeRouterScreen (role dispatch) + operator HomeScreen + ViewModel
│   ├── machine/                  # MachineDetailScreen + ViewModel
│   ├── machinemaster/            # Read-only catalog screen + ViewModel + MachineTypeChip
│   ├── history/                  # HistoryScreen + ViewModel
│   ├── breakdown/                # BreakdownFlagScreen (operator raise flag)
│   ├── technician/               # 4-tab home (3 BD tabs + PM tasks), ticket detail, PM detail, PM execute wizard
│   ├── qc/                       # 3-tab mixed BD+PM inbox, BD detail, PM detail, shared checklist sheet
│   ├── supervisor/               # 4-tab home (Templates / Plans / Active WOs / Approvals), template editor, plan editor, PM review
│   ├── sync/                     # Sync status screen + ViewModel
│   └── settings/                 # Settings screen + ViewModel (logout)
│
├── sync/                         # WorkManager workers
│   ├── SyncWorker.kt             # Drains SyncQueueEntity → backend
│   ├── BreakdownPollWorker.kt    # Polls latest breakdown state
│   ├── FlagCheckerWorker.kt      # Operator-side flag check
│   └── PmSchedulerWorker.kt      # Generates PM WOs + computes escalation tier + fires notifications
│
└── util/                         # Constants, DateTimeFormatter, NumberFormatter (Indian locale),
                                  # Extensions, BreakdownPhotoStorage (+ PmPhotoStorage)
```

---

## Clean-architecture data flow

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Compose UI (Screens)                          │
│            collectAsState() ── action() ───────────────┐               │
└────────────────────────────────────────────────────────┼───────────────┘
                          │                              ▼
                          │                ┌──────────────────────────┐
                          │  StateFlow<UiState>           │ ViewModel │
                          └──────────────────────────────┤            │
                                                         └──────┬─────┘
                                                                │ injects
                                                                ▼
                                                    ┌──────────────────────┐
                                                    │  UseCase (suspend)   │  ← pure Kotlin
                                                    └──────────┬───────────┘
                                                               │
                                                               ▼
                                              ┌────────────────────────────────┐
                                              │  Repository (interface)        │  ← domain
                                              │  RepositoryImpl                │  ← data
                                              └──────────┬────────────┬────────┘
                                                         │            │
                                              ┌──────────▼──┐  ┌──────▼──────────┐
                                              │  DAO        │  │  Retrofit Api    │
                                              │  (Room)     │  │  (Mock or Real)  │
                                              └─────────────┘  └──────────────────┘
                                                       │
                                                       ▼
                                              ┌────────────────────┐
                                              │  SyncQueueEntity   │ ──► drained by SyncWorker
                                              └────────────────────┘
```

---

## Offline-first contract

1. **Room is the single source of truth.** Every UI flow observes Room via `Flow<T>` and renders Compose state from it. Network responses funnel through DAOs first, never directly into the UI.
2. **Every write goes to Room first**, then a row is appended to `SyncQueueEntity` with an `ActionType` describing the operation (e.g. `BREAKDOWN_RAISE`, `PM_SUPERVISOR_APPROVE`, `PM_QC_DISAPPROVE`). `SyncWorker` drains the queue against the backend with retry + backoff.
3. **Every record carries `sync_status`** so failed sync can be retried without losing local user actions.
4. **Photos are saved to internal app storage first** (`BreakdownPhotoStorage`, `PmPhotoStorage`) with a stable local path, and the path is what gets serialized in the sync payload.

---

## Roles & navigation

`HomeRouterScreen` is the post-login switchboard. It inspects the authenticated user's `UserRole` and pushes the role-specific home:

| Role              | Home screen                  | Lives in                                              |
|-------------------|------------------------------|-------------------------------------------------------|
| `OPERATOR`        | `HomeScreen`                 | `presentation/home/`                                  |
| `TECHNICIAN`      | `TechnicianHomeScreen`       | `presentation/technician/` (4 tabs incl. PM tasks)    |
| `QC_HEAD` / `QC_MEMBER` | `QcHomeScreen`         | `presentation/qc/` (3 tabs, mixed BD + PM inbox)      |
| `SUPERVISOR`      | `SupervisorHomeScreen`       | `presentation/supervisor/` (4 tabs incl. Approvals)   |

Routes are declared as sealed objects in [Screen.kt](../app/src/main/java/com/candorfoods/factoryops/navigation/Screen.kt) and wired in [NavGraph.kt](../app/src/main/java/com/candorfoods/factoryops/navigation/NavGraph.kt).

---

## Background workers (WorkManager)

| Worker                  | Cadence                              | Purpose                                                                                                  |
|-------------------------|--------------------------------------|----------------------------------------------------------------------------------------------------------|
| `SyncWorker`            | Periodic + on-network constraint     | Drains `SyncQueueEntity` to backend (Phase 3 will wire real endpoints; today no-op for non-mock actions). |
| `BreakdownPollWorker`   | Periodic                             | Pulls latest breakdown state from backend so the operator sees the canonical status.                     |
| `FlagCheckerWorker`     | Periodic                             | Operator-side check for new flags raised against machines they're assigned to.                           |
| `PmSchedulerWorker`     | **Periodic 60 min + one-time on launch** | Walks active PM plans → generates `PmWorkOrder` rows; tier-walks all non-closed WOs and fires escalation notifications on tier increase. |

`PmSchedulerWorker` is the heart of the PM cascade (Phase 2C-1 + 2C-4):
- Plan-walk: compute `next_due_at`, generate `PmWorkOrder` (status `NOTIFIED`) when due within 7-day lead and no open WO exists for the plan.
- Tier-walk: `ComputePmEscalationTierUseCase(scheduledDate, now) → 0..4` using thresholds `T1=1d / T2=4d / T3=7d / T4=14d` overdue. If tier increased, persist via `woRepo.bumpTier(...)` and fire `NotificationHelper.showPmEscalationNotification(...)`. Tier resets to 0 silently on `acknowledge` / `start` / `submit` (no re-notify).

---

## Phase trail

| Phase | Title                                | Status | Notes                                                                  |
|-------|--------------------------------------|--------|------------------------------------------------------------------------|
| 1     | Power Consumption                    | ✅     | Scaffold + mock APIs + Room v1.                                        |
| 2A    | QC Verification                      | ✅     | 3-tab QC dashboard + checklist; mock seed users for QC_HEAD/QC_MEMBER. |
| 2B    | UI Polish                            | 🟡     | Theme tokens, PrimaryButton/FactoryCard/SyncIndicator landed; step (i) skeletons/empty/haptics next. |
| 2C-1  | PM setup (templates/plans/scheduler) | ✅     | DB v4, 6 new entities, `PmSchedulerWorker`, supervisor template+plan editors. |
| 2C-2  | PM execution + supervisor approval   | ✅     | Tech 4th tab + multi-step wizard; supervisor 4th tab + review screen.  |
| 2C-3  | PM QC gate (mixed inbox)             | ✅     | QC inbox merges BD + PM with leading chip; PM QC bounces to supervisor.|
| 2C-4  | PM escalation cascade                | ✅     | Tier-walk in scheduler + tier-chip on PM card + JIT POST_NOTIFICATIONS.|
| 3     | Real Backend (FastAPI + PG)          | ⏸     | Greenfield; flip `USE_MOCK_API` when wired.                            |

See [PROGRESS.md](../PROGRESS.md) for the canonical phase tracker.

---

## Architectural invariants

These must not be violated without explicit user approval:

1. **Offline-first.** Room is canonical. All writes hit Room first, then `SyncQueueEntity`.
2. **Stack is locked.** No Java sources, no XML Views, no RxJava, no DI other than Hilt.
3. **Indian locale formatting.** `Rs.1,23,456` grouping (lakhs/crores), `dd MMM yyyy` dates, 24-hour time. See [NumberFormatter](../app/src/main/java/com/candorfoods/factoryops/util/NumberFormatter.kt) and [DateTimeFormatter](../app/src/main/java/com/candorfoods/factoryops/util/DateTimeFormatter.kt).
4. **Factory-floor UX.** Large tap targets (`TouchTarget.primary=64dp`, `TouchTarget.hero=88dp`), high-contrast Material 3, semantic colors (green=running, red=stop, amber=flag), readable in dim/dusty environments.
5. **Approval-gated build.** Build screen-by-screen; report build status; wait for confirmation before proceeding to the next screen.

---

## Identity vs. namespace

| What                          | Value                                                                  |
|-------------------------------|------------------------------------------------------------------------|
| Kotlin namespace              | `com.candorfoods.factoryops` (folder layout matches)                   |
| `applicationId`               | `in.candorfoods.factoryops` (Play / Firebase identity preserved)       |
| `google-services.json` package| matches `applicationId` (`in.candorfoods.factoryops`), not namespace   |

The original `in.candorfoods.factoryops` Kotlin namespace was rejected during Phase 1 first build because `in` is a Kotlin keyword. The split above resolved it without breaking Firebase identity.
