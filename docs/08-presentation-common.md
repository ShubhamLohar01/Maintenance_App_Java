# Presentation — Theme & Common Components

This module defines the shared Compose UI foundation that every FactoryOps screen builds on: a Material 3 theme extended with semantic `FactoryColors` (status / sync / border / muted-on-surface), a 4-point `Spacing` scale and gloves-friendly `TouchTarget` sizes, a custom `FactoryTypography` slot scale bumped up for dim, dusty arm's-length reading plus mono extras for kWh/timestamps, and a small kit of reusable composables (cards, badges, buttons, overlays, empty states, skeletons, sync indicator). Together they enforce the Phase 2B factory-floor UX constraints — large tap targets, high contrast, status-coded colors, predictable press feedback.

## Theme

### Color.kt

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/theme/Color.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/theme/Color.kt)

Two layers:

1. **Raw brand/semantic tokens** — light/dark pairs for `BrandTeal`, `Success`, `Warning`, `Danger`, `Info`, `Surface`, `SurfaceVar`, `OnSurface`, `OnSurfaceMuted`, `Border`.
2. **Theme-aware semantic colors** exposed through `FactoryTheme.colors.*` (backed by a `staticCompositionLocalOf<FactoryColors>`):

| Accessor | Purpose |
| --- | --- |
| `FactoryTheme.colors.statusRunning` | Machine RUNNING (green) |
| `FactoryTheme.colors.statusIdle` | Machine IDLE (muted grey) |
| `FactoryTheme.colors.statusStopped` | Machine STOPPED (danger red) |
| `FactoryTheme.colors.statusFlagged` | Machine FLAGGED (warning amber) |
| `FactoryTheme.colors.statusPendingQc` | Awaiting QC (warning amber) |
| `FactoryTheme.colors.syncOk` | Sync indicator success |
| `FactoryTheme.colors.syncPending` | Sync indicator pending |
| `FactoryTheme.colors.syncFailed` | Sync indicator failed |
| `FactoryTheme.colors.info` | Informational accent |
| `FactoryTheme.colors.onSurfaceMuted` | Secondary text on surface |
| `FactoryTheme.colors.border` | 1dp card / divider stroke |

`LightFactoryColors` and `DarkFactoryColors` are the two concrete instances; the active one is picked by `FactoryOpsTheme`.

Legacy top-level aliases (`StatusRunning`, `SyncOk`, `Green80`/`Green40`, etc.) are kept for older call sites — new code should prefer `FactoryTheme.colors.*`.

### Spacing.kt

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/theme/Spacing.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/theme/Spacing.kt)

A 4-point `Spacing` scale used in place of raw `dp` literals:

| Token | Value |
| --- | --- |
| `Spacing.xs` | 4.dp |
| `Spacing.sm` | 8.dp |
| `Spacing.md` | 12.dp |
| `Spacing.base` | 16.dp |
| `Spacing.lg` | 24.dp |
| `Spacing.xl` | 32.dp |
| `Spacing.xxl` | 48.dp |
| `Spacing.xxxl` | 64.dp |

Gloves-friendly tap target sizes via `TouchTarget`:

| Token | Value | Intended use |
| --- | --- | --- |
| `TouchTarget.min` | 56.dp | Standard tap target |
| `TouchTarget.primary` | 64.dp | Primary actions (Login, Approve) |
| `TouchTarget.hero` | 88.dp | Hero actions (Production Start / Stop) |
| `TouchTarget.chip` | 28.dp | Compact status badge / chip |
| `TouchTarget.chipLarge` | 64.dp | Hero status badge variant |

### Type.kt

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/theme/Type.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/theme/Type.kt)

Custom `FactoryTypography` overrides Material 3 defaults so operators can read screens in dim/dusty light at arm's length. Font families are aliased through `FactoryFonts` (`sans = FontFamily.SansSerif`, `mono = FontFamily.Monospace`) so bundled `.ttf` files can later be swapped in one place.

Standard slots (all sans):

| Slot | Size / Line height | Weight | Notes |
| --- | --- | --- | --- |
| `displayLarge` | 36 / 44 sp | Bold | Huge numbers; letter-spacing -0.4 |
| `displayMedium` | 32 / 40 sp | Bold | |
| `headlineLarge` | 28 / 36 sp | SemiBold | Screen titles |
| `headlineMedium` | 22 / 30 sp | SemiBold | |
| `headlineSmall` | 20 / 28 sp | SemiBold | |
| `titleLarge` | 18 / 26 sp | Medium | Card titles |
| `titleMedium` | 16 / 22 sp | SemiBold | |
| `titleSmall` | 14 / 20 sp | Medium | |
| `bodyLarge` | 17 / 26 sp | Normal | Bumped from M3 default 16sp |
| `bodyMedium` | 15 / 24 sp | Normal | |
| `bodySmall` | 13 / 20 sp | Normal | |
| `labelLarge` | 13 / 20 sp | Medium | Letter-spacing 0.2 |
| `labelMedium` | 12 / 18 sp | Medium | Letter-spacing 0.3 |
| `labelSmall` | 11 / 16 sp | Medium | Letter-spacing 0.4 |

Extras outside Material 3's slot set, exposed via `FactoryTypeScale.extras.*` (backed by `LocalFactoryTypeExtras` and `DefaultFactoryTypeExtras`):

| Slot | Size / Line height | Family | Use |
| --- | --- | --- | --- |
| `displayHero` | 44 / 52 sp, Bold, ls -0.6 | sans | Hero numeric readouts |
| `monoLarge` | 24 / 32 sp, Bold | mono | kWh totals, large numeric values |
| `monoMedium` | 14 / 22 sp, Normal | mono | Timestamps, IDs, currency |

### Theme.kt

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/theme/Theme.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/theme/Theme.kt)

`FactoryOpsTheme(darkTheme = isSystemInDarkTheme(), content)` is the root wrapper. It:

- Picks `LightColorScheme` or `DarkColorScheme` (Material 3 `ColorScheme`) keyed off `darkTheme`.
- Picks matching `LightFactoryColors` / `DarkFactoryColors`.
- Installs both `LocalFactoryColors` and `LocalFactoryTypeExtras` via `CompositionLocalProvider`.
- Calls `MaterialTheme(colorScheme, typography = FactoryTypography, content)`.

The `LightColorScheme` wiring maps brand/semantic tokens to M3 roles:

- `primary` = `BrandTeal`, `onPrimary` = white, `primaryContainer` = teal @ 12% alpha.
- `secondary` = `WarningLight`, with a 14% alpha container.
- `tertiary` = `InfoLight`.
- `error` = `DangerLight` with white `onError` and a 12% alpha container.
- `background` / `surface` = `SurfaceLight`, `surfaceVariant` = `SurfaceVarLight`.
- `onSurface` = `OnSurfaceLight`, `onSurfaceVariant` = `OnSurfaceMutedL`.
- `outline` / `outlineVariant` = `BorderLight`.

`DarkColorScheme` mirrors that mapping with the `*Dark` tokens, bumps container alphas to 18%, and uses deep custom `onPrimary` / `onSecondary` / `onTertiary` / `onError` colors tuned for readable contrast on dark fills.

## Components

### ConfirmDialog

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/ConfirmDialog.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/ConfirmDialog.kt)

```
ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
```

- Thin wrapper around Material 3 `AlertDialog`.
- Title rendered as `titleLarge`, message as `bodyMedium`.
- Confirm action is a filled `Button`; dismiss is a `TextButton`.
- Dismissing via scrim/back also calls `onDismiss`.

### EmptyState

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/EmptyState.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/EmptyState.kt)

```
EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
)
```

- Centered icon + title + optional subtitle for zero-row list/tab states.
- Fades in `alpha 0 → 1` over a 200 ms `tween` on first composition.
- Fills its parent (`Modifier.fillMaxSize()` + `Spacing.lg` padding).
- Icon is 48 dp, tinted with `FactoryTheme.colors.onSurfaceMuted`.
- Title uses `titleMedium` on `onSurface`; subtitle uses `bodySmall` on `onSurfaceMuted`, separated by `Spacing.md` / `Spacing.xs` spacers respectively.

### FactoryCard

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/FactoryCard.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/FactoryCard.kt)

```
FactoryCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    statusBorder: Color? = null,
    containerColor: Color? = null,
    contentPadding: Dp = Spacing.base,   // 16dp
    cornerRadius: Dp = 12.dp,
    content: @Composable () -> Unit,
)
```

- Standard wrapper around M3 `Card` used by every screen.
- Default container: `MaterialTheme.colorScheme.surface` in light, `surfaceVariant` in dark; overridable via `containerColor` (e.g. T4 escalation red wash on PM cards).
- 1 dp border in `FactoryTheme.colors.border`, 12 dp corners.
- Elevation: 2 dp in light theme, 0 dp in dark (shadows are invisible on dark surfaces).
- When `statusBorder` is provided, a 4 dp colored stripe is painted on the left edge inside a `Box(fillMaxHeight)`; content padding is shifted right by 4 dp to compensate.
- When `onClick` is non-null, attaches a `clickable` with no ripple (`indication = null`) and animates `scale 1 → 0.98` on press via an 80 ms `tween` driven by `collectIsPressedAsState`.

### LoadingOverlay

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/LoadingOverlay.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/LoadingOverlay.kt)

```
LoadingOverlay(
    isLoading: Boolean,
    content: @Composable () -> Unit,
)
```

- Stacks `content` underneath an optional modal overlay.
- When `isLoading` is true, paints a full-size scrim of `Color.Black @ alpha 0.35` centered with a `CircularProgressIndicator` in `colorScheme.primary`.
- Pure conditional render — no animation between states.

### MachineCard

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/MachineCard.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/MachineCard.kt)

```
MachineCard(
    machine: Machine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- Specialized `FactoryCard` for the operator home grid.
- `statusBorder` color is derived from `machine.currentStatus` (`RUNNING → statusRunning`, `IDLE → statusIdle`, `STOPPED → statusStopped`, `FLAGGED → statusFlagged`, `PENDING_QC → statusPendingQc`).
- Layout: a `Row` with a weighted `Column` on the left and a `StatusBadge` on the right.
- Left column shows: machine name (`titleMedium`, Bold), `code` and `location` (`bodySmall`, muted), and `"{ratedKw} — {criticality} class"` (`bodySmall`) via `NumberFormatter.formatKw`.
- When status is `PENDING_QC`, an additional bold `labelMedium` "Awaiting QC verification" line is rendered in `statusPendingQc`.

### PrimaryButton

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/PrimaryButton.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/PrimaryButton.kt)

```
PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PrimaryButtonStyle = PrimaryButtonStyle.Primary,
    size: PrimaryButtonSize = PrimaryButtonSize.Min,
    loading: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
)

enum class PrimaryButtonStyle { Primary, Success, Danger, Outlined }
enum class PrimaryButtonSize  { Min, Primary, Hero }
```

- Style → container/content colors:
  - `Primary` — `colorScheme.primary` / `onPrimary`.
  - `Success` — `FactoryTheme.colors.statusRunning` / white.
  - `Danger` — `colorScheme.error` / `onError`.
  - `Outlined` — transparent container, `colorScheme.primary` content + 1 dp border in the same color.
- Size → minimum and fixed height: `Min = 56`, `Primary = 64`, `Hero = 88` dp (via `TouchTarget`).
- Press feedback: `scale 1 → 0.97` over a 100 ms `tween`, suppressed when `!enabled` or `loading`.
- Disabled / loading states multiply container & content colors by `DISABLED_ALPHA = 0.40`; loading also disables clicks (`effectiveEnabled = enabled && !loading`).
- Loading swap: the text+icon `Row` stays laid out (so intrinsic width does not collapse) but its alpha drops to 0, and a 20 dp `CircularProgressIndicator` (`strokeWidth = 2.5 dp`) is centered on top in the current `contentColor`.
- Optional 20 dp leading icon, rendered with `Spacing.sm` (8 dp) gap before the label; text uses `labelLarge`.

### SkeletonRow / SkeletonList

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/SkeletonRow.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/SkeletonRow.kt)

```
SkeletonRow(modifier: Modifier = Modifier)
SkeletonList(modifier: Modifier = Modifier, count: Int = 3)
```

- Static gray-box loading placeholder for list rows — no shimmer (Phase 2B "medium polish").
- `SkeletonRow` is a `Card` (12 dp corners, 1 dp border in `FactoryTheme.colors.border`, 0 dp elevation, `colorScheme.surface` container) containing three stacked bars at 60% / 40% / 80% width, heights 16 / 12 / 12 dp, filled with the border color and 4 dp rounded corners, separated by `Spacing.sm`. Inner padding `Spacing.base`.
- `SkeletonList` stacks `count` rows in a `Column`, `Spacing.md` apart, with `Spacing.base` outer padding.

### StatusBadge

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/StatusBadge.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/StatusBadge.kt)

```
StatusBadge(
    status: MachineStatus,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    icon: ImageVector? = null,
)
```

- Pill-shaped badge whose color is derived from `MachineStatus` and mapped to `FactoryTheme.colors.status*` (`RUNNING / IDLE / STOPPED / FLAGGED / PENDING_QC`).
- Container is the status color tinted at `CONTAINER_TINT_ALPHA = 0.18`; content (icon + label) uses the full-strength status color. Both transitions animate via `animateColorAsState` with a 200 ms `tween` (`STATUS_TWEEN_MS`).
- Shape: fully rounded pill (`RoundedCornerShape(percent = 50)`) with horizontal padding 12 dp.
- Sizing: `large = false` → height `TouchTarget.chip` (28 dp), 16 dp icon, `labelMedium` label at 12 sp; `large = true` → height `TouchTarget.chipLarge` (64 dp), 24 dp icon, `titleMedium` label at 18 sp.
- Label text is the uppercased status name (`PENDING_QC` shortens to `"QC"`), rendered Bold; optional leading icon followed by a 4 dp spacer.

### SyncIndicator

Source: [`../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/SyncIndicator.kt`](../app/src/main/java/com/candorfoods/factoryops/presentation/common/components/SyncIndicator.kt)

```
SyncIndicator(
    pendingCount: Int,
    isSyncing: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    hasFailed: Boolean = false,
)
```

- Compact, tappable header chip that visualizes the offline-sync queue. Min hit area 40×40 dp with 8 dp inner padding.
- Derives a single internal `SyncState` from inputs (precedence top to bottom):
  1. `Failed` — when `hasFailed`.
  2. `Syncing` — when `isSyncing`.
  3. `Pending` — when `pendingCount > 0`.
  4. `Synced` — otherwise.
- States are switched inside a `Crossfade` (200 ms `tween`) so visual changes feel calm rather than poppy:
  - **Synced** — 20 dp `Icons.Default.CloudDone` tinted `FactoryTheme.colors.syncOk`.
  - **Syncing** — 20 dp `Icons.Default.Sync` tinted `syncOk`, continuously rotated 0 → 360° via an `infiniteRepeatable(tween(1000, LinearEasing))`.
  - **Pending** — pill-shaped row with `syncPending` background containing a white 16 dp `CloudOff` icon and the pending count as `labelMedium`.
  - **Failed** — 20 dp `Icons.Default.SyncProblem` tinted `FactoryTheme.colors.syncFailed`.
- Whole row is `clickable(onTap)` so tapping any state opens the sync queue / triggers a manual retry.
