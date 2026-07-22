# Head Reports — History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder "Reports" tile on the Head home screen with four real, date-filterable history views — Machine Readings, Floor Readings, Breakdown History, QC Activity History — reading from `mt_machine_daily_kwh`, `mt_floor_utility_readings`, and `mt_breakdown_records` via new backend endpoints.

**Architecture:** One shared filter-bar pattern (`HistoryFilters.kt`: building chips + `DateField` date range + optional entity dropdown, reused from `UtilityComponents.kt`) feeding four independent, per-section Hilt ViewModels/Screens (Hilt requires concrete classes, so no generic base ViewModel — mirrors the existing parallel-not-shared pattern of `PreventiveListViewModel`/`BreakdownListViewModel`). Breakdown History and QC Activity History reuse the existing `BreakdownFlag` domain model (it already has every field needed); Machine/Floor readings get two new small domain models. Machine/floor filter options are derived from the existing `GetMachineMasterUseCase` (already backend-connected via `mt_asset_list`), not a new endpoint.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, Retrofit + kotlinx.serialization, coroutines/Flow, JUnit5 + MockK for tests. Backend: FastAPI/SQLAlchemy at `D:\Maintenance module\backend` (out of scope for direct edits — endpoints are specified in Task 13's handoff prompt only).

---

## REVISION (mid-execution, after Task 2) — reuse the concurrently-built Supervisor Reports pipeline

While executing this plan, a concurrent session finished a separate "Supervisor Reports" feature that already fully covers Machine Readings + Floor Readings history, generically (no role coupling):

- `data/remote/api/ReportsApi.kt` — `GET reports/machines` / `GET reports/floor-readings`, `plant`/`from`/`to` query params (`plant` nullable = both plants).
- `domain/repository/ReportsRepository.kt` / `data/repository/ReportsRepositoryImpl.kt` — `machinesReading(plant, from, to): Result<List<MachineReadingRow>>`, `floorReadingsReport(plant, from, to): Result<List<FloorReadingReportRow>>`. Already wired into Hilt (`NetworkModule.kt`/`RepositoryModule.kt`).
- `domain/model/reports/ReportRows.kt` — `MachineReadingRow`, `FloorReadingReportRow` domain models, functionally equivalent to what Task 1 originally built.
- `presentation/reports/FloorReadingsReportScreen.kt` + `FloorReadingsReportViewModel.kt` — a complete, **role-agnostic** screen (only takes `onBack: () -> Unit` + Hilt viewmodel, no Supervisor-specific logic) using the existing `PlantFilterChips` (`presentation/utilities/UtilityComponents.kt:50-61`, nullable `String?` = "A-185"/"W-202"/null-for-all) + `DateField` components.
- `presentation/reports/ReportsHubScreen.kt` — Supervisor's own hub (Machines Reading / Floor Readings), separate from the `HeadReportsScreen` this plan builds.
- No `MachinesReadingScreen`/`ViewModel` exists yet — that piece is still missing and this plan now builds it (revised Task 7), placed in the **same shared `presentation/reports/` package** for consistency, not under `presentation/head/`.

**What changed as a result:**
- Task 1's `MachineKwhHistoryEntry`/`FloorReadingHistoryEntry` and Task 2's `MachineKwhHistoryDto`/`FloorReadingHistoryDto` (+their tests) were **deleted/trimmed** — superseded by `MachineReadingRow`/`FloorReadingReportRow` and `ReportsDtos.kt`. `BreakdownHistoryDto` (Task 2) is kept — no overlap, still needed for Breakdown/QC history.
- **Task 3 (MachineKwhHistoryRepository) is dropped entirely** — reuse `ReportsRepository.machinesReading()`.
- **Task 4 (extend FloorReadingApi) is dropped entirely** — reuse `ReportsRepository.floorReadingsReport()`; `FloorReadingApi`/`FloorReadingRepository` are not touched by this plan at all.
- **Task 6 (shared filter components) is replaced**: reuse the existing `PlantFilterChips` + `DateField` (`presentation/utilities/UtilityComponents.kt`) instead of building a parallel `BuildingFilterChips`/`DateRangeRow`. No machine/floor entity dropdown is needed after all — the existing `ReportsApi` has no `machine_id`/`floor` filter param, so the Head Machine/Floor Readings screens filter by plant + date range only (same shape as the existing Floor Readings screen), not by individual machine/floor. `GetMachineMasterUseCase` is **not** used by this plan anymore.
- **Task 7 (Machine Reading History screen) is revised**: build the missing `MachinesReadingScreen`/`MachinesReadingViewModel` in `presentation/reports/` (mirroring `FloorReadingsReportScreen`/`ViewModel` exactly), consuming `ReportsRepository.machinesReading()` + `MachineReadingRow`. Filters: `PlantFilterChips` + `DateField` range only.
- **Task 8 (Floor Reading History screen) is dropped entirely** — reuse the existing `FloorReadingsReportScreen`/`FloorReadingsReportViewModel` verbatim; Head's hub just adds a new navigation route pointing at it.
- **Task 5, 9, 10 (Breakdown History + QC Activity History: repository extension + both screens) are unchanged** — no overlap with the concurrent work.
- **Task 11 (hub + nav)** now routes: Machine Readings → new shared `MachinesReadingScreen`; Floor Readings → existing `FloorReadingsReportScreen`; Breakdown History / QC Activity History → this plan's own new screens under `presentation/head/reports/`.
- **Task 12 (building-code mismatch)** is refocused: `PlantFilterChips` uses the **hyphenated** form (`"A-185"`/`"W-202"`/`null`-for-all), while `BreakdownApi`/`AssetScheduleApi` use the **short** form (`"W202"`/`"A185"`/`"both"`). Since Breakdown/QC History screens (Tasks 9–10) now also use `PlantFilterChips` for UI consistency across the whole Reports area, their ViewModels must convert hyphenated → short form before calling `BreakdownRepository.breakdownHistory()`/`qcActivityHistory()`. This conversion (not the original entity-filter-hyphen concern) is now Task 12's actual scope.
- **Task 13 (backend handoff prompt)** drops Endpoints 1 and 2 (Machine/Floor reading history) — those already exist via the concurrent session's own backend work. Only Endpoints 3 and 4 (Breakdown History, QC Activity History) are handed off.

The original Task 1–13 text below is left as originally written for historical reference; follow the REVISION notes above wherever they override a task's original file list/approach. Tasks 5, 9, 10, 12 (as refocused), and 13 (as trimmed) are the ones still executed close to their original text; Tasks 3, 4, 6, 8 are skipped; Task 7 is rebuilt against the shared `ReportsRepository` instead of a new one.

---

## Context for the implementing engineer

- Repo root (git) is `d:\Maintenance module\FactoryOps\`; the Gradle/Android module is the `app\` subdirectory. All paths below are relative to `app\` unless stated otherwise.
- The backend lives in a **separate** repo at `D:\Maintenance module\backend` — do not edit it. Task 13 produces a written handoff prompt instead.
- `Result<T>` (`domain/model/Result.kt`) is `sealed class Result<out T> { Success<T>(data); Error(message, exception?); Loading }` — every repository call returns this.
- Every Retrofit API interface takes `@Header("Authorization") token: String` as the first param on every method; repositories build it as `"Bearer ${authPreferences.getToken().orEmpty()}"` (see `FloorReadingRepositoryImpl`) or short-circuit with `Result.Error("Not logged in")` if there's no token (see `AssetScheduleRepositoryImpl`) — this plan uses the latter style throughout for consistency with the two closest existing "online-only" repositories.
- Repository interfaces are bound to their Impl via `@Binds` in `di/RepositoryModule.kt` — **adding methods to an existing interface + its existing Impl needs no DI wiring changes.** A brand-new repository interface needs one new `@Binds` line.
- Tests use JUnit5 (`org.junit.jupiter.api.*`) + MockK (`io.mockk.*`) + `kotlinx.coroutines.test.UnconfinedTestDispatcher`/`runTest`. Run with: `./gradlew test --tests "com.candorfoods.factoryops.*HistoryViewModelTest"` (adjust per task) from the repo root, or the relevant single test class.
- No APK builds — do not run `./gradlew assembleDebug`/`installDebug`. `./gradlew compileDebugKotlin` and `./gradlew test` are fine and are how each task is verified.
- Building codes used throughout the app/backend are the short forms `"W202"` / `"A185"` / `"both"` (see `AssetScheduleApi.list(plantId: String = "both")`, `BreakdownApi.listOpen(plantId: String = "both")`) — this plan's new `building` filter param follows the same convention.

---

## Task 1: Domain models for Machine & Floor reading history

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/domain/model/MachineKwhHistoryEntry.kt`
- Create: `src/main/java/com/candorfoods/factoryops/domain/model/FloorReadingHistoryEntry.kt`

Breakdown History and QC Activity History reuse the existing `BreakdownFlag` model (`domain/model/BreakdownFlag.kt`) — no new model needed for those two sections.

- [ ] **Step 1: Create `MachineKwhHistoryEntry`**

```kotlin
package com.candorfoods.factoryops.domain.model

/** One row of `mt_machine_daily_kwh` — a machine's recorded consumption for one day. */
data class MachineKwhHistoryEntry(
    val machineId: String,
    val machineName: String,
    val building: String,
    val floor: String,
    val readingDate: String,   // ISO yyyy-MM-dd
    val dailyKwh: Double,
    val source: String,        // RUN | SCHEDULE | MANUAL
    val startedAt: Long?,
    val endedAt: Long?,
    val status: String?,
    val operatorName: String?,
)
```

- [ ] **Step 2: Create `FloorReadingHistoryEntry`**

```kotlin
package com.candorfoods.factoryops.domain.model

/** One row of `mt_floor_utility_readings` — a floor's meter + system reading for one day. */
data class FloorReadingHistoryEntry(
    val building: String,
    val floor: String,
    val readingDate: String,   // ISO yyyy-MM-dd
    val systemReadingKwh: Double,
    val meterReading: Double?,
)
```

- [ ] **Step 3: Compile check**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (these are plain data classes with no dependents yet).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/domain/model/MachineKwhHistoryEntry.kt app/src/main/java/com/candorfoods/factoryops/domain/model/FloorReadingHistoryEntry.kt
git commit -m "feat(head-reports): add domain models for machine/floor reading history"
```

---

## Task 2: DTOs + mappers for the three new history payloads

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/data/remote/dto/HeadReportsDtos.kt`
- Test: `src/test/java/com/candorfoods/factoryops/HeadReportsDtoMappingTest.kt`

The enum-parsing convention for `BreakdownHistoryDto.toDomain()` mirrors
`BreakdownRepositoryImpl.toDomain(e: BreakdownFlagEntity)` (`data/repository/BreakdownRepositoryImpl.kt:498-539`) exactly, so severity/status/qcStatus strings parse identically to the rest of the app.

- [ ] **Step 1: Write the failing mapper tests**

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.remote.dto.BreakdownHistoryDto
import com.candorfoods.factoryops.data.remote.dto.FloorReadingHistoryDto
import com.candorfoods.factoryops.data.remote.dto.MachineKwhHistoryDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.domain.model.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HeadReportsDtoMappingTest {

    @Test
    fun `MachineKwhHistoryDto maps every field to the domain entry`() {
        val dto = MachineKwhHistoryDto(
            machineId = "A185-0007", machineName = "Roaster 1", building = "A-185",
            floor = "Ground floor", readingDate = "2026-07-20", dailyKwh = 12.5,
            source = "RUN", startedAt = 1000L, endedAt = 2000L,
            status = "COMPLETED", operatorName = "Ravi",
        )
        val entry = dto.toDomain()
        assertEquals("A185-0007", entry.machineId)
        assertEquals("Roaster 1", entry.machineName)
        assertEquals("A-185", entry.building)
        assertEquals("Ground floor", entry.floor)
        assertEquals("2026-07-20", entry.readingDate)
        assertEquals(12.5, entry.dailyKwh)
        assertEquals("RUN", entry.source)
        assertEquals(1000L, entry.startedAt)
        assertEquals(2000L, entry.endedAt)
        assertEquals("COMPLETED", entry.status)
        assertEquals("Ravi", entry.operatorName)
    }

    @Test
    fun `FloorReadingHistoryDto maps every field to the domain entry`() {
        val dto = FloorReadingHistoryDto(
            building = "W-202", floor = "First floor", readingDate = "2026-07-19",
            systemReading = 40.2, meterReading = 41.0,
        )
        val entry = dto.toDomain()
        assertEquals("W-202", entry.building)
        assertEquals("First floor", entry.floor)
        assertEquals("2026-07-19", entry.readingDate)
        assertEquals(40.2, entry.systemReadingKwh)
        assertEquals(41.0, entry.meterReading)
    }

    @Test
    fun `BreakdownHistoryDto maps status, severity and qcStatus like the rest of the app`() {
        val dto = BreakdownHistoryDto(
            id = "bd-1", assetId = "A185-0007", assetName = "Roaster 1", building = "A-185",
            reportedBy = "op-1", reporterName = "Ravi", severity = "CRITICAL",
            description = "Belt snapped", status = "CLOSED", reportedAt = 1000L,
            acknowledgedBy = "tech-1", acknowledgedByName = "Suresh", acknowledgedAt = 1100L,
            resolvedBy = "tech-1", resolvedByName = "Suresh", resolvedAt = 1200L,
            partsUsed = "V-belt", qcStatus = "APPROVED", qcDecidedBy = "qc-1",
            qcDecidedByName = "Sam", qcDecidedAt = 1300L, qcDecisionNotes = "Looks good",
        )
        val flag = dto.toDomain()
        assertEquals(Severity.CRITICAL, flag.severity)
        assertEquals(BreakdownStatus.CLOSED, flag.status)
        assertEquals(QcStatus.APPROVED, flag.qcStatus)
        assertEquals("Sam", flag.qcDecidedByName)
        assertNull(flag.beforePhotoLocalPath) // not present in the history payload
    }

    @Test
    fun `BreakdownHistoryDto defaults unknown severity to MINOR and unknown status to OPEN`() {
        val dto = BreakdownHistoryDto(
            id = "bd-2", assetId = "A185-0008", status = "SOME_FUTURE_STATUS", severity = null,
        )
        val flag = dto.toDomain()
        assertEquals(Severity.MINOR, flag.severity)
        assertEquals(BreakdownStatus.OPEN, flag.status)
        assertNull(flag.qcStatus)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.candorfoods.factoryops.HeadReportsDtoMappingTest"`
Expected: FAIL to compile — `HeadReportsDtos.kt` doesn't exist yet.

- [ ] **Step 3: Create the DTOs + mappers**

```kotlin
package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.FloorReadingHistoryEntry
import com.candorfoods.factoryops.domain.model.MachineKwhHistoryEntry
import com.candorfoods.factoryops.domain.model.QcStatus
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row of GET /machines/daily-kwh/history (mt_machine_daily_kwh). */
@Serializable
data class MachineKwhHistoryDto(
    @SerialName("machine_id") val machineId: String,
    @SerialName("machine_name") val machineName: String = "",
    val building: String = "",
    val floor: String = "",
    @SerialName("reading_date") val readingDate: String,
    @SerialName("daily_kwh") val dailyKwh: Double = 0.0,
    val source: String = "",
    @SerialName("started_at") val startedAt: Long? = null,
    @SerialName("ended_at") val endedAt: Long? = null,
    val status: String? = null,
    @SerialName("operator_name") val operatorName: String? = null,
)

fun MachineKwhHistoryDto.toDomain() = MachineKwhHistoryEntry(
    machineId = machineId,
    machineName = machineName,
    building = building,
    floor = floor,
    readingDate = readingDate,
    dailyKwh = dailyKwh,
    source = source,
    startedAt = startedAt,
    endedAt = endedAt,
    status = status,
    operatorName = operatorName,
)

/** One row of GET /floor-readings/history (mt_floor_utility_readings). */
@Serializable
data class FloorReadingHistoryDto(
    val building: String,
    val floor: String,
    @SerialName("reading_date") val readingDate: String,
    @SerialName("system_reading") val systemReading: Double = 0.0,
    @SerialName("meter_reading") val meterReading: Double? = null,
)

fun FloorReadingHistoryDto.toDomain() = FloorReadingHistoryEntry(
    building = building,
    floor = floor,
    readingDate = readingDate,
    systemReadingKwh = systemReading,
    meterReading = meterReading,
)

/**
 * One row of GET /breakdowns/history or GET /qc/history (mt_breakdown_records), richer than
 * [OpenBreakdownDto] — it carries the resolved/QC-decided fields open breakdowns don't have yet.
 */
@Serializable
data class BreakdownHistoryDto(
    val id: String,
    @SerialName("asset_id") val assetId: String,
    @SerialName("asset_name") val assetName: String = "",
    val building: String? = null,
    @SerialName("reported_by") val reportedBy: String? = null,
    @SerialName("reporter_name") val reporterName: String? = null,
    val severity: String? = null,
    val description: String = "",
    val status: String,
    @SerialName("reported_at") val reportedAt: Long? = null,
    @SerialName("acknowledged_by") val acknowledgedBy: String? = null,
    @SerialName("acknowledged_by_name") val acknowledgedByName: String? = null,
    @SerialName("acknowledged_at") val acknowledgedAt: Long? = null,
    @SerialName("resolved_by") val resolvedBy: String? = null,
    @SerialName("resolved_by_name") val resolvedByName: String? = null,
    @SerialName("resolved_at") val resolvedAt: Long? = null,
    @SerialName("parts_used") val partsUsed: String? = null,
    @SerialName("qc_status") val qcStatus: String? = null,
    @SerialName("qc_decided_by") val qcDecidedBy: String? = null,
    @SerialName("qc_decided_by_name") val qcDecidedByName: String? = null,
    @SerialName("qc_decided_at") val qcDecidedAt: Long? = null,
    @SerialName("qc_decision_notes") val qcDecisionNotes: String? = null,
)

fun BreakdownHistoryDto.toDomain() = BreakdownFlag(
    id = id,
    machineId = assetId,
    assetName = assetName,
    building = building,
    operatorId = reportedBy.orEmpty(),
    severity = when (severity) {
        "CRITICAL" -> Severity.CRITICAL
        "MAJOR"    -> Severity.MAJOR
        else       -> Severity.MINOR
    },
    description = description,
    beforePhotoLocalPath = null,
    raisedAt = reportedAt ?: 0L,
    status = when (status) {
        "ACKNOWLEDGED" -> BreakdownStatus.ACKNOWLEDGED
        "PENDING_QC"   -> BreakdownStatus.PENDING_QC
        "CLOSED"       -> BreakdownStatus.CLOSED
        "REOPENED"     -> BreakdownStatus.REOPENED
        else           -> BreakdownStatus.OPEN
    },
    acknowledgedBy = acknowledgedBy,
    acknowledgedByName = acknowledgedByName,
    acknowledgedAt = acknowledgedAt,
    resolvedBy = resolvedBy,
    resolvedByName = resolvedByName,
    resolvedAt = resolvedAt,
    partsUsed = partsUsed,
    afterPhotoLocalPath = null,
    qcStatus = when (qcStatus) {
        "PENDING"                  -> QcStatus.PENDING
        "APPROVED"                 -> QcStatus.APPROVED
        "REJECTED", "DISAPPROVED"  -> QcStatus.REJECTED
        else                       -> null
    },
    qcAcknowledgedBy = null,
    qcAcknowledgedByName = null,
    qcAcknowledgedAt = null,
    qcDecidedBy = qcDecidedBy,
    qcDecidedByName = qcDecidedByName,
    qcDecidedAt = qcDecidedAt,
    qcDecisionNotes = qcDecisionNotes,
    qcAfterPhotoLocalPath = null,
    qcChecklistJson = null,
    qcOverrideFromUserId = null,
    qcOverrideFromName = null,
    qcOverrideAt = null,
    syncStatus = SyncStatus.SYNCED,
)
```

Check `domain/model/Enums.kt` or wherever `SyncStatus` is declared for its exact case name (`SYNCED`) before using it — grep `enum class SyncStatus` if the build fails on this line, and use the matching case name.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.candorfoods.factoryops.HeadReportsDtoMappingTest"`
Expected: PASS (4 tests). If `SyncStatus.SYNCED` doesn't exist, fix the case name and rerun.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/data/remote/dto/HeadReportsDtos.kt app/src/test/java/com/candorfoods/factoryops/HeadReportsDtoMappingTest.kt
git commit -m "feat(head-reports): add history DTOs and domain mappers"
```

---

## Task 3: Machine Reading (kWh) history — API + repository

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/data/remote/api/MachineKwhHistoryApi.kt`
- Create: `src/main/java/com/candorfoods/factoryops/domain/repository/MachineKwhHistoryRepository.kt`
- Create: `src/main/java/com/candorfoods/factoryops/data/repository/MachineKwhHistoryRepositoryImpl.kt`
- Test: `src/test/java/com/candorfoods/factoryops/MachineKwhHistoryRepositoryImplTest.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/di/NetworkModule.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/di/RepositoryModule.kt`

This is a new repository (not an extension of `AssetScheduleRepository`, which only manages recording *windows*, not recorded values) — real-backend-only, same as `AssetScheduleApi`/`PmSyncApi`/`UtilitiesApi` (`di/NetworkModule.kt:143-171`).

- [ ] **Step 1: Create the Retrofit API**

```kotlin
package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.MachineKwhHistoryDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/** Head Reports — Machine Readings history (mt_machine_daily_kwh), row-level and date-ranged. */
interface MachineKwhHistoryApi {
    @GET("machines/daily-kwh/history")
    suspend fun history(
        @Header("Authorization") token: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("building") building: String = "both",
        @Query("machine_id") machineId: String? = null,
    ): List<MachineKwhHistoryDto>
}
```

- [ ] **Step 2: Create the repository interface**

```kotlin
package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.MachineKwhHistoryEntry
import com.candorfoods.factoryops.domain.model.Result

interface MachineKwhHistoryRepository {
    /** [from]/[to] are ISO yyyy-MM-dd, inclusive. building: "W202" / "A185" / "both". */
    suspend fun history(
        from: String,
        to: String,
        building: String = "both",
        machineId: String? = null,
    ): Result<List<MachineKwhHistoryEntry>>
}
```

- [ ] **Step 3: Write the failing repository test**

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.MachineKwhHistoryApi
import com.candorfoods.factoryops.data.remote.dto.MachineKwhHistoryDto
import com.candorfoods.factoryops.data.repository.MachineKwhHistoryRepositoryImpl
import com.candorfoods.factoryops.domain.model.Result
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MachineKwhHistoryRepositoryImplTest {

    private val api: MachineKwhHistoryApi = mockk()
    private val authPreferences: AuthPreferences = mockk()
    private val repo = MachineKwhHistoryRepositoryImpl(api, authPreferences)

    @Test
    fun `history maps every row and passes filters through`() = runTest {
        every { authPreferences.getToken() } returns "tok"
        coEvery {
            api.history("Bearer tok", "2026-07-01", "2026-07-20", "A185", "A185-0007")
        } returns listOf(
            MachineKwhHistoryDto(machineId = "A185-0007", readingDate = "2026-07-20", dailyKwh = 5.0)
        )
        val result = repo.history("2026-07-01", "2026-07-20", "A185", "A185-0007")
        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        assertEquals("A185-0007", result.data[0].machineId)
    }

    @Test
    fun `history returns an error when there is no token`() = runTest {
        every { authPreferences.getToken() } returns null
        val result = repo.history("2026-07-01", "2026-07-20")
        assertTrue(result is Result.Error)
    }

    @Test
    fun `history surfaces an exception message as Result Error`() = runTest {
        every { authPreferences.getToken() } returns "tok"
        coEvery { api.history(any(), any(), any(), any(), any()) } throws RuntimeException("timeout")
        val result = repo.history("2026-07-01", "2026-07-20")
        assertTrue(result is Result.Error)
        assertEquals("timeout", (result as Result.Error).message)
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew test --tests "com.candorfoods.factoryops.MachineKwhHistoryRepositoryImplTest"`
Expected: FAIL to compile — `MachineKwhHistoryRepositoryImpl` doesn't exist yet.

- [ ] **Step 5: Implement the repository**

```kotlin
package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.MachineKwhHistoryApi
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.MachineKwhHistoryEntry
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MachineKwhHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Online-only, read-only report — no Room cache, mirrors AssetScheduleRepositoryImpl. */
@Singleton
class MachineKwhHistoryRepositoryImpl @Inject constructor(
    private val api: MachineKwhHistoryApi,
    private val authPreferences: AuthPreferences,
) : MachineKwhHistoryRepository {

    override suspend fun history(
        from: String,
        to: String,
        building: String,
        machineId: String?,
    ): Result<List<MachineKwhHistoryEntry>> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            Result.Success(
                api.history("Bearer $token", from, to, building, machineId).map { it.toDomain() }
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load machine reading history", e)
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests "com.candorfoods.factoryops.MachineKwhHistoryRepositoryImplTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Wire DI** — add the API provider to `NetworkModule.kt` (real-backend-only block, alongside `providePmSyncApi` etc., `di/NetworkModule.kt:149-153`):

```kotlin
    // Real backend only — Head Reports machine-reading history (mt_machine_daily_kwh).
    @Provides
    @Singleton
    fun provideMachineKwhHistoryApi(retrofit: Retrofit): MachineKwhHistoryApi =
        retrofit.create(MachineKwhHistoryApi::class.java)
```

Add the import `import com.candorfoods.factoryops.data.remote.api.MachineKwhHistoryApi` near the other `data.remote.api` imports.

Add the `@Binds` in `RepositoryModule.kt`, next to `bindAssetScheduleRepository` (around line 61):

```kotlin
    @Binds
    abstract fun bindMachineKwhHistoryRepository(
        impl: MachineKwhHistoryRepositoryImpl
    ): MachineKwhHistoryRepository
```

Add its two imports (`data.repository.MachineKwhHistoryRepositoryImpl`, `domain.repository.MachineKwhHistoryRepository`) alongside the existing `AssetScheduleRepository` imports.

- [ ] **Step 8: Compile check**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/data/remote/api/MachineKwhHistoryApi.kt app/src/main/java/com/candorfoods/factoryops/domain/repository/MachineKwhHistoryRepository.kt app/src/main/java/com/candorfoods/factoryops/data/repository/MachineKwhHistoryRepositoryImpl.kt app/src/test/java/com/candorfoods/factoryops/MachineKwhHistoryRepositoryImplTest.kt app/src/main/java/com/candorfoods/factoryops/di/NetworkModule.kt app/src/main/java/com/candorfoods/factoryops/di/RepositoryModule.kt
git commit -m "feat(head-reports): add machine kWh reading history repository"
```

---

## Task 4: Floor reading history — extend the existing API + repository

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/data/remote/api/FloorReadingApi.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/domain/repository/FloorReadingRepository.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/data/repository/FloorReadingRepositoryImpl.kt`
- Test: `src/test/java/com/candorfoods/factoryops/FloorReadingHistoryTest.kt`

- [ ] **Step 1: Write the failing repository test**

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.FloorReadingApi
import com.candorfoods.factoryops.data.remote.dto.FloorReadingHistoryDto
import com.candorfoods.factoryops.data.repository.FloorReadingRepositoryImpl
import com.candorfoods.factoryops.domain.model.Result
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloorReadingHistoryTest {

    private val api: FloorReadingApi = mockk()
    private val authPreferences: AuthPreferences = mockk(relaxed = true)
    private val repo = FloorReadingRepositoryImpl(api, authPreferences)

    @Test
    fun `history maps rows and passes the floor filter through`() = runTest {
        coEvery {
            api.history(any(), "2026-07-01", "2026-07-20", "A185", "Ground floor")
        } returns listOf(
            FloorReadingHistoryDto(building = "A-185", floor = "Ground floor", readingDate = "2026-07-20", systemReading = 3.0)
        )
        val result = repo.history("2026-07-01", "2026-07-20", "A185", "Ground floor")
        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        assertEquals("Ground floor", result.data[0].floor)
    }

    @Test
    fun `history surfaces a network failure as Result Error`() = runTest {
        coEvery { api.history(any(), any(), any(), any(), any()) } throws RuntimeException("offline")
        val result = repo.history("2026-07-01", "2026-07-20")
        assertTrue(result is Result.Error)
        assertEquals("offline", (result as Result.Error).message)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.candorfoods.factoryops.FloorReadingHistoryTest"`
Expected: FAIL to compile — `FloorReadingApi.history`/`FloorReadingRepositoryImpl.history` don't exist yet.

- [ ] **Step 3: Add the API method** — append inside `interface FloorReadingApi` (`data/remote/api/FloorReadingApi.kt`):

```kotlin
    /** Head Reports — Floor Readings history (mt_floor_utility_readings), date-ranged. */
    @GET("floor-readings/history")
    suspend fun history(
        @Header("Authorization") token: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("building") building: String = "both",
        @Query("floor") floor: String? = null,
    ): List<FloorReadingHistoryDto>
```

Add the import `import com.candorfoods.factoryops.data.remote.dto.FloorReadingHistoryDto`.

- [ ] **Step 4: Add the repository interface method** — append inside `interface FloorReadingRepository`:

```kotlin
    /** GET /floor-readings/history — row-level, date-ranged. building: "W202"/"A185"/"both". */
    suspend fun history(
        from: String,
        to: String,
        building: String = "both",
        floor: String? = null,
    ): Result<List<FloorReadingHistoryEntry>>
```

Add the import `import com.candorfoods.factoryops.domain.model.FloorReadingHistoryEntry`.

- [ ] **Step 5: Implement it** — append inside `class FloorReadingRepositoryImpl`:

```kotlin
    override suspend fun history(
        from: String,
        to: String,
        building: String,
        floor: String?,
    ): Result<List<FloorReadingHistoryEntry>> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            Result.Success(
                floorReadingApi.history(token, from, to, building, floor).map { it.toDomain() }
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load floor reading history", e)
        }
    }
```

Add the imports `com.candorfoods.factoryops.data.remote.dto.FloorReadingHistoryDto` (transitively via `toDomain`, already resolved by the existing `import com.candorfoods.factoryops.data.remote.dto.toDomain` wildcard-style function import already present in this file) and `com.candorfoods.factoryops.domain.model.FloorReadingHistoryEntry`.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests "com.candorfoods.factoryops.FloorReadingHistoryTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/data/remote/api/FloorReadingApi.kt app/src/main/java/com/candorfoods/factoryops/domain/repository/FloorReadingRepository.kt app/src/main/java/com/candorfoods/factoryops/data/repository/FloorReadingRepositoryImpl.kt app/src/test/java/com/candorfoods/factoryops/FloorReadingHistoryTest.kt
git commit -m "feat(head-reports): add floor reading history to FloorReadingRepository"
```

---

## Task 5: Breakdown History + QC Activity History — extend `BreakdownRepository`

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/data/remote/api/BreakdownApi.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/domain/repository/BreakdownRepository.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/data/repository/BreakdownRepositoryImpl.kt`
- Test: `src/test/java/com/candorfoods/factoryops/BreakdownHistoryTest.kt`

Both new methods are online-only (no Room), added alongside the existing Flow/Room-backed methods on the same interface — consistent with how `refreshOpenFlags` already mixes a suspend network call into this otherwise-Room-backed repository.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.BreakdownApi
import com.candorfoods.factoryops.data.remote.api.ChecklistApi
import com.candorfoods.factoryops.data.remote.dto.BreakdownHistoryDto
import com.candorfoods.factoryops.data.repository.BreakdownRepositoryImpl
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.Result
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BreakdownHistoryTest {

    private val api: BreakdownApi = mockk()
    private val checklistApi: ChecklistApi = mockk(relaxed = true)
    private val mtMachineRepository: MtMachineRepository = mockk(relaxed = true)
    private val db: AppDatabase = mockk(relaxed = true)
    private val authPreferences: AuthPreferences = mockk(relaxed = true)
    private val repo = BreakdownRepositoryImpl(api, checklistApi, mtMachineRepository, db, authPreferences)

    @Test
    fun `breakdownHistory maps rows regardless of status`() = runTest {
        coEvery {
            api.breakdownHistory(any(), "2026-07-01", "2026-07-20", "both", null)
        } returns listOf(
            BreakdownHistoryDto(id = "bd-1", assetId = "A185-0007", status = "CLOSED"),
            BreakdownHistoryDto(id = "bd-2", assetId = "A185-0008", status = "OPEN"),
        )
        val result = repo.breakdownHistory("2026-07-01", "2026-07-20")
        assertTrue(result is Result.Success)
        val flags = (result as Result.Success).data
        assertEquals(2, flags.size)
        assertEquals(BreakdownStatus.CLOSED, flags[0].status)
    }

    @Test
    fun `qcActivityHistory maps rows and surfaces errors`() = runTest {
        coEvery { api.qcActivityHistory(any(), any(), any(), any()) } throws RuntimeException("timeout")
        val result = repo.qcActivityHistory("2026-07-01", "2026-07-20")
        assertTrue(result is Result.Error)
        assertEquals("timeout", (result as Result.Error).message)
    }
}
```

`BreakdownRepositoryImpl`'s real constructor (`data/repository/BreakdownRepositoryImpl.kt:32-38`) is `(breakdownApi: BreakdownApi, checklistApi: ChecklistApi, mtMachineRepository: MtMachineRepository, db: AppDatabase, authPreferences: AuthPreferences)` — the test above already matches it.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.candorfoods.factoryops.BreakdownHistoryTest"`
Expected: FAIL to compile — `breakdownHistory`/`qcActivityHistory` don't exist yet.

- [ ] **Step 3: Add the API methods** — append inside `interface BreakdownApi`:

```kotlin
    /** Head Reports — every breakdown (open + closed) in range. status: OPEN/CLOSED/null=all. */
    @GET("breakdowns/history")
    suspend fun breakdownHistory(
        @Header("Authorization") token: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("plant_id") plantId: String = "both",
        @Query("status") status: String? = null,
    ): List<BreakdownHistoryDto>

    /** Head Reports — every breakdown with a QC decision in range (latest decision only). */
    @GET("qc/history")
    suspend fun qcActivityHistory(
        @Header("Authorization") token: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("plant_id") plantId: String = "both",
    ): List<BreakdownHistoryDto>
```

Add the import `import com.candorfoods.factoryops.data.remote.dto.BreakdownHistoryDto`.

- [ ] **Step 4: Add the repository interface methods** — append inside `interface BreakdownRepository`:

```kotlin
    /** GET /breakdowns/history — every breakdown in range regardless of status (Head Reports). */
    suspend fun breakdownHistory(
        from: String,
        to: String,
        plantId: String = "both",
        status: String? = null,
    ): Result<List<BreakdownFlag>>

    /** GET /qc/history — every breakdown with a QC decision in range (Head Reports). */
    suspend fun qcActivityHistory(
        from: String,
        to: String,
        plantId: String = "both",
    ): Result<List<BreakdownFlag>>
```

- [ ] **Step 5: Implement both** — append inside `class BreakdownRepositoryImpl`:

```kotlin
    override suspend fun breakdownHistory(
        from: String,
        to: String,
        plantId: String,
        status: String?,
    ): Result<List<BreakdownFlag>> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            Result.Success(breakdownApi.breakdownHistory(token, from, to, plantId, status).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load breakdown history", e)
        }
    }

    override suspend fun qcActivityHistory(
        from: String,
        to: String,
        plantId: String,
    ): Result<List<BreakdownFlag>> {
        return try {
            val token = "Bearer ${authPreferences.getToken().orEmpty()}"
            Result.Success(breakdownApi.qcActivityHistory(token, from, to, plantId).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load QC activity history", e)
        }
    }
```

`breakdownApi` is the real constructor field name (confirmed in Step 1's note above). Add the import `com.candorfoods.factoryops.data.remote.dto.toDomain` if not already present in this file — it likely already is, for `OpenBreakdownDto`'s own mapping elsewhere in this class; check before adding a duplicate import.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests "com.candorfoods.factoryops.BreakdownHistoryTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/data/remote/api/BreakdownApi.kt app/src/main/java/com/candorfoods/factoryops/domain/repository/BreakdownRepository.kt app/src/main/java/com/candorfoods/factoryops/data/repository/BreakdownRepositoryImpl.kt app/src/test/java/com/candorfoods/factoryops/BreakdownHistoryTest.kt
git commit -m "feat(head-reports): add breakdown + QC activity history to BreakdownRepository"
```

---

## Task 6: Shared filter-bar UI components

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/HistoryFilters.kt`

Reuses the existing `DateField` composable (`presentation/utilities/UtilityComponents.kt:115-144`) rather than reimplementing a date picker.

- [ ] **Step 1: Create the shared filter composables**

```kotlin
package com.candorfoods.factoryops.presentation.head.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.candorfoods.factoryops.presentation.common.theme.Spacing
import com.candorfoods.factoryops.presentation.utilities.DateField

/** All / W202 / A185 building filter, shared by every Head Reports history screen. */
@Composable
fun BuildingFilterChips(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = modifier.fillMaxWidth()) {
        listOf("both" to "All", "W202" to "W-202", "A185" to "A-185").forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

/** From/To date range, each backed by the existing [DateField] date picker. */
@Composable
fun DateRangeRow(
    from: String,
    to: String,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = modifier.fillMaxWidth()) {
        DateField(value = from, onChange = onFromChange, modifier = Modifier.weight(1f))
        DateField(value = to, onChange = onToChange, modifier = Modifier.weight(1f))
    }
}

/** Generic label/id dropdown — used for the machine picker (Machine Readings) and the
 *  floor picker (Floor Readings). [options] is (id, label); null id/label = "All". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: "All"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenuDefaults.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelect(null); expanded = false })
            options.forEach { (id, optLabel) ->
                DropdownMenuItem(text = { Text(optLabel) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
```

`ExposedDropdownMenuBox`'s exact Material3 API (the `menuAnchor()` modifier requirement, `DropdownMenu` vs `ExposedDropdownMenuDefaults.DropdownMenu`) varies by Compose Material3 version — check the version already pinned in `gradle/libs.versions.toml` and, if `./gradlew compileDebugKotlin` in Step 2 reports a signature mismatch (e.g. `menuAnchor()` required on the `OutlinedTextField`), add `.menuAnchor()` to the `OutlinedTextField`'s `Modifier.fillMaxWidth()` chain — this is a routine version-specific API shape, not a design change.

- [ ] **Step 2: Compile check**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any Material3 API mismatch per the note above.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/HistoryFilters.kt
git commit -m "feat(head-reports): add shared building/date-range/entity filter components"
```

---

## Task 7: Machine Reading History screen

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/MachineReadingHistoryViewModel.kt`
- Create: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/MachineReadingHistoryScreen.kt`
- Test: `src/test/java/com/candorfoods/factoryops/MachineReadingHistoryViewModelTest.kt`

The machine-picker's options come from `GetMachineMasterUseCase` (already used the same way in `BreakdownFormViewModel.loadMachines()`, `presentation/checklist/BreakdownFormViewModel.kt:138-155`) rather than a new endpoint.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.MachineKwhHistoryEntry
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MachineKwhHistoryRepository
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import com.candorfoods.factoryops.presentation.head.reports.MachineReadingHistoryViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MachineReadingHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: MachineKwhHistoryRepository
    private lateinit var getMachineMaster: GetMachineMasterUseCase
    private lateinit var viewModel: MachineReadingHistoryViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk()
        getMachineMaster = mockk()
        coEvery { repo.history(any(), any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { getMachineMaster() } returns Result.Success(emptyList())
        viewModel = MachineReadingHistoryViewModel(repo, getMachineMaster)
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `load populates rows on success`() = runTest {
        val row = MachineKwhHistoryEntry(
            machineId = "A185-0007", machineName = "Roaster 1", building = "A-185", floor = "Ground floor",
            readingDate = "2026-07-20", dailyKwh = 5.0, source = "RUN", startedAt = null, endedAt = null,
            status = null, operatorName = null,
        )
        coEvery { repo.history(any(), any(), any(), any()) } returns Result.Success(listOf(row))
        viewModel.load()
        val state = viewModel.state.value
        assertEquals(listOf(row), state.rows)
        assertNull(state.error)
    }

    @Test
    fun `load surfaces an error message and clears loading`() = runTest {
        coEvery { repo.history(any(), any(), any(), any()) } returns Result.Error("offline")
        viewModel.load()
        val state = viewModel.state.value
        assertEquals("offline", state.error)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `setBuilding updates the filter and reloads`() = runTest {
        viewModel.setBuilding("A185")
        assertEquals("A185", viewModel.state.value.building)
    }

    @Test
    fun `setMachine updates the filter and reloads`() = runTest {
        viewModel.setMachine("A185-0007")
        assertEquals("A185-0007", viewModel.state.value.machineId)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.candorfoods.factoryops.MachineReadingHistoryViewModelTest"`
Expected: FAIL to compile — `MachineReadingHistoryViewModel` doesn't exist yet.

- [ ] **Step 3: Implement the ViewModel**

Default date range: last 30 days (`to` = today, `from` = today - 30 days), using `java.time.LocalDate` — matches the ISO `yyyy-MM-dd` format `DateField`/the backend both use.

`GetMachineMasterUseCase` is a plain `@Inject`-constructed use case (not a ViewModel), so it
is injected directly into this ViewModel's constructor — same pattern
`BreakdownFormViewModel` already uses at `presentation/checklist/BreakdownFormViewModel.kt:79`
— rather than being obtained separately in the Composable. Machine options live in
`state.machineOptions` so the Screen (Step 5) only needs the ViewModel.

```kotlin
package com.candorfoods.factoryops.presentation.head.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.MachineKwhHistoryEntry
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MachineKwhHistoryRepository
import com.candorfoods.factoryops.domain.usecase.mtmachine.GetMachineMasterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MachineReadingHistoryState(
    val rows: List<MachineKwhHistoryEntry> = emptyList(),
    val machineOptions: List<Pair<String, String>> = emptyList(), // (assetId, assetName)
    val building: String = "both",
    val machineId: String? = null,
    val from: String = LocalDate.now().minusDays(30).toString(),
    val to: String = LocalDate.now().toString(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MachineReadingHistoryViewModel @Inject constructor(
    private val repo: MachineKwhHistoryRepository,
    private val getMachineMaster: GetMachineMasterUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MachineReadingHistoryState())
    val state: StateFlow<MachineReadingHistoryState> = _state.asStateFlow()

    init {
        load()
        loadMachineOptions()
    }

    fun setBuilding(building: String) {
        _state.update { it.copy(building = building, machineId = null) }
        load()
        loadMachineOptions()
    }
    fun setMachine(machineId: String?) { _state.update { it.copy(machineId = machineId) }; load() }
    fun setFrom(from: String) { _state.update { it.copy(from = from) }; load() }
    fun setTo(to: String) { _state.update { it.copy(to = to) }; load() }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val s = _state.value
            when (val r = repo.history(s.from, s.to, s.building, s.machineId)) {
                is Result.Success -> _state.update { it.copy(isLoading = false, rows = r.data) }
                is Result.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading    -> Unit
            }
        }
    }

    /** Machine picker options for the current building — filters `MtMachine.building`
     *  ("W-202"/"A-185", hyphenated) against `state.building` ("W202"/"A185", not). */
    private fun loadMachineOptions() {
        viewModelScope.launch {
            val building = _state.value.building
            when (val r = getMachineMaster()) {
                is Result.Success -> _state.update { st ->
                    st.copy(machineOptions = r.data
                        .filter { building == "both" || it.building.replace("-", "") == building }
                        .map { it.assetId to it.assetName })
                }
                else -> Unit
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.candorfoods.factoryops.MachineReadingHistoryViewModelTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Build the screen**

```kotlin
package com.candorfoods.factoryops.presentation.head.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.MachineKwhHistoryEntry
import com.candorfoods.factoryops.presentation.common.components.EmptyState
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineReadingHistoryScreen(
    onBack: () -> Unit,
    viewModel: MachineReadingHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Machine Readings — History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { BuildingFilterChips(state.building, viewModel::setBuilding) }
            item { DateRangeRow(state.from, state.to, viewModel::setFrom, viewModel::setTo) }
            item {
                EntityDropdown(
                    label = "Machine", options = state.machineOptions,
                    selectedId = state.machineId, onSelect = viewModel::setMachine,
                )
            }
            when {
                state.isLoading -> item { CircularProgressIndicator() }
                state.error != null -> item { Text("Couldn't load: ${state.error}", color = MaterialTheme.colorScheme.error) }
                state.rows.isEmpty() -> item { EmptyState(title = "No readings", subtitle = "No machine readings in this range yet.") }
                else -> items(state.rows) { row -> MachineReadingRow(row) }
            }
        }
    }
}

@Composable
private fun MachineReadingRow(row: MachineKwhHistoryEntry) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("${row.machineName} (${row.machineId})", fontWeight = FontWeight.Bold)
            Text("${row.readingDate} · ${row.floor} · ${row.building}")
            Text("${row.dailyKwh} kWh · ${row.source}${row.operatorName?.let { " · $it" } ?: ""}")
        }
    }
}
```

Check `EmptyState`'s actual constructor params (`presentation/common/components/EmptyState.kt`) before finalizing this step — if its parameter names differ from `title`/`subtitle`, use the real ones.

- [ ] **Step 6: Compile check**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/MachineReadingHistoryViewModel.kt app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/MachineReadingHistoryScreen.kt app/src/test/java/com/candorfoods/factoryops/MachineReadingHistoryViewModelTest.kt
git commit -m "feat(head-reports): add Machine Readings history screen"
```

---

## Task 8: Floor Reading History screen

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/FloorReadingHistoryViewModel.kt`
- Create: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/FloorReadingHistoryScreen.kt`
- Test: `src/test/java/com/candorfoods/factoryops/FloorReadingHistoryViewModelTest.kt`

Floor options are the distinct `floor` values already present in the loaded `state.rows` (no separate "list floors" call) — re-derived each time a new date range/building loads.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.FloorReadingHistoryEntry
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.FloorReadingRepository
import com.candorfoods.factoryops.presentation.head.reports.FloorReadingHistoryViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FloorReadingHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FloorReadingRepository
    private lateinit var viewModel: FloorReadingHistoryViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk()
        coEvery { repo.history(any(), any(), any(), any()) } returns Result.Success(emptyList())
        viewModel = FloorReadingHistoryViewModel(repo)
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `load derives distinct floor options from the loaded rows`() = runTest {
        coEvery { repo.history(any(), any(), any(), any()) } returns Result.Success(listOf(
            FloorReadingHistoryEntry("A-185", "Ground floor", "2026-07-20", 3.0, 3.0),
            FloorReadingHistoryEntry("A-185", "Mezzanine floor", "2026-07-20", 1.0, 1.0),
            FloorReadingHistoryEntry("A-185", "Ground floor", "2026-07-19", 2.5, 2.5),
        ))
        viewModel.load()
        assertEquals(listOf("Ground floor", "Mezzanine floor"), viewModel.state.value.floorOptions)
    }

    @Test
    fun `setFloor updates the filter and reloads`() = runTest {
        viewModel.setFloor("Ground floor")
        assertEquals("Ground floor", viewModel.state.value.floor)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.candorfoods.factoryops.FloorReadingHistoryViewModelTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.candorfoods.factoryops.presentation.head.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.FloorReadingHistoryEntry
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.FloorReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class FloorReadingHistoryState(
    val rows: List<FloorReadingHistoryEntry> = emptyList(),
    val floorOptions: List<String> = emptyList(),
    val building: String = "both",
    val floor: String? = null,
    val from: String = LocalDate.now().minusDays(30).toString(),
    val to: String = LocalDate.now().toString(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FloorReadingHistoryViewModel @Inject constructor(
    private val repo: FloorReadingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FloorReadingHistoryState())
    val state: StateFlow<FloorReadingHistoryState> = _state.asStateFlow()

    init { load() }

    fun setBuilding(building: String) { _state.update { it.copy(building = building, floor = null) }; load() }
    fun setFloor(floor: String?) { _state.update { it.copy(floor = floor) }; load() }
    fun setFrom(from: String) { _state.update { it.copy(from = from) }; load() }
    fun setTo(to: String) { _state.update { it.copy(to = to) }; load() }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val s = _state.value
            when (val r = repo.history(s.from, s.to, s.building, s.floor)) {
                is Result.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        rows = r.data,
                        floorOptions = r.data.map { row -> row.floor }.distinct().sorted(),
                    )
                }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading  -> Unit
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.candorfoods.factoryops.FloorReadingHistoryViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Build the screen**

```kotlin
package com.candorfoods.factoryops.presentation.head.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.FloorReadingHistoryEntry
import com.candorfoods.factoryops.presentation.common.components.EmptyState
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorReadingHistoryScreen(
    onBack: () -> Unit,
    viewModel: FloorReadingHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Floor Readings — History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { BuildingFilterChips(state.building, viewModel::setBuilding) }
            item { DateRangeRow(state.from, state.to, viewModel::setFrom, viewModel::setTo) }
            item {
                EntityDropdown(
                    label = "Floor", options = state.floorOptions.map { it to it },
                    selectedId = state.floor, onSelect = viewModel::setFloor,
                )
            }
            when {
                state.isLoading -> item { CircularProgressIndicator() }
                state.error != null -> item { Text("Couldn't load: ${state.error}", color = MaterialTheme.colorScheme.error) }
                state.rows.isEmpty() -> item { EmptyState(title = "No readings", subtitle = "No floor readings in this range yet.") }
                else -> items(state.rows) { row -> FloorReadingRow(row) }
            }
        }
    }
}

@Composable
private fun FloorReadingRow(row: FloorReadingHistoryEntry) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("${row.floor} — ${row.building}", fontWeight = FontWeight.Bold)
            Text(row.readingDate)
            Text("System: ${row.systemReadingKwh} kWh · Meter: ${row.meterReading ?: "—"}")
        }
    }
}
```

- [ ] **Step 6: Compile check**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/FloorReadingHistoryViewModel.kt app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/FloorReadingHistoryScreen.kt app/src/test/java/com/candorfoods/factoryops/FloorReadingHistoryViewModelTest.kt
git commit -m "feat(head-reports): add Floor Readings history screen"
```

---

## Task 9: Breakdown History screen

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/BreakdownHistoryViewModel.kt`
- Create: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/BreakdownHistoryScreen.kt`
- Test: `src/test/java/com/candorfoods/factoryops/BreakdownHistoryViewModelTest.kt`

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.domain.model.SyncStatus
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.presentation.head.reports.BreakdownHistoryViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BreakdownHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: BreakdownRepository
    private lateinit var viewModel: BreakdownHistoryViewModel

    private fun flag(status: BreakdownStatus) = BreakdownFlag(
        id = "bd-1", machineId = "A185-0007", assetName = "Roaster 1", building = "A-185",
        operatorId = "op-1", severity = Severity.MAJOR, description = "desc",
        beforePhotoLocalPath = null, raisedAt = 1000L, status = status,
        acknowledgedBy = null, acknowledgedByName = null, acknowledgedAt = null,
        resolvedBy = null, resolvedByName = null, resolvedAt = null, partsUsed = null,
        afterPhotoLocalPath = null, qcStatus = null, qcAcknowledgedBy = null,
        qcAcknowledgedByName = null, qcAcknowledgedAt = null, qcDecidedBy = null,
        qcDecidedByName = null, qcDecidedAt = null, qcDecisionNotes = null,
        qcAfterPhotoLocalPath = null, qcChecklistJson = null, qcOverrideFromUserId = null,
        qcOverrideFromName = null, qcOverrideAt = null, syncStatus = SyncStatus.SYNCED,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk()
        coEvery { repo.breakdownHistory(any(), any(), any(), any()) } returns Result.Success(emptyList())
        viewModel = BreakdownHistoryViewModel(repo)
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `load populates rows regardless of status`() = runTest {
        coEvery { repo.breakdownHistory(any(), any(), any(), any()) } returns
            Result.Success(listOf(flag(BreakdownStatus.CLOSED), flag(BreakdownStatus.OPEN)))
        viewModel.load()
        assertEquals(2, viewModel.state.value.rows.size)
    }

    @Test
    fun `setStatus filters to the chosen status server-side`() = runTest {
        viewModel.setStatus("CLOSED")
        assertEquals("CLOSED", viewModel.state.value.status)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.candorfoods.factoryops.BreakdownHistoryViewModelTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.candorfoods.factoryops.presentation.head.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BreakdownHistoryState(
    val rows: List<BreakdownFlag> = emptyList(),
    val building: String = "both",
    val status: String? = null, // null = all, else "OPEN"/"CLOSED"
    val from: String = LocalDate.now().minusDays(30).toString(),
    val to: String = LocalDate.now().toString(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BreakdownHistoryViewModel @Inject constructor(
    private val repo: BreakdownRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BreakdownHistoryState())
    val state: StateFlow<BreakdownHistoryState> = _state.asStateFlow()

    init { load() }

    fun setBuilding(building: String) { _state.update { it.copy(building = building) }; load() }
    fun setStatus(status: String?) { _state.update { it.copy(status = status) }; load() }
    fun setFrom(from: String) { _state.update { it.copy(from = from) }; load() }
    fun setTo(to: String) { _state.update { it.copy(to = to) }; load() }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val s = _state.value
            when (val r = repo.breakdownHistory(s.from, s.to, s.building, s.status)) {
                is Result.Success -> _state.update { it.copy(isLoading = false, rows = r.data) }
                is Result.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading    -> Unit
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.candorfoods.factoryops.BreakdownHistoryViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Build the screen**

```kotlin
package com.candorfoods.factoryops.presentation.head.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.presentation.common.components.EmptyState
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownHistoryScreen(
    onBack: () -> Unit,
    viewModel: BreakdownHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breakdown — History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { BuildingFilterChips(state.building, viewModel::setBuilding) }
            item { DateRangeRow(state.from, state.to, viewModel::setFrom, viewModel::setTo) }
            item {
                EntityDropdown(
                    label = "Status", options = listOf("OPEN" to "Open", "CLOSED" to "Closed"),
                    selectedId = state.status, onSelect = viewModel::setStatus,
                )
            }
            when {
                state.isLoading -> item { CircularProgressIndicator() }
                state.error != null -> item { Text("Couldn't load: ${state.error}", color = MaterialTheme.colorScheme.error) }
                state.rows.isEmpty() -> item { EmptyState(title = "No breakdowns", subtitle = "No breakdowns in this range yet.") }
                else -> items(state.rows) { row -> BreakdownHistoryRow(row) }
            }
        }
    }
}

@Composable
private fun BreakdownHistoryRow(row: BreakdownFlag) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("${row.assetName ?: row.machineId} — ${row.status}", fontWeight = FontWeight.Bold)
            Text(row.description)
            Text("Severity: ${row.severity}${row.resolvedByName?.let { " · Resolved by $it" } ?: ""}")
        }
    }
}
```

- [ ] **Step 6: Compile check**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/BreakdownHistoryViewModel.kt app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/BreakdownHistoryScreen.kt app/src/test/java/com/candorfoods/factoryops/BreakdownHistoryViewModelTest.kt
git commit -m "feat(head-reports): add Breakdown history screen"
```

---

## Task 10: QC Activity History screen

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/QcActivityHistoryViewModel.kt`
- Create: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/QcActivityHistoryScreen.kt`
- Test: `src/test/java/com/candorfoods/factoryops/QcActivityHistoryViewModelTest.kt`

Same shape as Task 9 but calls `repo.qcActivityHistory` (no status filter — every row already has a QC decision by construction) and renders the QC-specific fields.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.presentation.head.reports.QcActivityHistoryViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QcActivityHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: BreakdownRepository
    private lateinit var viewModel: QcActivityHistoryViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk()
        coEvery { repo.qcActivityHistory(any(), any(), any()) } returns Result.Success(emptyList())
        viewModel = QcActivityHistoryViewModel(repo)
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `load surfaces an error message`() = runTest {
        coEvery { repo.qcActivityHistory(any(), any(), any()) } returns Result.Error("offline")
        viewModel.load()
        assertEquals("offline", viewModel.state.value.error)
    }

    @Test
    fun `setBuilding updates the filter and reloads`() = runTest {
        viewModel.setBuilding("W202")
        assertEquals("W202", viewModel.state.value.building)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.candorfoods.factoryops.QcActivityHistoryViewModelTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.candorfoods.factoryops.presentation.head.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class QcActivityHistoryState(
    val rows: List<BreakdownFlag> = emptyList(),
    val building: String = "both",
    val from: String = LocalDate.now().minusDays(30).toString(),
    val to: String = LocalDate.now().toString(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class QcActivityHistoryViewModel @Inject constructor(
    private val repo: BreakdownRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QcActivityHistoryState())
    val state: StateFlow<QcActivityHistoryState> = _state.asStateFlow()

    init { load() }

    fun setBuilding(building: String) { _state.update { it.copy(building = building) }; load() }
    fun setFrom(from: String) { _state.update { it.copy(from = from) }; load() }
    fun setTo(to: String) { _state.update { it.copy(to = to) }; load() }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val s = _state.value
            when (val r = repo.qcActivityHistory(s.from, s.to, s.building)) {
                is Result.Success -> _state.update { it.copy(isLoading = false, rows = r.data) }
                is Result.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading    -> Unit
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.candorfoods.factoryops.QcActivityHistoryViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Build the screen**

```kotlin
package com.candorfoods.factoryops.presentation.head.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.presentation.common.components.EmptyState
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QcActivityHistoryScreen(
    onBack: () -> Unit,
    viewModel: QcActivityHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QC Activity — History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { BuildingFilterChips(state.building, viewModel::setBuilding) }
            item { DateRangeRow(state.from, state.to, viewModel::setFrom, viewModel::setTo) }
            when {
                state.isLoading -> item { CircularProgressIndicator() }
                state.error != null -> item { Text("Couldn't load: ${state.error}", color = MaterialTheme.colorScheme.error) }
                state.rows.isEmpty() -> item { EmptyState(title = "No QC activity", subtitle = "No QC decisions in this range yet.") }
                else -> items(state.rows) { row -> QcActivityRow(row) }
            }
        }
    }
}

@Composable
private fun QcActivityRow(row: BreakdownFlag) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("${row.assetName ?: row.machineId} — ${row.qcStatus}", fontWeight = FontWeight.Bold)
            Text("Decided by ${row.qcDecidedByName ?: "—"}")
            row.qcDecisionNotes?.takeIf { it.isNotBlank() }?.let { Text(it) }
        }
    }
}
```

- [ ] **Step 6: Compile check**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/QcActivityHistoryViewModel.kt app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/QcActivityHistoryScreen.kt app/src/test/java/com/candorfoods/factoryops/QcActivityHistoryViewModelTest.kt
git commit -m "feat(head-reports): add QC Activity history screen"
```

---

## Task 11: Reports hub screen + navigation wiring

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/HeadReportsScreen.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/navigation/Screen.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/navigation/NavGraph.kt`

- [ ] **Step 1: Create the hub screen**

```kotlin
package com.candorfoods.factoryops.presentation.head.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadReportsScreen(
    onBack: () -> Unit,
    onMachineReadings: () -> Unit,
    onFloorReadings: () -> Unit,
    onBreakdownHistory: () -> Unit,
    onQcActivityHistory: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { ReportTile(Icons.Filled.Bolt, "Machine Readings", "Per-machine kWh, date-wise", onMachineReadings) }
            item { ReportTile(Icons.Filled.DateRange, "Floor Readings", "Per-floor meter readings, date-wise", onFloorReadings) }
            item { ReportTile(Icons.Filled.Warning, "Breakdown History", "Every breakdown, open or closed", onBreakdownHistory) }
            item { ReportTile(Icons.Filled.CheckCircle, "QC Activity History", "Every QC decision, date-wise", onQcActivityHistory) }
        }
    }
}

@Composable
private fun ReportTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(Spacing.base))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
```

`FactoryCard`'s exact `onClick` parameter shape mirrors `HeadHomeScreen.kt`'s `SectionCard` (`presentation/head/HeadHomeScreen.kt:114`) — copy that call shape exactly if this doesn't compile as written.

- [ ] **Step 2: Add the five new routes to `Screen.kt`** — insert after `object HeadChecklistRecords` (`navigation/Screen.kt:78`):

```kotlin
    object HeadReports              : Screen("head/reports")
    object HeadReportsMachine       : Screen("head/reports/machine-readings")
    object HeadReportsFloor         : Screen("head/reports/floor-readings")
    object HeadReportsBreakdowns    : Screen("head/reports/breakdowns")
    object HeadReportsQcActivity    : Screen("head/reports/qc-activity")
```

- [ ] **Step 3: Wire `NavGraph.kt`**

Change the `HeadHomeScreen`'s `onReports` callback (`navigation/NavGraph.kt:447`) from:

```kotlin
                onReports         = { navController.navigate(Screen.ComingSoon.createRoute("Reports")) },
```

to:

```kotlin
                onReports         = { navController.navigate(Screen.HeadReports.route) },
```

Then add five new `composable(...)` blocks right after the existing `composable(Screen.HeadChecklistRecords.route) { ... }` block (`navigation/NavGraph.kt:459-467`, before `composable(Screen.ManageUsers.route)`):

```kotlin
        composable(Screen.HeadReports.route) {
            HeadReportsScreen(
                onBack               = { navController.popBackStack() },
                onMachineReadings    = { navController.navigate(Screen.HeadReportsMachine.route) },
                onFloorReadings      = { navController.navigate(Screen.HeadReportsFloor.route) },
                onBreakdownHistory   = { navController.navigate(Screen.HeadReportsBreakdowns.route) },
                onQcActivityHistory  = { navController.navigate(Screen.HeadReportsQcActivity.route) },
            )
        }
        composable(Screen.HeadReportsMachine.route) {
            MachineReadingHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HeadReportsFloor.route) {
            FloorReadingHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HeadReportsBreakdowns.route) {
            BreakdownHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HeadReportsQcActivity.route) {
            QcActivityHistoryScreen(onBack = { navController.popBackStack() })
        }
```

`MachineReadingHistoryScreen` takes only `onBack` + its Hilt-provided `viewModel` (Task 7) — `GetMachineMasterUseCase` is injected straight into `MachineReadingHistoryViewModel`'s constructor, not routed through the NavGraph or the Screen's parameter list, exactly like every other screen in this plan.

- [ ] **Step 4: Add the necessary imports to `NavGraph.kt`** — five new `presentation.head.reports.*` screen imports, alongside the existing `presentation.head.*` imports (`navigation/NavGraph.kt:23-25`).

- [ ] **Step 5: Compile check**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL — this is the integration point for every prior task, so treat any failure here as the top priority to fix before moving on.

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests from Tasks 1–10 passing alongside the pre-existing suite.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/HeadReportsScreen.kt app/src/main/java/com/candorfoods/factoryops/navigation/Screen.kt app/src/main/java/com/candorfoods/factoryops/navigation/NavGraph.kt
git commit -m "feat(head-reports): wire Reports hub and all four history screens into navigation"
```

---

## Task 12: Fix the A185 Reports building-code mismatch

**Files:**
- Review only: `src/main/java/com/candorfoods/factoryops/presentation/head/reports/*.kt` (all files from Tasks 6–10)

`BuildingFilterChips` (Task 6) emits `"W202"`/`"A185"`/`"both"` (no hyphen), matching `AssetScheduleApi`/`BreakdownApi`'s existing `plantId` convention. But `MtMachine.building` (used by Task 7's machine-options filter) and `FloorReadingHistoryEntry.building`/`MachineKwhHistoryEntry.building` (Task 1, populated from the backend's `building` column) use the **hyphenated** form `"W-202"`/`"A-185"` (confirmed in `domain/model/MtMachine.kt:11` and the backend's `ALL_BUILDINGS = ["A-185", "W-202"]`). Task 7's `MachineReadingHistoryScreen` filter (`it.building.replace("-", "") == state.building`) already normalizes this correctly — but double check every other place a `building` value is compared against `state.building`/`state.floor` filter selections across Tasks 7–10 for the same hyphen mismatch, since a silent string-equality miss would make the entity/building filters look broken (empty results) without any error.

- [ ] **Step 1: Grep for the mismatch**

Run: `grep -rn "\.building ==" src/main/java/com/candorfoods/factoryops/presentation/head/reports/`

Expected: only the already-normalized comparison in `MachineReadingHistoryScreen` (or none, if that logic moved into the ViewModel per Task 11 Step 3's revision) — no other raw `== state.building` comparisons. If any are found, apply the same `.replace("-", "")` normalization (or normalize once centrally in a small `normalizeBuilding(building: String): String` helper in `HistoryFilters.kt` and use it everywhere) so hyphenated and non-hyphenated forms always compare correctly.

- [ ] **Step 2: Compile + test check**

Run: `./gradlew compileDebugKotlin && ./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit** (only if Step 1 found something to fix)

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/head/reports/
git commit -m "fix(head-reports): normalize hyphenated vs non-hyphenated building codes in filters"
```

---

## Task 13: Backend handoff prompt

**Files:**
- Create: `docs/backend/head-reports-history-api-requirements.md` (in the FactoryOps repo, i.e. `d:\Maintenance module\FactoryOps\app\docs\backend\`, matching the existing `pm-backend-integration-spec.md`/`breakdown-records-api-requirements.md` convention)

This is a **written specification only** — do not implement it against `D:\Maintenance module\backend` in this session; that is a separate repo/team.

- [ ] **Step 1: Write the handoff doc**

```markdown
# Backend Requirement — Head Reports History Endpoints

**For:** Backend (Python/FastAPI) team
**Priority:** Required — the Android app's new Head Reports section is built and waiting on these four read-only endpoints.

## Context

Head Reports needs to browse full history (date-ranged, entity-filterable) across four
existing tables. Every endpoint below is READ-ONLY and queries existing tables/columns —
no schema changes, no new tables. All follow the existing `scoped_buildings(user)` /
`norm_plant()` conventions already used by `/head/reports/power` and `/floor-readings/system`
(`app/utils.py`), and the same `building`/`plant_id` short-code convention ("W202"/"A185"/"both")
used throughout the existing API.

## Endpoint 1: Machine reading (kWh) history

`GET /machines/daily-kwh/history?from=&to=&building=both&machine_id=`

Row-level list from `mt_machine_daily_kwh`, `from`/`to` ISO `YYYY-MM-DD` inclusive,
`building` optional ("W202"/"A185"/"both", default "both" — HEAD sees both plants unless
narrowed), `machine_id` optional exact match. Response: JSON array, newest first:

```json
[{
  "machine_id": "A185-0007", "machine_name": "Roaster 1", "building": "A-185",
  "floor": "Ground floor", "reading_date": "2026-07-20", "daily_kwh": 12.5,
  "source": "RUN", "started_at": 1690000000000, "ended_at": 1690003600000,
  "status": "COMPLETED", "operator_name": "Ravi"
}]
```

`started_at`/`ended_at` epoch-ms (matches the app's existing `Long` timestamp convention
elsewhere), or `null` if not applicable to that row's `source`.

## Endpoint 2: Floor reading history

`GET /floor-readings/history?from=&to=&building=both&floor=`

Row-level list from `mt_floor_utility_readings`. Same `from`/`to`/`building` semantics as
Endpoint 1; `floor` optional exact match (`mt_asset_list.sub_location` value). Response:

```json
[{
  "building": "A-185", "floor": "Ground floor", "reading_date": "2026-07-20",
  "system_reading": 40.2, "meter_reading": 41.0
}]
```

`meter_reading` is `null` if the technician never submitted an actual reading for that
floor/date (system_reading is always populated — it's computed from `mt_machine_daily_kwh`,
same as the existing `/floor-readings/system` endpoint already does).

## Endpoint 3: Breakdown history (all statuses)

`GET /breakdowns/history?from=&to=&building=both&status=`

Every row of `mt_breakdown_records` reported in range, regardless of status (unlike the
existing `/breakdowns/open`, which excludes CLOSED). `status` optional exact match
("OPEN"/"ACKNOWLEDGED"/"PENDING_QC"/"CLOSED"/"REOPENED"); omitted = all statuses. Filter by
`reported_at` falling within `[from, to]`. Response — same fields as the existing
`OpenBreakdownDto` (`GET /breakdowns/open`) **plus** the resolved/QC-decision fields it
doesn't carry:

```json
[{
  "id": "bd-1", "asset_id": "A185-0007", "asset_name": "Roaster 1", "building": "A-185",
  "reported_by": "op-1", "reporter_name": "Ravi", "severity": "MAJOR",
  "description": "Belt snapped", "status": "CLOSED", "reported_at": 1690000000000,
  "acknowledged_by": "tech-1", "acknowledged_by_name": "Suresh", "acknowledged_at": 1690001000000,
  "resolved_by": "tech-1", "resolved_by_name": "Suresh", "resolved_at": 1690004000000,
  "parts_used": "V-belt A-45",
  "qc_status": "APPROVED", "qc_decided_by": "qc-1", "qc_decided_by_name": "Sam",
  "qc_decided_at": 1690005000000, "qc_decision_notes": "Looks good"
}]
```

Any field with no value: send `null` (or omit — the app already tolerates missing keys via
Kotlin default parameter values), never a bare empty string for the numeric/timestamp fields.

## Endpoint 4: QC activity history

`GET /qc/history?from=&to=&building=both`

Same response shape as Endpoint 3, but filtered to rows where `qc_decided_at` falls within
`[from, to]` (i.e. every breakdown that received a QC decision in the window, regardless of
its current `status`) rather than filtering by `reported_at`. **This surfaces only the
current/latest QC decision per record** — there is no separate audit-log table, so a record
that was approved, sent back, and re-approved will appear once, showing only its final
decision (confirmed acceptable scope with the app team).

## Auth / scope

All four: `Authorization: Bearer <token>`, any authenticated role may call them (the app
only exposes them under the Head Reports UI, but there's no reason to restrict server-side
beyond normal auth — same posture as `/head/reports/power`). Use the existing
`scoped_buildings(user)` helper so a non-HEAD caller is automatically narrowed to their own
plant if they omit `building`.

## Out of scope

No new tables, no new columns, no writes. Endpoint 4 does **not** require a QC audit-log
table — that was explicitly descoped on the app side in favor of latest-decision-only
history.

## Acceptance / quick test

```
curl -H "Authorization: Bearer <token>" \
  "https://<api-base>/machines/daily-kwh/history?from=2026-07-01&to=2026-07-20"
curl -H "Authorization: Bearer <token>" \
  "https://<api-base>/floor-readings/history?from=2026-07-01&to=2026-07-20&building=A185"
curl -H "Authorization: Bearer <token>" \
  "https://<api-base>/breakdowns/history?from=2026-07-01&to=2026-07-20"
curl -H "Authorization: Bearer <token>" \
  "https://<api-base>/qc/history?from=2026-07-01&to=2026-07-20"
```

Each should return `200` with a JSON array (`[]` when empty, never `404`).
```

- [ ] **Step 2: Commit**

```bash
git add app/docs/backend/head-reports-history-api-requirements.md
git commit -m "docs: backend handoff prompt for Head Reports history endpoints"
```

---

## Final check

- [ ] **Step 1: Full build + test**

Run: `./gradlew compileDebugKotlin && ./gradlew test`
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 2: Spec coverage sanity check**

Re-read `docs/superpowers/specs/2026-07-22-head-reports-history-design.md` section by section and confirm: Reports tile no longer routes to ComingSoon (Task 11); all four sections exist with building + date-range filters (Tasks 7–10); machine/floor entity filters exist (Tasks 7–8); breakdown history includes both open and closed (Task 9); QC activity history is latest-decision-only per the confirmed scope (Task 10); no backend code was touched, only a handoff doc was written (Task 13).

- [ ] **Step 3: Tell the user what's blocked on the backend**

Until the backend team implements and deploys the four endpoints in Task 13's prompt, all
four Head Reports screens will load and show a clean empty/error state rather than data —
this is expected, not a bug. Point the user at `docs/backend/head-reports-history-api-requirements.md`.
