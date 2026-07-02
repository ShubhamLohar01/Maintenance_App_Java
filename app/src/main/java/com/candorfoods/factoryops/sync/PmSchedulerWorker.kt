package com.candorfoods.factoryops.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.candorfoods.factoryops.data.remote.mock.MockSeedData
import com.candorfoods.factoryops.domain.model.PmPlan
import com.candorfoods.factoryops.domain.model.PmTaskLogStatus
import com.candorfoods.factoryops.domain.model.PmTriggerType
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.PmWoTaskLog
import com.candorfoods.factoryops.domain.model.PmWorkOrder
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.PmPlanRepository
import com.candorfoods.factoryops.domain.repository.PmTemplateRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * PM scheduler — Phase 2C-1.
 *
 * Runs hourly. For each active plan:
 *   - seeds mock templates/plans on first run if DB empty
 *   - computes next_due_at (TIME-based for now; USAGE-based deferred until running-hours integration)
 *   - if due within `PM_GENERATION_LEAD_DAYS` and no DRAFT/NOTIFIED/etc WO exists, generates a WO
 *     with `status = NOTIFIED` and snapshotted task logs.
 *
 * Escalation tier compute (2C-4) and local-notification firing land in subsequent sub-sessions.
 */
@HiltWorker
class PmSchedulerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val templateRepo: PmTemplateRepository,
    private val planRepo: PmPlanRepository,
    private val woRepo: PmWorkOrderRepository,
    private val machineRepo: MachineRepository,
    private val computeTier: com.candorfoods.factoryops.domain.usecase.pm.ComputePmEscalationTierUseCase,
    private val notificationHelper: com.candorfoods.factoryops.notification.NotificationHelper,
    private val authRepository: com.candorfoods.factoryops.domain.repository.AuthRepository,
    private val reminderEvaluator: com.candorfoods.factoryops.domain.usecase.reminder.ReminderEvaluator,
    private val reminderDispatcher: com.candorfoods.factoryops.notification.ReminderDispatcher,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // 1. First-run seed (idempotent — guarded by row count internally)
        templateRepo.seedMockIfEmpty(MockSeedData.pmTemplatesWithItems)
        planRepo.seedMockIfEmpty(MockSeedData.pmPlans)

        // 2. Walk active plans
        val now = System.currentTimeMillis()
        val plans = planRepo.getActivePlans()
        for (plan in plans) {
            val nextDue = computeNextDueAt(plan)
            val daysUntilDue = (nextDue - now) / DAY_MS

            // Skip plans that are too far out
            if (daysUntilDue > Constants.PM_GENERATION_LEAD_DAYS) continue

            // Skip plans that already have an open WO
            val openWos = woRepo.findOpenForPlan(plan.id)
            if (openWos.isNotEmpty()) continue

            // Generate
            val template = templateRepo.getTemplateById(plan.templateId) ?: continue
            val machine = machineRepo.observeAllMachines().first().firstOrNull { it.id == plan.machineId } ?: continue

            val woId = "wo-${plan.id}-${now}"
            val taskLogs = template.items.map { item ->
                PmWoTaskLog(
                    id = "log-${woId}-${item.id}",
                    woId = woId,
                    templateItemId = item.id,
                    orderIndex = item.orderIndex,
                    title = item.title,
                    description = item.description,
                    expectedResult = item.expectedResult,
                    requiresPhoto = item.requiresPhoto,
                    requiresMeasurement = item.requiresMeasurement,
                    measurementUnit = item.measurementUnit,
                    measurementMin = item.measurementMin,
                    measurementMax = item.measurementMax,
                    status = PmTaskLogStatus.PENDING,
                    measurementValue = null,
                    photoLocalPath = null,
                    notes = null,
                    completedAt = null,
                    completedBy = null
                )
            }

            val wo = PmWorkOrder(
                id = woId,
                planId = plan.id,
                machineId = plan.machineId,
                templateId = template.id,
                templateName = template.name,
                machineType = machine.machineType,
                estimatedDurationMinutes = template.estimatedDurationMinutes,
                tradeType = template.defaultTradeType,
                scheduledDate = nextDue,
                generatedAt = now,
                status = PmWoStatus.NOTIFIED,
                assignedTechnicianId = plan.assignedTechnicianId,
                assignedTechnicianName = "Priya Sharma",   // TODO 2C-2: lookup user-by-id once user repo exposes it
                acknowledgedAt = null,
                startedAt = null,
                submittedAt = null,
                supervisorApprovedBy = null,
                supervisorApprovedByName = null,
                supervisorApprovedAt = null,
                supervisorRejectedAt = null,
                supervisorRejectionNotes = null,
                qcAcknowledgedBy = null,
                qcAcknowledgedByName = null,
                qcAcknowledgedAt = null,
                qcDecidedBy = null,
                qcDecidedByName = null,
                qcDecidedAt = null,
                qcDecisionNotes = null,
                qcAfterPhotoLocalPath = null,
                qcChecklistJson = null,
                closedAt = null,
                finalNotes = null,
                escalationTier = 0,
                taskLogs = taskLogs,
                spares = emptyList()
            )

            woRepo.insertGeneratedWo(wo)
            // 2C-2: fire local notification "PM scheduled on [machine]" — TODO once NotificationHelper extends.
        }

        // 3. Phase 2C-4 — escalation tier compute + notifications.
        val openWos = woRepo.getAllNonClosed()
        val nowForTier = System.currentTimeMillis()
        for (wo in openWos) {
            val newTier = computeTier(wo.scheduledDate, nowForTier)
            if (newTier == wo.escalationTier) continue

            woRepo.bumpTier(wo.id, newTier)

            // Notify only when tier increases (not on tier reset to 0).
            if (newTier > wo.escalationTier) {
                val machine = machineRepo.observeAllMachines().first()
                    .firstOrNull { it.id == wo.machineId } ?: continue
                val daysOverdue = ((nowForTier - wo.scheduledDate)
                    .coerceAtLeast(0L) / DAY_MS)
                notificationHelper.showPmEscalationNotification(
                    woId = wo.id,
                    machineName = machine.name,
                    templateName = wo.templateName,
                    tier = newTier,
                    daysOverdue = daysOverdue
                )
                // Q3A cascade: T1 = tech only; T2+ = tech + supervisor. In the mock-only
                // stage, the worker fires a local notification on whichever device is
                // running. There's no FCM fan-out yet — full cascade behaviour ships
                // with the Phase 3 backend.
            }
        }

        // 4. Role-based PM handoff reminders (P2-P5). P1 overdue escalation is handled
        //    by step 3 above; the evaluator deliberately does not emit P1.
        if (Constants.NOTIFICATIONS_SOURCE == Constants.NOTIFICATIONS_SOURCE_LOCAL_POLL) {
            val user = authRepository.getCurrentUser().first()
            if (user != null) {
                val reminders = reminderEvaluator(
                    user = user,
                    flags = emptyList(),
                    workOrders = openWos,
                    now = System.currentTimeMillis(),
                )
                reminderDispatcher.dispatch(reminders)
            }
        }

        return Result.success()
    }

    private fun computeNextDueAt(plan: PmPlan): Long = when (plan.triggerType) {
        PmTriggerType.TIME -> {
            val base = plan.lastCompletedAt ?: plan.createdAt
            base + plan.triggerInterval * DAY_MS
        }
        PmTriggerType.USAGE -> {
            // TODO 2C-2: integrate cumulative-running-hours tracker. For now treat as 30-day fallback.
            val base = plan.lastCompletedAt ?: plan.createdAt
            base + 30L * DAY_MS
        }
    }

    companion object {
        private const val DAY_MS = 86_400_000L

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

        /** Triggers an immediate run, used at app startup so 2C-1 demo data appears without an hour wait. */
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
