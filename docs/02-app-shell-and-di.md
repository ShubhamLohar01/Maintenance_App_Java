# App Shell, Dependency Injection & Navigation

This document describes the FactoryOps Android app's bootstrap layer: the `Application` subclass that wires up Hilt and WorkManager, the single Compose-hosting `Activity`, the three Hilt modules that provide the database, network, and repository graphs, and the Compose-based navigation graph that routes users through role-specific home screens and feature flows. Together these components form the shell that hosts every feature module in the codebase. The app is currently in a mock-only stage (`BuildConfig.USE_MOCK_API=true`), so all API bindings resolve to in-memory `Mock*Api` implementations rather than Retrofit-generated proxies.

## FactoryOpsApp

**File:** [FactoryOpsApp.kt](../app/src/main/java/com/candorfoods/factoryops/FactoryOpsApp.kt)

**Responsibility:** Application entry point that initializes Hilt and schedules all background WorkManager jobs at process start.

- Annotated `@HiltAndroidApp` — root of the generated Hilt component tree.
- Implements `Configuration.Provider` and injects `HiltWorkerFactory` so WorkManager can construct `@HiltWorker` workers with dependencies.
- `workManagerConfiguration` builds a `Configuration` with the injected `workerFactory`; this enables Hilt-aware worker instantiation across the app.
- `onCreate()` schedules four background workers immediately on launch:
  - `SyncWorker.schedulePeriodic(workManager)` — periodic offline-queue flush.
  - `FlagCheckerWorker.schedulePeriodic(workManager)` — periodic flag/SLA checks.
  - `BreakdownPollWorker.enqueueImmediately(this)` — one-shot breakdown polling at startup.
  - `PmSchedulerWorker.schedulePeriodic(workManager)` plus `PmSchedulerWorker.enqueueImmediately(this)` — both the recurring PM scheduler and an immediate first-run kick (Phase 2C PM escalation cascade).

## MainActivity

**File:** [MainActivity.kt](../app/src/main/java/com/candorfoods/factoryops/MainActivity.kt)

**Responsibility:** The single `ComponentActivity` that hosts the entire Compose UI tree.

- Annotated `@AndroidEntryPoint` so injected ViewModels and dependencies resolve through Hilt.
- Calls `enableEdgeToEdge()` for modern edge-to-edge insets.
- `setContent { ... }` mounts `FactoryOpsTheme` (Material3 theme wrapper) and delegates all rendering and routing to `AppNavGraph()`. There are no other activities — navigation is entirely Compose-internal.

## DatabaseModule

**File:** [DatabaseModule.kt](../app/src/main/java/com/candorfoods/factoryops/di/DatabaseModule.kt)

**Responsibility:** Hilt module that provides the singleton Room `AppDatabase`.

- `@Module @InstallIn(SingletonComponent::class)` — application-scoped bindings.
- `provideDatabase(@ApplicationContext context)` builds the Room database with name `Constants.DB_NAME` and uses `fallbackToDestructiveMigration()` — appropriate for the mock-only stage, but should be replaced with real migrations before production.
- The provided `AppDatabase` is a `@Singleton`, so all DAOs derived from it share a single instance.

## NetworkModule

**File:** [NetworkModule.kt](../app/src/main/java/com/candorfoods/factoryops/di/NetworkModule.kt)

**Responsibility:** Provides Retrofit infrastructure and selects between mock and real API implementations based on `BuildConfig.USE_MOCK_API`.

- Configures a shared `kotlinx.serialization` `Json` with `ignoreUnknownKeys = true` and `isLenient = true`.
- `provideRetrofit()` builds an `OkHttpClient` whose `HttpLoggingInterceptor` logs at `BODY` level in debug builds and `NONE` otherwise. Retrofit uses `BuildConfig.BASE_URL` and the kotlinx-serialization converter (`application/json`).
- Each API binding follows the same pattern: if `BuildConfig.USE_MOCK_API` is true, the provider returns the corresponding `Mock*Api` class; otherwise it returns `retrofit.create(...)`. Currently the entire app runs against mocks.
- APIs provided (all `@Singleton`):
  - `provideAuthApi` -> `AuthApi` / `MockAuthApi`
  - `provideMachineApi` -> `MachineApi` / `MockMachineApi`
  - `provideEnergyApi` -> `EnergyApi` / `MockEnergyApi`
  - `provideBreakdownApi` -> `BreakdownApi` / `MockBreakdownApi`
  - `provideMtMachineApi` -> `MtMachineApi` / `MockMtMachineApi`
- Note: `provideRetrofit()` is still invoked under mock mode (it just isn't used by the API providers), so misconfigured `BASE_URL` will not crash the mock build.

## RepositoryModule

**File:** [RepositoryModule.kt](../app/src/main/java/com/candorfoods/factoryops/di/RepositoryModule.kt)

**Responsibility:** Hilt module that binds each domain-layer repository interface to its data-layer implementation.

- `abstract class` using `@Binds` (cheaper than `@Provides` for simple interface->impl wiring).
- `@InstallIn(SingletonComponent::class)` with every binding marked `@Singleton` — one repository instance per process.
- Bindings:
  - `AuthRepository` <- `AuthRepositoryImpl`
  - `MachineRepository` <- `MachineRepositoryImpl`
  - `MtMachineRepository` <- `MtMachineRepositoryImpl`
  - `EnergyRepository` <- `EnergyRepositoryImpl`
  - `BreakdownRepository` <- `BreakdownRepositoryImpl`
  - `PmTemplateRepository` <- `PmTemplateRepositoryImpl`
  - `PmPlanRepository` <- `PmPlanRepositoryImpl`
  - `PmWorkOrderRepository` <- `PmWorkOrderRepositoryImpl`
- This is the seam that enforces the Clean Architecture rule that the domain layer depends only on interfaces, never on `data/` implementations.

## AppNavGraph

**File:** [NavGraph.kt](../app/src/main/java/com/candorfoods/factoryops/navigation/NavGraph.kt)

**Responsibility:** Single Compose `NavHost` declaring every route in the app and wiring per-screen lambdas for navigation.

- Resolves the start destination by collecting `LoginViewModel.isLoggedIn` (via `collectAsStateWithLifecycle`): logged-in users start at `Screen.Home`, otherwise `Screen.Login`.
- Routes and their behavior:
  - **`Login`** — on success navigates to `Home` and pops `Login` inclusive.
  - **`Home`** (router) — hosts `HomeRouterScreen`; on `onResolved(role)` it switches to one of `OperatorHome`, `TechnicianHome`, `QcHome` (for both `QC_HEAD` and `QC_MEMBER`), or `SupervisorHome`, popping `Home` inclusive. `onGiveUp` returns to `Login` and clears the back stack (`popUpTo(0)`).
  - **`OperatorHome`** — `HomeScreen` with navigation callbacks to machine detail, sync status, settings, and machine master.
  - **`TechnicianHome`** — `TechnicianHomeScreen` with callbacks for ticket detail, PM detail, settings, and machine master.
  - **`QcHome`** — `QcHomeScreen`; inbox rows of type `QcInboxRow.Breakdown` route to `QcTicketDetail`, `QcInboxRow.Pm` to `QcPmDetail`.
  - **`SupervisorHome`** — `SupervisorHomeScreen` with callbacks for create/edit template, create/edit plan, review PM, and settings.
- Argument-bearing routes use `navArgument(...)` with `NavType.StringType`. The two supervisor editors use query-style routes (`?id={...}`) marked `nullable = true; defaultValue = null` so they support both create-new and edit-existing modes.
- Detail routes and their flows:
  - `TicketDetail` (`ticket/{ticketId}`) — back pops the stack.
  - `TechnicianPmDetail` (`tech/pm/{woId}`) — `onLaunchExecution(woId)` navigates to `TechnicianPmExecute`.
  - `TechnicianPmExecute` (`tech/pm/{woId}/execute`) — `onSubmitted` pops back to `TechnicianHome` (non-inclusive) so the technician lands on their inbox.
  - `SupervisorPmReview` (`supervisor/pm/{woId}`) — `onDecided` pops back.
  - `QcTicketDetail`, `QcPmDetail` — simple back-pop screens.
  - `MachineDetail` (`machine/{machineId}`) — provides further navigation to `History.createRoute(id)` and `BreakdownFlag.createRoute(id)`.
  - `History`, `BreakdownFlag` — leaf screens with back-pop only.
  - `SyncStatus`, `MachineMaster` — chromeless utility screens with back-pop.
  - `Settings` — `onLogout` navigates to `Login` and clears the entire back stack (`popUpTo(0) { inclusive = true }`).
- All ViewModels referenced in this graph are obtained via `hiltViewModel()` inside their respective screens, except `LoginViewModel`, which is hoisted at the `AppNavGraph` level to drive start-destination selection.

## Screen

**File:** [Screen.kt](../app/src/main/java/com/candorfoods/factoryops/navigation/Screen.kt)

**Responsibility:** Sealed class enumerating every navigation route as a type-safe object, including argument-name constants and `createRoute(...)` builders.

- Flat (no-argument) routes: `Login` (`login`), `Home` (`home` — role router that replaces itself), `OperatorHome` (`home/operator`), `TechnicianHome` (`home/technician`), `QcHome` (`home/qc`), `SupervisorHome` (`home/supervisor`), `SyncStatus` (`sync`), `Settings` (`settings`), `MachineMaster` (`machine_master`).
- Path-parameter routes (each exposes `ARG` and `createRoute(...)`):
  - `MachineDetail` — `machine/{machineId}`
  - `History` — `history/{machineId}`
  - `BreakdownFlag` — `breakdown/{machineId}`
  - `TicketDetail` — `ticket/{ticketId}`
  - `QcTicketDetail` — `qc/ticket/{ticketId}`
  - `QcPmDetail` — `qc/pm/{woId}`
  - `TechnicianPmDetail` (Phase 2C-2) — `tech/pm/{woId}`
  - `TechnicianPmExecute` (Phase 2C-2) — `tech/pm/{woId}/execute`
  - `SupervisorPmReview` (Phase 2C-2) — `supervisor/pm/{woId}`
- Query-parameter routes for editors that support create-or-edit:
  - `SupervisorTemplateEditor` — `supervisor/template?id={templateId}`; `createRoute(null)` emits `supervisor/template?id=` (empty value, treated as "create new").
  - `SupervisorPlanEditor` — `supervisor/plan?id={planId}`; same nullable pattern.
- The `ARG` constants are consumed by ViewModels via `SavedStateHandle` to read route parameters in a type-safe way.
