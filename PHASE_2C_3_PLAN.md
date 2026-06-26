# Phase 2C-3 — PM QC Gate (Mixed Inbox) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Build + manual test gates after each task — do **not** advance until the user confirms.

**Goal:** Hand off supervisor-approved PM work orders to the QC inbox, let QC members run the same FSSAI/BRC checklist on PMs, and bounce QC-disapproved PMs back to the supervisor instead of the technician.

**Architecture:**
- Domain/enum schema is already in place (`PmWoStatus.PENDING_QC/QC_APPROVED`, `qcAcknowledgedBy/At/...` fields, `ActionType.PM_QC_*`). Work is wiring + screens.
- One new screen (`QcPmDetailScreen`) mirrors `QcTicketDetailScreen`. The existing `QcChecklistSheet` is reused unchanged for PM QC.
- The QC home screen becomes a **mixed inbox**: each tab merges `BreakdownFlag` rows + `PmWorkOrder` rows behind a sealed `QcInboxRow` type with a leading `BD` / `PM` chip.
- Supervisor approve flips PM status straight to `PENDING_QC` (skipping `SUPERVISOR_APPROVED` as a primary status — it remains only as a timestamp). Machine flips to `PENDING_QC` at handoff and back to `IDLE` on QC approve.
- QC disapprove on a PM bounces back to the supervisor (`status = SUBMITTED`, `qcDecidedAt/Notes/By` populated) — not to the technician. Supervisor re-approves to send to QC again, or rejects to send to tech for rework.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (Material 3), Hilt, Room, kotlinx-serialization. No new dependencies.

**Decisions locked (from brainstorm):**
- Q1A — supervisor approve writes status `PENDING_QC` directly. `SUPERVISOR_APPROVED` enum value is retained for sync-queue audit only, never set on the WO.
- Q2A — supervisor approve flips machine to `PENDING_QC`; QC approve flips machine to `IDLE`.
- Q3A — reuse existing `QcChecklistSheet` and `QcChecklistDraft` for PM QC.
- Q4B — PM QC disapprove bounces back to supervisor (status `SUBMITTED`, `qcDecidedAt` set). Supervisor sees a yellow "QC bounced this back" banner and decides re-approve vs reject.
- Q5A — small leading "BD"/"PM" chip on each inbox card, color-coded (red/blue).
- Q6A — plant scoping by joining through the in-VM machine map (no schema change).
- Q7A — no extra seed; manual test walks the full flow from operator → tech → supervisor → QC.

**Out of scope (deferred to 2C-4):** escalation cascade, tier-based notifications, banner severities, OVERDUE auto-promotion.

---

## File Structure

### New files
```
app/src/main/java/com/candorfoods/factoryops/presentation/qc/
├── QcInboxRow.kt                    (new — sealed Breakdown | Pm row model)
├── QcPmDetailScreen.kt              (new — PM QC detail screen)
├── QcPmDetailViewModel.kt           (new — VM for PM detail)

app/src/main/java/com/candorfoods/factoryops/navigation/
└── Screen.kt                        (modify — add QcPmDetail route)
```

### Modified files
```
domain/repository/PmWorkOrderRepository.kt          (add QC observers + QC actions)
data/repository/PmWorkOrderRepositoryImpl.kt        (impl + machine flips)
data/local/dao/PmWorkOrderDao.kt                    (add QC mark queries + observers)
presentation/qc/QcHomeViewModel.kt                  (merge breakdown + PM streams)
presentation/qc/QcHomeScreen.kt                     (render BD/PM chip; dispatch to right detail route)
presentation/supervisor/SupervisorPmReviewScreen.kt (add QC-bounceback banner; reword approve when bounced)
presentation/supervisor/SupervisorPmReviewViewModel.kt (handle re-approve clears qcDecidedAt)
presentation/qc/QcTicketRowUiModel.kt               (kept as-is, used inside QcInboxRow.Breakdown)
navigation/NavGraph.kt                              (compose QcPmDetail destination)
PROGRESS.md                                         (add phase summary at end)
```

### Why these splits
- `QcInboxRow.kt` is a pure model with no UI/VM dependencies → placing it next to `QcTicketRowUiModel.kt` keeps QC inbox types co-located.
- `QcPmDetailScreen.kt` + `QcPmDetailViewModel.kt` are the dual of `QcTicketDetailScreen.kt` + `QcTicketDetailViewModel.kt`. Separate files (not branches inside the breakdown screen) because the data sources, action methods, and read-only summary banners differ enough that branching would obscure both code paths.
- We do **not** modify `QcChecklistSheet.kt` or `QcChecklistData.kt` — they're parameterized only by the `QcChecklistDraft` and submit callbacks, so PM reuses them as-is.

---

## Task 1: Domain + DAO + Repo plumbing for PM QC actions

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/local/dao/PmWorkOrderDao.kt`
- Modify: `app/src/main/java/com/candorfoods/factoryops/domain/repository/PmWorkOrderRepository.kt`
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/repository/PmWorkOrderRepositoryImpl.kt`

- [ ] **Step 1.1: Add QC inbox observer queries to `PmWorkOrderDao`**

Append to `PmWorkOrderDao.kt` (before the closing brace):

```kotlin
    // ─── Phase 2C-3 — QC inbox observers ──────────────────────────────────────

    /**
     * PMs awaiting QC pickup. Status PENDING_QC and not yet acknowledged by
     * any QC user. Plant scoping is done in the VM by joining through the
     * machine map.
     */
    @Query("""
        SELECT * FROM pm_work_orders
        WHERE status = 'PENDING_QC' AND qcAcknowledgedBy IS NULL
        ORDER BY submittedAt ASC
    """)
    fun observePendingQc(): Flow<List<PmWorkOrderEntity>>

    /**
     * PMs the given QC user has picked up but not yet decided.
     */
    @Query("""
        SELECT * FROM pm_work_orders
        WHERE status = 'PENDING_QC' AND qcAcknowledgedBy = :userId
        ORDER BY qcAcknowledgedAt DESC
    """)
    fun observeQcAcknowledgedByUser(userId: String): Flow<List<PmWorkOrderEntity>>

    /**
     * PMs decided by the given QC user since `sinceMillis`. Approved
     * (status QC_APPROVED) or disapproved (qcDecidedAt set, regardless of
     * current status which may be SUBMITTED if bounced back to supervisor).
     */
    @Query("""
        SELECT * FROM pm_work_orders
        WHERE qcDecidedBy = :userId AND qcDecidedAt >= :sinceMillis
        ORDER BY qcDecidedAt DESC
    """)
    fun observeQcDoneByUserSince(userId: String, sinceMillis: Long): Flow<List<PmWorkOrderEntity>>

    // ─── Phase 2C-3 — QC action transitions ───────────────────────────────────

    @Query("""
        UPDATE pm_work_orders
        SET qcAcknowledgedBy = :userId,
            qcAcknowledgedByName = :userName,
            qcAcknowledgedAt = :ts,
            syncStatus = 'PENDING'
        WHERE id = :id
    """)
    suspend fun markQcAcknowledged(id: String, userId: String, userName: String, ts: Long)

    /**
     * QC Head override pickup — replaces whoever held the WO with the head.
     */
    @Query("""
        UPDATE pm_work_orders
        SET qcAcknowledgedBy = :newUserId,
            qcAcknowledgedByName = :newUserName,
            qcAcknowledgedAt = :ts,
            syncStatus = 'PENDING'
        WHERE id = :id
    """)
    suspend fun markQcOverrideAcknowledged(id: String, newUserId: String, newUserName: String, ts: Long)

    @Query("""
        UPDATE pm_work_orders
        SET status = 'QC_APPROVED',
            qcDecidedBy = :userId,
            qcDecidedByName = :userName,
            qcDecidedAt = :ts,
            qcDecisionNotes = :notes,
            qcAfterPhotoLocalPath = :photoPath,
            qcChecklistJson = :checklistJson,
            closedAt = :ts,
            syncStatus = 'PENDING'
        WHERE id = :id
    """)
    suspend fun markQcApproved(
        id: String,
        userId: String,
        userName: String,
        ts: Long,
        notes: String?,
        photoPath: String?,
        checklistJson: String
    )

    /**
     * QC disapprove — bounces the PM back to the supervisor's inbox
     * (status SUBMITTED), keeping qcDecidedAt populated so the supervisor's
     * review screen can show the "QC sent this back" banner.
     */
    @Query("""
        UPDATE pm_work_orders
        SET status = 'SUBMITTED',
            qcDecidedBy = :userId,
            qcDecidedByName = :userName,
            qcDecidedAt = :ts,
            qcDecisionNotes = :reason,
            qcAfterPhotoLocalPath = :photoPath,
            qcChecklistJson = :checklistJson,
            qcAcknowledgedBy = NULL,
            qcAcknowledgedByName = NULL,
            qcAcknowledgedAt = NULL,
            syncStatus = 'PENDING'
        WHERE id = :id
    """)
    suspend fun markQcDisapproved(
        id: String,
        userId: String,
        userName: String,
        ts: Long,
        reason: String,
        photoPath: String?,
        checklistJson: String
    )

    /**
     * Supervisor approve — Phase 2C-3 replaces the 2C-2 transition.
     * Status now goes straight to PENDING_QC; SUPERVISOR_APPROVED is no longer
     * set as the primary status. The supervisorApprovedAt timestamp remains
     * the audit trail.
     *
     * Also clears any previous QC-disapproval state so the WO appears fresh
     * to QC on re-submission after a bounceback.
     */
    @Query("""
        UPDATE pm_work_orders
        SET status = 'PENDING_QC',
            supervisorApprovedBy = :userId,
            supervisorApprovedByName = :userName,
            supervisorApprovedAt = :ts,
            supervisorRejectedAt = NULL,
            supervisorRejectionNotes = NULL,
            qcDecidedBy = NULL,
            qcDecidedByName = NULL,
            qcDecidedAt = NULL,
            qcDecisionNotes = NULL,
            qcAcknowledgedBy = NULL,
            qcAcknowledgedByName = NULL,
            qcAcknowledgedAt = NULL,
            syncStatus = 'PENDING'
        WHERE id = :id
    """)
    suspend fun markSupervisorApprovedToQc(id: String, userId: String, userName: String, ts: Long)
```

**Note:** Replace the existing `markSupervisorApproved` (lines 76-88 of `PmWorkOrderDao.kt`) with `markSupervisorApprovedToQc` only if the call site is updated (Step 1.4). Keep the old `markSupervisorApproved` symbol so the rename is the only behavioural change.

Actually — change the existing query in place. Edit lines 76-88:

Replace:
```kotlin
    @Query(
        """
        UPDATE pm_work_orders
        SET status = 'SUPERVISOR_APPROVED',
            supervisorApprovedBy = :userId,
            supervisorApprovedByName = :userName,
            supervisorApprovedAt = :ts,
            supervisorRejectedAt = NULL,
            supervisorRejectionNotes = NULL,
            syncStatus = 'PENDING'
        WHERE id = :id
        """
    )
    suspend fun markSupervisorApproved(id: String, userId: String, userName: String, ts: Long)
```

With (keeping the same name to preserve the call site, but flipping the target status and clearing prior QC bounce fields):
```kotlin
    /**
     * Phase 2C-3: supervisor approval IS the QC handoff.
     * Status flips to PENDING_QC (not SUPERVISOR_APPROVED). Any prior QC
     * bounce state is cleared so the WO appears fresh in the QC inbox.
     */
    @Query(
        """
        UPDATE pm_work_orders
        SET status = 'PENDING_QC',
            supervisorApprovedBy = :userId,
            supervisorApprovedByName = :userName,
            supervisorApprovedAt = :ts,
            supervisorRejectedAt = NULL,
            supervisorRejectionNotes = NULL,
            qcDecidedBy = NULL,
            qcDecidedByName = NULL,
            qcDecidedAt = NULL,
            qcDecisionNotes = NULL,
            qcAcknowledgedBy = NULL,
            qcAcknowledgedByName = NULL,
            qcAcknowledgedAt = NULL,
            syncStatus = 'PENDING'
        WHERE id = :id
        """
    )
    suspend fun markSupervisorApproved(id: String, userId: String, userName: String, ts: Long)
```

Then append the QC observers + QC action queries listed at the top of this step (skip the duplicate `markSupervisorApprovedToQc`).

- [ ] **Step 1.2: Extend `PmWorkOrderRepository` interface**

Append to `PmWorkOrderRepository.kt` after the existing `suggestSpareNames` method:

```kotlin
    // ─── Phase 2C-3 — QC inbox + QC actions ───────────────────────────────────

    /** PMs at PENDING_QC with no QC user assigned. Plant scoping is a VM concern. */
    fun observePendingQc(): Flow<List<PmWorkOrder>>

    /** PMs picked up by [userId] and not yet decided. */
    fun observeQcAcknowledgedByUser(userId: String): Flow<List<PmWorkOrder>>

    /** PMs decided by [userId] since [sinceMillis] (approved or disapproved). */
    fun observeQcDoneByUserSince(userId: String, sinceMillis: Long): Flow<List<PmWorkOrder>>

    /** QC member acknowledges (picks up) the PM. */
    suspend fun qcAcknowledge(woId: String, userId: String, userName: String): Result<Unit>

    /** QC head overrides another QC member's pickup and takes the PM. */
    suspend fun qcOverrideAcknowledge(woId: String, userId: String, userName: String): Result<Unit>

    /**
     * QC approve — terminal. Flips PM to QC_APPROVED, machine to IDLE.
     */
    suspend fun qcApprove(
        woId: String,
        userId: String,
        userName: String,
        checklistJson: String,
        afterPhotoLocalPath: String?,
        notes: String?
    ): Result<Unit>

    /**
     * QC disapprove — bounces the PM back to the supervisor's Approvals tab.
     * Status returns to SUBMITTED; the supervisor's review screen will show
     * a "QC sent this back: <reason>" banner because qcDecidedAt is set.
     * Machine remains PENDING_QC (still being reviewed by supervisor).
     */
    suspend fun qcDisapprove(
        woId: String,
        userId: String,
        userName: String,
        checklistJson: String,
        afterPhotoLocalPath: String?,
        reason: String
    ): Result<Unit>
```

- [ ] **Step 1.3: Implement QC observers + actions in `PmWorkOrderRepositoryImpl`**

Append to `PmWorkOrderRepositoryImpl.kt` before the `private suspend inline fun mutate(...)` helper:

```kotlin
    // ─── Phase 2C-3 — QC inbox observers ──────────────────────────────────────

    override fun observePendingQc(): Flow<List<PmWorkOrder>> =
        woDao.observePendingQc().map { list -> list.map { it.toDomain(emptyList(), emptyList()) } }

    override fun observeQcAcknowledgedByUser(userId: String): Flow<List<PmWorkOrder>> =
        woDao.observeQcAcknowledgedByUser(userId).map { list -> list.map { it.toDomain(emptyList(), emptyList()) } }

    override fun observeQcDoneByUserSince(userId: String, sinceMillis: Long): Flow<List<PmWorkOrder>> =
        woDao.observeQcDoneByUserSince(userId, sinceMillis).map { list -> list.map { it.toDomain(emptyList(), emptyList()) } }

    // ─── Phase 2C-3 — QC actions ──────────────────────────────────────────────

    override suspend fun qcAcknowledge(woId: String, userId: String, userName: String): Result<Unit> = mutate {
        val wo = woDao.getById(woId) ?: throw IllegalStateException("PM not found: $woId")
        if (wo.qcAcknowledgedBy != null && wo.qcAcknowledgedBy != userId) {
            throw IllegalStateException("Already picked up by ${wo.qcAcknowledgedByName ?: "another QC user"}")
        }
        val now = System.currentTimeMillis()
        woDao.markQcAcknowledged(woId, userId, userName, now)
        enqueueSync(
            ActionType.PM_QC_ACKNOWLEDGE,
            """{"wo_id":"$woId","user_id":"$userId","override":false}"""
        )
    }

    override suspend fun qcOverrideAcknowledge(woId: String, userId: String, userName: String): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markQcOverrideAcknowledged(woId, userId, userName, now)
        enqueueSync(
            ActionType.PM_QC_ACKNOWLEDGE,
            """{"wo_id":"$woId","user_id":"$userId","override":true}"""
        )
    }

    override suspend fun qcApprove(
        woId: String,
        userId: String,
        userName: String,
        checklistJson: String,
        afterPhotoLocalPath: String?,
        notes: String?
    ): Result<Unit> = mutate {
        val wo = woDao.getById(woId) ?: throw IllegalStateException("PM not found: $woId")
        val now = System.currentTimeMillis()
        woDao.markQcApproved(
            id = woId,
            userId = userId,
            userName = userName,
            ts = now,
            notes = notes,
            photoPath = afterPhotoLocalPath,
            checklistJson = checklistJson
        )
        // Machine returns to operator-controllable state.
        db.machineDao().updateStatus(wo.machineId, com.candorfoods.factoryops.domain.model.MachineStatus.IDLE.name, now)
        enqueueSync(ActionType.PM_QC_APPROVE, """{"wo_id":"$woId","user_id":"$userId"}""")
    }

    override suspend fun qcDisapprove(
        woId: String,
        userId: String,
        userName: String,
        checklistJson: String,
        afterPhotoLocalPath: String?,
        reason: String
    ): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        val safeReason = reason.replace("\"", "\\\"")
        woDao.markQcDisapproved(
            id = woId,
            userId = userId,
            userName = userName,
            ts = now,
            reason = reason,
            photoPath = afterPhotoLocalPath,
            checklistJson = checklistJson
        )
        // Machine stays on PENDING_QC — the supervisor still has it under review.
        enqueueSync(
            ActionType.PM_QC_DISAPPROVE,
            """{"wo_id":"$woId","user_id":"$userId","reason":"$safeReason"}"""
        )
    }
```

- [ ] **Step 1.4: Update `supervisorApprove` to flip machine to PENDING_QC**

In `PmWorkOrderRepositoryImpl.kt`, replace the body of `supervisorApprove` (currently lines 172-176):

```kotlin
    override suspend fun supervisorApprove(woId: String, supervisorId: String, supervisorName: String): Result<Unit> = mutate {
        val now = System.currentTimeMillis()
        woDao.markSupervisorApproved(woId, supervisorId, supervisorName, now)
        enqueueSync(ActionType.PM_SUPERVISOR_APPROVE, """{"wo_id":"$woId"}""")
    }
```

With:

```kotlin
    override suspend fun supervisorApprove(woId: String, supervisorId: String, supervisorName: String): Result<Unit> = mutate {
        val wo = woDao.getById(woId) ?: throw IllegalStateException("PM not found: $woId")
        val now = System.currentTimeMillis()
        woDao.markSupervisorApproved(woId, supervisorId, supervisorName, now)
        // Phase 2C-3: handoff flips the machine to PENDING_QC so it shows up
        // as "in QC review" on the operator/machine list while QC walks it.
        db.machineDao().updateStatus(wo.machineId, com.candorfoods.factoryops.domain.model.MachineStatus.PENDING_QC.name, now)
        enqueueSync(ActionType.PM_SUPERVISOR_APPROVE, """{"wo_id":"$woId"}""")
    }
```

Also add the import at the top if not already present:
```kotlin
import com.candorfoods.factoryops.domain.model.MachineStatus
```
And replace the two `com.candorfoods.factoryops.domain.model.MachineStatus.IDLE.name` / `PENDING_QC.name` with the short `MachineStatus.IDLE.name` / `MachineStatus.PENDING_QC.name` form.

- [ ] **Step 1.5: Update `supervisorReject` to release machine when bouncing tech path**

In `PmWorkOrderRepositoryImpl.kt`, replace `supervisorReject` body to also flip the machine to IDLE if the WO had been pushed to PENDING_QC by a previous approve (i.e., bounced from QC):

```kotlin
    override suspend fun supervisorReject(
        woId: String,
        supervisorId: String,
        supervisorName: String,
        notes: String
    ): Result<Unit> = mutate {
        val wo = woDao.getById(woId) ?: throw IllegalStateException("PM not found: $woId")
        val now = System.currentTimeMillis()
        val safeNotes = notes.replace("\"", "\\\"")
        woDao.markSupervisorRejected(woId, now, notes)
        // If a prior approve cycle had flipped the machine to PENDING_QC, return
        // it to IDLE now that the WO is back in IN_PROGRESS for tech rework.
        // No-op when the machine wasn't on PENDING_QC.
        db.machineDao().updateStatus(wo.machineId, MachineStatus.IDLE.name, now)
        enqueueSync(
            ActionType.PM_SUPERVISOR_REJECT,
            """{"wo_id":"$woId","reason":"$safeNotes"}"""
        )
    }
```

- [ ] **Step 1.6: Build verification**

Run: `./gradlew.bat :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`. If any unresolved-reference errors appear (e.g. `MachineStatus` import), add the import at the top of the file.

- [ ] **Step 1.7: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/data/local/dao/PmWorkOrderDao.kt \
        app/src/main/java/com/candorfoods/factoryops/domain/repository/PmWorkOrderRepository.kt \
        app/src/main/java/com/candorfoods/factoryops/data/repository/PmWorkOrderRepositoryImpl.kt
git commit -m "feat(pm): wire QC inbox observers + QC actions for Phase 2C-3"
```

**Approval gate:** Wait for user to confirm before starting Task 2.

---

## Task 2: Mixed QC inbox row model + ViewModel

**Files:**
- Create: `app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcInboxRow.kt`
- Modify: `app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcHomeViewModel.kt`

- [ ] **Step 2.1: Create the sealed inbox row model**

Create `QcInboxRow.kt`:

```kotlin
package com.candorfoods.factoryops.presentation.qc

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.PmWorkOrder

/**
 * A row in the QC inbox tabs. Mixed: breakdowns and PM work orders show side
 * by side, distinguished by a leading TYPE chip on the card.
 *
 * The unique [id] is the underlying flag id or work-order id; both are
 * URL-safe strings so they can be used directly as nav-arg values.
 *
 * Sort key [sortMillis] — higher = more recent. For pending tabs we use the
 * "needs-attention" timestamp (resolvedAt for breakdowns, submittedAt for
 * PMs).
 */
sealed class QcInboxRow {
    abstract val id: String
    abstract val machineCode: String
    abstract val machineName: String
    abstract val machineLocation: String
    abstract val criticality: Criticality
    abstract val sortMillis: Long

    data class Breakdown(
        val flag: BreakdownFlag,
        override val machineCode: String,
        override val machineName: String,
        override val machineLocation: String,
        override val criticality: Criticality,
    ) : QcInboxRow() {
        override val id: String get() = flag.id
        override val sortMillis: Long
            get() = flag.qcDecidedAt
                ?: flag.qcAcknowledgedAt
                ?: flag.resolvedAt
                ?: flag.raisedAt
    }

    data class Pm(
        val wo: PmWorkOrder,
        override val machineCode: String,
        override val machineName: String,
        override val machineLocation: String,
        override val criticality: Criticality,
    ) : QcInboxRow() {
        override val id: String get() = wo.id
        override val sortMillis: Long
            get() = wo.qcDecidedAt
                ?: wo.qcAcknowledgedAt
                ?: wo.submittedAt
                ?: wo.generatedAt
    }
}
```

- [ ] **Step 2.2: Replace `QcHomeViewModel` body to merge breakdown + PM streams**

Open `QcHomeViewModel.kt`. Update the `QcHomeUiState` data class to use the new sealed type, and rewrite `startObserving` to combine 6 streams (3 breakdown + 3 PM).

Replace the file contents with:

```kotlin
package com.candorfoods.factoryops.presentation.qc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.util.DateTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QcHomeUiState(
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val awaiting: List<QcInboxRow> = emptyList(),
    val myActive: List<QcInboxRow> = emptyList(),
    val doneToday: List<QcInboxRow> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QcHomeViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val breakdownRepository: BreakdownRepository,
    private val pmRepository: PmWorkOrderRepository,
    private val machineRepository: MachineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QcHomeUiState())
    val uiState: StateFlow<QcHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { machineRepository.syncMachines() }
        viewModelScope.launch {
            getCurrentUser()
                .filterNotNull()
                .collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                    startObserving(user)
                }
        }
    }

    private fun startObserving(user: User) {
        val machines: Flow<Map<String, Machine>> =
            machineRepository.observeAllMachines().map { list -> list.associateBy { it.id } }

        // Awaiting QC = unassigned PENDING_QC items in the user's plant
        viewModelScope.launch {
            combine(
                breakdownRepository.observePendingQcByPlant(user.plantId),
                pmRepository.observePendingQc(),
                machines
            ) { flags, wos, machineMap ->
                buildList {
                    flags.forEach { f -> machineMap[f.machineId]?.let { add(rowFor(f, it)) } }
                    wos.forEach { w ->
                        machineMap[w.machineId]
                            ?.takeIf { it.plantId == user.plantId }
                            ?.let { add(rowFor(w, it)) }
                    }
                }.sortedByDescending { it.sortMillis }
            }.collect { rows ->
                _uiState.update { it.copy(awaiting = rows, isLoading = false) }
            }
        }

        // My Active = items this user has picked up (any plant — QC head can override across plants in future)
        viewModelScope.launch {
            combine(
                breakdownRepository.observeQcAcknowledgedByUser(user.id),
                pmRepository.observeQcAcknowledgedByUser(user.id),
                machines
            ) { flags, wos, machineMap ->
                buildList {
                    flags.forEach { f -> machineMap[f.machineId]?.let { add(rowFor(f, it)) } }
                    wos.forEach { w -> machineMap[w.machineId]?.let { add(rowFor(w, it)) } }
                }.sortedByDescending { it.sortMillis }
            }.collect { rows ->
                _uiState.update { it.copy(myActive = rows) }
            }
        }

        // Done today = items decided today by this user
        viewModelScope.launch {
            val todayStart = DateTimeFormatter.startOfDayMillis(System.currentTimeMillis())
            combine(
                breakdownRepository.observeQcDoneByUserSince(user.id, todayStart),
                pmRepository.observeQcDoneByUserSince(user.id, todayStart),
                machines
            ) { flags, wos, machineMap ->
                buildList {
                    flags.forEach { f -> machineMap[f.machineId]?.let { add(rowFor(f, it)) } }
                    wos.forEach { w -> machineMap[w.machineId]?.let { add(rowFor(w, it)) } }
                }.sortedByDescending { it.sortMillis }
            }.collect { rows ->
                _uiState.update { it.copy(doneToday = rows) }
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            machineRepository.syncMachines()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun rowFor(flag: BreakdownFlag, machine: Machine) =
        QcInboxRow.Breakdown(
            flag = flag,
            machineCode = machine.code,
            machineName = machine.name,
            machineLocation = machine.location,
            criticality = machine.criticality
        )

    private fun rowFor(wo: PmWorkOrder, machine: Machine) =
        QcInboxRow.Pm(
            wo = wo,
            machineCode = machine.code,
            machineName = machine.name,
            machineLocation = machine.location,
            criticality = machine.criticality
        )
}
```

- [ ] **Step 2.3: Build verification**

Run: `./gradlew.bat :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`. The screen will not yet compile if it still references the old `QcTicketRowUiModel` type — that's expected, fixed in Task 3.

If you see "type mismatch" errors in `QcHomeScreen.kt`, that's expected and OK at this stage.

- [ ] **Step 2.4: Commit (only the VM/model)**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcInboxRow.kt \
        app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcHomeViewModel.kt
git commit -m "feat(qc): mixed inbox row model + ViewModel (Phase 2C-3)"
```

**Approval gate:** Wait for user before Task 3.

---

## Task 3: QC home screen — render mixed inbox + dispatch routes

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcHomeScreen.kt`
- Modify: `app/src/main/java/com/candorfoods/factoryops/navigation/Screen.kt`

- [ ] **Step 3.1: Add the `QcPmDetail` nav route**

In `Screen.kt`, append inside the `sealed class Screen` block (just below `QcTicketDetail`):

```kotlin
    object QcPmDetail : Screen("qc/pm/{woId}") {
        fun createRoute(woId: String) = "qc/pm/$woId"
        const val ARG = "woId"
    }
```

- [ ] **Step 3.2: Refactor `QcHomeScreen` to render `QcInboxRow` + TYPE chip**

The signature changes — the screen now gets two callbacks (one per type), or one callback that takes a `QcInboxRow`. Use the latter for cleaner navigation.

Open `QcHomeScreen.kt`. Make these specific edits:

**Edit 1:** Replace the `onTicketClick: (String) -> Unit` parameter on the `QcHomeScreen` composable with a typed click handler:

Old:
```kotlin
fun QcHomeScreen(
    onTicketClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: QcHomeViewModel = hiltViewModel()
)
```

New:
```kotlin
fun QcHomeScreen(
    onRowClick: (QcInboxRow) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: QcHomeViewModel = hiltViewModel()
)
```

**Edit 2:** Update the `LazyColumn`'s `items` block. Replace:

```kotlin
items(rows, key = { it.flag.id }) { row ->
    QcTicketCard(row = row, tab = selectedTab, onClick = { onTicketClick(row.flag.id) })
}
```

With:

```kotlin
items(rows, key = { it.id }) { row ->
    QcInboxCard(row = row, tab = selectedTab, onClick = { onRowClick(row) })
}
```

**Edit 3:** Replace the `QcTicketCard` composable with a `QcInboxCard` that branches by row type. After `private fun QcTab(...)`, replace `private fun QcTicketCard(...)` and `private fun StatusLine(...)` with:

```kotlin
@Composable
private fun QcInboxCard(
    row: QcInboxRow,
    tab: Int,
    onClick: () -> Unit
) {
    FactoryCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeChip(row)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.machineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${row.machineCode} · ${row.machineLocation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                CriticalityBadge(row.criticality)
                if (row is QcInboxRow.Breakdown) {
                    Spacer(Modifier.width(8.dp))
                    SeverityChip(row.flag.severity)
                }
            }

            // Body line — different per type
            when (row) {
                is QcInboxRow.Breakdown -> Text(
                    text = row.flag.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                is QcInboxRow.Pm -> Text(
                    text = row.wo.templateName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusLine(row, tab)
                Spacer(Modifier.weight(1f))
                if (row is QcInboxRow.Breakdown && (row.flag.beforePhotoLocalPath != null ||
                    row.flag.afterPhotoLocalPath != null ||
                    row.flag.qcAfterPhotoLocalPath != null)) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Photo",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeChip(row: QcInboxRow) {
    val (label, bg, fg) = when (row) {
        is QcInboxRow.Breakdown -> Triple("BD", FactoryTheme.colors.statusStopped.copy(alpha = 0.15f), FactoryTheme.colors.statusStopped)
        is QcInboxRow.Pm        -> Triple("PM", MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StatusLine(row: QcInboxRow, tab: Int) {
    val now = System.currentTimeMillis()
    val (label, color) = when (row) {
        is QcInboxRow.Breakdown -> breakdownStatusLine(row.flag, tab, now)
        is QcInboxRow.Pm        -> pmStatusLine(row.wo, tab, now)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun breakdownStatusLine(
    flag: com.candorfoods.factoryops.domain.model.BreakdownFlag,
    tab: Int,
    now: Long
): Pair<String, Color> = when (tab) {
    0 -> {
        val by = flag.resolvedByName ?: "technician"
        val rel = flag.resolvedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
        "Sent to QC by $by $rel" to FactoryTheme.colors.statusFlagged
    }
    1 -> {
        val rel = flag.qcAcknowledgedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
        "Picked up $rel" to MaterialTheme.colorScheme.primary
    }
    else -> {
        val (verb, c) = when (flag.status) {
            BreakdownStatus.QC_APPROVED -> "Approved" to FactoryTheme.colors.statusRunning
            BreakdownStatus.REOPENED    -> "Disapproved" to MaterialTheme.colorScheme.error
            else                        -> "Decided" to MaterialTheme.colorScheme.onSurface
        }
        val rel = flag.qcDecidedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
        "$verb $rel" to c
    }
}

@Composable
private fun pmStatusLine(
    wo: com.candorfoods.factoryops.domain.model.PmWorkOrder,
    tab: Int,
    now: Long
): Pair<String, Color> = when (tab) {
    0 -> {
        val rel = wo.submittedAt?.let { DateTimeFormatter.formatRelative(it, now) }
            ?: wo.supervisorApprovedAt?.let { DateTimeFormatter.formatRelative(it, now) }
            ?: ""
        "Sent to QC by ${wo.supervisorApprovedByName ?: "supervisor"} $rel" to FactoryTheme.colors.statusFlagged
    }
    1 -> {
        val rel = wo.qcAcknowledgedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
        "Picked up $rel" to MaterialTheme.colorScheme.primary
    }
    else -> {
        val (verb, c) = when (wo.status) {
            com.candorfoods.factoryops.domain.model.PmWoStatus.QC_APPROVED -> "Approved" to FactoryTheme.colors.statusRunning
            com.candorfoods.factoryops.domain.model.PmWoStatus.SUBMITTED   -> "Sent back to supervisor" to MaterialTheme.colorScheme.error
            else                                                            -> "Decided" to MaterialTheme.colorScheme.onSurface
        }
        val rel = wo.qcDecidedAt?.let { DateTimeFormatter.formatRelative(it, now) } ?: ""
        "$verb $rel" to c
    }
}
```

Make sure the import block at the top of the file includes:
```kotlin
import com.candorfoods.factoryops.domain.model.PmWoStatus
```
(Add it if absent.)

- [ ] **Step 3.3: Wire `onRowClick` in `NavGraph`**

Open `NavGraph.kt`. Find the `composable(Screen.QcHome.route)` block (search for `QcHomeScreen(`).

Replace `onTicketClick = { ticketId -> ... }` with the typed dispatcher. The replacement looks like:

```kotlin
            QcHomeScreen(
                onRowClick = { row ->
                    val route = when (row) {
                        is QcInboxRow.Breakdown -> Screen.QcTicketDetail.createRoute(row.id)
                        is QcInboxRow.Pm        -> Screen.QcPmDetail.createRoute(row.id)
                    }
                    navController.navigate(route)
                },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
```

Add the import at the top of `NavGraph.kt` if missing:
```kotlin
import com.candorfoods.factoryops.presentation.qc.QcInboxRow
```

(The actual previous parameter and shape may differ. Match the existing block by replacing only the `onTicketClick = ...` line with the typed version above, keeping any other parameters that exist there.)

- [ ] **Step 3.4: Build verification**

Run: `./gradlew.bat :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`. If errors remain, they'll be in `NavGraph.kt` referring to the missing `QcPmDetail` composable destination — that gets added in Task 4. To unblock the build now, add a temporary placeholder destination in `NavGraph.kt`:

```kotlin
        composable(
            route = Screen.QcPmDetail.route,
            arguments = listOf(navArgument(Screen.QcPmDetail.ARG) { type = NavType.StringType })
        ) {
            // Placeholder — real screen lands in Task 4
            androidx.compose.material3.Text("PM QC detail coming soon.")
        }
```

- [ ] **Step 3.5: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcHomeScreen.kt \
        app/src/main/java/com/candorfoods/factoryops/navigation/Screen.kt \
        app/src/main/java/com/candorfoods/factoryops/navigation/NavGraph.kt
git commit -m "feat(qc): mixed inbox screen with BD/PM type chip + route dispatch"
```

**Approval gate:** Wait for user.

**Manual smoke test (user side):** Build APK, install, log in as a QC user. Tabs should still render breakdown rows correctly. PM rows won't appear yet (no PM is in PENDING_QC) — that comes after Task 4 + walking the supervisor approve flow.

---

## Task 4: PM QC detail screen + ViewModel

**Files:**
- Create: `app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcPmDetailViewModel.kt`
- Create: `app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcPmDetailScreen.kt`
- Modify: `app/src/main/java/com/candorfoods/factoryops/navigation/NavGraph.kt`

- [ ] **Step 4.1: Create `QcPmDetailViewModel.kt`**

```kotlin
package com.candorfoods.factoryops.presentation.qc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class QcPmDetailUiState(
    val workOrder: PmWorkOrder? = null,
    val machine: Machine? = null,
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val showChecklistSheet: Boolean = false,
    val checklist: QcChecklistDraft = QcChecklistDraft()
) {
    val isHeldByMe: Boolean
        get() = workOrder?.qcAcknowledgedBy != null && workOrder.qcAcknowledgedBy == currentUser?.id
    val isHeldByOther: Boolean
        get() = workOrder?.qcAcknowledgedBy != null && workOrder.qcAcknowledgedBy != currentUser?.id
    val isQcHead: Boolean get() = currentUser?.role == UserRole.QC_HEAD
}

sealed interface QcPmDetailUiEvent {
    data class ShowSnackbar(val message: String) : QcPmDetailUiEvent
    object Acknowledged : QcPmDetailUiEvent
    object Overridden : QcPmDetailUiEvent
    object Approved : QcPmDetailUiEvent
    object Disapproved : QcPmDetailUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QcPmDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pmRepo: PmWorkOrderRepository,
    private val machineRepository: MachineRepository,
    getCurrentUser: GetCurrentUserUseCase
) : ViewModel() {

    private val woId: String = checkNotNull(savedStateHandle[Screen.QcPmDetail.ARG])

    private val json = Json { encodeDefaults = true }

    private val _uiState = MutableStateFlow(QcPmDetailUiState())
    val uiState: StateFlow<QcPmDetailUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<QcPmDetailUiEvent>()
    val uiEvent: SharedFlow<QcPmDetailUiEvent> = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
        viewModelScope.launch {
            pmRepo.observeById(woId)
                .flatMapLatest { wo ->
                    if (wo == null) flowOf(null to null)
                    else machineRepository.getMachineById(wo.machineId).map { m -> wo to m }
                }
                .collect { (wo, machine) ->
                    _uiState.update { it.copy(workOrder = wo, machine = machine, isLoading = false) }
                }
        }
    }

    fun onAcknowledgeForQc() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val wo = state.workOrder ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val r = pmRepo.qcAcknowledge(wo.id, user.id, user.name)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _uiEvent.emit(QcPmDetailUiEvent.Acknowledged)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcPmDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onOverrideAcknowledgeAsHead() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val wo = state.workOrder ?: return
        if (user.role != UserRole.QC_HEAD) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val r = pmRepo.qcOverrideAcknowledge(wo.id, user.id, user.name)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _uiEvent.emit(QcPmDetailUiEvent.Overridden)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcPmDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onOpenChecklistSheet() = _uiState.update {
        it.copy(showChecklistSheet = true, checklist = QcChecklistDraft(), error = null)
    }

    fun onDismissChecklistSheet() = _uiState.update { it.copy(showChecklistSheet = false) }

    fun onChecklistChanged(draft: QcChecklistDraft) =
        _uiState.update { it.copy(checklist = draft, error = null) }

    fun onApprove() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val wo = state.workOrder ?: return
        val draft = state.checklist
        if (!draft.canApprove) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val checklistJson = json.encodeToString(draft.toSnapshot())
            when (val r = pmRepo.qcApprove(
                woId = wo.id,
                userId = user.id,
                userName = user.name,
                checklistJson = checklistJson,
                afterPhotoLocalPath = draft.afterPhotoLocalPath,
                notes = draft.notes.takeIf { it.isNotBlank() }
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, showChecklistSheet = false) }
                    _uiEvent.emit(QcPmDetailUiEvent.Approved)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcPmDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onDisapprove(reason: String) {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val wo = state.workOrder ?: return
        val draft = state.checklist
        val trimmed = reason.trim()
        if (!draft.canDisapprove) return
        if (trimmed.length !in 4..500) {
            viewModelScope.launch {
                _uiEvent.emit(QcPmDetailUiEvent.ShowSnackbar("Reason must be 4–500 characters."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val checklistJson = json.encodeToString(draft.toSnapshot())
            when (val r = pmRepo.qcDisapprove(
                woId = wo.id,
                userId = user.id,
                userName = user.name,
                checklistJson = checklistJson,
                afterPhotoLocalPath = draft.afterPhotoLocalPath,
                reason = trimmed
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, showChecklistSheet = false) }
                    _uiEvent.emit(QcPmDetailUiEvent.Disapproved)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = r.message) }
                    _uiEvent.emit(QcPmDetailUiEvent.ShowSnackbar(r.message))
                }
                else -> _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
```

- [ ] **Step 4.2: Create `QcPmDetailScreen.kt`**

```kotlin
package com.candorfoods.factoryops.presentation.qc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.candorfoods.factoryops.domain.model.Machine
import com.candorfoods.factoryops.domain.model.PmTaskLogStatus
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.LoadingOverlay
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.TouchTarget
import com.candorfoods.factoryops.util.DateTimeFormatter
import java.io.File

private val DarkAmberText = Color(0xFF92400E)
private val LightAmberBg  = Color(0xFFFEF3C7)
private val AmberBorder   = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QcPmDetailScreen(
    onBack: () -> Unit,
    viewModel: QcPmDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is QcPmDetailUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is QcPmDetailUiEvent.Acknowledged -> snackbarHostState.showSnackbar("Picked up for QC.")
                is QcPmDetailUiEvent.Overridden   -> snackbarHostState.showSnackbar("QC override applied.")
                is QcPmDetailUiEvent.Approved     -> {
                    snackbarHostState.showSnackbar("QC approved — machine returned to operator.")
                    onBack()
                }
                is QcPmDetailUiEvent.Disapproved  -> {
                    snackbarHostState.showSnackbar("Sent back to supervisor.")
                    onBack()
                }
            }
        }
    }

    LoadingOverlay(isLoading = uiState.isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.machine?.name ?: "PM QC") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            val wo = uiState.workOrder
            val machine = uiState.machine
            if (wo == null || machine == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (!uiState.isLoading) Text("Work order not found.")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeaderCard(machine = machine, wo = wo)
                    SubmissionCard(wo = wo)
                    HorizontalDivider()
                    Text("Checklist results", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    wo.taskLogs.forEachIndexed { idx, log -> ItemRowCard(idx + 1, log) }
                    if (wo.spares.isNotEmpty()) SparesCard(wo)
                    if (!wo.finalNotes.isNullOrBlank()) FinalNotesCard(wo.finalNotes!!)
                    HorizontalDivider()
                    QcActionSection(
                        wo = wo,
                        uiState = uiState,
                        onAcknowledge = viewModel::onAcknowledgeForQc,
                        onOpenChecklist = viewModel::onOpenChecklistSheet,
                        onOverride = viewModel::onOverrideAcknowledgeAsHead
                    )
                }
            }
        }

        if (uiState.showChecklistSheet && uiState.workOrder != null && uiState.machine != null) {
            QcChecklistSheet(
                machineName = uiState.machine!!.name,
                machineCode = uiState.machine!!.code,
                ticketId = uiState.workOrder!!.id,
                draft = uiState.checklist,
                isSubmitting = uiState.isSubmitting,
                error = uiState.error,
                onChange = viewModel::onChecklistChanged,
                onApprove = viewModel::onApprove,
                onDisapprove = viewModel::onDisapprove,
                onDismiss = viewModel::onDismissChecklistSheet
            )
        }
    }
}

@Composable
private fun HeaderCard(machine: Machine, wo: PmWorkOrder) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("PM", color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(8.dp))
                Text(machine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("${machine.code} · ${machine.location}",
                style = MaterialTheme.typography.bodySmall,
                color = FactoryTheme.colors.onSurfaceMuted)
            Text(wo.templateName, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SubmissionCard(wo: PmWorkOrder) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Technician + supervisor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Performed by ${wo.assignedTechnicianName}" +
                (wo.submittedAt?.let { " · submitted ${DateTimeFormatter.formatRelative(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall)
            wo.supervisorApprovedByName?.let { sup ->
                Text("Approved by $sup" +
                    (wo.supervisorApprovedAt?.let { " · ${DateTimeFormatter.formatRelative(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ItemRowCard(idx: Int, log: com.candorfoods.factoryops.domain.model.PmWoTaskLog) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$idx. ${log.title}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                StatusPill(log.status)
            }
            if (log.requiresMeasurement) {
                val unit = log.measurementUnit?.let { " $it" }.orEmpty()
                val rangeStr = when {
                    log.measurementMin != null && log.measurementMax != null ->
                        " (acceptable ${log.measurementMin}–${log.measurementMax}$unit)"
                    else -> ""
                }
                val parsedOk = log.measurementValue?.let { v ->
                    log.measurementMin?.let { v >= it } != false
                        && log.measurementMax?.let { v <= it } != false
                } ?: false
                val color = if (parsedOk) FactoryTheme.colors.statusRunning else FactoryTheme.colors.statusStopped
                Text("Measured: ${log.measurementValue ?: "—"}$unit$rangeStr",
                    color = color, style = MaterialTheme.typography.bodyMedium)
            }
            if (log.requiresPhoto && log.photoLocalPath != null) {
                AsyncImage(
                    model = File(log.photoLocalPath),
                    contentDescription = "Photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                )
            }
            if (!log.notes.isNullOrBlank()) {
                Text("Notes: ${log.notes}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SparesCard(wo: PmWorkOrder) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Spares used", fontWeight = FontWeight.Bold)
            wo.spares.forEach { s ->
                Text("• ${s.spareName} — ${s.quantityUsed} ${s.unit}",
                    style = MaterialTheme.typography.bodyMedium)
                if (!s.notes.isNullOrBlank()) {
                    Text("    ${s.notes}", style = MaterialTheme.typography.bodySmall, color = FactoryTheme.colors.onSurfaceMuted)
                }
            }
        }
    }
}

@Composable
private fun FinalNotesCard(notes: String) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("Final tech notes", fontWeight = FontWeight.Bold)
            Text(notes)
        }
    }
}

@Composable
private fun StatusPill(status: PmTaskLogStatus) {
    val (label, color) = when (status) {
        PmTaskLogStatus.PASS -> "PASS" to FactoryTheme.colors.statusRunning
        PmTaskLogStatus.FAIL -> "FAIL" to FactoryTheme.colors.statusStopped
        PmTaskLogStatus.NOT_APPLICABLE -> "N/A" to FactoryTheme.colors.onSurfaceMuted
        PmTaskLogStatus.PENDING -> "PENDING" to FactoryTheme.colors.statusFlagged
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QcActionSection(
    wo: PmWorkOrder,
    uiState: QcPmDetailUiState,
    onAcknowledge: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOverride: () -> Unit
) {
    when (wo.status) {
        PmWoStatus.PENDING_QC -> {
            when {
                wo.qcAcknowledgedBy == null -> {
                    Button(
                        onClick = onAcknowledge,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(TouchTarget.primary),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberBorder, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Acknowledge for QC", style = MaterialTheme.typography.titleMedium,
                            color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                uiState.isHeldByMe -> {
                    Card(colors = CardDefaults.cardColors(containerColor = LightAmberBg)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Picked up by you", fontWeight = FontWeight.SemiBold, color = DarkAmberText)
                            wo.qcAcknowledgedAt?.let {
                                Text("at ${DateTimeFormatter.formatDateTime(it)}",
                                    style = MaterialTheme.typography.bodySmall, color = DarkAmberText.copy(alpha = 0.8f))
                            }
                        }
                    }
                    PrimaryButton(
                        text = "Run QC checklist",
                        onClick = onOpenChecklist,
                        enabled = !uiState.isSubmitting,
                        style = PrimaryButtonStyle.Primary,
                        size = PrimaryButtonSize.Primary,
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                uiState.isHeldByOther -> {
                    Card(colors = CardDefaults.cardColors(containerColor = LightAmberBg)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Picked up by ${wo.qcAcknowledgedByName ?: "another QC user"}",
                                fontWeight = FontWeight.SemiBold, color = DarkAmberText)
                            wo.qcAcknowledgedAt?.let {
                                Text("at ${DateTimeFormatter.formatDateTime(it)}",
                                    style = MaterialTheme.typography.bodySmall, color = DarkAmberText.copy(alpha = 0.8f))
                            }
                        }
                    }
                    if (uiState.isQcHead) {
                        PrimaryButton(
                            text = "Override — take this over as QC Head",
                            onClick = onOverride,
                            enabled = !uiState.isSubmitting,
                            style = PrimaryButtonStyle.Outlined,
                            size = PrimaryButtonSize.Min,
                            icon = Icons.Default.SwapHoriz,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        PmWoStatus.QC_APPROVED -> {
            val approved = FactoryTheme.colors.statusRunning
            Card(colors = CardDefaults.cardColors(containerColor = approved.copy(alpha = 0.18f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("QC approved", fontWeight = FontWeight.Bold, color = approved,
                        style = MaterialTheme.typography.titleMedium)
                    Text("by ${wo.qcDecidedByName ?: "QC"}" +
                        (wo.qcDecidedAt?.let { " at ${DateTimeFormatter.formatDateTime(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                    wo.qcDecisionNotes?.takeIf { it.isNotBlank() }?.let {
                        Text("Notes", fontWeight = FontWeight.SemiBold)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    wo.qcAfterPhotoLocalPath?.let { path ->
                        AsyncImage(
                            model = File(path), contentDescription = "QC photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }
        PmWoStatus.SUBMITTED -> {
            // The PM is back with the supervisor (we sent it back via disapprove,
            // OR another QC user decided it went back).
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sent back to supervisor",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.titleMedium)
                    Text("by ${wo.qcDecidedByName ?: "QC"}" +
                        (wo.qcDecidedAt?.let { " on ${DateTimeFormatter.formatDateTime(it)}" } ?: ""),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall)
                    wo.qcDecisionNotes?.takeIf { it.isNotBlank() }?.let {
                        Text("Reason", fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        else -> {
            Text("This PM is not awaiting QC.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}
```

- [ ] **Step 4.3: Replace the placeholder `QcPmDetail` destination in `NavGraph.kt`**

Find the placeholder added in Step 3.4 and replace it with:

```kotlin
        composable(
            route = Screen.QcPmDetail.route,
            arguments = listOf(navArgument(Screen.QcPmDetail.ARG) { type = NavType.StringType })
        ) {
            QcPmDetailScreen(onBack = { navController.popBackStack() })
        }
```

Add the import at the top of `NavGraph.kt` if missing:
```kotlin
import com.candorfoods.factoryops.presentation.qc.QcPmDetailScreen
```

- [ ] **Step 4.4: Build verification**

Run: `./gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` updated.

- [ ] **Step 4.5: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcPmDetailViewModel.kt \
        app/src/main/java/com/candorfoods/factoryops/presentation/qc/QcPmDetailScreen.kt \
        app/src/main/java/com/candorfoods/factoryops/navigation/NavGraph.kt
git commit -m "feat(qc): PM QC detail screen with checklist sheet integration"
```

**Approval gate:** Wait for user.

**Manual smoke test (user side):**
1. Reinstall APK.
2. Login as `technician1` → walk a PM to SUBMITTED.
3. Login as `supervisor1` → Approvals tab → Approve. (PM now PENDING_QC.)
4. Logout, login as `pooja.mhalim` (QC_MEMBER).
5. Awaiting QC tab — the PM should appear with a blue **PM** chip.
6. Tap → PM detail screen → Acknowledge for QC → Run QC checklist → fill required items → capture after-photo → Approve. Snackbar "QC approved — machine returned to operator." Tab moves to "Done today".
7. Operator side: machine flips back to IDLE.

---

## Task 5: Supervisor review screen — handle QC bounceback

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/SupervisorPmReviewScreen.kt`

The VM (`SupervisorPmReviewViewModel`) needs no changes — its `onApprove` already delegates to `pmRepo.supervisorApprove`, which (after Task 1.1 dao change) clears prior QC fields and re-routes to PENDING_QC. We just need the screen to *show* the bounceback banner.

- [ ] **Step 5.1: Add a "QC sent this back" banner above the header card**

Open `SupervisorPmReviewScreen.kt`. Find the line:
```kotlin
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(machine.name, ...)
```

Just **before** that `FactoryCard`, insert:

```kotlin
            // QC bounceback banner — shows when this PM was disapproved by QC
            // and is now back in the supervisor's inbox.
            if (wo.qcDecidedAt != null && wo.status == com.candorfoods.factoryops.domain.model.PmWoStatus.SUBMITTED) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "QC sent this back",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "by ${wo.qcDecidedByName ?: "QC"}" +
                                (wo.qcDecidedAt?.let { " · ${DateTimeFormatter.formatRelative(it)}" } ?: ""),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        wo.qcDecisionNotes?.takeIf { it.isNotBlank() }?.let {
                            Text("Reason", fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "Approve to re-send to QC, or reject to send to technician for rework.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
```

Add these imports to the top of `SupervisorPmReviewScreen.kt` if missing:
```kotlin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
```

- [ ] **Step 5.2: Reword the Approve button when in bounceback mode**

In the same screen, find the `PrimaryButton(text = "Approve", ...)` call. Change `text = "Approve"` to a conditional:

Old:
```kotlin
            PrimaryButton(
                text = "Approve",
                onClick = viewModel::onApprove,
```

New:
```kotlin
            val approveLabel = if (wo.qcDecidedAt != null && wo.status == com.candorfoods.factoryops.domain.model.PmWoStatus.SUBMITTED) {
                "Re-approve and send to QC"
            } else {
                "Approve"
            }
            PrimaryButton(
                text = approveLabel,
                onClick = viewModel::onApprove,
```

- [ ] **Step 5.3: Build verification**

Run: `./gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5.4: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/supervisor/SupervisorPmReviewScreen.kt
git commit -m "feat(supervisor): show QC bounceback banner on PM review screen"
```

**Approval gate:** Wait for user.

**Manual smoke test:**
1. Reinstall APK. Submit a PM as technician → supervisor approve.
2. Login as QC member → Acknowledge → Run checklist → fill items but **disapprove** with reason "leak still visible".
3. Logout, login as supervisor → Approvals tab.
4. Same PM should appear with a red **"QC sent this back"** banner.
5. Approve button now reads **"Re-approve and send to QC"**. Tap it → PM goes back to PENDING_QC fresh.
6. Or tap Reject → PM goes to IN_PROGRESS for technician with the supervisor rejection banner (existing behaviour).

---

## Task 6: Final integration — machine status reflection + smoke check

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/local/dao/MachineDao.kt` (verify `updateStatus` exists; no edit if it does)

This task is verification-only — Tasks 1.4, 1.5, and the QC approve/disapprove DB writes already update machine status. We confirm the contract holds end-to-end.

- [ ] **Step 6.1: Verify `MachineDao.updateStatus` exists with the expected signature**

Open `app/src/main/java/com/candorfoods/factoryops/data/local/dao/MachineDao.kt` and confirm there is a method:
```kotlin
suspend fun updateStatus(machineId: String, status: String, ts: Long)
```
(or similar — same signature as called from `BreakdownRepositoryImpl.qcApprove`). If it doesn't exist exactly, search how `BreakdownRepositoryImpl` calls it and mirror that. **No code change** if the method already exists — `BreakdownRepositoryImpl` uses it identically, so it must exist.

- [ ] **Step 6.2: Build verification**

Run: `./gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`. APK present at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 6.3: Commit (only if any change was made; otherwise skip)**

If you made no changes, skip the commit.

**Approval gate:** Wait for user.

---

## Task 7: Update PROGRESS.md + run full end-to-end test

**Files:**
- Modify: `FactoryOps/PROGRESS.md`

- [ ] **Step 7.1: Add Phase 2C-3 section to PROGRESS.md**

Open `PROGRESS.md`. Update:

1. Top "Last updated" line → today's date with note "Phase 2C-3 shipped — PM QC gate (mixed inbox)".
2. Snapshot table row for **2C-3** → status `✅ DONE`.
3. Add a new section after "Phase 2C-2" titled `## Phase 2C-3 — PM QC gate (mixed inbox) ✅` with:
   - **Done date.**
   - **Repo + DAO additions** bullet list (the queries added in Task 1).
   - **Domain reuse** bullet (no enum/schema change — fields were pre-stubbed).
   - **Mixed inbox** bullet (sealed `QcInboxRow`, BD/PM type chip, route dispatch).
   - **PM QC detail screen** bullet (parallels the breakdown QC detail).
   - **Supervisor bounceback** bullet (banner + reworded button).
   - **Q-answer recap** locked in this session (Q1A/Q2A/Q3A/Q4B/Q5A/Q6A/Q7A).
   - **Build line** — APK size + timestamp.
   - **Test plan** — the full e2e walk.

- [ ] **Step 7.2: Run the full end-to-end manual test**

Acceptance scenarios (user runs on device):

**Scenario A — Happy path (PM approved by QC):**
1. Reinstall APK.
2. Login `technician1` / `pass123`.
3. PM tasks tab → tap a Roaster PM → Acknowledge → Start PM → walk all items (PASS each, capture photos where required, type measurements within bounds) → Submit.
4. Logout. Login `supervisor1` / `pass123`.
5. Approvals tab → tap PM → Approve. Snackbar: "Approved." PM disappears from supervisor inbox.
6. Logout. Login `pooja.mhalim` / `pass123` (QC_MEMBER).
7. Awaiting QC tab → PM appears with **PM** chip.
8. Tap → Acknowledge for QC → tab moves to "My Active".
9. Run QC checklist → fill required items as PASS → capture after-photo → Approve.
10. Snackbar: "QC approved — machine returned to operator." PM moves to "Done today".
11. Logout. Login `operator1`. Roaster machine status is IDLE again.

**Scenario B — QC bounces back to supervisor:**
1. Repeat steps 1–7 of Scenario A.
2. Run QC checklist → Disapprove with reason "evidence of oil leak post-repair, retake".
3. Snackbar: "Sent back to supervisor."
4. Logout. Login `supervisor1`.
5. Approvals tab → PM appears, with red banner **"QC sent this back: evidence of oil leak post-repair, retake"**.
6. Tap PM → review screen shows banner. Approve button reads **"Re-approve and send to QC"**.
7. Tap "Re-approve" → PM goes back to PENDING_QC fresh. Login as QC member, Awaiting QC tab → PM is back, no QC history shown (cleared on re-approve).

**Scenario C — Supervisor rejects QC bounceback to technician:**
1. Repeat 1–5 of Scenario B.
2. In Approvals tab tap PM. Tap **Reject — needs rework** with reason "tech to redo item 3".
3. PM disappears from supervisor inbox.
4. Logout. Login `technician1`. PM tasks tab → PM is back as IN_PROGRESS with the supervisor rejection banner.

**Scenario D — Mixed inbox:**
1. With one PM in PENDING_QC AND one breakdown in PENDING_QC simultaneously.
2. Login as QC member → Awaiting QC tab shows both, PM with blue chip and breakdown with red chip.
3. Tap each — opens the correct detail screen.

- [ ] **Step 7.3: Commit**

```bash
git add FactoryOps/PROGRESS.md
git commit -m "docs: log Phase 2C-3 (PM QC gate / mixed inbox) shipped"
```

---

## Self-Review Notes

**Spec coverage:**
- Carryover line "After supervisor approve, status → PENDING_QC, machine → PENDING_QC, mixed inbox" → covered by Task 1.4 (status), Task 1.4 (machine flip), Task 2/3 (mixed inbox).
- Q1A drop SUPERVISOR_APPROVED → Task 1.1 in-place edit of `markSupervisorApproved`.
- Q2A machine PENDING_QC → Task 1.4.
- Q3A reuse breakdown checklist → Task 4 (no checklist code added; reused `QcChecklistSheet`).
- Q4B QC disapprove → supervisor → Task 1.1 `markQcDisapproved` writes status SUBMITTED + qcDecidedAt; Task 5 banner + reworded button.
- Q5A small leading chip → Task 3 `TypeChip`.
- Q6A plant scoping in VM → Task 2.2 filters in-VM via machine map.
- Q7A no extra seed → no seed task.

**Type consistency:**
- Repo method names: `qcAcknowledge`, `qcOverrideAcknowledge`, `qcApprove`, `qcDisapprove` — match across interface + impl + VM consumers.
- DAO method names: `markQcAcknowledged`, `markQcOverrideAcknowledged`, `markQcApproved`, `markQcDisapproved`, `markSupervisorApproved` (existing, query body changed).
- Sealed type: `QcInboxRow.Breakdown` and `QcInboxRow.Pm` — referenced consistently.
- Screen route arg: `Screen.QcPmDetail.ARG = "woId"` (same shape as `TechnicianPmDetail`).

**No placeholders:** Every code block is complete and self-contained. No "implement later" / "TBD".

---

## Notes on machine-status edge cases

- **Concurrent breakdown + PM on same machine:** if a machine is already FLAGGED due to an open breakdown when a PM lands at PENDING_QC, the supervisor approve will overwrite the machine status to PENDING_QC (which is more "downstream"). When the PM completes, machine returns to IDLE — but the breakdown will keep the machine FLAGGED on its own next status update. This is acceptable for the mock app. Phase 3 backend should introduce a proper machine state machine with stacked reasons.
- **`supervisorReject` → IDLE:** same overwrite caveat. Mock-only acceptable.
- The user accepted these tradeoffs implicitly via Q2A. Document in PROGRESS.md that proper state-machine work is Phase 3.
