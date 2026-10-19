package com.candorfoods.factoryops.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.util.Constants
import com.candorfoods.factoryops.domain.usecase.pm.PmDueNoticePolicy
import com.candorfoods.factoryops.util.DateTimeFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Hourly PM sync + notify.
 *
 * Work-order GENERATION moved to the backend (GET /pm/work-orders runs the due-plan sweep
 * server-side), so this worker no longer creates WOs — a local generator would mint
 * duplicate ids alongside the server's. Instead it:
 *   1. pulls the signed-in user's slice of mt_pm_work_order into Room (role-scoped), so
 *      reminders below fire from server truth even when no screen is open;
 *   2. recomputes overdue escalation tiers (they colour the PM cards; they no longer
 *      notify on their own — the tier resets on every refresh, so that fired hourly);
 *   3. reminds the technician ONCE A DAY, from shift start, about work still pending and
 *      due today — their only PM notification, mirroring the Today tab;
 *   4. runs the role-based handoff reminders (P2–P5) through the shared dispatcher.
 */
@HiltWorker
class PmSchedulerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val woRepo: PmWorkOrderRepository,
    private val computeTier: com.candorfoods.factoryops.domain.usecase.pm.ComputePmEscalationTierUseCase,
    private val notificationHelper: com.candorfoods.factoryops.notification.NotificationHelper,
    private val authRepository: com.candorfoods.factoryops.domain.repository.AuthRepository,
    private val reminderEvaluator: com.candorfoods.factoryops.domain.usecase.reminder.ReminderEvaluator,
    private val reminderDispatcher: com.candorfoods.factoryops.notification.ReminderDispatcher,
    private val reminderPreferences: com.candorfoods.factoryops.data.local.PmReminderPreferences,
    private val pendingDueWork: com.candorfoods.factoryops.domain.usecase.pm.PendingPmDueWorkUseCase,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val user = authRepository.getCurrentUser().first() ?: return Result.success()

            // 1. Role-scoped pull of mt_pm_work_order (best-effort; offline keeps local cache).
            runCatching {
                when (user.role) {
                    UserRole.TECHNICIAN -> woRepo.refreshForTechnician(user.id)
                    UserRole.SUPERVISOR, UserRole.HEAD -> woRepo.refreshByStatuses(OPEN_STATUSES)
                    UserRole.QC_HEAD, UserRole.QC_MEMBER ->
                        woRepo.refreshByStatuses(listOf(PmWoStatus.PENDING_QC))
                    else -> Result.success()
                }
            }

            // 2. Keep escalation tiers current — they colour the PM cards on screen.
            //    Tier changes no longer notify on their own: the tier is device-computed
            //    and reset by every server refresh, so "notify when the tier rises" fired
            //    again every hour for the same work order. Step 3 owns the reminding.
            val openWos = woRepo.getAllNonClosed()
            val now = System.currentTimeMillis()
            for (wo in openWos) {
                val newTier = computeTier(wo.scheduledDate, now)
                if (newTier != wo.escalationTier) woRepo.bumpTier(wo.id, newTier)
            }

            // 3. The technician's ONLY PM notification: one notice a day, from shift start,
            //    covering just the work that is genuinely still theirs to do. Submitted and
            //    in-QC tasks have left their hands, and nagging about those is what buried
            //    the real ones. It mirrors the Today tab exactly — same filter, same work.
            if (user.role == UserRole.TECHNICIAN) {
                val pending = pendingDueWork(openWos, user.id, now)
                if (PmDueNoticePolicy.shouldNotify(pending.size, reminderPreferences.lastDueNoticeDate(), now)) {
                    val oldest = pending.first()
                    notificationHelper.showPmDueSummaryNotification(
                        count = pending.size,
                        leadTaskName = oldest.templateName,
                        maxDaysOverdue = ((now - oldest.scheduledDate).coerceAtLeast(0L) / DAY_MS),
                    )
                    reminderPreferences.markDueNoticeShown(DateTimeFormatter.isoDate(now))
                }
            }

            // 4. Role-based PM handoff reminders (P2-P5) — supervisor/QC/head hand-offs.
            //    The technician's own overdue nudge is step 3; the evaluator emits no P1.
            if (Constants.NOTIFICATIONS_SOURCE == Constants.NOTIFICATIONS_SOURCE_LOCAL_POLL) {
                val reminders = reminderEvaluator(
                    user = user,
                    flags = emptyList(),
                    workOrders = openWos,
                    now = now,
                )
                reminderDispatcher.dispatch(reminders)
            }
        } catch (_: Exception) {
            // Never let a periodic tick crash-loop; the next hourly run retries.
        }
        return Result.success()
    }

    companion object {
        private const val DAY_MS = 86_400_000L

        /** Everything a supervisor/head tracks — all statuses still in flight. */
        private val OPEN_STATUSES = listOf(
            PmWoStatus.NOTIFIED, PmWoStatus.ACKNOWLEDGED, PmWoStatus.IN_PROGRESS,
            PmWoStatus.SUBMITTED, PmWoStatus.PENDING_QC,
        )

        fun schedulePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<PmSchedulerWorker>(
                Constants.PM_SCHEDULER_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .addTag(Constants.WORKER_TAG_PM_SCHEDULER)
                .build()
            workManager.enqueueUniquePeriodicWork(
                Constants.WORKER_TAG_PM_SCHEDULER,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Triggers an immediate sync at app startup so PM data appears without an hour wait. */
        fun enqueueImmediately(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<PmSchedulerWorker>()
                .addTag(Constants.WORKER_TAG_PM_SCHEDULER)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${Constants.WORKER_TAG_PM_SCHEDULER}_immediate",
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
