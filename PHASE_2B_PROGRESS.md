# Phase 2B — UI Polish Progress

Tracking the visual/UX polish pass on FactoryOps. Phase 2A (functional) is
complete; this phase is purely cosmetic + ergonomic refinement. **No new
features, no nav changes, no data/API edits.**

---

## Scope decisions agreed with user

- **"Medium polish, not too much."** Drop the most elaborate animations from the
  original spec.
- **Stack is Kotlin / Jetpack Compose / Material 3** (the original spec
  referenced React Native — ignore that wording).
- **Fonts**: using **system fonts** (`FontFamily.SansSerif` /
  `FontFamily.Monospace`) for now. Bundling Plus Jakarta Sans / Inter /
  JetBrains Mono `.ttf` files is deferred — `FactoryFonts` in `Type.kt` is the
  single swap point if we revisit.
- **Animations dropped**: chip pulse on critical statuses, sync-indicator
  shake-every-5s, kWh/cost number-spring transitions.
- **Animations kept**: smooth color tween on status changes, button press
  feedback, screen transitions, fade-in for empty states.
- **Empty states**: icon + text + fade-in. **No SVG illustrations.**

## Open questions — answer before continuing

These came up during alignment and were not resolved before the user paused:

1. **Paper-equivalent question doesn't apply** (Compose, not RN). Skip.
2. **Dark-mode trigger** — system-only for now? Or add a user toggle? (Currently
   driven by `isSystemInDarkTheme()` in `Theme.kt`.)
3. **Haptics** — primary actions only (Start/Stop/Approve/Disapprove/Flag), or
   every press? Default plan: primary only.
4. **Critical-chip pulse list** — moot; pulse animation dropped.
5. **Numeric spring** — moot; animation dropped.
6. **Production Start/Stop confirm dialog** — keep `AlertDialog` (current
   `ConfirmDialog.kt`) or upgrade to a `ModalBottomSheet`? Default plan: stick
   with AlertDialog for medium scope.
7. **Empty-state style** — moot; dropped illustrations.
8. **Test device** — user has not specified; I'll target a Pixel 4a-equivalent
   emulator until told otherwise.
9. **i18n stub** — defer; English-only strings stay inline for this phase.
10. **Splash / font preload** — moot; system fonts need no preload.

---

## Plan (6 steps, approval-gated)

| # | Step                                                         | Status      |
|---|--------------------------------------------------------------|-------------|
| a | Theme foundations (Color + Spacing + Type + Theme)           | **DONE** ✅ |
| b | Bundle real fonts (Plus Jakarta / Inter / JetBrains Mono)    | **SKIPPED** (using system fonts; revisit if user wants) |
| c | Polish core components (Button, StatusBadge, Card, SyncIndicator, Snackbar) | pending |
| d | Sync indicator + Snackbar polish                             | folded into step c |
| e | Compose nav screen transitions (slide-from-right / bottom)   | pending |
| f | Operator path: Login → Home → MachineDetail → History        | pending |
| g | Technician inbox + ticket detail                             | pending |
| h | QC inbox + checklist sheet                                   | pending |
| i | Final pass: skeletons, empty states, haptic feedback         | pending |

User wants a build + test checkpoint after each major component.

---

## Step (a) — Theme foundations — ✅ DONE

### Files changed

```
app/src/main/java/com/candorfoods/factoryops/presentation/common/theme/
├── Color.kt        (rewritten)
├── Spacing.kt      (new)
├── Type.kt         (rewritten)
└── Theme.kt        (rewritten)
```

### What's available now

**Colors** (`Color.kt`)
- Raw tokens: `BrandTeal`, `BrandTealDark`, `SuccessLight/Dark`,
  `WarningLight/Dark`, `DangerLight/Dark`, `InfoLight/Dark`,
  `SurfaceLight/Dark`, `SurfaceVarLight/Dark`, `OnSurfaceLight/Dark`,
  `OnSurfaceMutedL/D`, `BorderLight/Dark`.
- Theme-aware semantic accessor: **`FactoryTheme.colors.<token>`** —
  `statusRunning`, `statusIdle`, `statusStopped`, `statusFlagged`,
  `statusPendingQc`, `syncOk`, `syncPending`, `syncFailed`, `info`,
  `onSurfaceMuted`, `border`.
- **Legacy aliases kept**: `StatusRunning`, `StatusIdle`, `StatusStopped`,
  `StatusFlagged`, `SyncOk`, `SyncPending`, `SyncFailed`, plus `Green80/40`,
  `Amber80/40`, `Red80/40`, `Blue80/40`, `Gray80/40`. These point at
  light-theme values so existing files compile unchanged. **Migrate
  callers to `FactoryTheme.colors.*` as you touch each screen** so dark
  mode works correctly.

**Spacing** (`Spacing.kt` — new)
- `Spacing.xs (4)`, `sm (8)`, `md (12)`, `base (16)`, `lg (24)`, `xl (32)`,
  `xxl (48)`, `xxxl (64)`.
- `TouchTarget.min (56)`, `primary (64)`, `hero (88)`,
  `chip (28)`, `chipLarge (64)`.

**Typography** (`Type.kt`)
- Full M3 slot scale per spec. `bodyLarge` bumped 16→17sp.
- Extras: `FactoryTypeScale.extras.displayHero` (44/52),
  `monoLarge` (24/32, mono bold), `monoMedium` (14/22, mono).
- `FactoryFonts.sans` / `FactoryFonts.mono` — single swap point when
  bundling real `.ttf` files later.

**Theme wiring** (`Theme.kt`)
- Light + Dark `ColorScheme` populated for all relevant M3 slots
  (`primary`, `secondary`, `tertiary`, `error`, `surface`,
  `surfaceVariant`, `outline`, `errorContainer`, `onErrorContainer`, etc.).
- Provides `LocalFactoryColors` and `LocalFactoryTypeExtras`.

### Build status

`./gradlew.bat :app:compileDebugKotlin` — **BUILD SUCCESSFUL** (no warnings).

### Phone test for this step

1. Login screen title weight — slightly lighter/tighter (SemiBold vs Bold).
2. Login button — should now be **deep teal #0F6E56**, not green. Biggest
   visible change.
3. HomeScreen TopAppBar title at 18sp Medium.
4. Body text on machine cards now 15sp.
5. Dark mode (system flip) — primary becomes mint-teal #2DD4BF; surfaces
   shift to warm near-black #1A1A18.
6. Status badges still render in original colors (RUNNING green, FLAGGED
   amber, STOPPED red) — kept on legacy aliases until step (c).

---

## Resume here next session

### Where we are

- Foundations laid in `presentation/common/theme/`. Compiles clean.
- User has not yet run the debug build to verify visually.
- All 4 roles' screens still use the **old visual** other than colors that
  Material picked up automatically (primary button color, surface tint).

### Step (c) — what to build next

Polish these 4 components in order. Each cascades benefits across many
screens:

#### 1. `PrimaryButton` — new component
Path: `presentation/common/components/PrimaryButton.kt`

- Default height 56dp (`TouchTarget.min`), `primary = 64dp`,
  `hero = 88dp` (Production Start/Stop).
- Press state: scale 0.97 with 100ms `tween`.
- Loading: spinner replaces text, button stays same width
  (no layout shift).
- Disabled: 40% alpha.
- Variants: `Primary` (filled, teal), `Success` (filled, green),
  `Danger` (filled, red), `Outlined`.
- API:
  ```kotlin
  PrimaryButton(
      text     = "Production Start",
      onClick  = ...,
      style    = PrimaryButtonStyle.Success,
      size     = PrimaryButtonSize.Hero,   // Min / Primary / Hero
      loading  = false,
      enabled  = true,
      icon     = Icons.Default.PlayArrow,  // optional
      modifier = Modifier.fillMaxWidth(),
  )
  ```

#### 2. `StatusBadge` — refactor existing
Path: `presentation/common/components/StatusBadge.kt`

- Pill shape: 28dp height (compact), 64dp height (large/hero).
- 12dp horizontal padding, optional 12dp leading icon, 4dp gap.
- **Smooth color tween (200ms) when status changes** — wrap container
  color and content color in `animateColorAsState`.
- Migrate from legacy `StatusRunning` etc. to `FactoryTheme.colors.*`
  (so dark mode works).
- Keep API backward compatible (`large: Boolean` param works).

#### 3. `FactoryCard` — new wrapper
Path: `presentation/common/components/FactoryCard.kt`

- 16dp internal padding (default), 12dp corner radius, 1dp border in
  `FactoryTheme.colors.border`.
- Subtle shadow on light theme (elevation 2dp; on dark theme use
  surfaceVariant background instead — shadows are invisible).
- Optional `statusBorder: Color?` param for status-tinted left border on
  HomeScreen machine cards.
- Press feedback: scale to 0.98 with 80ms tween if `onClick != null`.
- Replaces direct `Card { ... }` usage in `MachineCard`,
  `MachineDetailScreen` HeaderCard, etc.

#### 4. `SyncIndicator` — refactor existing
Path: `presentation/common/components/SyncIndicator.kt`

- Migrate to `FactoryTheme.colors.syncOk/syncPending/syncFailed`.
- States: synced (static check), syncing (smooth rotation, current rotate
  works fine), pending (count badge in white on amber), failed (static red
  with `Icons.Default.SyncProblem`). **No shake animation** (dropped).
- Cross-fade between states (200ms `Crossfade`).
- Tap target ≥ 40dp (currently 4dp padding around 20dp icon).

After step (c) builds clean: pause, ask user to verify on phone, then
proceed to step (e) screen transitions or (f) operator path — user's
preference.

### Tasks tracker (TaskCreate IDs from previous session)

```
#1 Set up theme foundations              — completed
#2 Add fonts via expo-font / res/font    — pending (likely SKIP — system fonts chosen)
#3 Polish core components                — pending  ← NEXT
#4 Polish operator path                  — pending
#5 Polish technician + QC screens        — pending
#6 Final polish (transitions, skeletons, empty, haptics) — pending
```

Tasks list resets across sessions; recreate from the table above when
resuming.

### Files NOT yet touched (safe to read for context)

- `presentation/home/HomeScreen.kt` — operator home, will polish in (f).
- `presentation/machine/MachineDetailScreen.kt` — has hand-rolled
  `LifecycleSection` and `LifecycleBanner`; biggest screen, will polish in (f).
- `presentation/login/LoginScreen.kt` — straightforward; polish in (f).
- `presentation/history/HistoryScreen.kt` — polish in (f).
- `presentation/technician/*` — polish in (g).
- `presentation/qc/*` — polish in (h).

### Key reference paths

- Project root: `D:\Maintenance module\FactoryOps`
- Build: `./gradlew.bat :app:compileDebugKotlin` (~35s clean)
- Components dir: `app/src/main/java/com/candorfoods/factoryops/presentation/common/components/`
- Theme dir: `app/src/main/java/com/candorfoods/factoryops/presentation/common/theme/`
- Nav graph: `app/src/main/java/com/candorfoods/factoryops/navigation/NavGraph.kt`
