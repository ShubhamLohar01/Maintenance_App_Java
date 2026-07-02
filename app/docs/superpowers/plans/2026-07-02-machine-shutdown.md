# Supervisor Machine Shut-Down Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Supervisor/Admin/Head shut down (soft-decommission) any machine via `MtMachine.condition = "Shut Down"`, locking it for everyone, with a reversible Restore.

**Architecture:** Reuse the existing `MtMachine.condition` field + the existing `PUT /mt-machines/{asset_id}` write path (backend-persisted, cross-device). A single pure helper `MtMachine.isShutDown()` is the source of truth; guards call it in the detail ViewModel, the runnable-asset use case, and the breakdown-flag ViewModel; the detail + list screens render the locked/greyed state and the shut-down/restore actions.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, JUnit 5 (Jupiter) unit tests, Gradle.

---

## Conventions

- **Run from the Gradle repo root** `d:/Maintenance module/FactoryOps` (where `gradlew` lives); module is `:app`.
- **Universal verification command:** `./gradlew :app:testDebugUnitTest --console=plain` — it compiles all main + test sources (KSP/Hilt included) and runs the unit tests. Use it as both the compile check and the test gate. (A cold run is ~3 min; use the Bash tool with a long timeout, e.g. 600000 ms. Do **not** rely on `:app:compileDebugKotlin` alone — it has shown a spurious KSP hiccup on this machine; `:app:testDebugUnitTest` is the proven-green gate.)
- Single test class: append `--tests "com.candorfoods.factoryops.<pkg>.<Class>"`.
- Tests use JUnit 5: `import org.junit.jupiter.api.Test`, `import org.junit.jupiter.api.Assertions.*`.
- **CRITICAL — protect unrelated work:** the working tree has uncommitted warehouse-filter WIP and (on this branch) role-based-reminders commits. When committing, `git add` ONLY the exact files listed in the task. NEVER `git add -A` / `git add .`.

## File Structure

**Create:**
- `src/test/java/com/candorfoods/factoryops/machine/MtMachineShutDownTest.kt` — helper tests.
- `src/test/java/com/candorfoods/factoryops/machine/GetRunnableAssetsShutDownTest.kt` — use-case filter test.

**Modify:**
- `domain/model/MtMachine.kt` — `CONDITION_SHUT_DOWN` constant + `MtMachine.isShutDown()`.
- `domain/usecase/mtmachine/GetRunnableAssetsUseCase.kt` — exclude shut-down assets.
- `presentation/machine/MachineDetailViewModel.kt` — state, guards, shut-down/restore.
- `presentation/machine/MachineDetailScreen.kt` — top-bar action, dialogs, disabled Start, hidden Report Breakdown, banner.
- `presentation/breakdown/BreakdownFlagViewModel.kt` — defensive submit guard.
- `presentation/machinelist/MachineListScreen.kt` — greyed row + "Shut Down" badge.

**Out of scope (per spec):** PM-plan machine picker filter; any `Machine`/`MachineStatus` change; hard delete; blocking in-flight PM / existing-breakdown completion.

---

## Task 1: `isShutDown` helper + sentinel constant

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/domain/model/MtMachine.kt`
- Test: `src/test/java/com/candorfoods/factoryops/machine/MtMachineShutDownTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/candorfoods/factoryops/machine/MtMachineShutDownTest.kt`:

```kotlin
package com.candorfoods.factoryops.machine

import com.candorfoods.factoryops.domain.model.CONDITION_SHUT_DOWN
import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.isShutDown
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MtMachineShutDownTest {

    private fun machine(condition: String?) = MtMachine(
        assetId = "W202-1", building = "W-202", assetName = "Band Sealer",
        category = "Production Equipment", subLocation = "Floor 1", ratedKw = 1.2,
        powerLoadRaw = "120Watt", section = MachineCategory.PRODUCTION, condition = condition,
    )

    @Test fun `sentinel is the canonical spelling`() {
        assertEquals("Shut Down", CONDITION_SHUT_DOWN)
    }

    @Test fun `isShutDown true for the sentinel regardless of case or whitespace`() {
        assertTrue(machine("Shut Down").isShutDown())
        assertTrue(machine("shut down").isShutDown())
        assertTrue(machine("  SHUT DOWN  ").isShutDown())
    }

    @Test fun `isShutDown false for other conditions, blank, or null`() {
        assertFalse(machine("Good").isShutDown())
        assertFalse(machine("").isShutDown())
        assertFalse(machine(null).isShutDown())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.machine.MtMachineShutDownTest" --console=plain`
Expected: FAIL — unresolved references `CONDITION_SHUT_DOWN`, `isShutDown`.

- [ ] **Step 3: Add the constant + helper**

In `src/main/java/com/candorfoods/factoryops/domain/model/MtMachine.kt`, append at the end of the file (top-level, after `MtMachine.isRunnable()`):

```kotlin

/** Canonical value written to [MtMachine.condition] to mark a machine as shut down. */
const val CONDITION_SHUT_DOWN = "Shut Down"

/**
 * True when this asset has been shut down (soft-decommissioned). Trim + case-insensitive
 * so pre-existing/backend variants still match. Single source of truth for every guard.
 */
fun MtMachine.isShutDown(): Boolean =
    condition?.trim().equals(CONDITION_SHUT_DOWN, ignoreCase = true) == true
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.machine.MtMachineShutDownTest" --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/domain/model/MtMachine.kt src/test/java/com/candorfoods/factoryops/machine/MtMachineShutDownTest.kt
git commit -m "feat: add MtMachine.isShutDown() + Shut Down sentinel"
```

---

## Task 2: Exclude shut-down assets from the runnable picker

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/domain/usecase/mtmachine/GetRunnableAssetsUseCase.kt`
- Test: `src/test/java/com/candorfoods/factoryops/machine/GetRunnableAssetsShutDownTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/candorfoods/factoryops/machine/GetRunnableAssetsShutDownTest.kt`:

```kotlin
package com.candorfoods.factoryops.machine

import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetRunnableAssetsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetRunnableAssetsShutDownTest {

    private fun machine(id: String, condition: String?) = MtMachine(
        assetId = id, building = "W-202", assetName = "Asset $id",
        category = "Production Equipment", subLocation = "Floor 1", ratedKw = 1.0,
        powerLoadRaw = "120Watt", section = MachineCategory.PRODUCTION, condition = condition,
    )

    private class FakeRepo(private val data: List<MtMachine>) : MtMachineRepository {
        override suspend fun list(building: String?, subLocation: String?, subCategory: String?) =
            Result.Success(data)
        override suspend fun getByAssetId(assetId: String) =
            Result.Success(data.firstOrNull { it.assetId == assetId })
        override suspend fun addOther(
            name: String, building: String, subLocation: String,
            subCategory: String, powerLoad: String?,
        ): Result<MtMachine> = error("unused")
        override suspend fun updateAsset(machine: MtMachine): Result<MtMachine> = error("unused")
    }

    @Test fun `shut-down machine is excluded even when otherwise runnable`() = runBlocking {
        val repo = FakeRepo(listOf(machine("A", null), machine("B", "Shut Down")))
        val result = GetRunnableAssetsUseCase(repo)() as Result.Success
        assertEquals(listOf("A"), result.data.map { it.assetId })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.machine.GetRunnableAssetsShutDownTest" --console=plain`
Expected: FAIL — "B" still present (filter not applied yet).

- [ ] **Step 3: Add the filter**

In `src/main/java/com/candorfoods/factoryops/domain/usecase/mtmachine/GetRunnableAssetsUseCase.kt`, add the import and the extra filter:

Add to imports:
```kotlin
import com.candorfoods.factoryops.domain.model.isShutDown
```

Change the success branch from:
```kotlin
        is Result.Success -> Result.Success(
            r.data.filter { it.isRunnable() }.sortedBy { it.assetName.lowercase() }
        )
```
to:
```kotlin
        is Result.Success -> Result.Success(
            r.data.filter { it.isRunnable() && !it.isShutDown() }.sortedBy { it.assetName.lowercase() }
        )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.machine.GetRunnableAssetsShutDownTest" --console=plain`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/domain/usecase/mtmachine/GetRunnableAssetsUseCase.kt src/test/java/com/candorfoods/factoryops/machine/GetRunnableAssetsShutDownTest.kt
git commit -m "feat: exclude shut-down machines from the runnable-asset picker"
```

---

## Task 3: MachineDetailViewModel — state, guards, shut-down/restore

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailViewModel.kt`

No new unit test (VM has Android/coroutine deps and the codebase has no VM tests); the guard decision is the already-tested `isShutDown()` helper. Verify by full compile.

- [ ] **Step 1: Add imports**

Add to the imports block:
```kotlin
import com.candorfoods.factoryops.domain.model.CONDITION_SHUT_DOWN
import com.candorfoods.factoryops.domain.model.isShutDown
```

- [ ] **Step 2: Add dialog flags to the UI-state data class**

In `MachineDetailUiState`, after the line `val showStopDialog: Boolean       = false,` add:
```kotlin
    val showShutDownDialog: Boolean     = false,
    val showRestoreDialog: Boolean      = false,
```

- [ ] **Step 3: Add derived flags**

In `MachineDetailUiState`, after the `underBreakdown` getter (just before the closing `}` of the class body) add:
```kotlin
    /** This asset has been shut down (soft-decommissioned) — locked for all users. */
    val isShutDown: Boolean get() = asset?.isShutDown() == true
    /** Supervisor / Admin / Head may shut down or restore a machine. */
    val canToggleShutDown: Boolean
        get() = currentUser?.role == UserRole.SUPERVISOR ||
            currentUser?.role == UserRole.ADMIN ||
            currentUser?.role == UserRole.HEAD
```

- [ ] **Step 4: Add guards to start/resume**

Replace `onStartClicked()`:
```kotlin
    fun onStartClicked() {
        // Guard: a machine under breakdown or shut down can't start.
        if (_uiState.value.underBreakdown || _uiState.value.isShutDown) return
        _uiState.update { it.copy(showStartDialog = true) }
    }
```

In `onResumeClicked()`, add the shut-down guard right after `val asset = _uiState.value.asset ?: return`:
```kotlin
        if (_uiState.value.isShutDown) return
```

- [ ] **Step 5: Add shut-down / restore methods**

Add after `onSaveEdit()` (before the `private companion object`):
```kotlin
    // ─── Shut down / restore (SUPERVISOR / ADMIN / HEAD) ──────────────────────────
    fun onShutDownClicked() = _uiState.update { it.copy(showShutDownDialog = true) }
    fun onRestoreClicked()  = _uiState.update { it.copy(showRestoreDialog = true) }
    fun onDismissShutDownDialogs() =
        _uiState.update { it.copy(showShutDownDialog = false, showRestoreDialog = false) }

    fun onConfirmShutDown() = setCondition(CONDITION_SHUT_DOWN, "Machine shut down")
    fun onConfirmRestore()  = setCondition(null, "Machine restored")

    /** Writes condition via PUT /mt-machines/{asset_id} (backend-authoritative). */
    private fun setCondition(condition: String?, successMsg: String) {
        onDismissShutDownDialogs()
        val current = _uiState.value.asset ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val r = mtMachineRepository.updateAsset(current.copy(condition = condition))) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, asset = r.data) }
                    _uiEvent.emit(MachineDetailUiEvent.ShowSnackbar(successMsg))
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(MachineDetailUiEvent.ShowSnackbar(r.message))
                }
                is Result.Loading -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
```

- [ ] **Step 6: Verify full compile + tests**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL (all existing + Task 1/2 tests pass).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailViewModel.kt
git commit -m "feat: MachineDetailViewModel shut-down/restore + start guards"
```

---

## Task 4: MachineDetailScreen — action, dialogs, locked UI

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailScreen.kt`

No unit test (Compose UI); verify by full compile + manual run.

- [ ] **Step 1: Add icon imports**

Add to the imports block:
```kotlin
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Refresh
```

- [ ] **Step 2: Add confirm dialogs**

After the `if (uiState.showStopDialog) { ... }` block, add:
```kotlin
    if (uiState.showShutDownDialog) {
        ConfirmDialog(
            title        = "Shut Down Machine",
            message      = "Shut down ${uiState.asset?.assetName}? It will be locked for all users until restored.",
            confirmLabel = "Shut Down",
            onConfirm    = viewModel::onConfirmShutDown,
            onDismiss    = viewModel::onDismissShutDownDialogs
        )
    }
    if (uiState.showRestoreDialog) {
        ConfirmDialog(
            title        = "Restore Machine",
            message      = "Restore ${uiState.asset?.assetName}? Operators will be able to use it again.",
            confirmLabel = "Restore",
            onConfirm    = viewModel::onConfirmRestore,
            onDismiss    = viewModel::onDismissShutDownDialogs
        )
    }
```

- [ ] **Step 3: Add the top-bar action**

Replace the `actions = { ... }` block in the `TopAppBar` with:
```kotlin
                    actions = {
                        if (uiState.asset != null) {
                            if (uiState.isSupervisor) {
                                IconButton(onClick = viewModel::onEditClicked) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit machine")
                                }
                            }
                            if (uiState.canToggleShutDown) {
                                if (uiState.isShutDown) {
                                    IconButton(onClick = viewModel::onRestoreClicked) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Restore machine")
                                    }
                                } else {
                                    IconButton(onClick = viewModel::onShutDownClicked) {
                                        Icon(Icons.Default.Block, contentDescription = "Shut down machine")
                                    }
                                }
                            }
                        }
                    },
```

- [ ] **Step 4: Add the shut-down banner**

After the `item { AssetHeaderCard(asset) }` line, add:
```kotlin
                    if (uiState.isShutDown) {
                        item { ShutDownBanner() }
                    }
```

Then add the composable near `BreakdownBanner` (top-level, private):
```kotlin
@Composable
private fun ShutDownBanner() {
    val danger = FactoryTheme.colors.statusStopped
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "● SHUT DOWN",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = danger,
            )
            Text(
                "This machine has been shut down and is locked for all users.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "A supervisor, admin, or head can restore it from the top-right menu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
```

- [ ] **Step 5: Disable Start for a shut-down machine**

In the operator-action `when` block, add this branch immediately BEFORE the `uiState.underBreakdown -> {` branch:
```kotlin
                                uiState.isShutDown -> {
                                    // Start is blocked while the machine is shut down.
                                    PrimaryButton(
                                        text = "Production Start",
                                        onClick = {},
                                        enabled = false,
                                        style = PrimaryButtonStyle.Success,
                                        size = PrimaryButtonSize.Hero,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
```

- [ ] **Step 6: Hide Report Breakdown for a shut-down machine**

Change the condition:
```kotlin
                    if (uiState.isOperator && !uiState.underBreakdown) {
```
to:
```kotlin
                    if (uiState.isOperator && !uiState.underBreakdown && !uiState.isShutDown) {
```

- [ ] **Step 7: Verify full compile**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailScreen.kt
git commit -m "feat: shut-down/restore action + locked UI on machine detail"
```

---

## Task 5: BreakdownFlagViewModel — defensive submit guard

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/presentation/breakdown/BreakdownFlagViewModel.kt`

No unit test; verify by full compile + manual run.

- [ ] **Step 1: Add imports + repository dependency**

Add to imports:
```kotlin
import com.candorfoods.factoryops.domain.model.isShutDown
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
```

Add the constructor parameter (after `savedStateHandle`):
```kotlin
    savedStateHandle: SavedStateHandle,
    private val raiseBreakdownFlag: RaiseBreakdownFlagUseCase,
    private val mtMachineRepository: MtMachineRepository,
```

- [ ] **Step 2: Guard onSubmit**

Replace the body of `onSubmit()` with:
```kotlin
    fun onSubmit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Defensive: a shut-down machine cannot receive a breakdown report.
            val machine = (mtMachineRepository.getByAssetId(machineId) as? Result.Success)?.data
            if (machine?.isShutDown() == true) {
                _uiState.update { it.copy(isLoading = false, error = "This machine is shut down.") }
                _uiEvent.emit(BreakdownFlagUiEvent.ShowSnackbar("This machine is shut down."))
                return@launch
            }
            val s = _uiState.value
            when (val r = raiseBreakdownFlag(machineId, s.severity, s.description, s.beforePhotoLocalPath)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(BreakdownFlagUiEvent.FlagRaised)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = r.message) }
                    _uiEvent.emit(BreakdownFlagUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
```

- [ ] **Step 3: Verify full compile**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/presentation/breakdown/BreakdownFlagViewModel.kt
git commit -m "feat: block breakdown reports on a shut-down machine"
```

---

## Task 6: MachineListScreen — greyed row + "Shut Down" badge

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/presentation/machinelist/MachineListScreen.kt`

No unit test; verify by full compile + manual run.

- [ ] **Step 1: Add import**

Add to imports:
```kotlin
import com.candorfoods.factoryops.domain.model.isShutDown
```

- [ ] **Step 2: Grey the row + swap the badge**

Replace the `MachineRow` composable body header (the `val muted` line through the name `Text` + badge) so it reads:
```kotlin
private fun MachineRow(item: MachineListItem, onClick: () -> Unit) {
    val muted = FactoryTheme.colors.onSurfaceMuted
    val shutDown = item.asset.isShutDown()
    val nameAlpha = if (shutDown) 0.5f else 1f
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.asset.assetName.ifBlank { item.asset.assetId },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = nameAlpha),
                    modifier = Modifier.weight(1f),
                )
                if (shutDown) ShutDownBadge() else StatusBadge(item.status)
            }
```
(Leave the rest of `MachineRow` — the RUNNING line, spacer, and location `Text` — unchanged.)

- [ ] **Step 3: Add the badge composable**

Add next to `StatusBadge` (top-level, private):
```kotlin
@Composable
private fun ShutDownBadge() {
    Text(
        "● SHUT DOWN",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = FactoryTheme.colors.statusStopped,
    )
}
```

- [ ] **Step 4: Verify full compile**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/presentation/machinelist/MachineListScreen.kt
git commit -m "feat: grey + badge shut-down machines in the machine list"
```

---

## Task 7: Full suite, manual verification, final commit

**Files:** none (verification only)

- [ ] **Step 1: Run the whole unit-test suite**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL; `MtMachineShutDownTest` (3), `GetRunnableAssetsShutDownTest` (1) and all pre-existing tests pass.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual verification (record results in the commit body)**

On an emulator/device against the real backend:
1. Sign in as **Supervisor** → open a machine → top-right **Shut Down** (Block icon) → confirm. The detail shows the SHUT DOWN banner; the machine list shows it greyed with a "SHUT DOWN" badge.
2. Sign in as **Operator** → the same machine is greyed in the list; on detail, Production Start is disabled and "Report Breakdown" is hidden; the machine is absent from the runnable/start picker.
3. Sign in as **Head** → open the machine → top-right **Restore** (Refresh icon) → confirm. Condition clears; operator can start production again.

- [ ] **Step 4: Final commit (targeted; never `git add -A`)**

```bash
git add src/main/java/com/candorfoods/factoryops/domain/model/MtMachine.kt \
        src/main/java/com/candorfoods/factoryops/domain/usecase/mtmachine/GetRunnableAssetsUseCase.kt \
        src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailViewModel.kt \
        src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailScreen.kt \
        src/main/java/com/candorfoods/factoryops/presentation/breakdown/BreakdownFlagViewModel.kt \
        src/main/java/com/candorfoods/factoryops/presentation/machinelist/MachineListScreen.kt \
        src/test/java/com/candorfoods/factoryops/machine/
git commit -m "test: verify machine shut-down end-to-end (manual notes in body)" || echo "nothing to commit"
```

---

## Self-Review (completed during planning)

- **Spec coverage:** mechanism/helper → Task 1; runnable-picker lock → Task 2; permissions + set/restore + start guards → Task 3; trigger UI + dialogs + Start-disabled + Report-Breakdown hidden + banner → Task 4; breakdown-submit guard → Task 5; list greying/badge → Task 6; testing → Tasks 1,2,7. PM-plan picker is explicitly out of scope (spec §3/§6.2). In-flight PM/breakdown completion intentionally not blocked.
- **Placeholder scan:** none — every code step is complete.
- **Type consistency:** `CONDITION_SHUT_DOWN`, `MtMachine.isShutDown()`, `MachineDetailUiState.isShutDown/canToggleShutDown/showShutDownDialog/showRestoreDialog`, `onShutDownClicked/onRestoreClicked/onConfirmShutDown/onConfirmRestore/onDismissShutDownDialogs`, `ShutDownBanner()`, `ShutDownBadge()` are used identically across tasks. `MtMachineRepository.updateAsset` / `getByAssetId` / `list` signatures match the interface. `Result.Success/Error/Loading` matches the domain `Result`.
```
