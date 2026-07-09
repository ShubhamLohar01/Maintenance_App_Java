# Breakdown Pre-fill Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** Replace the separate "complete F.06" screen + on-device draft with a single flow: tapping a QC-approved breakdown opens the existing **Create F.06 form pre-filled** from the breakdown's lifecycle data; the technician edits and **submits to the DB** (existing endpoint). Remove the now-unused completion/draft code. Finish the records list (submitted F.06 + QC-approved, per-record download).

**Architecture:** A pure `breakdownFlagToEntry` mapping seeds one form row from a `BreakdownFlag`. `BreakdownFormViewModel` accepts an optional `breakdownId` nav-arg and pre-fills. The QC-approved card navigates to the pre-filled form instead of the removed completion screen. The records list merges submitted F.06 records (Feature 3 GET) with QC-approved flags.

**Tech Stack:** Kotlin, Compose, Hilt, Room, Coroutines, JUnit5. Spec: `docs/superpowers/specs/2026-06-27-breakdown-records-both-sources-design.md` + this consolidation supersedes the Feature-2 completion screen in `2026-06-26-breakdown-f06-completion-design.md`.

## Notes for the implementer
- **Not a git repo** — skip "Commit". Compile from repo root: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -40)`.
- This is ONE coupled change — apply ALL tasks, then compile once at the end (intermediate states won't compile).
- **Concurrent edits:** errors only in `presentation/qc`/`presentation/technician` you didn't touch are NOT yours.
- **READ each file before editing.** Delete files with `rm` via Bash.
- Reuse (keep): `util/pdf/*` (renderer/share/BreakdownRow/BreakdownDocData), `ChecklistRepository.listBreakdowns()` + `BreakdownRecord` + `BreakdownRecordDto` (Feature 3 Tasks 1–2, already in), `isApproved`, `breakdownStatusLabel`, `toF06Row(BreakdownRecord)` in `BreakdownF06Logic.kt`.

---

## Task 1: Delete the superseded completion + draft code

- [ ] **Step 1: Delete these files** (Bash `rm`):
```
src/main/java/com/candorfoods/factoryops/presentation/checklist/BreakdownCompleteScreen.kt
src/main/java/com/candorfoods/factoryops/presentation/checklist/BreakdownCompleteViewModel.kt
src/main/java/com/candorfoods/factoryops/data/local/entity/BreakdownF06DraftEntity.kt
src/main/java/com/candorfoods/factoryops/data/local/dao/BreakdownF06DraftDao.kt
src/main/java/com/candorfoods/factoryops/domain/model/BreakdownF06Draft.kt
src/main/java/com/candorfoods/factoryops/domain/repository/BreakdownF06DraftRepository.kt
src/main/java/com/candorfoods/factoryops/data/repository/BreakdownF06DraftRepositoryImpl.kt
```

- [ ] **Step 2: AppDatabase** — in `data/local/AppDatabase.kt`: remove `BreakdownF06DraftEntity::class` from `entities`, remove `abstract fun breakdownF06DraftDao(): BreakdownF06DraftDao`, change `version = 9` → `version = 10`, add comment `// v9 → v10: removes breakdown_f06_drafts (F.06 now submits straight to the backend).`

- [ ] **Step 3: RepositoryModule** — in `di/RepositoryModule.kt`: remove the `bindBreakdownF06DraftRepository` `@Binds` method and its two imports (`BreakdownF06DraftRepositoryImpl`, `BreakdownF06DraftRepository`).

- [ ] **Step 4: Screen.kt** — remove the `object BreakdownComplete : Screen(...) { ... }` block entirely.

- [ ] **Step 5: NavGraph.kt** — remove the `import ...BreakdownCompleteScreen` and the whole `composable(route = Screen.BreakdownComplete.route, ...) { BreakdownCompleteScreen(...) }` block. (The `onOpen` wiring is rewired in Task 4.)

---

## Task 2: Trim `BreakdownF06Logic.kt` + its tests; add `breakdownFlagToEntry`

**Files:** Edit `presentation/checklist/BreakdownF06Logic.kt`, `test/.../BreakdownF06LogicTest.kt`.

- [ ] **Step 1: Trim the logic file.** In `BreakdownF06Logic.kt` REMOVE these now-unused declarations: `data class F06Fields`, `fun defaultF06Fields`, `fun BreakdownF06Draft.toFields`, `fun F06Fields.toDraft`, `fun buildF06Row(flag, fields)`. Also remove the `import ...BreakdownF06Draft`. KEEP `isApproved`, `breakdownStatusLabel`, `toF06Row(BreakdownRecord)`. Then ADD (it references `BreakdownEntryState` from `BreakdownFormViewModel.kt`, same package — no import needed):
```kotlin
/** Pre-fill one F.06 form row from a QC-approved breakdown's lifecycle data. */
fun breakdownFlagToEntry(flag: BreakdownFlag, key: String): BreakdownEntryState = BreakdownEntryState(
    key = key,
    date = DateTimeFormatter.isoDate(flag.raisedAt),
    location = flag.building.orEmpty(),
    machineName = flag.assetName?.ifBlank { null } ?: flag.machineId,
    equipmentModel = flag.machineId,
    problem = flag.description,
    typeOfMaintenance = "",
    partOfMachine = "",
    reason = flag.partsUsed.orEmpty(),                                  // spare parts → Reason
    durationStart = DateTimeFormatter.formatTime(flag.raisedAt),
    durationEnd = flag.resolvedAt?.let { DateTimeFormatter.formatTime(it) }.orEmpty(),
    machineOperatorSign = "",                                          // record stores operator id, not name
    maintenancePersonSign = flag.resolvedByName.orEmpty(),
    qcClearanceSign = flag.qcDecidedByName.orEmpty(),
)
```

- [ ] **Step 2: Trim + extend the test.** In `BreakdownF06LogicTest.kt` REMOVE the tests that reference the deleted helpers: `defaultF06Fields ...` (both), `buildF06Row maps flag plus entered fields ...`, and `fields round-trip through a draft`. KEEP the `isApproved`, `status label`, `toF06Row`, and `BreakdownRecordDto toDomain` tests + the `flag(...)` and `record(...)` builders. ADD:
```kotlin
    @Test fun `breakdownFlagToEntry pre-fills the reusable F06 columns`() {
        val e = breakdownFlagToEntry(flag(), key = "bd-0")
        assertEquals("bd-0", e.key)
        assertEquals("W-202", e.location)
        assertEquals("Band Sealer", e.machineName)
        assertEquals("W202-0007", e.equipmentModel)
        assertEquals("Belt snapped", e.problem)
        assertEquals("QC Sam", e.qcClearanceSign)
        assertEquals("Tech Ravi", e.maintenancePersonSign)
        assertEquals("", e.machineOperatorSign)   // gap: only operator id is stored
    }
```
  Add the import `import com.candorfoods.factoryops.presentation.checklist.breakdownFlagToEntry`.
  (The `flag()` builder already exists from the kept tests; it sets `resolvedByName = "Tech Ravi"`,
  `qcDecidedByName = "QC Sam"`, `building = "W-202"`, `machineId = "W202-0007"`,
  `assetName = "Band Sealer"`, `description = "Belt snapped"`.)

- [ ] **Step 3: Run tests** — `... --tests "com.candorfoods.factoryops.BreakdownF06LogicTest"` → PASS.

---

## Task 3: `BreakdownFormViewModel` pre-fill from a breakdown

**Files:** Edit `presentation/checklist/BreakdownFormViewModel.kt`.

- [ ] **Step 1:** Add imports:
```kotlin
import androidx.lifecycle.SavedStateHandle
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.navigation.Screen
import kotlinx.coroutines.flow.firstOrNull
```
Change the constructor to add `savedStateHandle` + `breakdownRepository`:
```kotlin
@HiltViewModel
class BreakdownFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val checklistRepository: ChecklistRepository,
    private val breakdownRepository: BreakdownRepository,
    private val getMachineMaster: GetMachineMasterUseCase,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {

    private val breakdownId: String? = savedStateHandle.get<String>(Screen.BreakdownForm.ARG)
```
Replace the `init { loadMachines() }` with:
```kotlin
    init {
        loadMachines()
        if (!breakdownId.isNullOrBlank()) prefillFromBreakdown(breakdownId)
    }

    /** Seed the single form row from a QC-approved breakdown (technician then reviews/edits). */
    private fun prefillFromBreakdown(id: String) {
        viewModelScope.launch {
            val flag = breakdownRepository.observeFlagById(id).firstOrNull() ?: return@launch
            _state.update { it.copy(entries = listOf(breakdownFlagToEntry(flag, "bd-${seq++}"))) }
        }
    }
```

- [ ] **Step 2:** Compile happens at the end (Task 6). (`breakdownFlagToEntry` is same-package; `observeFlagById` exists on `BreakdownRepository`.)

---

## Task 4: Nav — optional `breakdownId` on the form + rewire the list tap

**Files:** Edit `navigation/Screen.kt`, `navigation/NavGraph.kt`.

- [ ] **Step 1: Screen.kt** — replace `object BreakdownForm : Screen("checklist/breakdown/new")` with:
```kotlin
    object BreakdownForm : Screen("checklist/breakdown/new?breakdownId={breakdownId}") {
        // No id → blank F.06. With id → pre-fill from that QC-approved breakdown.
        fun createRoute(breakdownId: String? = null) =
            if (breakdownId == null) "checklist/breakdown/new"
            else "checklist/breakdown/new?breakdownId=$breakdownId"
        const val ARG = "breakdownId"
    }
```

- [ ] **Step 2: NavGraph.kt** — (a) ensure imports `androidx.navigation.NavType` + `androidx.navigation.navArgument` exist (add if missing). (b) Replace the `BreakdownForm` composable with an arg-declaring version:
```kotlin
        composable(
            route = Screen.BreakdownForm.route,
            arguments = listOf(navArgument(Screen.BreakdownForm.ARG) {
                type = NavType.StringType; nullable = true; defaultValue = null
            }),
        ) {
            BreakdownFormScreen(
                onBack      = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
            )
        }
```
   (c) In the `BreakdownList` composable, point `onOpen` at the pre-filled form:
```kotlin
                onOpen   = { id -> navController.navigate(Screen.BreakdownForm.createRoute(breakdownId = id)) },
```
   The Create FAB still calls `onCreate = { navController.navigate(Screen.BreakdownForm.route) }` (no id) — `Screen.BreakdownForm.route` is the templated string; navigating to it with the optional nullable arg resolves to a blank form. (If navigation to the templated route misbehaves, use `Screen.BreakdownForm.createRoute()` for the FAB too.)

---

## Task 5: Finish the records list (merge + two sections + download, no drafts)

**Files:** Edit `presentation/checklist/BreakdownListViewModel.kt`, `presentation/checklist/BreakdownListScreen.kt`.

- [ ] **Step 1: ViewModel** — replace the whole file with:
```kotlin
package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.util.pdf.BreakdownDocData
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BreakdownListItem(val flag: BreakdownFlag, val isApproved: Boolean)

data class BreakdownListUiState(
    val isLoading: Boolean = true,
    val items: List<BreakdownListItem> = emptyList(),
    val submitted: List<BreakdownRecord> = emptyList(),
    val submittedLoading: Boolean = true,
    val submittedError: String? = null,
    val isExporting: Boolean = false,
    val pdfToShare: File? = null,
    val exportError: String? = null,
)

@HiltViewModel
class BreakdownListViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val checklistRepository: ChecklistRepository,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreakdownListUiState())
    val uiState: StateFlow<BreakdownListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { breakdownRepository.refreshOpenFlags("both") }
        viewModelScope.launch {
            breakdownRepository.observeAllFlags().collect { flags ->
                _uiState.update {
                    it.copy(isLoading = false, items = flags.map { f -> BreakdownListItem(f, isApproved(f)) })
                }
            }
        }
        loadSubmitted()
    }

    fun loadSubmitted() {
        viewModelScope.launch {
            _uiState.update { it.copy(submittedLoading = true, submittedError = null) }
            when (val r = checklistRepository.listBreakdowns()) {
                is Result.Success -> _uiState.update {
                    it.copy(submittedLoading = false, submitted = r.data.sortedByDescending { rec -> rec.id })
                }
                is Result.Error -> _uiState.update {
                    it.copy(submittedLoading = false, submittedError = r.message)
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { breakdownRepository.refreshOpenFlags("both") }
        loadSubmitted()
    }

    fun exportSubmitted(record: BreakdownRecord) {
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }
            try {
                val file = pdfRenderer.renderBreakdown(
                    BreakdownDocData("CFPLA.C4.F.06", record.verifiedBy, listOf(record.toF06Row()))
                )
                _uiState.update { it.copy(isExporting = false, pdfToShare = file) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, exportError = e.message ?: "Couldn't create PDF") }
            }
        }
    }

    fun onPdfShared() = _uiState.update { it.copy(pdfToShare = null) }
    fun onExportErrorShown() = _uiState.update { it.copy(exportError = null) }
}
```

- [ ] **Step 2: Screen** — replace `BreakdownListScreen.kt` with the two-section version (uses the new `BreakdownListItem(flag, isApproved)` — no `isCompleted`):
```kotlin
package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.BreakdownRecord
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.pdf.PdfShareLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: BreakdownListViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.pdfToShare) {
        ui.pdfToShare?.let { file ->
            try {
                if (!PdfShareLauncher.share(context, file)) snackbar.showSnackbar("No app available to open the PDF")
            } catch (e: Exception) {
                snackbar.showSnackbar("Couldn't share the PDF")
            } finally {
                viewModel.onPdfShared()
            }
        }
    }
    LaunchedEffect(ui.exportError) {
        ui.exportError?.let { snackbar.showSnackbar("Couldn't create the PDF"); viewModel.onExportErrorShown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breakdown Checklist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Create") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onCreate,
            )
        },
    ) { padding ->
        val nothing = ui.items.isEmpty() && ui.submitted.isEmpty() && !ui.isLoading && !ui.submittedLoading
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (ui.isLoading && ui.submittedLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (nothing && ui.submittedError == null) {
                Text(
                    "No breakdown records yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item { SectionHeader("Submitted F.06 records") }
                    if (ui.submittedError != null) {
                        item { SoftNote("Couldn't load submitted records — tap Create to add one, or retry once the server is ready.") }
                    } else if (ui.submitted.isEmpty()) {
                        item { SoftNote(if (ui.submittedLoading) "Loading…" else "None yet.") }
                    } else {
                        items(ui.submitted, key = { "sub-${it.id}" }) { rec ->
                            SubmittedRecordCard(rec, onDownload = { viewModel.exportSubmitted(rec) })
                        }
                    }

                    item { SectionHeader("Awaiting F.06 completion (QC-approved)") }
                    if (ui.items.isEmpty()) {
                        item { SoftNote("No operator breakdowns yet.") }
                    } else {
                        items(ui.items, key = { "flag-${it.flag.id}" }) { item ->
                            BreakdownFlagCard(item, onOpen = { onOpen(item.flag.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = Spacing.sm))
}

@Composable
private fun SoftNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}

@Composable
private fun SubmittedRecordCard(record: BreakdownRecord, onDownload: () -> Unit) {
    val name = record.machineName.ifBlank { record.equipmentModelNo }
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (record.location.isNotBlank()) {
                Text(record.location, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Text("${record.recordDate} · ${record.problemInBrief}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            OutlinedButton(onClick = onDownload, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Download")
            }
        }
    }
}

@Composable
private fun BreakdownFlagCard(item: BreakdownListItem, onOpen: () -> Unit) {
    val flag = item.flag
    val name = flag.assetName?.ifBlank { null } ?: flag.machineId
    val enabled = item.isApproved
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.45f)

    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = if (enabled) onOpen else null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else muted,
                    modifier = Modifier.weight(1f))
                StatusChip(breakdownStatusLabel(flag), enabled)
            }
            if (!flag.building.isNullOrBlank()) {
                Text(flag.building!!, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Text("${DateTimeFormatter.formatDate(flag.raisedAt)} · ${flag.description}",
                style = MaterialTheme.typography.bodyMedium, color = muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (enabled) {
                    Text("Tap to complete F.06",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null,
                        modifier = Modifier.size(16.dp), tint = muted)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Awaiting QC approval", style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, approved: Boolean) {
    val container = if (approved) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
    AssistChip(
        onClick = {}, enabled = false, label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        ),
    )
}
```

---

## Task 6: Build, tests, manual handoff

- [ ] **Step 1:** `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:testDebugUnitTest :app:assembleDebug 2>&1 | tail -25)` → BUILD SUCCESSFUL (incl. `BreakdownF06LogicTest`). Report any unrelated qc/technician errors separately.
- [ ] **Step 2 (user):** Breakdown page → tap a QC-approved breakdown → the F.06 form opens pre-filled (machine/date/location/problem/duration/maintenance & QC signs); edit → Submit → it appears under "Submitted F.06 records" (once the backend GET is live) with a Download button. Create (blank) still works.

---

## Self-review
**Coverage:** remove completion/draft (T1, T2) ✅; pre-fill mapping + test (T2) ✅; form pre-fill via nav arg (T3, T4) ✅; tap rewire to pre-filled form (T4) ✅; records list merge + two sections + download, no drafts (T5) ✅.
**Placeholders:** none.
**Type consistency:** `BreakdownListItem(flag, isApproved)` (no isCompleted) used consistently in VM + screen; `breakdownFlagToEntry(flag, key): BreakdownEntryState` matches `BreakdownEntryState` fields; `Screen.BreakdownForm.ARG = "breakdownId"` matches the VM's `savedStateHandle.get<String>(... .ARG)`; `listBreakdowns()`/`toF06Row()`/`renderBreakdown` unchanged from Feature 3.
**Risk:** removing the Room entity (v10 destructive) wipes local breakdown_flags — re-syncs from backend; acceptable. Confirm no other code references the deleted draft repo/`F06Fields`/`buildF06Row`/`defaultF06Fields` (grep after deletion).
