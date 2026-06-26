package com.candorfoods.factoryops.presentation.qc

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Criticality
import com.candorfoods.factoryops.domain.model.PmWorkOrder

/**
 * A row in the QC inbox tabs. Mixed: breakdowns and PM work orders show side
 * by side, distinguished by a leading TYPE chip on the card.
 *
 * The unique [id] is the underlying flag id or work-order id; both are
 * URL-safe strings so they can be used directly as nav-arg values.
 *
 * Sort key [sortMillis] — higher = more recent. For pending tabs we use the
 * "needs-attention" timestamp (resolvedAt for breakdowns, submittedAt for
 * PMs).
 */
sealed class QcInboxRow {
    abstract val id: String
    abstract val machineCode: String
    abstract val machineName: String
    abstract val machineLocation: String
    abstract val criticality: Criticality
    abstract val sortMillis: Long

    data class Breakdown(
        val flag: BreakdownFlag,
        override val machineCode: String,
        override val machineName: String,
        override val machineLocation: String,
        override val criticality: Criticality,
    ) : QcInboxRow() {
        override val id: String get() = flag.id
        override val sortMillis: Long
            get() = flag.qcDecidedAt
                ?: flag.qcAcknowledgedAt
                ?: flag.resolvedAt
                ?: flag.raisedAt
    }

    data class Pm(
        val wo: PmWorkOrder,
        override val machineCode: String,
        override val machineName: String,
        override val machineLocation: String,
        override val criticality: Criticality,
    ) : QcInboxRow() {
        override val id: String get() = wo.id
        override val sortMillis: Long
            get() = wo.qcDecidedAt
                ?: wo.qcAcknowledgedAt
                ?: wo.submittedAt
                ?: wo.generatedAt
    }
}
