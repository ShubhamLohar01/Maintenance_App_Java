# Breakdown Records — Both Sources + Per-Record Download — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** The Breakdown records page lists submitted F.06 sheets (new backend GET) AND QC-approved operator breakdowns, each downloadable; root-cause fix for "submitted record not showing" (data-source mismatch).

**Architecture:** Add a read path for submitted F.06 records (`GET /preventive-maintenance/breakdowns` → DTO → domain → repo), merge it into `BreakdownListViewModel` alongside the existing QC-approved flags, and render two sections with per-record Download reusing `ChecklistPdfRenderer.renderBreakdown`.

**Tech Stack:** Kotlin, Compose, Hilt, Retrofit/kotlinx-serialization, Coroutines, JUnit5. Spec: `docs/superpowers/specs/2026-06-27-breakdown-records-both-sources-design.md`.

## Notes for the implementer
- **Not a git repo** — skip "Commit" steps.
- Compile from repo root (Git Bash): `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -40)`. Tests: `... :app:testDebugUnitTest --tests "<Class>" ...`.
- **Concurrent edits:** errors only in `presentation/qc`/`presentation/technician` you didn't touch are NOT yours — report separately.
- READ each existing file before editing.
- The submitted-records section stays EMPTY at runtime until the backend GET ships — that is expected; the app must handle the error/empty softly (don't treat a failing GET as a build problem).
- Reuse (already present): `util/pdf/ChecklistPdfRenderer.renderBreakdown`, `util/pdf/PdfShareLauncher.share`, `util/pdf/BreakdownRow`, `util/pdf/BreakdownDocData`; `presentation/checklist/BreakdownF06Logic.kt`, `BreakdownListItem`, `isApproved`, `breakdownStatusLabel` (Feature 2).

---

## Task 1: Read path — domain model + DTO + API + repository

**Files:** Create `domain/model/BreakdownRecord.kt`; Edit `data/remote/dto/ChecklistDtos.kt`, `data/remote/api/ChecklistApi.kt`, `domain/repository/ChecklistRepository.kt`, `data/repository/ChecklistRepositoryImpl.kt`.

- [ ] **Step 1: Domain model** — `src/main/java/com/candorfoods/factoryops/domain/model/BreakdownRecord.kt`:
```kotlin
package com.candorfoods.factoryops.domain.model

/** A submitted F.06 Breakdown Maintenance Record entry (one register row),
 *  read from GET /preventive-maintenance/breakdowns. */
data class BreakdownRecord(
    val id: Int,
    val docNo: String,
    val recordDate: String,
    val location: String,
    val machineName: String,
    val equipmentModelNo: String,
    val problemInBrief: String,
    val typeOfMaintenance: String,
    val partOfMachine: String,
    val temporaryReason: String,
    val durationStart: String,
    val durationEnd: String,
    val machineOperatorSign: String,
    val maintenancePersonSign: String,
    val qcClearanceSign: String,
    val verifiedBy: String,
    val createdAt: String,
)
```

- [ ] **Step 2: DTO + mapper** — in `data/remote/dto/ChecklistDtos.kt` add the import
  `import com.candorfoods.factoryops.domain.model.BreakdownRecord` and append:
```kotlin
@Serializable
data class BreakdownRecordDto(
    @SerialName("id")                      val id: Int,
    @SerialName("doc_no")                  val docNo: String = "CFPLA.C4.F.06",
    @SerialName("record_date")             val recordDate: String = "",
    @SerialName("location")                val location: String = "",
    @SerialName("machine_name")            val machineName: String = "",
    @SerialName("equipment_model_no")      val equipmentModelNo: String = "",
    @SerialName("problem_in_brief")        val problemInBrief: String = "",
    @SerialName("type_of_maintenance")     val typeOfMaintenance: String = "",
    @SerialName("part_of_machine")         val partOfMachine: String = "",
    @SerialName("temporary_reason")        val temporaryReason: String = "",
    @SerialName("duration_start")          val durationStart: String = "",
    @SerialName("duration_end")            val durationEnd: String = "",
    @SerialName("machine_operator_sign")   val machineOperatorSign: String = "",
    @SerialName("maintenance_person_sign") val maintenancePersonSign: String = "",
    @SerialName("qc_clearance_sign")       val qcClearanceSign: String = "",
    @SerialName("verified_by")             val verifiedBy: String = "",
    @SerialName("created_at")              val createdAt: String = "",
)

fun BreakdownRecordDto.toDomain() = BreakdownRecord(
    id = id, docNo = docNo, recordDate = recordDate, location = location,
    machineName = machineName, equipmentModelNo = equipmentModelNo,
    problemInBrief = problemInBrief, typeOfMaintenance = typeOfMaintenance,
    partOfMachine = partOfMachine, temporaryReason = temporaryReason,
    durationStart = durationStart, durationEnd = durationEnd,
    machineOperatorSign = machineOperatorSign, maintenancePersonSign = maintenancePersonSign,
    qcClearanceSign = qcClearanceSign, verifiedBy = verifiedBy, createdAt = createdAt,
)
```

- [ ] **Step 3: API** — in `data/remote/api/ChecklistApi.kt` add import
  `import com.candorfoods.factoryops.data.remote.dto.BreakdownRecordDto` and the method:
```kotlin
    /** List submitted Breakdown F.06 records (newest first). */
    @GET("preventive-maintenance/breakdowns")
    suspend fun listBreakdowns(
        @Header("Authorization") token: String,
    ): List<BreakdownRecordDto>
```

- [ ] **Step 4: Repository interface** — in `domain/repository/ChecklistRepository.kt` add import
  `import com.candorfoods.factoryops.domain.model.BreakdownRecord` and:
```kotlin
    /** List submitted Breakdown F.06 records (newest first). */
    suspend fun listBreakdowns(): Result<List<BreakdownRecord>>
```

- [ ] **Step 5: Repository impl** — in `data/repository/ChecklistRepositoryImpl.kt` add import
  `import com.candorfoods.factoryops.domain.model.BreakdownRecord` and:
```kotlin
    override suspend fun listBreakdowns(): Result<List<BreakdownRecord>> = call { token ->
        Result.Success(checklistApi.listBreakdowns(token).map { it.toDomain() })
    }
```
  (`toDomain` resolves via the existing `import ...data.remote.dto.toDomain`.)

- [ ] **Step 6: Compile** — `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -40)` → BUILD SUCCESSFUL.

---

## Task 2: Pure mapper `BreakdownRecord.toF06Row()` + tests (TDD)

**Files:** Edit `presentation/checklist/BreakdownF06Logic.kt`; Edit/Create test.

- [ ] **Step 1: Write the failing test** — append to
  `src/test/java/com/candorfoods/factoryops/BreakdownF06LogicTest.kt` (add imports as needed:
  `com.candorfoods.factoryops.domain.model.BreakdownRecord`,
  `com.candorfoods.factoryops.data.remote.dto.BreakdownRecordDto`,
  `com.candorfoods.factoryops.data.remote.dto.toDomain`,
  `com.candorfoods.factoryops.presentation.checklist.toF06Row`):
```kotlin
    private fun record(
        id: Int = 12,
        machineName: String = "Band Sealer",
        equipmentModelNo: String = "W202-0007",
    ) = BreakdownRecord(
        id = id, docNo = "CFPLA.C4.F.06", recordDate = "2026-06-27", location = "W-202",
        machineName = machineName, equipmentModelNo = equipmentModelNo,
        problemInBrief = "Belt snapped", typeOfMaintenance = "Mechanical", partOfMachine = "Belt",
        temporaryReason = "", durationStart = "10:00", durationEnd = "11:30",
        machineOperatorSign = "Ravi", maintenancePersonSign = "Suresh", qcClearanceSign = "QC Sam",
        verifiedBy = "Head", createdAt = "2026-06-27T11:35:00Z",
    )

    @Test fun `toF06Row copies fields and uses srNo 1`() {
        val row = record().toF06Row()
        assertEquals(1, row.srNo)
        assertEquals("2026-06-27", row.date)
        assertEquals("W-202", row.location)
        assertEquals("Band Sealer", row.machineName)
        assertEquals("W202-0007", row.equipmentModel)
        assertEquals("Belt snapped", row.problem)
        assertEquals("Mechanical", row.typeOfMaintenance)
        assertEquals("Belt", row.partOfMachine)
        assertEquals("10:00", row.durationStart)
        assertEquals("QC Sam", row.qcClearanceSign)
    }

    @Test fun `toF06Row falls back to equipment model when machine name is blank`() {
        assertEquals("W202-0007", record(machineName = "").toF06Row().machineName)
    }

    @Test fun `BreakdownRecordDto toDomain maps snake_case fields`() {
        val dto = BreakdownRecordDto(
            id = 7, recordDate = "2026-06-01", machineName = "Pet Sealer",
            equipmentModelNo = "A185-0003", problemInBrief = "Jam", verifiedBy = "QA",
        )
        val d = dto.toDomain()
        assertEquals(7, d.id)
        assertEquals("2026-06-01", d.recordDate)
        assertEquals("Pet Sealer", d.machineName)
        assertEquals("A185-0003", d.equipmentModelNo)
        assertEquals("Jam", d.problemInBrief)
        assertEquals("QA", d.verifiedBy)
        assertEquals("CFPLA.C4.F.06", d.docNo) // default
    }
```

- [ ] **Step 2: Run → FAIL** — `... --tests "com.candorfoods.factoryops.BreakdownF06LogicTest"` → unresolved `toF06Row`.

- [ ] **Step 3: Implement** — append to `presentation/checklist/BreakdownF06Logic.kt`
  (add import `import com.candorfoods.factoryops.domain.model.BreakdownRecord`):
```kotlin
/** Map a submitted F.06 record to a single register row for PDF export. */
fun BreakdownRecord.toF06Row(): BreakdownRow = BreakdownRow(
    srNo = 1,
    date = recordDate,
    location = location,
    machineName = machineName.ifBlank { equipmentModelNo },
    equipmentModel = equipmentModelNo,
    problem = problemInBrief,
    typeOfMaintenance = typeOfMaintenance,
    partOfMachine = partOfMachine,
    reason = temporaryReason,
    durationStart = durationStart,
    durationEnd = durationEnd,
    machineOperatorSign = machineOperatorSign,
    maintenancePersonSign = maintenancePersonSign,
    qcClearanceSign = qcClearanceSign,
)
```

- [ ] **Step 4: Run → PASS** (all `BreakdownF06LogicTest` tests, old + 3 new).

---

## Task 3: Merge sources in `BreakdownListViewModel`

**Files:** Edit `presentation/checklist/BreakdownListViewModel.kt`.

- [ ] **Step 1: Extend state + VM.** Replace the file's `BreakdownListUiState` and the
  `BreakdownListViewModel` class with (keep `BreakdownListItem` as-is):
```kotlin
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
    private val draftRepository: BreakdownF06DraftRepository,
    private val checklistRepository: ChecklistRepository,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreakdownListUiState())
    val uiState: StateFlow<BreakdownListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { breakdownRepository.refreshOpenFlags("both") }
        viewModelScope.launch {
            combine(
                breakdownRepository.observeAllFlags(),
                draftRepository.observeSavedIds(),
            ) { flags, savedIds ->
                flags.map { f -> BreakdownListItem(f, isApproved(f), f.id in savedIds) }
            }.collect { items -> _uiState.update { it.copy(isLoading = false, items = items) } }
        }
        loadSubmitted()
    }

    /** Best-effort fetch of submitted F.06 records (empty/soft-error until backend ships). */
    fun loadSubmitted() {
        viewModelScope.launch {
            _uiState.update { it.copy(submittedLoading = true, submittedError = null) }
            when (val r = checklistRepository.listBreakdowns()) {
                is Result.Success -> _uiState.update {
                    it.copy(submittedLoading = false,
                            submitted = r.data.sortedByDescending { rec -> rec.id })
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
                    BreakdownDocData(
                        docNo = "CFPLA.C4.F.06",
                        verifiedBy = record.verifiedBy,
                        rows = listOf(record.toF06Row()),
                    )
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
  Add imports: `com.candorfoods.factoryops.domain.model.BreakdownRecord`,
  `com.candorfoods.factoryops.domain.model.Result`,
  `com.candorfoods.factoryops.domain.repository.ChecklistRepository`,
  `com.candorfoods.factoryops.util.pdf.BreakdownDocData`,
  `com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer`, `java.io.File`.
  (`toF06Row`, `isApproved`, `BreakdownListItem` are in the same package — no import.)

- [ ] **Step 2: Compile** → BUILD SUCCESSFUL.

---

## Task 4: Two-section list + per-record Download in `BreakdownListScreen`

**Files:** Edit `presentation/checklist/BreakdownListScreen.kt`.

- [ ] **Step 1: Rewrite the screen.** Replace the whole file with (keeps the existing
  `BreakdownRecordCard`/`StatusChip` for the flags section; adds a submitted section + share):
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
        val nothing = ui.items.isEmpty() && ui.submitted.isEmpty() &&
            !ui.isLoading && !ui.submittedLoading
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
                        item { SoftNote("Couldn't load submitted records — pull to retry once the server is ready.") }
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
                        items(ui.items, key = { "flag-${it.flag.id}" }) { it2 ->
                            BreakdownRecordCard(it2, onOpen = { onOpen(it2.flag.id) })
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
private fun BreakdownRecordCard(item: BreakdownListItem, onOpen: () -> Unit) {
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
                    Text(if (item.isCompleted) "Completed — tap to view / download" else "Tap to complete F.06",
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

- [ ] **Step 2: Compile** → BUILD SUCCESSFUL. (`NavGraph` already passes `onBack/onCreate/onOpen` from Feature 2 — unchanged.)

---

## Task 5: Full build + manual handoff

- [ ] **Step 1:** `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:testDebugUnitTest :app:assembleDebug 2>&1 | tail -25)` → BUILD SUCCESSFUL (incl. `BreakdownF06LogicTest`).
- [ ] **Step 2 (user):** install + verify:
  1. Breakdown page shows two sections.
  2. "Submitted F.06 records": empty/"Couldn't load" until the backend GET ships; once live, submitted sheets appear with a **Download** button → opens the share sheet → F.06 PDF with the saved values.
  3. "Awaiting F.06 completion": QC-approved breakdowns tappable, others locked (Feature 2 behavior intact).

---

## Self-review
**Spec coverage:** new GET read path (T1) ✅; pure mapper + tests (T2) ✅; merge both sources + per-record export (T3) ✅; two-section UI + per-card Download + share + soft errors (T4) ✅; reuse renderer/share (T3/T4) ✅; backend-absent handled softly (T3 `submittedError`, T4 SoftNote) ✅.
**Placeholders:** none — full code/commands throughout.
**Type consistency:** `BreakdownRecord` fields identical across model (T1), DTO mapper (T1), `toF06Row` (T2), VM (T3); `listBreakdowns(): Result<List<BreakdownRecord>>` consistent API↔repo↔VM; `exportSubmitted`/`onPdfShared`/`onExportErrorShown` + state fields consistent VM↔screen; `BreakdownListScreen(onBack,onCreate,onOpen)` signature unchanged from Feature 2 (NavGraph compiles).
