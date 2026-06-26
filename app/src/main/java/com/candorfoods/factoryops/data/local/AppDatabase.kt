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
        BreakdownF06DraftEntity::class
    ],
    version = 9,
    exportSchema = false
    // v3 → v4 (Phase 2C): adds 6 PM tables and `machineType` column on `machines`.
    // v4 → v5: adds `building` column on `machines` (W-202 / A-185).
    // v5 → v6: adds nullable `category` column on `machines`.
    // v6 → v7: production_runs gains assetName/ratedKw/serverRunId snapshot columns
    //          for the asset-register energy flow (offline kWh + server-run-id stop).
    // v7 → v8: breakdown_flags gains assetName/building snapshot columns so asset-
    //          register breakdowns can display human name + building without a
    //          legacy `machines`-table row.
    // v8 → v9: adds breakdown_f06_drafts (technician-entered F.06 columns per breakdown).
    // Destructive migration is acceptable until Phase 3 production seed lands.
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun machineDao(): MachineDao
    abstract fun productionRunDao(): ProductionRunDao
    abstract fun userDao(): UserDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun breakdownFlagDao(): BreakdownFlagDao
    abstract fun breakdownF06DraftDao(): BreakdownF06DraftDao
    abstract fun pmTemplateDao(): PmTemplateDao
    abstract fun pmPlanDao(): PmPlanDao
    abstract fun pmWorkOrderDao(): PmWorkOrderDao
    abstract fun pmWoTaskLogDao(): PmWoTaskLogDao
    abstract fun pmWoSpareDao(): PmWoSpareDao
}
