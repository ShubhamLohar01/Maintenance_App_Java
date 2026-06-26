# Breakdown F.06 Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Show all breakdowns from `mt_breakdown_records` on the Breakdown page; QC-approved ones open a pre-filled F.06 form the technician completes, saves on-device, and downloads as a PDF.

**Architecture:** A new Room table (`breakdown_f06_drafts`) stores the technician-entered F.06 columns per breakdown. A list ViewModel combines all breakdown flags with saved-draft ids; an "F.06 complete" screen pre-fills from the flag + draft, saves, and reuses the existing `ChecklistPdfRenderer.renderBreakdown` to export.

**Tech Stack:** Kotlin, Compose, Hilt, Room 2.6, Coroutines, JUnit5. Spec: `docs/superpowers/specs/2026-06-26-breakdown-f06-completion-design.md`.

---

## Notes for the implementer
- **Not a git repo** — skip all "Commit" steps (logical checkpoints only).
- **Commands** run from repo root `d:\Maintenance module\FactoryOps` (Git Bash): compile with
  `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -40)`,
  tests with `... ./gradlew :app:testDebugUnitTest --tests "<Class>" ...`.
- **Concurrent edits:** the `breakdown`/`qc` packages may be edited by the user's IDE. If a
  compile shows errors ONLY in `presentation/qc` or `presentation/technician` files you didn't
  touch, report them separately — they are not yours.
- **READ a file before editing it** (several already exist).
- Reuses from the prior feature (already in the codebase, do not recreate):
  `util/pdf/ChecklistPdfRenderer.renderBreakdown(BreakdownDocData): File`,
  `util/pdf/PdfShareLauncher.share(context, file): Boolean`,
  `util/pdf/BreakdownRow`, `util/pdf/BreakdownDocData`.

## File structure
| File | Responsibility |
|---|---|
| `data/local/entity/BreakdownF06DraftEntity.kt` (new) | Room row: technician-entered F.06 fields per breakdown |
| `data/local/dao/BreakdownF06DraftDao.kt` (new) | upsert/observe draft + observe saved ids |
| `data/local/AppDatabase.kt` (edit) | register entity + dao, version 8→9 |
| `data/local/dao/BreakdownFlagDao.kt` (edit) | `observeAll()` |
| `domain/model/BreakdownF06Draft.kt` (new) | draft domain model |
| `domain/repository/BreakdownF06DraftRepository.kt` (new) | draft repo interface |
| `data/repository/BreakdownF06DraftRepositoryImpl.kt` (new) | draft repo impl + mappers |
| `domain/repository/BreakdownRepository.kt` + Impl (edit) | `observeAllFlags()` |
| `di/RepositoryModule.kt` (edit) | bind draft repo |
| `presentation/checklist/BreakdownF06Logic.kt` (new) | pure helpers (fields, mapping, labels) |
| `presentation/checklist/BreakdownListViewModel.kt` (new) | list state |
| `presentation/checklist/BreakdownListScreen.kt` (rewrite) | the records list |
| `presentation/checklist/BreakdownCompleteViewModel.kt` (new) | F.06 form state + save + export |
| `presentation/checklist/BreakdownCompleteScreen.kt` (new) | the F.06 fill-in screen |
| `navigation/Screen.kt` + `navigation/NavGraph.kt` (edit) | BreakdownComplete route |
| `test/.../BreakdownF06LogicTest.kt` (new) | unit tests for the pure helpers |

---

## Task 1: Room draft table (entity + DAO + AppDatabase v9)

**Files:** Create `data/local/entity/BreakdownF06DraftEntity.kt`, `data/local/dao/BreakdownF06DraftDao.kt`; Edit `data/local/AppDatabase.kt`.

- [ ] **Step 1: Create the entity**

`src/main/java/com/candorfoods/factoryops/data/local/entity/BreakdownF06DraftEntity.kt`:
```kotlin
package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Technician-entered F.06 columns for one breakdown (the date/machine/problem stay sourced
 *  from breakdown_flags and are not copied here). A row existing = "saved/completed". */
@Entity(tableName = "breakdown_f06_drafts")
data class BreakdownF06DraftEntity(
    @PrimaryKey val breakdownId: String,
    val location: String,
    val equipmentModel: String,
    val typeOfMaintenance: String,
    val partOfMachine: String,
    val reason: String,
    val durationStart: String,
    val durationEnd: String,
    val machineOperatorSign: String,
    val maintenancePersonSign: String,
    val qcClearanceSign: String,
    val verifiedBy: String,
    val updatedAt: Long,
)
```

- [ ] **Step 2: Create the DAO**

`src/main/java/com/candorfoods/factoryops/data/local/dao/BreakdownF06DraftDao.kt`:
```kotlin
package com.candorfoods.factoryops.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.candorfoods.factoryops.data.local.entity.BreakdownF06DraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakdownF06DraftDao {
    @Upsert
    suspend fun upsert(draft: BreakdownF06DraftEntity)

    @Query("SELECT * FROM breakdown_f06_drafts WHERE breakdownId = :id")
    fun observeDraft(id: String): Flow<BreakdownF06DraftEntity?>

    @Query("SELECT * FROM breakdown_f06_drafts WHERE breakdownId = :id")
    suspend fun getDraftOnce(id: String): BreakdownF06DraftEntity?

    @Query("SELECT breakdownId FROM breakdown_f06_drafts")
    fun observeSavedIds(): Flow<List<String>>
}
```

- [ ] **Step 3: Register in AppDatabase + bump version**

In `data/local/AppDatabase.kt`: add `BreakdownF06DraftEntity::class` to the `entities` array,
change `version = 8` to `version = 9`, add the abstract dao accessor, and a migration comment.
```kotlin
// in entities = [ ... ] add:
        BreakdownF06DraftEntity::class
// change:
    version = 9,
// add comment after the v7→v8 comment:
    // v8 → v9: adds breakdown_f06_drafts (technician-entered F.06 columns per breakdown).
// add accessor next to breakdownFlagDao():
    abstract fun breakdownF06DraftDao(): BreakdownF06DraftDao
```
(The wildcard imports `data.local.dao.*` and `data.local.entity.*` already cover the new types.)

- [ ] **Step 4: Verify compile (KSP generates Room code)**

Run: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -40)`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** (skip — not a git repo)

---

## Task 2: `observeAll()` on BreakdownFlagDao + `observeAllFlags()` on the repository

**Files:** Edit `data/local/dao/BreakdownFlagDao.kt`, `domain/repository/BreakdownRepository.kt`, `data/repository/BreakdownRepositoryImpl.kt`.

- [ ] **Step 1: Add the DAO query**

In `BreakdownFlagDao.kt`, add (e.g. just after `observeAllOpenFlags()`):
```kotlin
    /** Every breakdown in Room (all statuses), newest first — the Breakdown records page. */
    @Query("SELECT * FROM breakdown_flags ORDER BY raisedAt DESC")
    fun observeAll(): Flow<List<BreakdownFlagEntity>>
```

- [ ] **Step 2: Add to the repository interface**

In `domain/repository/BreakdownRepository.kt`, add:
```kotlin
    /** Every breakdown in Room (all statuses) for the Breakdown records page. */
    fun observeAllFlags(): Flow<List<BreakdownFlag>>
```

- [ ] **Step 3: Implement it**

In `data/repository/BreakdownRepositoryImpl.kt`, add (the private `toDomain(e)` mapper already
exists in this file; reuse it exactly as the other observers do):
```kotlin
    override fun observeAllFlags(): Flow<List<BreakdownFlag>> =
        db.breakdownFlagDao().observeAll().map { list -> list.map { toDomain(it) } }
```

- [ ] **Step 4: Verify compile**

Run: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -40)`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** (skip)

---

## Task 3: Draft domain model + repository + DI bind

**Files:** Create `domain/model/BreakdownF06Draft.kt`, `domain/repository/BreakdownF06DraftRepository.kt`, `data/repository/BreakdownF06DraftRepositoryImpl.kt`; Edit `di/RepositoryModule.kt`.

- [ ] **Step 1: Domain model**

`src/main/java/com/candorfoods/factoryops/domain/model/BreakdownF06Draft.kt`:
```kotlin
package com.candorfoods.factoryops.domain.model

/** Technician-entered F.06 fields for one breakdown, persisted on-device. */
data class BreakdownF06Draft(
    val breakdownId: String,
    val location: String,
    val equipmentModel: String,
    val typeOfMaintenance: String,
    val partOfMachine: String,
    val reason: String,
    val durationStart: String,
    val durationEnd: String,
    val machineOperatorSign: String,
    val maintenancePersonSign: String,
    val qcClearanceSign: String,
    val verifiedBy: String,
    val updatedAt: Long,
)
```

- [ ] **Step 2: Repository interface**

`src/main/java/com/candorfoods/factoryops/domain/repository/BreakdownF06DraftRepository.kt`:
```kotlin
package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.BreakdownF06Draft
import kotlinx.coroutines.flow.Flow

interface BreakdownF06DraftRepository {
    fun observeDraft(breakdownId: String): Flow<BreakdownF06Draft?>
    suspend fun getDraftOnce(breakdownId: String): BreakdownF06Draft?
    suspend fun saveDraft(draft: BreakdownF06Draft)
    /** Ids of breakdowns that already have a saved draft (= "completed"). */
    fun observeSavedIds(): Flow<Set<String>>
}
```

- [ ] **Step 3: Repository impl**

`src/main/java/com/candorfoods/factoryops/data/repository/BreakdownF06DraftRepositoryImpl.kt`:
```kotlin
package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.entity.BreakdownF06DraftEntity
import com.candorfoods.factoryops.domain.model.BreakdownF06Draft
import com.candorfoods.factoryops.domain.repository.BreakdownF06DraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreakdownF06DraftRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
) : BreakdownF06DraftRepository {

    private val dao get() = db.breakdownF06DraftDao()

    override fun observeDraft(breakdownId: String): Flow<BreakdownF06Draft?> =
        dao.observeDraft(breakdownId).map { it?.toDomain() }

    override suspend fun getDraftOnce(breakdownId: String): BreakdownF06Draft? =
        dao.getDraftOnce(breakdownId)?.toDomain()

    override suspend fun saveDraft(draft: BreakdownF06Draft) = dao.upsert(draft.toEntity())

    override fun observeSavedIds(): Flow<Set<String>> =
        dao.observeSavedIds().map { it.toSet() }
}

private fun BreakdownF06DraftEntity.toDomain() = BreakdownF06Draft(
    breakdownId = breakdownId, location = location, equipmentModel = equipmentModel,
    typeOfMaintenance = typeOfMaintenance, partOfMachine = partOfMachine, reason = reason,
    durationStart = durationStart, durationEnd = durationEnd,
    machineOperatorSign = machineOperatorSign, maintenancePersonSign = maintenancePersonSign,
    qcClearanceSign = qcClearanceSign, verifiedBy = verifiedBy, updatedAt = updatedAt,
)

private fun BreakdownF06Draft.toEntity() = BreakdownF06DraftEntity(
    breakdownId = breakdownId, location = location, equipmentModel = equipmentModel,
    typeOfMaintenance = typeOfMaintenance, partOfMachine = partOfMachine, reason = reason,
    durationStart = durationStart, durationEnd = durationEnd,
    machineOperatorSign = machineOperatorSign, maintenancePersonSign = maintenancePersonSign,
    qcClearanceSign = qcClearanceSign, verifiedBy = verifiedBy, updatedAt = updatedAt,
)
```

- [ ] **Step 4: Bind in RepositoryModule**

In `di/RepositoryModule.kt`: add imports for `BreakdownF06DraftRepositoryImpl` and
`BreakdownF06DraftRepository`, and add inside the class:
```kotlin
    @Binds @Singleton
    abstract fun bindBreakdownF06DraftRepository(
        impl: BreakdownF06DraftRepositoryImpl,
    ): BreakdownF06DraftRepository
```

- [ ] **Step 5: Verify compile**

Run: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -40)`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit** (skip)

---

## Task 4: Pure F.06 logic helpers (TDD)

**Files:** Create `presentation/checklist/BreakdownF06Logic.kt`; Test `test/.../BreakdownF06LogicTest.kt`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/candorfoods/factoryops/BreakdownF06LogicTest.kt`:
```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.BreakdownF06Draft
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.presentation.checklist.breakdownStatusLabel
import com.candorfoods.factoryops.presentation.checklist.buildF06Row
import com.candorfoods.factoryops.presentation.checklist.defaultF06Fields
import com.candorfoods.factoryops.presentation.checklist.isApproved
import com.candorfoods.factoryops.presentation.checklist.toDraft
import com.candorfoods.factoryops.presentation.checklist.toFields
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BreakdownF06LogicTest {

    private fun flag(
        id: String = "bd1",
        machineId: String = "W202-0007",
        assetName: String? = "Band Sealer",
        building: String? = "W-202",
        description: String = "Belt snapped",
        raisedAt: Long = 0L,
        status: BreakdownStatus = BreakdownStatus.CLOSED,
        qcStatus: QcStatus? = QcStatus.APPROVED,
        resolvedByName: String? = "Tech Ravi",
        qcDecidedByName: String? = "QC Sam",
    ) = BreakdownFlag(
        id = id, machineId = machineId, assetName = assetName, building = building,
        operatorId = "op1", severity = Severity.MAJOR, description = description,
        beforePhotoLocalPath = null, raisedAt = raisedAt, status = status,
        acknowledgedBy = null, acknowledgedByName = null, acknowledgedAt = null,
        resolvedBy = null, resolvedByName = resolvedByName, resolvedAt = null,
        partsUsed = null, afterPhotoLocalPath = null, qcStatus = qcStatus,
        qcAcknowledgedBy = null, qcAcknowledgedByName = null, qcAcknowledgedAt = null,
        qcDecidedBy = null, qcDecidedByName = qcDecidedByName, qcDecidedAt = null,
        qcDecisionNotes = null, qcAfterPhotoLocalPath = null, qcChecklistJson = null,
        qcOverrideFromUserId = null, qcOverrideFromName = null, qcOverrideAt = null,
        syncStatus = SyncStatus.SYNCED,
    )

    @Test fun `isApproved is true only when qcStatus is APPROVED`() {
        assertTrue(isApproved(flag(qcStatus = QcStatus.APPROVED)))
        assertFalse(isApproved(flag(qcStatus = null, status = BreakdownStatus.PENDING_QC)))
        assertFalse(isApproved(flag(qcStatus = QcStatus.REJECTED, status = BreakdownStatus.REOPENED)))
    }

    @Test fun `status label reflects approval and stage`() {
        assertEquals("Approved", breakdownStatusLabel(flag(qcStatus = QcStatus.APPROVED)))
        assertEquals("Pending QC", breakdownStatusLabel(flag(qcStatus = null, status = BreakdownStatus.PENDING_QC)))
        assertEquals("In progress", breakdownStatusLabel(flag(qcStatus = null, status = BreakdownStatus.ACKNOWLEDGED)))
        assertEquals("Reopened", breakdownStatusLabel(flag(qcStatus = QcStatus.REJECTED, status = BreakdownStatus.REOPENED)))
        assertEquals("Open", breakdownStatusLabel(flag(qcStatus = null, status = BreakdownStatus.OPEN)))
    }

    @Test fun `defaultF06Fields seeds location, equipment and signer suggestions from the flag`() {
        val f = defaultF06Fields(flag())
        assertEquals("W-202", f.location)
        assertEquals("W202-0007", f.equipmentModel)
        assertEquals("Tech Ravi", f.maintenancePersonSign)
        assertEquals("QC Sam", f.qcClearanceSign)
        assertEquals("", f.typeOfMaintenance)
    }

    @Test fun `defaultF06Fields tolerates null building and names`() {
        val f = defaultF06Fields(flag(building = null, resolvedByName = null, qcDecidedByName = null))
        assertEquals("", f.location)
        assertEquals("", f.maintenancePersonSign)
        assertEquals("", f.qcClearanceSign)
    }

    @Test fun `buildF06Row maps flag plus entered fields and falls back to machineId for name`() {
        val fields = defaultF06Fields(flag()).copy(typeOfMaintenance = "Mechanical", partOfMachine = "Belt")
        val row = buildF06Row(flag(), fields)
        assertEquals(1, row.srNo)
        assertEquals("Band Sealer", row.machineName)
        assertEquals("W202-0007", row.equipmentModel)
        assertEquals("Belt snapped", row.problem)
        assertEquals("Mechanical", row.typeOfMaintenance)
        assertEquals("Belt", row.partOfMachine)
        // name falls back to machineId when assetName is blank/null
        assertEquals("W202-0007", buildF06Row(flag(assetName = null), fields).machineName)
    }

    @Test fun `fields round-trip through a draft`() {
        val fields = defaultF06Fields(flag()).copy(reason = "temp fix", verifiedBy = "Head")
        val draft = fields.toDraft(breakdownId = "bd1", updatedAt = 123L)
        assertEquals("bd1", draft.breakdownId)
        assertEquals(123L, draft.updatedAt)
        assertEquals(fields, draft.toFields())
    }
}
```

- [ ] **Step 2: Run it (fails — unresolved references)**

Run: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.BreakdownF06LogicTest" 2>&1 | tail -30)`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement the helpers**

`src/main/java/com/candorfoods/factoryops/presentation/checklist/BreakdownF06Logic.kt`:
```kotlin
package com.candorfoods.factoryops.presentation.checklist

import com.candorfoods.factoryops.domain.model.BreakdownF06Draft
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.pdf.BreakdownRow

/** The editable F.06 columns the technician completes (everything not already in the record). */
data class F06Fields(
    val location: String = "",
    val equipmentModel: String = "",
    val typeOfMaintenance: String = "",
    val partOfMachine: String = "",
    val reason: String = "",
    val durationStart: String = "",
    val durationEnd: String = "",
    val machineOperatorSign: String = "",
    val maintenancePersonSign: String = "",
    val qcClearanceSign: String = "",
    val verifiedBy: String = "",
)

/** A breakdown is fillable only once QC has approved it. */
fun isApproved(flag: BreakdownFlag): Boolean = flag.qcStatus == QcStatus.APPROVED

/** Short status label for the records list chip. */
fun breakdownStatusLabel(flag: BreakdownFlag): String = when {
    flag.qcStatus == QcStatus.APPROVED          -> "Approved"
    flag.status == BreakdownStatus.PENDING_QC   -> "Pending QC"
    flag.status == BreakdownStatus.ACKNOWLEDGED -> "In progress"
    flag.status == BreakdownStatus.REOPENED     -> "Reopened"
    else                                        -> "Open"
}

/** Seed the editable fields from what the system already knows. */
fun defaultF06Fields(flag: BreakdownFlag): F06Fields = F06Fields(
    location = flag.building.orEmpty(),
    equipmentModel = flag.machineId,
    maintenancePersonSign = flag.resolvedByName.orEmpty(),
    qcClearanceSign = flag.qcDecidedByName.orEmpty(),
)

fun BreakdownF06Draft.toFields(): F06Fields = F06Fields(
    location = location, equipmentModel = equipmentModel, typeOfMaintenance = typeOfMaintenance,
    partOfMachine = partOfMachine, reason = reason, durationStart = durationStart,
    durationEnd = durationEnd, machineOperatorSign = machineOperatorSign,
    maintenancePersonSign = maintenancePersonSign, qcClearanceSign = qcClearanceSign,
    verifiedBy = verifiedBy,
)

fun F06Fields.toDraft(breakdownId: String, updatedAt: Long): BreakdownF06Draft = BreakdownF06Draft(
    breakdownId = breakdownId, location = location, equipmentModel = equipmentModel,
    typeOfMaintenance = typeOfMaintenance, partOfMachine = partOfMachine, reason = reason,
    durationStart = durationStart, durationEnd = durationEnd,
    machineOperatorSign = machineOperatorSign, maintenancePersonSign = maintenancePersonSign,
    qcClearanceSign = qcClearanceSign, verifiedBy = verifiedBy, updatedAt = updatedAt,
)

/** Merge the immutable breakdown record + the entered fields into one F.06 register row. */
fun buildF06Row(flag: BreakdownFlag, fields: F06Fields): BreakdownRow = BreakdownRow(
    srNo = 1,
    date = DateTimeFormatter.formatDate(flag.raisedAt),
    location = fields.location,
    machineName = flag.assetName?.ifBlank { null } ?: flag.machineId,
    equipmentModel = fields.equipmentModel,
    problem = flag.description,
    typeOfMaintenance = fields.typeOfMaintenance,
    partOfMachine = fields.partOfMachine,
    reason = fields.reason,
    durationStart = fields.durationStart,
    durationEnd = fields.durationEnd,
    machineOperatorSign = fields.machineOperatorSign,
    maintenancePersonSign = fields.maintenancePersonSign,
    qcClearanceSign = fields.qcClearanceSign,
)
```

- [ ] **Step 4: Run tests (pass)**

Run: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.BreakdownF06LogicTest" 2>&1 | tail -30)`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit** (skip)

---

## Task 5: Breakdown records list (ViewModel + screen rewrite)

**Files:** Create `presentation/checklist/BreakdownListViewModel.kt`; Rewrite `presentation/checklist/BreakdownListScreen.kt`.

- [ ] **Step 1: ViewModel**

`src/main/java/com/candorfoods/factoryops/presentation/checklist/BreakdownListViewModel.kt`:
```kotlin
package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.repository.BreakdownF06DraftRepository
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BreakdownListItem(
    val flag: BreakdownFlag,
    val isApproved: Boolean,
    val isCompleted: Boolean,
)

data class BreakdownListUiState(
    val isLoading: Boolean = true,
    val items: List<BreakdownListItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class BreakdownListViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val draftRepository: BreakdownF06DraftRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreakdownListUiState())
    val uiState: StateFlow<BreakdownListUiState> = _uiState.asStateFlow()

    init {
        // Best-effort pull of the open pool so freshly-raised breakdowns appear.
        viewModelScope.launch { breakdownRepository.refreshOpenFlags("both") }
        viewModelScope.launch {
            combine(
                breakdownRepository.observeAllFlags(),
                draftRepository.observeSavedIds(),
            ) { flags, savedIds ->
                flags.map { f -> BreakdownListItem(f, isApproved(f), f.id in savedIds) }
            }.collect { items ->
                _uiState.update { it.copy(isLoading = false, items = items) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { breakdownRepository.refreshOpenFlags("both") }
    }
}
```

- [ ] **Step 2: Rewrite the screen**

Replace the entire contents of `presentation/checklist/BreakdownListScreen.kt` with:
```kotlin
package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.util.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: BreakdownListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Create") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onCreate,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.items.isEmpty() -> Text(
                    "No breakdown records yet.\nApproved breakdowns will appear here to complete the F.06.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(uiState.items, key = { it.flag.id }) { item ->
                        BreakdownRecordCard(item, onOpen = { onOpen(item.flag.id) })
                    }
                }
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

    FactoryCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (enabled) onOpen else null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else muted,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(breakdownStatusLabel(flag), enabled)
            }
            if (!flag.building.isNullOrBlank()) {
                Text(flag.building!!, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Text(
                "${DateTimeFormatter.formatDate(flag.raisedAt)} · ${flag.description}",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (enabled) {
                    Text(
                        if (item.isCompleted) "Completed — tap to view / download" else "Tap to complete F.06",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
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
    val container =
        if (approved) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        ),
    )
}
```
Note: confirmed — `FactoryCard(onClick: (() -> Unit)? = null, ...)` is nullable, so passing
`onClick = if (enabled) onOpen else null` compiles and disables the press animation for locked
cards. Also confirmed it accepts `modifier` + a trailing `content` lambda as used above. Do not
modify `FactoryCard`. `Modifier.size`/`width`/`Spacer` need `androidx.compose.foundation.layout.*`
(already wildcard-imported) and `dp` from `androidx.compose.ui.unit.dp` (add the import).

- [ ] **Step 3: Verify compile**

Run: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -40)`
Expected: BUILD SUCCESSFUL. (Note: `BreakdownListScreen` now needs an `onOpen` param — the
NavGraph call is updated in Task 7; until then the compile of `NavGraph.kt` will fail on the
missing arg. If running this task in isolation, it is acceptable for `NavGraph.kt` to be the
only error; it is fixed in Task 7. Prefer to run Task 7 immediately after.)

- [ ] **Step 4: Commit** (skip)

---

## Task 6: F.06 completion screen (ViewModel + screen)

**Files:** Create `presentation/checklist/BreakdownCompleteViewModel.kt`, `presentation/checklist/BreakdownCompleteScreen.kt`. (Depends on `Screen.BreakdownComplete` from Task 7 for the nav-arg key — define the ViewModel to read the arg key string `"id"`; Task 7 adds the route.)

- [ ] **Step 1: ViewModel**

`src/main/java/com/candorfoods/factoryops/presentation/checklist/BreakdownCompleteViewModel.kt`:
```kotlin
package com.candorfoods.factoryops.presentation.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.repository.BreakdownF06DraftRepository
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.navigation.Screen
import com.candorfoods.factoryops.util.pdf.BreakdownDocData
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BreakdownCompleteUiState(
    val isLoading: Boolean = true,
    val flag: BreakdownFlag? = null,
    val fields: F06Fields = F06Fields(),
    val isApproved: Boolean = false,
    val isSaved: Boolean = false,
    val isExporting: Boolean = false,
    val pdfToShare: File? = null,
    val exportError: String? = null,
    val error: String? = null,
)

@HiltViewModel
class BreakdownCompleteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val breakdownRepository: BreakdownRepository,
    private val draftRepository: BreakdownF06DraftRepository,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {

    private val id: String = savedStateHandle.get<String>(Screen.BreakdownComplete.ARG).orEmpty()

    private val _uiState = MutableStateFlow(BreakdownCompleteUiState())
    val uiState: StateFlow<BreakdownCompleteUiState> = _uiState.asStateFlow()

    private var seeded = false

    init {
        viewModelScope.launch {
            combine(
                breakdownRepository.observeFlagById(id),
                draftRepository.observeDraft(id),
            ) { flag, draft -> flag to draft }
                .collect { (flag, draft) ->
                    if (flag == null) {
                        _uiState.update { it.copy(isLoading = false, error = "Breakdown not found") }
                        return@collect
                    }
                    if (!seeded) {
                        seeded = true
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                flag = flag,
                                fields = draft?.toFields() ?: defaultF06Fields(flag),
                                isApproved = isApproved(flag),
                                isSaved = draft != null,
                            )
                        }
                    } else {
                        // Preserve in-progress edits; only refresh flag-derived bits.
                        _uiState.update { it.copy(flag = flag, isApproved = isApproved(flag)) }
                    }
                }
        }
    }

    fun updateFields(fields: F06Fields) = _uiState.update { it.copy(fields = fields) }

    fun save() {
        val s = _uiState.value
        val flag = s.flag ?: return
        if (!s.isApproved) return
        viewModelScope.launch {
            draftRepository.saveDraft(s.fields.toDraft(flag.id, System.currentTimeMillis()))
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun exportPdf() {
        val s = _uiState.value
        val flag = s.flag ?: return
        if (s.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }
            try {
                val row = buildF06Row(flag, s.fields)
                val file = pdfRenderer.renderBreakdown(
                    BreakdownDocData(
                        docNo = "CFPLA.C4.F.06",
                        verifiedBy = s.fields.verifiedBy.trim(),
                        rows = listOf(row),
                    )
                )
                _uiState.update { it.copy(isExporting = false, pdfToShare = file) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isExporting = false, exportError = e.message ?: "Couldn't create PDF")
                }
            }
        }
    }

    fun onPdfShared() = _uiState.update { it.copy(pdfToShare = null) }
    fun onExportErrorShown() = _uiState.update { it.copy(exportError = null) }
}
```

- [ ] **Step 2: Screen**

`src/main/java/com/candorfoods/factoryops/presentation/checklist/BreakdownCompleteScreen.kt`:
```kotlin
package com.candorfoods.factoryops.presentation.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.util.DateTimeFormatter
import com.candorfoods.factoryops.util.pdf.PdfShareLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownCompleteScreen(
    onBack: () -> Unit,
    viewModel: BreakdownCompleteViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.pdfToShare) {
        ui.pdfToShare?.let { file ->
            if (!PdfShareLauncher.share(context, file)) {
                snackbar.showSnackbar("No app available to open the PDF")
            }
            viewModel.onPdfShared()
        }
    }
    LaunchedEffect(ui.exportError) {
        ui.exportError?.let { snackbar.showSnackbar("Couldn't create PDF — $it"); viewModel.onExportErrorShown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Complete F.06") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val flag = ui.flag
        when {
            ui.isLoading -> Box(Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            flag == null -> Box(Modifier.fillMaxSize().padding(padding)) {
                Text(ui.error ?: "Not found", Modifier.align(Alignment.Center))
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).padding(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val f = ui.fields
                fun set(n: F06Fields) = viewModel.updateFields(n)

                FactoryCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(flag.assetName?.ifBlank { null } ?: flag.machineId,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        ReadLine("Date", DateTimeFormatter.formatDate(flag.raisedAt))
                        if (!flag.building.isNullOrBlank()) ReadLine("Location", flag.building!!)
                        ReadLine("Problem", flag.description)
                        ReadLine("Status", breakdownStatusLabel(flag))
                        if (!flag.resolvedByName.isNullOrBlank()) ReadLine("Resolved by", flag.resolvedByName!!)
                    }
                }

                if (!ui.isApproved) {
                    Text(
                        "This breakdown is not QC-approved yet, so it can't be completed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Field("Location", f.location) { set(f.copy(location = it)) }
                    Field("Equipment Model No./Sr. No.", f.equipmentModel) { set(f.copy(equipmentModel = it)) }
                    Field("Type of Maintenance", f.typeOfMaintenance) { set(f.copy(typeOfMaintenance = it)) }
                    Field("Part of machine", f.partOfMachine) { set(f.copy(partOfMachine = it)) }
                    Field("Reason (if temporary)", f.reason, singleLine = false) { set(f.copy(reason = it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f)) { TimeField("Duration Start", f.durationStart) { set(f.copy(durationStart = it)) } }
                        Box(Modifier.weight(1f)) { TimeField("Duration End", f.durationEnd) { set(f.copy(durationEnd = it)) } }
                    }
                    Field("Machine operator sign", f.machineOperatorSign) { set(f.copy(machineOperatorSign = it)) }
                    Field("Maintenance person sign", f.maintenancePersonSign) { set(f.copy(maintenancePersonSign = it)) }
                    Field("QC clearance sign", f.qcClearanceSign) { set(f.copy(qcClearanceSign = it)) }
                    Field("Verified By", f.verifiedBy) { set(f.copy(verifiedBy = it)) }

                    PrimaryButton(
                        text = "Save",
                        onClick = viewModel::save,
                        size = PrimaryButtonSize.Hero,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ui.isSaved) {
                        if (ui.isExporting) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            }
                        } else {
                            OutlinedButton(onClick = viewModel::exportPdf, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(Spacing.sm))
                                Text("Download PDF")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadLine(label: String, value: String) {
    Text("$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) }, placeholder = { Text("HH:mm") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { show = true })
    }
    if (show) {
        val parts = value.split(":")
        val initialH = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val initialM = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val tp = rememberTimePickerState(initialHour = initialH, initialMinute = initialM, is24Hour = true)
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = { onChange("%02d:%02d".format(tp.hour, tp.minute)); show = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
            text = { TimePicker(state = tp) },
        )
    }
}
```

- [ ] **Step 3: Verify compile** (together with Task 7, since the screen and route reference each other)

Run after Task 7: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -40)`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** (skip)

---

## Task 7: Navigation route + wiring

**Files:** Edit `navigation/Screen.kt`, `navigation/NavGraph.kt`.

- [ ] **Step 1: Add the route**

In `navigation/Screen.kt`, just after the `BreakdownForm` object, add:
```kotlin
    object BreakdownComplete : Screen("checklist/breakdown/complete/{id}") {
        fun createRoute(id: String) = "checklist/breakdown/complete/$id"
        const val ARG = "id"
    }
```

- [ ] **Step 2: Wire the list + add the complete composable**

In `navigation/NavGraph.kt`:
1. Add import:
```kotlin
import com.candorfoods.factoryops.presentation.checklist.BreakdownCompleteScreen
```
2. Update the existing `BreakdownList` composable to pass `onOpen`:
```kotlin
        composable(Screen.BreakdownList.route) {
            BreakdownListScreen(
                onBack   = { navController.popBackStack() },
                onCreate = { navController.navigate(Screen.BreakdownForm.route) },
                onOpen   = { id -> navController.navigate(Screen.BreakdownComplete.createRoute(id)) },
            )
        }
```
3. Add a new composable (e.g. right after the `BreakdownForm` composable). Use a `navArgument`
   of type String for `{id}`:
```kotlin
        composable(
            route = Screen.BreakdownComplete.route,
            arguments = listOf(navArgument(Screen.BreakdownComplete.ARG) { type = NavType.StringType }),
        ) {
            BreakdownCompleteScreen(onBack = { navController.popBackStack() })
        }
```
   If `navArgument` / `NavType` are not already imported in `NavGraph.kt`, add:
```kotlin
import androidx.navigation.NavType
import androidx.navigation.navArgument
```
   (Other routes like `PreventiveDetail` already use string path args — match their existing
   pattern in this file; if they don't declare `arguments=`, you may omit it and read the raw
   arg, but declaring it is safe.)

- [ ] **Step 3: Verify compile**

Run: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -40)`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** (skip)

---

## Task 8: Full build, unit suite, manual handoff

- [ ] **Step 1: Unit tests + APK**

Run: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:testDebugUnitTest :app:assembleDebug 2>&1 | tail -30)`
Expected: BUILD SUCCESSFUL, including `BreakdownF06LogicTest` and `ChecklistPdfModelTest`.

- [ ] **Step 2: Install (needs device/emulator)**

Run: `(cd "/d/Maintenance module/FactoryOps" && ./gradlew :app:installDebug 2>&1 | tail -10)`

- [ ] **Step 3: Manual verification (user)**

1. Open Checklist → Breakdown Checklist. Confirm the list shows breakdowns with status chips;
   not-yet-approved ones are muted/locked ("Awaiting QC approval") and not tappable.
2. Tap a **QC-approved** one → the F.06 form opens with Date/Machine/Problem pre-filled and the
   editable fields seeded (Location=building, Equipment=asset id, Maintenance/QC signs from
   names). Fill some fields → **Save**. Confirm a **Download PDF** button then appears.
3. Tap Download → share sheet → open the PDF → confirm `CFPLA.C4.F.06`, the entered values, and
   the pre-filled ones; unentered columns blank.
4. Leave and reopen the same record → fields are restored from the saved draft, Download present.
5. (If reachable) opening a non-approved one shows the "not QC-approved yet" view-only message.

- [ ] **Step 4: Commit** (skip)

---

## Self-review

**Spec coverage:** all-breakdowns list (Task 2,5) ✅; approved-only editable + locked others
(Task 4 `isApproved`, Task 5 card, Task 6 guard) ✅; pre-filled F.06 from flag (Task 4
`defaultF06Fields`/`buildF06Row`, Task 6) ✅; on-device draft save (Task 1,3,6) ✅; Download
after save, WYSIWYG (Task 6 `isSaved` gate + `exportPdf` from current fields) ✅; reuse renderer
+ share (Task 6) ✅; nav route (Task 7) ✅; DB v9 destructive (Task 1) ✅; cross-device CLOSED
limitation — documented, not implemented (out of scope per spec) ✅.

**Placeholder scan:** none — every step has full code/commands. The two "verify `FactoryCard`
API" / "match existing nav-arg pattern" notes are explicit verification instructions, not
TODOs.

**Type consistency:** `F06Fields` shape identical across Task 4/6; `BreakdownF06Draft` fields
match entity (Task 1) and mappers (Task 3,4); `buildF06Row` returns `BreakdownRow` (14 fields,
from the prior feature); `BreakdownDocData(docNo, verifiedBy, rows)` matches the existing model;
`observeAllFlags()`/`observeAll()`/`observeSavedIds()`/`observeDraft()` names consistent between
DAO, repo, and ViewModels; `Screen.BreakdownComplete.ARG = "id"` matches the ViewModel's
`savedStateHandle.get<String>(... .ARG)`.

**Risk:** `FactoryCard` nullable-`onClick` (Task 5 note) and the NavGraph arg-declaration style
(Task 7) are the two spots to verify against real signatures during implementation.
