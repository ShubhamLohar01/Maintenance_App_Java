package com.candorfoods.factoryops.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.candorfoods.factoryops.data.local.AppDatabase
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.notification.NotificationHelper
import com.candorfoods.factoryops.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

@HiltWorker
class BreakdownPollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val authPreferences: AuthPreferences,
    private val breakdownRepository: BreakdownRepository,
    private val db: AppDatabase,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val user = authRepository.getCurrentUser().firstOrNull()
            if (user == null || user.role != UserRole.TECHNICIAN) {
                // Not a technician — don't poll, but stay enrolled so the next login picks up.
                reschedule(applicationContext)
                return Result.success()
            }

            val lastSeen = authPreferences.getBreakdownLastSeenAt()
                .takeIf { it > 0 }
                ?: (System.currentTimeMillis() - Constants.BREAKDOWN_POLL_INTERVAL_MINUTES * 60_000L)

            val newFlags = breakdownRepository.getNewOpenFlagsSince(user.plantId, lastSeen)

            for (flag in newFlags) {
                val machineName = db.machineDao().getMachineByIdOnce(flag.machineId)?.name ?: "Machine ${flag.machineId}"
                notificationHelper.showBreakdownNotification(
                    flagId = flag.id,
                    machineName = machineName,
                    severity = flag.severity.name,
                    descriptionPreview = flag.description
                )
            }

            authPreferences.saveBreakdownLastSeenAt(System.currentTimeMillis())
        } catch (_: Exception) {
            // Swallow — never let a polling tick crash the chain.
        } finally {
            reschedule(applicationContext)
        }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BreakdownPollWorker>()
                .setInitialDelay(Constants.BREAKDOWN_POLL_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .addTag(Constants.WORKER_TAG_BREAKDOWN_POLL)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                Constants.WORKER_TAG_BREAKDOWN_POLL,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueImmediately(context: Context) {
            val request = OneTimeWorkRequestBuilder<BreakdownPollWorker>()
                .addTag(Constants.WORKER_TAG_BREAKDOWN_POLL)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                Constants.WORKER_TAG_BREAKDOWN_POLL,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun reschedule(context: Context) = enqueue(context)
    }
}
