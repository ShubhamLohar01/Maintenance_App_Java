# FactoryOps — Component Documentation

This folder is the **component-by-component reference** for the FactoryOps Android codebase. Every Kotlin file under `app/src/main/` is described in one of the docs below, grouped by architectural layer.

For **phase status, decisions, test plans, and what-to-do-next**, see [PROGRESS.md](../PROGRESS.md) in the project root — that file is the canonical project tracker and is updated after every milestone. This `docs/` folder describes **how the code is structured today**.

---

## Index

| # | Doc | Covers |
|---|---|---|
| 01 | [Architecture Overview](01-architecture.md) | Stack, package layout, data-flow diagram, offline-first contract, role-based routing, WorkManager cadence, invariants. |
| 02 | [App Shell, DI & Navigation](02-app-shell-and-di.md) | `FactoryOpsApp`, `MainActivity`, Hilt modules (`DatabaseModule`, `NetworkModule`, `RepositoryModule`), `NavGraph`, `Screen` routes. |
| 03 | [Domain Layer](03-domain-layer.md) | Pure-Kotlin models, enums, `Result<T>`, repository interfaces, use cases (auth / energy / machine / breakdown / pm / mtmachine / sync). |
| 04 | [Data — Local (Room + DataStore)](04-data-local.md) | `AppDatabase` (v4), `AuthPreferences`, all entities + DAOs. |
| 05 | [Data — Remote (Retrofit + Mocks)](05-data-remote.md) | API interfaces, DTOs, mock implementations, `MockSeedData`. |
| 06 | [Data — Repository Implementations](06-data-repositories.md) | How repos bridge APIs ↔ DAOs ↔ `SyncQueueEntity`, plus cross-entity state flips (machine status on QC approve, tier reset on tech actions). |
| 07 | [Sync Workers & Notifications](07-sync-and-notifications.md) | `SyncWorker`, `BreakdownPollWorker`, `FlagCheckerWorker`, `PmSchedulerWorker`, `FcmService`, `NotificationHelper` channels + tier-driven cascade. |
| 08 | [Presentation — Theme & Common Components](08-presentation-common.md) | `FactoryTheme.colors/Spacing/Type`, `PrimaryButton`, `FactoryCard`, `StatusBadge`, `SyncIndicator`, `MachineCard`, dialogs, skeletons, empty states. |
| 09 | [Presentation — Auth, Home, Machine, History, Machine Master](09-presentation-auth-home-machine.md) | `LoginScreen`, `HomeRouterScreen` (role dispatch), operator `HomeScreen`, `MachineDetailScreen`, `HistoryScreen`, `MachineMasterScreen`. |
| 10 | [Presentation — Technician](10-presentation-technician.md) | 4-tab `TechnicianHomeScreen`, breakdown ticket flow, PM detail, multi-step PM execute wizard, `PmTaskCard` with tier chip. |
| 11 | [Presentation — QC](11-presentation-qc.md) | Mixed BD+PM inbox (`QcInboxRow`), `QcHomeScreen` 3 tabs, breakdown detail, PM detail (QC-side), shared `QcChecklistSheet`. |
| 12 | [Presentation — Supervisor](12-presentation-supervisor.md) | 4-tab `SupervisorHomeScreen`, template editor, plan editor, `SupervisorPmReviewScreen` with QC-bounceback banner. |
| 13 | [Presentation — Breakdown Flag, Sync Status, Settings](13-presentation-breakdown-sync-settings.md) | Operator-side flag entry, pending-sync inspector, settings + logout. |
| 14 | [Utilities](14-utilities.md) | `Constants`, `DateTimeFormatter`, `NumberFormatter` (Indian grouping), `Extensions`, `BreakdownPhotoStorage` + `PmPhotoStorage`. |

---

## How to use this folder

- **Looking for a class?** Start with the index above, jump to the section, then click the source-file link inside the section.
- **Onboarding a new contributor?** Read `01-architecture.md` end-to-end, then skim the layer docs (`03` → `06` → `07` → `08`) before diving into any feature screen.
- **Touching a screen?** Open the matching `presentation/` doc (09–13) for the screen's state shape, actions, and which repo methods it calls.
- **Adding a new entity?** `04-data-local.md` shows the DAO conventions (atomic `@Query UPDATE` mark-methods, Flow observers, `suggestSpareNames`-style aggregate queries).
- **Adding a new sync action?** `06-data-repositories.md` documents the write pattern (Room first, then `SyncQueueEntity` with `ActionType`, then cross-entity flips).

---

## Keeping these docs current

These docs describe code state — they should be updated when:

- A new screen/ViewModel is added (extend the relevant `presentation/` doc).
- A new repository, DAO, entity, or use case lands (extend `03` / `04` / `06`).
- A new WorkManager job or notification channel is added (extend `07`).
- A new theme token, common component, or design rule is introduced (extend `08`).
- The architecture changes (e.g. real backend wired in Phase 3 — update `01` and `05`).

For phase milestones, update [PROGRESS.md](../PROGRESS.md) at the project root, not this folder.
