package com.candorfoods.factoryops.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.usecase.reminder.ReminderEvaluator
import com.candorfoods.factoryops.notification.ReminderDispatcher
import com.candorfoods.factoryops.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

/**
 * Breakdown reminder poll. Runs every BREAKDOWN_POLL_INTERVAL_MINUTES for ANY logged-in
 * user (not just technicians) so escalation can reach supervisor/QC/head on their own
 * device. Refreshes open flags from the backend, then runs the ReminderEvaluator for the
 * current user's role and dispatches the result.
 */
@HiltWorker
class BreakdownPollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val breakdownRepository: BreakdownRepository,
    private val reminderEvaluator: ReminderEvaluator,
    private val reminderDispatcher: ReminderDispatcher,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            // Server push, when enabled, owns reminder delivery — stand down locally.
            if (Constants.NOTIFICATIONS_SOURCE != Constants.NOTIFICATIONS_SOURCE_LOCAL_POLL) {
                return Result.success()
            }
            val user = authRepository.getCurrentUser().firstOrNull()
            if (user == null) {
                return Result.success()
            }

            // Pull latest open breakdowns into Room (best-effort; offline-safe).
            runCatching { breakdownRepository.refreshOpenFlags("both") }

            val flags = breakdownRepository.observeAllFlags().first()
            val reminders = reminderEvaluator(
                user = user,
                flags = flags,
                workOrders = emptyList(),
                now = System.currentTimeMillis(),
            )
            reminderDispatcher.dispatch(reminders)
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
