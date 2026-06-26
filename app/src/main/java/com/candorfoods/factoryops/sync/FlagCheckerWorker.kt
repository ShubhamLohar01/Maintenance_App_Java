package com.candorfoods.factoryops.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.notification.NotificationHelper
import com.candorfoods.factoryops.util.Constants
import java.util.concurrent.TimeUnit

@HiltWorker
class FlagCheckerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: AppDatabase,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val activeRuns = db.productionRunDao().getAllActiveRuns()
        for (run in activeRuns) {
            val timeSinceExpectedStop = now - run.scheduledEndAt
            val thresholdMs = Constants.MISSED_STOP_THRESHOLD_MINUTES * 60_000L
            if (timeSinceExpectedStop > thresholdMs && run.flagStatus != "MISSED_STOP") {
                db.productionRunDao().updateFlagStatus(run.id, "MISSED_STOP", now)
                val machineName = db.machineDao().getMachineByIdOnce(run.machineId)?.name ?: run.machineId
                notificationHelper.showMissedStopNotification(
                    machineId = run.machineId,
                    machineName = machineName,
                    runId = run.id
                )
                // Machine stays RUNNING on a missed stop — the run's MISSED_STOP flag and
                // the push notification surface the alert; a missed stop is not a QC matter.
            }
        }
        return Result.success()
    }

    companion object {
        fun schedulePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<FlagCheckerWorker>(
                Constants.SYNC_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .addTag(Constants.WORKER_TAG_FLAG_CHECKER)
                .build()
            workManager.enqueueUniquePeriodicWork(
                Constants.WORKER_TAG_FLAG_CHECKER,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
