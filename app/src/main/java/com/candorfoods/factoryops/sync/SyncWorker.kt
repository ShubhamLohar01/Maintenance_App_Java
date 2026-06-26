package com.candorfoods.factoryops.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import com.candorfoods.factoryops.util.Constants
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val energyRepository: EnergyRepository,
    private val breakdownRepository: BreakdownRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        energyRepository.syncPendingRuns()
        return Result.success()
    }

    companion object {

        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(Constants.WORKER_TAG_SYNC)
                .build()
            workManager.enqueueUniqueWork(
                Constants.WORKER_TAG_SYNC,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun schedulePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                Constants.SYNC_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .addTag(Constants.WORKER_TAG_SYNC)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "${Constants.WORKER_TAG_SYNC}_periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
