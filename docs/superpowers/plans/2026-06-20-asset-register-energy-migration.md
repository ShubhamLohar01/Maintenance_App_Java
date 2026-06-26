# Asset-Register Energy Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the production-run / energy flow off the old `machines` catalog onto the `mt_asset_list` asset register, keyed by `assetId`, fixing the "stop production" 404.

**Architecture:** Operator Home lists *runnable* assets from `GET /mt-machines` (rule: non-blank `power_load` AND `category` not in a blocklist). Runs are keyed by `assetId`; at Start the asset's `ratedKw`+name are snapshotted onto the local run so Stop computes `kWh = ratedKw × hours × 0.99` offline. Daily-kWh posts to `POST /mt-machines/{asset_id}/daily-kwh`. Sync stores the server `run_id` from start and uses it for stop (current code wrongly stops on the local id).

**Tech Stack:** Kotlin, Retrofit + kotlinx.serialization, Room, Hilt, Jetpack Compose. JVM unit tests: JUnit5 + MockK + kotlinx-coroutines-test.

**Conventions for every task:**
- Build/test command base: `& "d:\Maintenance module\FactoryOps\gradlew.bat" -p "d:\Maintenance module\FactoryOps" <tasks> --console=plain`
- Run one test class: append `:app:testDebugUnitTest --tests "com.candorfoods.factoryops.<ClassName>"` (do **not** combine `--tests` with `assembleDebug` in one invocation).
- **This project is NOT under git** — replace "commit" with a **verification checkpoint** (build/test green) at the end of each task. (If the user later `git init`s, add commits.)
- Spec: `docs/superpowers/specs/2026-06-20-asset-register-energy-migration-design.md`.

---

## File map

| File | Change |
|---|---|
| `data/remote/dto/MtMachineDto.kt` | `sub_category`→`category`; add `rated_kw`,`condition`,`assigned_to`; new `toDomain` |
| `domain/model/MtMachine.kt` | add `category`,`condition`,`assignedTo`; rename derived enum to `section`; add `isRunnable()` + `RUNNABLE` rule |
| `data/remote/api/MtMachineApi.kt` | `@Query("sub_category")`→`@Query("category")` |
| `data/remote/dto/EnergyDtos.kt` | `RunStartRequestDto`+`clientRunId`; `RunStartResponseDto` −`operatorId`+`clientRunId`; replace `MachineHistoryDto` with `DailyHistoryDto`/`DailyRunDto` |
| `data/remote/api/EnergyApi.kt` | `getMachineHistory` returns `List<DailyHistoryDto>` |
| `data/remote/api/MachineDailyKwhApi.kt` | path `mt-machines/{machine_id}/daily-kwh`; drop `list()` |
| `data/remote/dto/MachineDailyKwhDtos.kt` | `building`/`floor` nullable optional |
| `data/local/entity/ProductionRunEntity.kt` | add `assetName`,`ratedKw`,`serverRunId` |
| `data/local/AppDatabase.kt` | version 6→7 |
| `data/local/dao/ProductionRunDao.kt` | add `setServerRunId(...)` |
| `data/repository/MachineDailyKwhRepositoryImpl.kt` | drop machineDao lookup; post `assetId` |
| `data/repository/EnergyRepositoryImpl.kt` | snapshot at start; stop kWh from snapshot; sync stores+uses `serverRunId` |
| `domain/repository/EnergyRepository.kt` | `startProductionRun` signature gains asset name + ratedKw |
| `presentation/home/HomeViewModel.kt` + `HomeScreen.kt` | source runnable assets from `MtMachineRepository` |
| `presentation/machine/MachineDetailViewModel.kt` (+screen) | start run with asset snapshot |
| `presentation/history/HistoryViewModel.kt` (+screen) | consume `List<DailyHistoryDto>` |

---

## Phase 1 — Contract layer (TDD)

### Task 1: `MtMachine` — real `category`, new fields, `isRunnable()`

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/remote/dto/MtMachineDto.kt`
- Modify: `app/src/main/java/com/candorfoods/factoryops/domain/model/MtMachine.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/MtAssetMappingTest.kt`

- [ ] **Step 1: Write failing tests** (append inside `MtAssetMappingTest`, and update the `dto(...)` helper to pass `category` instead of `subCategory`):

```kotlin
// ---- category + runnable rule ----------------------------------------------

@Test
fun `maps category and new fields from the asset payload`() {
    val m = MtMachineDto(
        assetId = "W202-0005", building = "W-202", assetName = "Shrink Wrap",
        category = "Packaging", subLocation = "2nd Floor", powerLoad = "3 kW",
        ratedKw = 3.0, quantity = 1, condition = "Good", assignedTo = "Prod Team",
    ).toDomain()
    assertEquals("Packaging", m.category)
    assertEquals(3.0, m.ratedKw!!, 1e-9)
    assertEquals("Good", m.condition)
    assertEquals("Prod Team", m.assignedTo)
}

@Test
fun `runnable when power_load present and category not blocked`() {
    fun mt(cat: String, pl: String?) = MtMachineDto(
        assetId = "X", building = "W-202", assetName = "n",
        category = cat, subLocation = "f", powerLoad = pl, ratedKw = 1.0,
    ).toDomain()

    assertTrue(mt("Packaging", "3 kW").isRunnable())
    assertTrue(mt("Roasting", "120Watt").isRunnable())
    assertFalse(mt("Packaging", null).isRunnable())          // no power_load
    assertFalse(mt("Packaging", "  ").isRunnable())          // blank power_load
    assertFalse(mt("Electric Asset", "120Watt").isRunnable())   // tubelight
    assertFalse(mt("IT/Communication", "20 W").isRunnable())    // CCTV
    assertFalse(mt("Power Distribution", "5 kW").isRunnable())   // panel
    assertFalse(mt("electric asset", "120Watt").isRunnable())    // case-insensitive block
}
```

- [ ] **Step 2: Run, verify FAIL**

Run: `… :app:testDebugUnitTest --tests "com.candorfoods.factoryops.MtAssetMappingTest"`
Expected: FAIL — `category`/`condition`/`assignedTo`/`isRunnable` unresolved.

- [ ] **Step 3: Update `MtMachineDto.kt`** to:

```kotlin
@Serializable
data class MtMachineDto(
    @SerialName("asset_id")     val assetId: String,
    @SerialName("building")     val building: String = "",
    @SerialName("asset_name")   val assetName: String = "",
    @SerialName("category")     val category: String = "",
    @SerialName("sub_location") val subLocation: String = "",
    @SerialName("power_load")   val powerLoad: String? = null,
    @SerialName("rated_kw")     val ratedKw: Double? = null,
    @SerialName("quantity")     val quantity: Int? = null,
    @SerialName("condition")    val condition: String? = null,
    @SerialName("assigned_to")  val assignedTo: String? = null,
)

fun MtMachineDto.toDomain(): MtMachine = MtMachine(
    assetId      = assetId,
    building     = building,
    assetName    = assetName,
    category     = category,
    subLocation  = subLocation,
    ratedKw      = ratedKw ?: PowerLoad.parseKw(powerLoad),   // prefer backend, fall back to client parse
    powerLoadRaw = powerLoad?.takeIf { it.isNotBlank() },
    section      = deriveMachineSection(category),
    quantity     = quantity,
    condition    = condition,
    assignedTo   = assignedTo,
)
```

- [ ] **Step 4: Update `MtMachine.kt`** — add fields, rename derived `category: MachineCategory` → `section`, add the runnable rule. Replace the whole file body:

```kotlin
data class MtMachine(
    val assetId: String,
    val building: String,
    val assetName: String,
    val category: String,           // free-text backend category, e.g. "Packaging"
    val subLocation: String,
    val ratedKw: Double?,
    val powerLoadRaw: String?,
    val section: MachineCategory,   // PRODUCTION/OTHERS grouping for Machine Master
    val quantity: Int? = null,
    val condition: String? = null,
    val assignedTo: String? = null,
)

/** Machine Master section grouping: only "Production Equipment" is PRODUCTION. */
fun deriveMachineSection(category: String): MachineCategory =
    if (category.trim().equals("Production Equipment", ignoreCase = true)) MachineCategory.PRODUCTION
    else MachineCategory.OTHERS

/** Categories that are NOT real production machinery (no "Start production"). */
val NON_RUNNABLE_CATEGORIES: Set<String> = setOf(
    "Electric Asset", "Housekeeping", "Utility", "IT/Communication",
    "Power Distribution", "Infrastructure", "Fire & Safety", "Utility/Infrastructure",
).map { it.lowercase() }.toSet()

/** A run can be started only on real machinery that has a known power draw. */
fun MtMachine.isRunnable(): Boolean =
    !powerLoadRaw.isNullOrBlank() && category.trim().lowercase() !in NON_RUNNABLE_CATEGORIES
```

> Note: `deriveMachineCategory` is removed/renamed to `deriveMachineSection`. Update its only other caller `MachineMasterViewModel` (it groups by `row.section` now) and any `MtMachineRow`/`SubCategoryChip(subCategory = machine.subCategory)` reference → `machine.category`. Search: `grep "subCategory\|deriveMachineCategory\|\.section\b"`.

- [ ] **Step 5: Run tests, verify PASS** (also fix `MachineMasterViewModel`/screen references until the module compiles).

Run: `… :app:testDebugUnitTest --tests "com.candorfoods.factoryops.MtAssetMappingTest"`
Expected: PASS.

- [ ] **Step 6: Checkpoint** — `… :app:assembleDebug --console=plain` → BUILD SUCCESSFUL.

---

### Task 2: `MtMachineApi` query rename

**Files:** Modify `app/src/main/java/com/candorfoods/factoryops/data/remote/api/MtMachineApi.kt`

- [ ] **Step 1:** change `@Query("sub_category") subCategory` → `@Query("category") category` in `list(...)` and update the `MtMachineRepository.list(...)` param name `subCategory`→`category` end-to-end (interface, impl, `GetMachineMasterUseCase`, `MachineMasterViewModel`). (No new behavior; the filter just stops no-op-ing.)
- [ ] **Step 2: Checkpoint** — `:app:assembleDebug` BUILD SUCCESSFUL.

---

### Task 3: Energy DTOs — `client_run_id`, drop `operator_id`, history list

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/remote/dto/EnergyDtos.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/EnergyDtoTest.kt` (new)

- [ ] **Step 1: Write failing test** (`EnergyDtoTest.kt`):

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.remote.dto.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnergyDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `start request serialises client_run_id`() {
        val s = json.encodeToString(
            RunStartRequestDto(machineId = "W202-0005", clientRunId = "uuid-1",
                startedAt = 1750000000000, scheduledEndAt = 1750003600000)
        )
        assertTrue(s.contains("\"client_run_id\":\"uuid-1\""), s)
        assertTrue(s.contains("\"machine_id\":\"W202-0005\""), s)
    }

    @Test fun `start response parses without operator_id`() {
        val r = json.decodeFromString<RunStartResponseDto>(
            """{"run_id":"r1","client_run_id":"uuid-1","started_at":1,"scheduled_end_at":2}"""
        )
        assertEquals("r1", r.runId)
        assertEquals("uuid-1", r.clientRunId)
    }

    @Test fun `history parses as a list of daily summaries`() {
        val list = json.decodeFromString<List<DailyHistoryDto>>(
            """[{"date":"2026-06-20","total_run_hours":1.0,"total_kwh":0.12,
                 "estimated_cost":1.02,"runs":[{"id":"run-1","started_at":1,"ended_at":2,
                 "duration_hours":1.0,"kwh":0.12}]}]"""
        )
        assertEquals(1, list.size)
        assertEquals("2026-06-20", list[0].date)
        assertEquals(1, list[0].runs.size)
        assertEquals("run-1", list[0].runs[0].id)
    }
}
```

- [ ] **Step 2: Run, verify FAIL** (`… --tests "com.candorfoods.factoryops.EnergyDtoTest"`).

- [ ] **Step 3: Edit `EnergyDtos.kt`:**

```kotlin
@Serializable
data class RunStartRequestDto(
    @SerialName("machine_id") val machineId: String,
    @SerialName("client_run_id") val clientRunId: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("scheduled_end_at") val scheduledEndAt: Long,
)

@Serializable
data class RunStartResponseDto(
    @SerialName("run_id") val runId: String,
    @SerialName("client_run_id") val clientRunId: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("scheduled_end_at") val scheduledEndAt: Long,
)

// RunStopRequestDto, RunStopResponseDto, ProductionRunDto: unchanged.

@Serializable
data class DailyRunDto(
    val id: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("ended_at") val endedAt: Long?,
    @SerialName("duration_hours") val durationHours: Double,
    val kwh: Double,
)

@Serializable
data class DailyHistoryDto(
    val date: String,
    @SerialName("total_run_hours") val totalRunHours: Double,
    @SerialName("total_kwh") val totalKwh: Double,
    @SerialName("estimated_cost") val estimatedCost: Double,
    val runs: List<DailyRunDto>,
)
// Delete: data class MachineHistoryDto.
```

- [ ] **Step 4: Run tests, verify PASS.**
- [ ] **Step 5: Checkpoint** — module won't fully build until Task 4 (EnergyApi) + callers update; proceed to Task 4 before the assemble checkpoint.

---

### Task 4: `EnergyApi.getMachineHistory` returns a list

**Files:** Modify `app/src/main/java/com/candorfoods/factoryops/data/remote/api/EnergyApi.kt`

- [ ] **Step 1:** change return type `MachineHistoryDto` → `List<DailyHistoryDto>`. Import update.
- [ ] **Step 2:** Update any caller (`EnergyRepositoryImpl.getMachineHistory` maps `.runs`; it now receives daily summaries — see Task 8 / Task 10). Leave the mapping fix to Task 10; for now make it compile (temporary: flatten `list.flatMap { it.runs }`). 
- [ ] **Step 3: Checkpoint** — `:app:assembleDebug` BUILD SUCCESSFUL (with Task 3).

---

### Task 5: Daily-kWh endpoint path + optional building/floor

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/remote/api/MachineDailyKwhApi.kt`
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/remote/dto/MachineDailyKwhDtos.kt`

- [ ] **Step 1:** In `MachineDailyKwhApi.kt`, change `@POST("machines/{machine_id}/daily-kwh")` → `@POST("mt-machines/{machine_id}/daily-kwh")`. **Delete** the `list()` GET function and the `MachineDailyKwhDto` GET usages it needs (the read path has no backend equivalent).
- [ ] **Step 2:** In `MachineDailyKwhDtos.kt`, make `building`/`floor` optional on the upsert DTO:

```kotlin
@Serializable
data class MachineDailyKwhUpsertDto(
    @SerialName("machine_id")   val machineId: String,
    @SerialName("reading_date") val readingDate: String,
    @SerialName("daily_kwh")    val dailyKwh: Double,
    @SerialName("source")       val source: String = "CALCULATED",
    @SerialName("building")     val building: String? = null,  // server fills; optional
    @SerialName("floor")        val floor: String? = null,
)
```
Keep `MachineDailyKwhDto` (response) — building/floor/updated_at nullable. If `list()` was the only user of `MockMachineDailyKwhApi.list`, simplify the mock to upsert-only.
- [ ] **Step 3: Checkpoint** — defer assemble to Task 7 (repo uses these).

---

## Phase 2 — Run lifecycle, offline snapshot, sync fix

### Task 6: `ProductionRunEntity` snapshot columns + Room v7

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/local/entity/ProductionRunEntity.kt`
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/local/dao/ProductionRunDao.kt`

- [ ] **Step 1:** Add columns (nullable so destructive-fallback recreate is clean):

```kotlin
@Entity(tableName = "production_runs")
data class ProductionRunEntity(
    @PrimaryKey val id: String,          // = clientRunId (device UUID)
    val machineId: String,               // = assetId
    val operatorId: String,
    val startedAt: Long,
    val scheduledEndAt: Long,
    val endedAt: Long?,
    val computedKwh: Double?,
    val syncStatus: String,
    val flagStatus: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val assetName: String? = null,       // snapshot
    val ratedKw: Double? = null,         // snapshot — for offline kWh compute
    val serverRunId: String? = null,     // set after start sync; used for stop sync
)
```

- [ ] **Step 2:** `AppDatabase.kt` — bump `version = 6` → `version = 7`, add a comment line `// v6 → v7: production_runs gains assetName/ratedKw/serverRunId snapshot columns.` (`fallbackToDestructiveMigration` already set — no manual migration.)
- [ ] **Step 3:** `ProductionRunDao.kt` — add:

```kotlin
@Query("UPDATE production_runs SET serverRunId = :serverRunId, updatedAt = :updatedAt WHERE id = :id")
suspend fun setServerRunId(id: String, serverRunId: String, updatedAt: Long)
```

- [ ] **Step 4: Checkpoint** — defer assemble to Task 8.

---

### Task 7: `MachineDailyKwhRepositoryImpl` posts by `assetId` (no local lookup)

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/repository/MachineDailyKwhRepositoryImpl.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/MachineDailyKwhRepositoryImplTest.kt` (rewrite)

- [ ] **Step 1: Rewrite the test** — no machineDao; assert path+body use the assetId and no building/floor lookup:

```kotlin
class MachineDailyKwhRepositoryImplTest {
    private val api = mockk<MachineDailyKwhApi>()
    private val db = mockk<AppDatabase>()
    private val prodDao = mockk<ProductionRunDao>()
    private val auth = mockk<AuthPreferences>()
    private lateinit var repo: MachineDailyKwhRepositoryImpl

    private val atMillis = 1_750_000_000_000L
    private val dayStart = DateTimeFormatter.startOfDayMillis(atMillis)
    private val nextDayStart = dayStart + 24L * 60 * 60 * 1000

    @BeforeEach fun setUp() {
        every { db.productionRunDao() } returns prodDao
        every { auth.getToken() } returns "tok"
        repo = MachineDailyKwhRepositoryImpl(api, db, auth)
    }

    @Test fun `posts the day total to mt-machines path with matching asset id`() = runTest {
        coEvery { prodDao.sumComputedKwhInRange("W202-0005", dayStart, nextDayStart) } returns 12.5
        val pathId = slot<String>(); val body = slot<MachineDailyKwhUpsertDto>()
        coEvery { api.upsert("Bearer tok", capture(pathId), capture(body)) } returns
            MachineDailyKwhDto("W202-0005", DateTimeFormatter.isoDate(atMillis), null, null, 12.5, "CALCULATED")

        val result = repo.upsertForMachineOnDate("W202-0005", atMillis)

        assertTrue(result is Result.Success)
        assertEquals("W202-0005", pathId.captured)
        assertEquals("W202-0005", body.captured.machineId)
        assertEquals(12.5, body.captured.dailyKwh, 1e-9)
        assertEquals("CALCULATED", body.captured.source)
    }

    @Test fun `errors when not logged in`() = runTest {
        every { auth.getToken() } returns null
        assertTrue(repo.upsertForMachineOnDate("W202-0005", atMillis) is Result.Error)
        coVerify(exactly = 0) { api.upsert(any(), any(), any()) }
    }
}
```
> `MachineDailyKwhDto` constructor order must match Task 5's response DTO; adjust if needed.

- [ ] **Step 2: Run, verify FAIL.**

- [ ] **Step 3: Rewrite `upsertForMachineOnDate`** — drop the machineDao lookup:

```kotlin
override suspend fun upsertForMachineOnDate(machineId: String, atMillis: Long): Result<Unit> {
    val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
    return try {
        val dayStart = DateTimeFormatter.startOfDayMillis(atMillis)
        val totalKwh = db.productionRunDao()
            .sumComputedKwhInRange(machineId, dayStart, dayStart + DAY_MILLIS)
        machineDailyKwhApi.upsert(
            token = "Bearer $token",
            machineId = machineId,                 // assetId
            dto = MachineDailyKwhUpsertDto(
                machineId = machineId,
                readingDate = DateTimeFormatter.isoDate(atMillis),
                dailyKwh = totalKwh,
                source = "CALCULATED",
            ),
        )
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to upsert daily kWh", e)
    }
}
private companion object { const val DAY_MILLIS = 24L * 60 * 60 * 1000 }
```
Remove the now-unused `AppDatabase.machineDao()` import usage if any.

- [ ] **Step 4: Run tests, verify PASS.**

---

### Task 8: `EnergyRepositoryImpl` — snapshot start, offline stop kWh, sync `serverRunId` fix

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/domain/repository/EnergyRepository.kt` (signature)
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/repository/EnergyRepositoryImpl.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/EnergyRunComputeTest.kt` (new, pure-function level)

- [ ] **Step 1:** Extract the stop-kWh formula to a testable top-level function and test it:

```kotlin
// in EnergyRepositoryImpl.kt (top-level, same file)
/** Server-matching formula: rated_kw × hours × 0.99. Null rating → null (server computes on sync). */
fun computeRunKwh(ratedKw: Double?, startedAt: Long, endedAt: Long): Double? {
    if (ratedKw == null) return null
    val hours = (endedAt - startedAt) / 3_600_000.0
    return ratedKw * hours * 0.99
}
```
Test `EnergyRunComputeTest.kt`:
```kotlin
class EnergyRunComputeTest {
    @Test fun `kwh is rated times hours times 0_99`() {
        val kwh = computeRunKwh(3.0, 0L, 3_600_000L)!!   // 1 hour
        assertEquals(3.0 * 1.0 * 0.99, kwh, 1e-9)
    }
    @Test fun `null rating yields null kwh`() {
        assertEquals(null, computeRunKwh(null, 0L, 3_600_000L))
    }
}
```
Run `… --tests "com.candorfoods.factoryops.EnergyRunComputeTest"` → FAIL then PASS.

- [ ] **Step 2:** `EnergyRepository.kt` — change the start signature:
```kotlin
suspend fun startProductionRun(
    assetId: String, assetName: String, ratedKw: Double?, scheduledEndAt: Long
): Result<ProductionRun>
```

- [ ] **Step 3:** `EnergyRepositoryImpl.startProductionRun` — write snapshot + clientRunId(=id):
```kotlin
override suspend fun startProductionRun(
    assetId: String, assetName: String, ratedKw: Double?, scheduledEndAt: Long
): Result<ProductionRun> = try {
    val now = System.currentTimeMillis()
    val localId = UUID.randomUUID().toString()       // = clientRunId
    val entity = ProductionRunEntity(
        id = localId, machineId = assetId, operatorId = authPreferences.getUserId().orEmpty(),
        startedAt = now, scheduledEndAt = scheduledEndAt, endedAt = null, computedKwh = null,
        syncStatus = "PENDING", flagStatus = null, createdAt = now, updatedAt = now,
        assetName = assetName, ratedKw = ratedKw, serverRunId = null,
    )
    db.productionRunDao().insert(entity)
    Result.Success(entity.toDomain())
} catch (e: Exception) { Result.Error(e.message ?: "Failed to start run", e) }
```
Remove the `db.machineDao().updateStatus(...)` call (status now derives from production_runs).

- [ ] **Step 4:** `stopProductionRun` — compute from snapshot, keep daily-kWh by assetId:
```kotlin
override suspend fun stopProductionRun(runId: String, endedAt: Long): Result<ProductionRun> = try {
    val run = db.productionRunDao().getRunById(runId) ?: return Result.Error("Run not found")
    val computedKwh = computeRunKwh(run.ratedKw, run.startedAt, endedAt)
    val now = System.currentTimeMillis()
    db.productionRunDao().stopRun(runId = runId, endedAt = endedAt, computedKwh = computedKwh,
        syncStatus = "PENDING", updatedAt = now)
    machineDailyKwhRepository.upsertForMachineOnDate(run.machineId, run.startedAt)
    Result.Success(run.copy(endedAt = endedAt, computedKwh = computedKwh,
        syncStatus = "PENDING", updatedAt = now).toDomain())
} catch (e: Exception) { Result.Error(e.message ?: "Failed to stop run", e) }
```
> `ProductionRunDao.stopRun` already accepts `computedKwh: Double?` — verify; if it's non-null, widen it.

- [ ] **Step 5:** `syncPendingRuns` — store `serverRunId` on start; stop on `serverRunId`:
```kotlin
for (run in pending) {
    db.productionRunDao().updateSyncStatus(run.id, "SYNCING", System.currentTimeMillis())
    try {
        if (run.endedAt == null) {
            val resp = energyApi.startRun("Bearer $token",
                RunStartRequestDto(run.machineId, run.id, run.startedAt, run.scheduledEndAt))
            db.productionRunDao().setServerRunId(run.id, resp.runId, System.currentTimeMillis())
        } else {
            // ensure the run exists server-side first
            val serverId = run.serverRunId ?: run {
                val resp = energyApi.startRun("Bearer $token",
                    RunStartRequestDto(run.machineId, run.id, run.startedAt, run.scheduledEndAt))
                db.productionRunDao().setServerRunId(run.id, resp.runId, System.currentTimeMillis())
                resp.runId
            }
            energyApi.stopRun("Bearer $token", serverId, RunStopRequestDto(run.endedAt))
        }
        db.productionRunDao().updateSyncStatus(run.id, "SYNCED", System.currentTimeMillis())
    } catch (e: Exception) {
        db.productionRunDao().updateSyncStatus(run.id, "FAILED", System.currentTimeMillis())
    }
}
```
> `start` is idempotent on `client_run_id`, so re-starting an already-started run to recover the `serverRunId` is safe.

- [ ] **Step 6:** Update callers of `startProductionRun` (Task 9 wires the asset args). Run all unit tests + assemble:

Run: `… :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass. (Compile errors here point to start-run callers — fixed in Task 9.)

---

## Phase 3 — Home / asset selection (build-verified)

### Task 9: Home lists runnable assets; run starts with the snapshot

**Files (read each before editing):**
- `app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeViewModel.kt`
- `app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeScreen.kt`
- `app/src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailViewModel.kt` (+ its screen) — where `startProductionRun` is invoked

- [ ] **Step 1:** Add a use case `domain/usecase/mtmachine/GetRunnableAssetsUseCase.kt`:
```kotlin
class GetRunnableAssetsUseCase @Inject constructor(
    private val repo: MtMachineRepository,
) {
    suspend operator fun invoke(): Result<List<MtMachine>> = when (val r = repo.list()) {
        is Result.Success -> Result.Success(r.data.filter { it.isRunnable() }
            .sortedBy { it.assetName.lowercase() })
        else -> r
    }
}
```
(`MtMachineRepository.list()` has nullable filter params with defaults — call with none.)

- [ ] **Step 2:** Rework `HomeViewModel` to hold `List<MtMachine>` from `GetRunnableAssetsUseCase` instead of `Machine` from `GetAssignedMachinesUseCase`. Keep the existing search + category chip filtering, but over `MtMachine` (`category`, `assetName`, `assetId`, `subLocation`). `HomeUiState.machines: List<MtMachine>`, `categories` from `machines.map { it.category }`. Provide `onRefresh()` re-calling the use case. Loading/error/empty states preserved.

- [ ] **Step 3:** Update `HomeScreen` `MachineCard` usage → render `MtMachine` (assetName, assetId chip, category chip, `ratedKw`/power). The card's `onClick(assetId)` opens the run screen. (Reuse `SubCategoryChip(subCategory = machine.category)`.)

- [ ] **Step 4:** In the run screen/VM, call `startProductionRun(asset.assetId, asset.assetName, asset.ratedKw, scheduledEndAt)`; show `ratedKw`. The detail screen must fetch the chosen `MtMachine` (pass `assetId` via nav args and look it up from the runnable list / a single-asset fetch).

- [ ] **Step 5: Checkpoint** — `:app:assembleDebug` BUILD SUCCESSFUL; manual smoke (Task 11).

---

## Phase 4 — History

### Task 10: History consumes the daily-summary list

**Files (read first):**
- `app/src/main/java/com/candorfoods/factoryops/data/repository/EnergyRepositoryImpl.kt` (`getMachineHistory`)
- `app/src/main/java/com/candorfoods/factoryops/presentation/history/HistoryViewModel.kt` (+ screen)

- [ ] **Step 1:** `EnergyRepositoryImpl.getMachineHistory` (the remote variant, if used) maps `List<DailyHistoryDto>` → domain. Decide the domain shape: simplest is to flatten to per-run rows for the existing `HistoryUiState.runs`, OR add a `DailyHistory` domain model and group in the UI. Pick flatten for minimal change: `list.flatMap { d -> d.runs.map { it.toProductionRun(machineId) } }`. (The local-Room history path `getMachineHistory` reading `production_runs` can stay for offline.)
- [ ] **Step 2:** Update the history screen if grouping by day is desired (optional; YAGNI — keep flat list first).
- [ ] **Step 3: Checkpoint** — `:app:assembleDebug` BUILD SUCCESSFUL.

---

## Phase 5 — End-to-end verification

### Task 11: Live smoke test through the tunnel

- [ ] **Step 1:** `local.properties` already set (`USE_MOCK_API=false`, `USE_MOCK_AUTH=false`, tunnel `BASE_URL`). Rebuild + clean reinstall:
  `:app:assembleDebug`; `adb uninstall in.candorfoods.factoryops`; `adb install …/app-debug.apk`.
- [ ] **Step 2:** Login `operator1/pass123`. Home shows ~64 runnable assets (no lights/CCTV/panels).
- [ ] **Step 3:** Pick a `W202-####` asset → Start → Stop. Watch `adb logcat -s OkHttp`:
  - `POST …/energy/runs/start` → 200 with `run_id`+`client_run_id`.
  - `POST …/energy/runs/{run_id}/stop` → 200 with non-null `computed_kwh`.
  - `POST …/mt-machines/{asset_id}/daily-kwh` → **200** (no 404).
- [ ] **Step 4 (backend confirm):** row present in `mt_machine_daily_kwh` and `production_runs` for the asset.

---

## Acceptance (from spec)
- Stop production → `POST /mt-machines/{asset_id}/daily-kwh` 200; rows in `mt_machine_daily_kwh` + `production_runs`.
- `/energy/runs/start` 200 (no 422); `/stop` 200 with non-null `computed_kwh`.
- Home shows runnable assets only (no lights/CCTV/panels/furniture).
- No `machines/{id}/daily-kwh` call remains (grep the codebase to confirm).
