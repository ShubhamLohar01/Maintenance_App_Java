# Warehouse Filter for Machine/Asset Lists — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fixed `A-185 / W-202` warehouse dropdown to every machine/asset list in the FactoryOps Android app so users (all roles) can filter the list to one warehouse, defaulting to their home warehouse.

**Architecture:** Frontend-only. The asset register (`GET /mt-machines`) is already fetched unscoped (both buildings on device) and live status is already derived client-side, so this is a pure display filter. One shared `Buildings` domain object (mirrors backend `ALL_BUILDINGS` / `building_for`) supplies the canonical codes and the home-warehouse default; one shared `WarehouseFilter` Compose component renders the dropdown; each list ViewModel/UiState gains a `selectedBuilding` (seeded to the user's home warehouse) and a building predicate composed with its existing filters.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, MVVM with `StateFlow`. Tests: JUnit 5 (`org.junit.jupiter`), MockK, kotlinx-coroutines-test, Turbine.

**Spec:** `docs/superpowers/specs/2026-06-26-warehouse-filter-machine-lists-design.md`

---

## Conventions for every task

- **Working directory for all commands:** `d:\Maintenance module\FactoryOps` (the app root, where `gradlew` lives). This is a different folder from the backend.
- **Run a single test class:** `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.<ClassName>"` (Windows shell: use `.\gradlew.bat` instead of `./gradlew`).
- **Run all unit tests:** `./gradlew :app:testDebugUnitTest`
- **Compile-check Compose changes (no unit test):** `./gradlew :app:assembleDebug`
- **Commit steps assume FactoryOps is a git repo.** If `git status` errors with "not a git repository", run `git init` once (or skip the commit steps and commit however the team prefers). Never commit secrets.
- Canonical building values are `"A-185"` and `"W-202"` (hyphenated) — both `MtMachine.building` and `Machine.building` store this exact form.

---

## File Structure

**Create:**
- `app/src/main/java/com/candorfoods/factoryops/domain/model/Buildings.kt` — canonical warehouse codes + `forPlant` / `defaultFor` resolver. One responsibility: the warehouse vocabulary and home-warehouse default.
- `app/src/main/java/com/candorfoods/factoryops/presentation/common/components/WarehouseFilter.kt` — the shared fixed-option dropdown composable.
- `app/src/test/java/com/candorfoods/factoryops/BuildingsTest.kt`
- `app/src/test/java/com/candorfoods/factoryops/MachineListBuildingFilterTest.kt`
- `app/src/test/java/com/candorfoods/factoryops/MachineMasterBuildingFilterTest.kt`
- `app/src/test/java/com/candorfoods/factoryops/AdminHomeBuildingFilterTest.kt`
- `app/src/test/java/com/candorfoods/factoryops/PlanEditorBuildingFilterTest.kt`

**Modify:**
- `presentation/machinelist/MachineListViewModel.kt` — `selectedBuilding` state, building predicate in `items`/`categories`/`countOf`, seed default, `onBuildingSelected`.
- `presentation/machinelist/MachineListScreen.kt` — render `WarehouseFilter`.
- `presentation/machinemaster/MachineMasterViewModel.kt` — replace dynamic building dropdown source with fixed default-home selection; inject `GetCurrentUserUseCase`.
- `presentation/machinemaster/MachineMasterScreen.kt` — swap the building `FilterDropdown` for `WarehouseFilter`.
- `presentation/admin/AdminHomeViewModel.kt` — `selectedBuilding` + building predicate + seed default + handler.
- `presentation/admin/AdminHomeScreen.kt` — render `WarehouseFilter`.
- `presentation/supervisor/PlanEditorViewModel.kt` — `selectedBuilding` + `machinesForBuilding` + seed + handler; inject `GetCurrentUserUseCase`.
- `presentation/supervisor/PlanEditorScreen.kt` — render `WarehouseFilter`, feed `machinesForBuilding` to the picker.

---

## Task 1: `Buildings` domain object (vocabulary + home default)

**Files:**
- Create: `app/src/main/java/com/candorfoods/factoryops/domain/model/Buildings.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/BuildingsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/candorfoods/factoryops/BuildingsTest.kt`:

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.Buildings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BuildingsTest {

    @Test
    fun `ALL is exactly the two canonical buildings in order`() {
        assertEquals(listOf("A-185", "W-202"), Buildings.ALL)
    }

    @Test
    fun `forPlant resolves the canonical hyphenated form`() {
        assertEquals("A-185", Buildings.forPlant("A-185"))
        assertEquals("W-202", Buildings.forPlant("W-202"))
    }

    @Test
    fun `forPlant resolves descriptive and compact spellings`() {
        assertEquals("A-185", Buildings.forPlant("A-185-Koparkhairne"))
        assertEquals("A-185", Buildings.forPlant("185-Koparkhairnae")) // digits-only match
        assertEquals("W-202", Buildings.forPlant("w202"))
        assertEquals("W-202", Buildings.forPlant(" W-202-Koparkhairne "))
    }

    @Test
    fun `forPlant returns null for both, blank and unknown`() {
        assertNull(Buildings.forPlant("both"))
        assertNull(Buildings.forPlant(""))
        assertNull(Buildings.forPlant(null))
        assertNull(Buildings.forPlant("UNKNOWN"))
    }

    @Test
    fun `defaultFor returns home warehouse, else first building`() {
        assertEquals("W-202", Buildings.defaultFor("W-202-Koparkhairne"))
        assertEquals("A-185", Buildings.defaultFor("both")) // HEAD/blank -> first
        assertEquals("A-185", Buildings.defaultFor(null))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.BuildingsTest"`
Expected: FAIL — `Buildings` is unresolved (compilation error).

- [ ] **Step 3: Write the minimal implementation**

Create `app/src/main/java/com/candorfoods/factoryops/domain/model/Buildings.kt`:

```kotlin
package com.candorfoods.factoryops.domain.model

/**
 * Canonical warehouse/building codes, mirroring the backend `ALL_BUILDINGS` and
 * `building_for()` in `backend/app/utils.py`. The asset register (`MtMachine`)
 * and legacy machines (`Machine`) both store the hyphenated form: "A-185" / "W-202".
 */
object Buildings {
    const val A185 = "A-185"
    const val W202 = "W-202"

    /** Fixed, ordered list shown in every warehouse dropdown. */
    val ALL: List<String> = listOf(A185, W202)

    /** Compact, comparable code: "A-185"/"a185"/" A185 " -> "A185". */
    private fun norm(s: String?): String =
        s?.replace(Regex("[^A-Za-z0-9]"), "")?.uppercase().orEmpty()

    /**
     * Resolve any plant/location spelling to a canonical building, or null.
     * Mirrors backend `building_for()`: match the building code ("A185"/"W202")
     * OR just its digits ("185"/"202") appearing in the normalized input.
     * "both" / blank / unrelated -> null.
     */
    fun forPlant(plantId: String?): String? {
        val n = norm(plantId)
        if (n.isEmpty()) return null
        for (b in ALL) {
            val nb = norm(b)                          // "A185" / "W202"
            val digits = nb.filter { it.isDigit() }   // "185" / "202"
            if (n.contains(nb) || (digits.isNotEmpty() && n.contains(digits))) return b
        }
        return null
    }

    /** Default warehouse selection for a user: their home warehouse, else the first building. */
    fun defaultFor(plantId: String?): String = forPlant(plantId) ?: ALL.first()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.BuildingsTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/domain/model/Buildings.kt \
        app/src/test/java/com/candorfoods/factoryops/BuildingsTest.kt
git commit -m "feat: add Buildings vocabulary + home-warehouse resolver"
```

---

## Task 2: Shared `WarehouseFilter` composable

No unit test (Compose UI component); verified by compilation here and by the screen tasks that consume it.

**Files:**
- Create: `app/src/main/java/com/candorfoods/factoryops/presentation/common/components/WarehouseFilter.kt`

- [ ] **Step 1: Write the component**

Create `app/src/main/java/com/candorfoods/factoryops/presentation/common/components/WarehouseFilter.kt`:

```kotlin
package com.candorfoods.factoryops.presentation.common.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.candorfoods.factoryops.domain.model.Buildings

/**
 * Fixed two-option warehouse filter (A-185 / W-202). No "All" option — callers
 * seed [selected] to the user's home warehouse via [Buildings.defaultFor].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseFilter(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Warehouse",
    options: List<String> = Buildings.ALL,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/common/components/WarehouseFilter.kt
git commit -m "feat: add shared WarehouseFilter composable"
```

---

## Task 3: Operator / Supervisor / HEAD — Machine List

`MachineListScreen` is the home for OPERATOR and is opened by SUPERVISOR & HEAD via the Dashboard "Machine List" tile. The list state (`MachineListUiState`) holds all derived filtering, so it is unit-tested directly (the ViewModel runs an infinite active-runs poll loop in `init`, so we do not construct it in tests).

**Files:**
- Modify: `presentation/machinelist/MachineListViewModel.kt`
- Modify: `presentation/machinelist/MachineListScreen.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/MachineListBuildingFilterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/candorfoods/factoryops/MachineListBuildingFilterTest.kt`:

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.presentation.machinelist.MachineListUiState
import com.candorfoods.factoryops.presentation.machinelist.MachineLiveStatus
import com.candorfoods.factoryops.presentation.machinelist.MachineStatusFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MachineListBuildingFilterTest {

    private fun asset(
        id: String,
        building: String,
        name: String = id,
        category: String = "Packaging",
    ) = MtMachine(
        assetId = id,
        building = building,
        assetName = name,
        category = category,
        subLocation = "Floor 1",
        ratedKw = null,
        powerLoadRaw = null,
        section = MachineCategory.OTHERS,
    )

    private val assets = listOf(
        asset("A185-1", "A-185", name = "Roaster A"),
        asset("A185-2", "A-185", name = "Sealer A", category = "Production Equipment"),
        asset("W202-1", "W-202", name = "Roaster W"),
    )

    @Test
    fun `selecting a warehouse shows only that building`() {
        val state = MachineListUiState(assets = assets, selectedBuilding = "A-185")
        val ids = state.items.map { it.asset.assetId }
        assertEquals(listOf("A185-1", "A185-2"), ids.sorted())
        assertTrue(state.items.all { it.asset.building == "A-185" })
    }

    @Test
    fun `switching warehouse switches the visible set`() {
        val state = MachineListUiState(assets = assets, selectedBuilding = "W-202")
        assertEquals(listOf("W202-1"), state.items.map { it.asset.assetId })
    }

    @Test
    fun `categories and counts reflect the building-filtered set`() {
        val state = MachineListUiState(assets = assets, selectedBuilding = "A-185")
        assertEquals(listOf("Packaging", "Production Equipment"), state.categories)
        // All idle (no active runs / breakdowns), and only A-185's two assets counted.
        assertEquals(2, state.countOf(MachineLiveStatus.IDLE))
    }

    @Test
    fun `building predicate composes with the search filter`() {
        val state = MachineListUiState(
            assets = assets,
            selectedBuilding = "A-185",
            searchQuery = "Roaster",
        )
        assertEquals(listOf("A185-1"), state.items.map { it.asset.assetId })
    }

    @Test
    fun `null selectedBuilding (pre-seed) shows all buildings`() {
        val state = MachineListUiState(assets = assets, selectedBuilding = null)
        assertEquals(3, state.items.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.MachineListBuildingFilterTest"`
Expected: FAIL — `selectedBuilding` is not a parameter of `MachineListUiState` (compilation error).

- [ ] **Step 3: Add `selectedBuilding` + building predicate to `MachineListUiState`**

In `presentation/machinelist/MachineListViewModel.kt`, add the field to `MachineListUiState` (after `selectedCategory`):

```kotlin
    val selectedCategory: String? = null,
    val selectedBuilding: String? = null,
    val statusFilter: MachineStatusFilter = MachineStatusFilter.ALL,
```

Then, inside the `MachineListUiState` body, add a building-filtered base list and route `categories`, `items`, and `countOf` through it. Replace the existing `categories`, `items`, and `countOf` members with:

```kotlin
    /** Assets in the selected warehouse (all buildings until seeded). */
    private val buildingFiltered: List<MtMachine>
        get() = assets.filter { a ->
            selectedBuilding == null || a.building.equals(selectedBuilding, ignoreCase = true)
        }

    val categories: List<String>
        get() = buildingFiltered.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    /** Filtered (warehouse + search + category + status) and ordered (status group, then name). */
    val items: List<MachineListItem>
        get() {
            val runs = runByAsset
            val q = searchQuery.trim()
            return buildingFiltered
                .asSequence()
                .map { asset ->
                    val run = runs[asset.assetId]
                    MachineListItem(asset, statusOf(asset.assetId), run?.operatorName, run?.startedAtEpochMs)
                }
                .filter { item ->
                    q.isEmpty() ||
                        item.asset.assetName.contains(q, true) ||
                        item.asset.assetId.contains(q, true) ||
                        item.asset.subLocation.contains(q, true)
                }
                .filter { selectedCategory == null || it.asset.category.equals(selectedCategory, true) }
                .filter { statusFilter.matches(it.status) }
                .sortedWith(compareBy({ it.status.order }, { it.asset.assetName.lowercase() }))
                .toList()
        }

    fun countOf(status: MachineLiveStatus): Int =
        buildingFiltered.count { statusOf(it.assetId) == status }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.MachineListBuildingFilterTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Seed the default + add the handler in `MachineListViewModel`**

In `presentation/machinelist/MachineListViewModel.kt`, add the import:

```kotlin
import com.candorfoods.factoryops.domain.model.Buildings
```

Replace the current-user collector in `init` (the `getCurrentUser().collect { user -> ... }` block) with one that seeds `selectedBuilding` once a real user arrives, without overwriting a manual choice:

```kotlin
        viewModelScope.launch {
            getCurrentUser().collect { user ->
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        selectedBuilding = it.selectedBuilding
                            ?: user?.let { u -> Buildings.defaultFor(u.plantId) },
                    )
                }
            }
        }
```

Add the handler next to the other `on...` functions:

```kotlin
    fun onBuildingSelected(building: String) =
        _uiState.update { it.copy(selectedBuilding = building) }
```

- [ ] **Step 6: Render the dropdown in `MachineListScreen`**

In `presentation/machinelist/MachineListScreen.kt`, add imports:

```kotlin
import com.candorfoods.factoryops.domain.model.Buildings
import com.candorfoods.factoryops.presentation.common.components.WarehouseFilter
```

Inside the `else -> Column(Modifier.fillMaxSize()) {` branch, add the filter as the FIRST child (above the search `OutlinedTextField`):

```kotlin
                else -> Column(Modifier.fillMaxSize()) {
                    WarehouseFilter(
                        selected = uiState.selectedBuilding
                            ?: Buildings.defaultFor(uiState.currentUser?.plantId),
                        onSelected = viewModel::onBuildingSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.base, vertical = Spacing.xs),
                    )
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        // ... unchanged ...
```

- [ ] **Step 7: Verify the whole module compiles and tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.MachineListBuildingFilterTest"`
Then: `./gradlew :app:compileDebugKotlin`
Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/machinelist/MachineListViewModel.kt \
        app/src/main/java/com/candorfoods/factoryops/presentation/machinelist/MachineListScreen.kt \
        app/src/test/java/com/candorfoods/factoryops/MachineListBuildingFilterTest.kt
git commit -m "feat: warehouse filter on operator/supervisor/head machine list"
```

---

## Task 4: Technician — Machine Master (standardize to fixed dropdown)

`MachineMasterScreen` is opened by TECHNICIAN via the Dashboard "Machine List" tile. It already has a *dynamic* building dropdown; replace that one dropdown with the shared fixed `WarehouseFilter` defaulting to the user's home warehouse. Location and Sub-category dropdowns stay unchanged.

**Files:**
- Modify: `presentation/machinemaster/MachineMasterViewModel.kt`
- Modify: `presentation/machinemaster/MachineMasterScreen.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/MachineMasterBuildingFilterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/candorfoods/factoryops/MachineMasterBuildingFilterTest.kt`:

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.MachineCategory
import com.candorfoods.factoryops.domain.model.MtMachine
import com.candorfoods.factoryops.presentation.machinemaster.MachineMasterUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MachineMasterBuildingFilterTest {

    private fun asset(id: String, building: String, section: MachineCategory) = MtMachine(
        assetId = id,
        building = building,
        assetName = id,
        category = if (section == MachineCategory.PRODUCTION) "Production Equipment" else "Utility",
        subLocation = "Floor 1",
        ratedKw = null,
        powerLoadRaw = null,
        section = section,
    )

    private val rows = listOf(
        asset("A185-1", "A-185", MachineCategory.PRODUCTION),
        asset("A185-2", "A-185", MachineCategory.OTHERS),
        asset("W202-1", "W-202", MachineCategory.PRODUCTION),
    )

    @Test
    fun `sections show only the selected building`() {
        val state = MachineMasterUiState(rows = rows, selectedBuilding = "A-185")
        val shown = state.sections.flatMap { it.second }.map { it.assetId }
        assertEquals(listOf("A185-1", "A185-2"), shown.sorted())
        assertTrue(state.sections.flatMap { it.second }.all { it.building == "A-185" })
    }

    @Test
    fun `switching building switches the visible set`() {
        val state = MachineMasterUiState(rows = rows, selectedBuilding = "W-202")
        assertEquals(listOf("W202-1"), state.sections.flatMap { it.second }.map { it.assetId })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.MachineMasterBuildingFilterTest"`
Expected: PASS-compiles-but? No — it currently compiles (`selectedBuilding` already exists as `String?`) and PASSES, because the existing `filtered` already does `selectedBuilding == null || row.building.equals(...)`. This test pins existing behavior; it must stay green through the refactor. Run it and confirm PASS now, then proceed — Steps 3–5 must keep it green.

- [ ] **Step 3: Update `MachineMasterViewModel` — inject current user, seed default, drop dynamic `buildings`, make handler non-null**

In `presentation/machinemaster/MachineMasterViewModel.kt`:

Add imports:

```kotlin
import com.candorfoods.factoryops.domain.model.Buildings
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import kotlinx.coroutines.flow.collect
```

Remove the now-unused dynamic `buildings` derived property from `MachineMasterUiState` (the `val buildings: List<String> get() = ...` block). Leave `locations` and `subCategories` as-is.

Change the constructor to inject the current-user use case:

```kotlin
@HiltViewModel
class MachineMasterViewModel @Inject constructor(
    private val getMachineMaster: GetMachineMasterUseCase,
    private val getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {
```

Change `init` to also seed the default warehouse:

```kotlin
    init {
        load()
        viewModelScope.launch {
            getCurrentUser().collect { user ->
                _uiState.update {
                    it.copy(
                        selectedBuilding = it.selectedBuilding
                            ?: user?.let { u -> Buildings.defaultFor(u.plantId) },
                    )
                }
            }
        }
    }
```

Change the building handler to non-null:

```kotlin
    fun onBuildingSelected(building: String) =
        _uiState.update { it.copy(selectedBuilding = building) }
```

(`onLocationSelected` and `onSubCategorySelected` are unchanged.)

- [ ] **Step 4: Update `MachineMasterScreen` — swap the building dropdown for `WarehouseFilter`**

In `presentation/machinemaster/MachineMasterScreen.kt`:

Add imports:

```kotlin
import com.candorfoods.factoryops.domain.model.Buildings
import com.candorfoods.factoryops.presentation.common.components.WarehouseFilter
```

In `Content(...)`, update the `FilterBar(...)` call to drop the `buildings` argument and pass a non-null selected building:

```kotlin
        FilterBar(
            locations           = state.locations,
            subCategories       = state.subCategories,
            selectedBuilding    = state.selectedBuilding ?: Buildings.ALL.first(),
            selectedLocation    = state.selectedLocation,
            selectedSubCategory = state.selectedSubCategory,
            onBuildingSelect    = viewModel::onBuildingSelected,
            onLocationSelect    = viewModel::onLocationSelected,
            onSubCategorySelect = viewModel::onSubCategorySelected,
        )
```

Replace the `FilterBar` composable's signature and its building row. The new version:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    locations: List<String>,
    subCategories: List<String>,
    selectedBuilding: String,
    selectedLocation: String?,
    selectedSubCategory: String?,
    onBuildingSelect: (String) -> Unit,
    onLocationSelect: (String?) -> Unit,
    onSubCategorySelect: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            WarehouseFilter(
                modifier = Modifier.weight(1f),
                selected = selectedBuilding,
                onSelected = onBuildingSelect,
            )
            FilterDropdown(
                modifier = Modifier.weight(1f),
                label    = "Location",
                options  = listOf<String?>(null) + locations,
                selected = selectedLocation,
                display  = { it ?: "All locations" },
                onSelect = onLocationSelect,
            )
        }

        FilterDropdown(
            modifier = Modifier.fillMaxWidth(),
            label    = "Sub-category",
            options  = listOf<String?>(null) + subCategories,
            selected = selectedSubCategory,
            display  = { it ?: "All sub-categories" },
            onSelect = onSubCategorySelect,
        )
    }
}
```

(The generic `FilterDropdown` composable and the `<T>` building usages are otherwise unchanged; it is still used for Location and Sub-category.)

- [ ] **Step 5: Run the test + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.MachineMasterBuildingFilterTest"`
Then: `./gradlew :app:compileDebugKotlin`
Expected: PASS (2 tests), then BUILD SUCCESSFUL. (If compile fails on an unused-import for `collect`, remove the `kotlinx.coroutines.flow.collect` import — `collect` on a `Flow` is available without it on newer coroutines; keep the build green.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/machinemaster/MachineMasterViewModel.kt \
        app/src/main/java/com/candorfoods/factoryops/presentation/machinemaster/MachineMasterScreen.kt \
        app/src/test/java/com/candorfoods/factoryops/MachineMasterBuildingFilterTest.kt
git commit -m "feat: standardize Machine Master to fixed warehouse filter (technician)"
```

---

## Task 5: Admin — machine tagging list

`AdminHomeViewModel` already injects `GetCurrentUserUseCase` and exposes `currentUser`. Add `selectedBuilding`, a building predicate in `filteredRows`, seed the default, and render the dropdown. This list is over the legacy `Machine` model (`Machine.building`).

**Files:**
- Modify: `presentation/admin/AdminHomeViewModel.kt`
- Modify: `presentation/admin/AdminHomeScreen.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/AdminHomeBuildingFilterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/candorfoods/factoryops/AdminHomeBuildingFilterTest.kt`:

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.LoadFactorSource
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.MachineStatus
import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.presentation.admin.AdminHomeUiState
import com.candorfoods.factoryops.presentation.admin.AdminMachineRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdminHomeBuildingFilterTest {

    private fun machine(id: String, building: String, name: String = id) = Machine(
        id = id,
        code = id,
        name = name,
        location = "Warehouse 1",
        building = building,
        plantId = "PLANT-01",
        machineType = MachineType.OTHER,
        category = null,
        ratedKw = 1.0,
        loadFactor = 0.5,
        loadFactorSource = LoadFactorSource.ASSUMED,
        criticality = Criticality.C,
        expectedRunHours = 8.0,
        currentStatus = MachineStatus.IDLE,
        updatedAt = 0L,
    )

    private val rows = listOf(
        AdminMachineRow(machine("A185-1", "A-185", name = "Fan A"), isTagged = false),
        AdminMachineRow(machine("W202-1", "W-202", name = "Fan W"), isTagged = false),
    )

    @Test
    fun `filteredRows shows only the selected building`() {
        val state = AdminHomeUiState(rows = rows, selectedBuilding = "A-185")
        assertEquals(listOf("A185-1"), state.filteredRows.map { it.machine.id })
        assertTrue(state.filteredRows.all { it.machine.building == "A-185" })
    }

    @Test
    fun `building predicate composes with search`() {
        val state = AdminHomeUiState(rows = rows, selectedBuilding = "W-202", searchQuery = "Fan")
        assertEquals(listOf("W202-1"), state.filteredRows.map { it.machine.id })
    }

    @Test
    fun `null selectedBuilding shows all`() {
        val state = AdminHomeUiState(rows = rows, selectedBuilding = null)
        assertEquals(2, state.filteredRows.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.AdminHomeBuildingFilterTest"`
Expected: FAIL — `selectedBuilding` is not a parameter of `AdminHomeUiState` (compilation error).

- [ ] **Step 3: Add `selectedBuilding` + predicate to `AdminHomeUiState`**

In `presentation/admin/AdminHomeViewModel.kt`, add the field to `AdminHomeUiState` (after `searchQuery`):

```kotlin
    val searchQuery: String           = "",
    val selectedBuilding: String?     = null,
```

Replace the `filteredRows` getter with one that also applies the building predicate:

```kotlin
    val filteredRows: List<AdminMachineRow>
        get() {
            val q = searchQuery.trim()
            return rows.filter { row ->
                (selectedBuilding == null || row.machine.building.equals(selectedBuilding, ignoreCase = true)) &&
                    (q.isEmpty() ||
                        row.machine.name.contains(q, ignoreCase = true) ||
                        row.machine.code.contains(q, ignoreCase = true) ||
                        row.machine.location.contains(q, ignoreCase = true))
            }
        }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.AdminHomeBuildingFilterTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Seed the default + add handler in `AdminHomeViewModel`**

Add the import:

```kotlin
import com.candorfoods.factoryops.domain.model.Buildings
```

Replace the current-user collector in `init`:

```kotlin
        viewModelScope.launch {
            getCurrentUser().collect { user ->
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        selectedBuilding = it.selectedBuilding
                            ?: user?.let { u -> Buildings.defaultFor(u.plantId) },
                    )
                }
            }
        }
```

Add the handler (next to `onSearchChange`):

```kotlin
    fun onBuildingSelected(building: String) =
        _uiState.update { it.copy(selectedBuilding = building) }
```

- [ ] **Step 6: Render the dropdown in `AdminHomeScreen`**

In `presentation/admin/AdminHomeScreen.kt`, add imports:

```kotlin
import com.candorfoods.factoryops.domain.model.Buildings
import com.candorfoods.factoryops.presentation.common.components.WarehouseFilter
```

In the `else -> Column(modifier = Modifier.fillMaxSize()) {` branch, add the filter as the FIRST child (above the search `OutlinedTextField`):

```kotlin
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    WarehouseFilter(
                        selected = uiState.selectedBuilding
                            ?: Buildings.defaultFor(uiState.currentUser?.plantId),
                        onSelected = viewModel::onBuildingSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        // ... unchanged ...
```

- [ ] **Step 7: Compile + test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.AdminHomeBuildingFilterTest"`
Then: `./gradlew :app:compileDebugKotlin`
Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/admin/AdminHomeViewModel.kt \
        app/src/main/java/com/candorfoods/factoryops/presentation/admin/AdminHomeScreen.kt \
        app/src/test/java/com/candorfoods/factoryops/AdminHomeBuildingFilterTest.kt
git commit -m "feat: warehouse filter on admin machine-tagging list"
```

---

## Task 6: Supervisor — PM plan machine picker

`PlanEditorScreen`'s `MachineDropdown` lets a supervisor pick from `List<Machine>`. Add a `WarehouseFilter` above it that scopes the picker options by warehouse (default home), and clear the machine selection if it falls outside the chosen warehouse.

**Files:**
- Modify: `presentation/supervisor/PlanEditorViewModel.kt`
- Modify: `presentation/supervisor/PlanEditorScreen.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/PlanEditorBuildingFilterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/candorfoods/factoryops/PlanEditorBuildingFilterTest.kt`:

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.LoadFactorSource
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.MachineStatus
import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.presentation.supervisor.PlanEditorUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlanEditorBuildingFilterTest {

    private fun machine(id: String, building: String) = Machine(
        id = id,
        code = id,
        name = id,
        location = "Warehouse 1",
        building = building,
        plantId = "PLANT-01",
        machineType = MachineType.PUMP,
        category = null,
        ratedKw = 1.0,
        loadFactor = 0.5,
        loadFactorSource = LoadFactorSource.ASSUMED,
        criticality = Criticality.C,
        expectedRunHours = 8.0,
        currentStatus = MachineStatus.IDLE,
        updatedAt = 0L,
    )

    private val machines = listOf(
        machine("A185-1", "A-185"),
        machine("W202-1", "W-202"),
    )

    @Test
    fun `machinesForBuilding scopes the picker to the selected warehouse`() {
        val state = PlanEditorUiState(machines = machines, selectedBuilding = "A-185")
        assertEquals(listOf("A185-1"), state.machinesForBuilding.map { it.id })
    }

    @Test
    fun `null selectedBuilding shows all machines`() {
        val state = PlanEditorUiState(machines = machines, selectedBuilding = null)
        assertEquals(2, state.machinesForBuilding.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.PlanEditorBuildingFilterTest"`
Expected: FAIL — `selectedBuilding` / `machinesForBuilding` are not members of `PlanEditorUiState` (compilation error).

- [ ] **Step 3: Add `selectedBuilding` + `machinesForBuilding` to `PlanEditorUiState`**

In `presentation/supervisor/PlanEditorViewModel.kt`, add the field to `PlanEditorUiState` (after `machines`):

```kotlin
    val machines: List<Machine> = emptyList(),
    val selectedBuilding: String? = null,
```

Add the derived list inside the `PlanEditorUiState` body (next to `templatesForSelectedMachine`):

```kotlin
    val machinesForBuilding: List<Machine>
        get() = machines.filter {
            selectedBuilding == null || it.building.equals(selectedBuilding, ignoreCase = true)
        }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.PlanEditorBuildingFilterTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Inject current user, seed default, add handler in `PlanEditorViewModel`**

Add imports:

```kotlin
import com.candorfoods.factoryops.domain.model.Buildings
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
```

Add the use case to the constructor:

```kotlin
@HiltViewModel
class PlanEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val machineRepo: MachineRepository,
    private val templateRepo: PmTemplateRepository,
    private val planRepo: PmPlanRepository,
    private val getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {
```

In `init`, seed the default warehouse when the machines/templates load (extend the existing first `update`):

```kotlin
            val machines = machineRepo.observeAllMachines().first()
            val templates = templateRepo.observeActiveTemplates().first()
            val user = getCurrentUser().first()
            _uiState.update {
                it.copy(
                    machines = machines,
                    allTemplates = templates,
                    selectedBuilding = it.selectedBuilding ?: Buildings.defaultFor(user?.plantId),
                    isLoading = false,
                )
            }
```

Add the handler (it also clears a machine selection that no longer belongs to the chosen warehouse):

```kotlin
    fun onBuildingSelected(building: String) = _uiState.update { state ->
        val stillValid = state.machines.any {
            it.id == state.selectedMachineId && it.building.equals(building, ignoreCase = true)
        }
        state.copy(
            selectedBuilding = building,
            selectedMachineId = state.selectedMachineId?.takeIf { stillValid },
        )
    }
```

- [ ] **Step 6: Render the dropdown + scope the picker in `PlanEditorScreen`**

In `presentation/supervisor/PlanEditorScreen.kt`, add imports:

```kotlin
import com.candorfoods.factoryops.domain.model.Buildings
import com.candorfoods.factoryops.presentation.common.components.WarehouseFilter
```

In the "Plan setup" card `Column`, add the `WarehouseFilter` directly ABOVE the existing `MachineDropdown(...)`, and feed the picker the building-scoped list:

```kotlin
                    Text("Plan setup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    WarehouseFilter(
                        selected = uiState.selectedBuilding ?: Buildings.ALL.first(),
                        onSelected = viewModel::onBuildingSelected,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    MachineDropdown(
                        machines = uiState.machinesForBuilding,
                        selectedId = uiState.selectedMachineId,
                        onSelect = viewModel::onMachineSelected
                    )
```

- [ ] **Step 7: Compile + test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.PlanEditorBuildingFilterTest"`
Then: `./gradlew :app:compileDebugKotlin`
Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/PlanEditorViewModel.kt \
        app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/PlanEditorScreen.kt \
        app/src/test/java/com/candorfoods/factoryops/PlanEditorBuildingFilterTest.kt
git commit -m "feat: warehouse filter on supervisor PM plan machine picker"
```

---

## Task 7: Full verification

- [ ] **Step 1: Run the entire unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all existing tests plus the 5 new test classes (`BuildingsTest`, `MachineListBuildingFilterTest`, `MachineMasterBuildingFilterTest`, `AdminHomeBuildingFilterTest`, `PlanEditorBuildingFilterTest`) pass.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke test (emulator or device)**

Verify the warehouse dropdown appears and filters, defaulting to the user's home warehouse, on each surface:

| Login as | Open | Expect |
|---|---|---|
| OPERATOR (home A-185) | Machines (home) | Dropdown defaults to **A-185**; list shows only A-185; switching to W-202 shows W-202 machines; search/category/status still work within the warehouse. |
| SUPERVISOR | Dashboard → Machine List | Dropdown present; defaults to home (or A-185 if `both`); filters list. |
| HEAD | Dashboard → Machine List | Dropdown present; defaults to A-185; filters list. |
| TECHNICIAN | Dashboard → Machine List (Machine Master) | Building dropdown is now the fixed A-185/W-202 control; Location + Sub-category dropdowns unchanged; sections filter by warehouse. |
| ADMIN | Admin home | Dropdown present above search; tagging list filters by warehouse. |
| SUPERVISOR | PM Manager → New PM plan | Warehouse dropdown above the Machine picker; picker lists only that warehouse's machines; changing warehouse clears an out-of-warehouse machine selection. |

- [ ] **Step 4: Commit any final touch-ups** (only if Step 3 surfaced fixes)

```bash
git add -A
git commit -m "fix: warehouse filter manual-test follow-ups"
```

---

## Self-Review (completed by plan author)

**1. Spec coverage:**
- Shared fixed A-185/W-202 dropdown, no "All", default home → Task 1 (`Buildings.defaultFor`) + Task 2 (`WarehouseFilter`) + per-screen seeding (Tasks 3–6). ✓
- Operator Machine List (also SUPERVISOR/HEAD) → Task 3. ✓
- Machine Master standardized to fixed control (TECHNICIAN) → Task 4. ✓
- Admin tagging list → Task 5. ✓
- PM plan machine picker (SUPERVISOR) → Task 6. ✓
- TransferForm → dropped in spec (no browsable list). Not a task. ✓
- Building predicate composes with existing search/category/status → covered by tests in Tasks 3 & 5. ✓
- Home-warehouse normalizer tested across the spec's example inputs → Task 1. ✓
- No backend/permission/action changes → none in plan. ✓

**2. Placeholder scan:** No "TBD"/"add error handling"/"similar to Task N". Every code step shows full code. (One deliberate callout: Task 6 Step 4 flags a package-typo guard.) ✓

**3. Type consistency:** `selectedBuilding: String?` everywhere; `onBuildingSelected(building: String)` (non-null) everywhere; `WarehouseFilter(selected: String, onSelected: (String)->Unit)` consumed consistently; `Buildings.ALL` / `Buildings.defaultFor` signatures match Task 1. `MachineListUiState` routes `categories`/`items`/`countOf` through one `buildingFiltered`. ✓
