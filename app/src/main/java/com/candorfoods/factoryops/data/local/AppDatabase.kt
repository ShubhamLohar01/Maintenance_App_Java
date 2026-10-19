package com.candorfoods.factoryops.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.candorfoods.factoryops.data.local.dao.*
import com.candorfoods.factoryops.data.local.entity.*

@Database(
    entities = [
        MachineEntity::class,
        ProductionRunEntity::class,
        UserEntity::class,
        SyncQueueEntity::class,
        BreakdownFlagEntity::class,
        PmTemplateEntity::class,
        PmTemplateItemEntity::class,
        PmPlanEntity::class,
        PmWorkOrderEntity::class,
        PmWoTaskLogEntity::class,
        PmWoSpareEntity::class,
        SparePartPickEntity::class,
        BreakdownSpareEntity::class,
        RecessStateEntity::class
    ],
    version = 14,
    exportSchema = false
    // v3 → v4 (Phase 2C): adds 6 PM tables and `machineType` column on `machines`.
    // v4 → v5: adds `building` column on `machines` (W202 / A185; was W-202 / A-185).
    // v5 → v6: adds nullable `category` column on `machines`.
    // v6 → v7: production_runs gains assetName/ratedKw/serverRunId snapshot columns
    //          for the asset-register energy flow (offline kWh + server-run-id stop).
    // v7 → v8: breakdown_flags gains assetName/building snapshot columns so asset-
    //          register breakdowns can display human name + building without a
    //          legacy `machines`-table row.
    // v8 → v9: adds breakdown_f06_drafts (technician-entered F.06 columns per breakdown).
    // v9 → v10: adds recess_state (operator-side production pause/resume session state).
    // v10 → v11: removes breakdown_f06_drafts (F.06 now submits straight to the backend).
    // v11 → v12: PM checklist simplified — pm_template_items/pm_wo_task_logs drop
    //            expectedResult/photo/measurement columns; pm_work_orders gains overallResult
    //            (machine-level PASS/FAIL). Per-item verdict is the "Is good?" status.
    // v12 → v13: no schema change. Plant codes went compact ('W-202' -> 'W202',
    //             see backend migrations/2026-08-29_plant_codes_compact.sql), and the
    //             cache holds `building` values in the OLD spelling. Filters compare
    //             building strings, so a mixed cache splits every per-plant list in
    //             two. Bumping the version drops the cache and the next refresh
    //             refills it in the new spelling.
    // v13 -> v14: work-order spare parts are picked from stock instead of typed.
    //             Adds spare_part_picks (offline copy of GET /spare-parts/picker)
    //             and breakdown_spares (parts picked on a repair, held until
    //             "Submit resolution"); pm_wo_spares gains partId/machineName.
    // Destructive migration is acceptable until Phase 3 production seed lands.
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun machineDao(): MachineDao
    abstract fun productionRunDao(): ProductionRunDao
    abstract fun userDao(): UserDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun breakdownFlagDao(): BreakdownFlagDao
    abstract fun pmTemplateDao(): PmTemplateDao
    abstract fun pmPlanDao(): PmPlanDao
    abstract fun pmWorkOrderDao(): PmWorkOrderDao
    abstract fun pmWoTaskLogDao(): PmWoTaskLogDao
    abstract fun pmWoSpareDao(): PmWoSpareDao
    abstract fun sparePartPickDao(): SparePartPickDao
    abstract fun breakdownSpareDao(): BreakdownSpareDao
    abstract fun recessStateDao(): RecessStateDao
}
