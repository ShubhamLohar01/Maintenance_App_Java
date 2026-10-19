package com.candorfoods.factoryops.util.pdf

import com.candorfoods.factoryops.domain.model.reports.SummaryReport
import java.util.Locale

/**
 * Turns a [SummaryReport] into flat, render-ready sections, tables and cells.
 *
 * Split from [SummaryPdfRenderer] deliberately: this is where the formatting
 * decisions live (what is a column, how a null prints, what order rows come in),
 * and it holds no Android types, so it unit-tests on the JVM without a
 * PdfDocument. The renderer stays a draw-only class.
 */
data class PdfKpi(val label: String, val value: String)

data class PdfTable(
    val caption: String,
    val headers: List<String>,
    val weights: List<Float>,
    val rows: List<List<String>>,
)

data class PdfSection(val title: String, val lines: List<String>, val tables: List<PdfTable>)

data class SummaryPdfContent(
    val title: String,
    val subtitle: String,
    val kpis: List<PdfKpi>,
    val sections: List<PdfSection>,
    val notes: List<String>,
)

object SummaryPdfModel {

    fun from(r: SummaryReport): SummaryPdfContent = SummaryPdfContent(
        title = "Maintenance Summary Report",
        subtitle = "${r.periodFrom} to ${r.periodTo}  ·  ${r.plants.joinToString(" + ")}" +
            "  ·  by ${r.generatedBy}",
        kpis = listOf(
            PdfKpi("Readings logged", r.readings.totalEntries.toString()),
            PdfKpi("PM completed", r.pm.completedInPeriod.toString()),
            PdfKpi("PM overdue", r.pm.overdueTotal.toString()),
            PdfKpi("Breakdowns open", r.breakdowns.stillOpen.toString()),
            PdfKpi("Active users", "${r.people.activeUsers} of ${r.people.totalUsers}"),
        ),
        sections = listOf(readings(r), pm(r), breakdowns(r), people(r), assets(r)),
        notes = r.notes,
    )

    private fun readings(r: SummaryReport) = PdfSection(
        title = "1. Daily readings",
        lines = listOf("Window: ${r.readings.windowDays} days · ${r.readings.totalEntries} entries"),
        tables = listOf(
            PdfTable(
                caption = "By source",
                headers = listOf("Source", "Table", "Entries", "Days covered", "Days missing"),
                weights = listOf(2.2f, 2.6f, 1f, 1.2f, 1.2f),
                rows = r.readings.sources.map {
                    listOf(it.source, it.table, it.entries.toString(),
                           it.daysCovered.toString(), it.daysMissing.toString())
                },
            ),
        ),
    )

    private fun pm(r: SummaryReport) = PdfSection(
        title = "2. Preventive maintenance",
        lines = listOf(
            "Plans: ${r.pm.plansActive} active, ${r.pm.plansInactive} inactive",
            "Work orders: ${r.pm.workOrdersTotal} total · ${r.pm.completedInPeriod} " +
                "completed this period · ${r.pm.overdueTotal} overdue",
        ),
        tables = listOf(
            PdfTable("By status", listOf("Status", "Count"), listOf(3f, 1f),
                     r.pm.byStatus.map { listOf(it.label, it.count.toString()) }),
            PdfTable(
                "Most overdue",
                listOf("Work order", "Machine", "Plant", "Scheduled", "Days late", "Status"),
                listOf(1.4f, 2.4f, 1f, 1.3f, 1f, 1.4f),
                r.pm.overdueTop.map {
                    listOf(it.workOrderId, "${it.machineName} (${it.machineId})",
                           it.building ?: "—", it.scheduledDate, it.daysLate.toString(), it.status)
                },
            ),
        ),
    )

    private fun breakdowns(r: SummaryReport) = PdfSection(
        title = "3. Breakdowns",
        lines = listOf(
            "Raised this period: ${r.breakdowns.raisedInPeriod} · " +
                "closed: ${r.breakdowns.closedInPeriod} · still open: ${r.breakdowns.stillOpen}",
            "Mean time to resolve: ${hours(r.breakdowns.avgHoursToResolve)} · " +
                "to QC decision: ${hours(r.breakdowns.avgHoursToQc)}",
        ),
        tables = listOf(
            PdfTable("By status", listOf("Status", "Count"), listOf(3f, 1f),
                     r.breakdowns.byStatus.map { listOf(it.label, it.count.toString()) }),
            PdfTable("By severity", listOf("Severity", "Count"), listOf(3f, 1f),
                     r.breakdowns.bySeverity.map { listOf(it.label, it.count.toString()) }),
            PdfTable(
                "Still open",
                listOf("Machine", "Plant", "Severity", "Raised by", "Raised", "Days open", "Stage"),
                listOf(2.4f, 1f, 1.1f, 1.5f, 1.2f, 1f, 1.5f),
                r.breakdowns.openRows.map {
                    listOf("${it.machineName} (${it.machineId})", it.building ?: "—",
                           it.severity, it.raisedBy ?: "—", it.raisedAt,
                           it.daysOpen.toString(), it.status)
                },
            ),
        ),
    )

    private fun people(r: SummaryReport) = PdfSection(
        title = "4. App usage by person",
        lines = listOf("${r.people.activeUsers} of ${r.people.totalUsers} accounts active this period"),
        tables = listOf(
            // "Reads" carries readingsFiled, which the backend computes and the
            // Notes block already footnotes — leaving the column out meant the
            // PDF explained a caveat about a figure it never printed. Room was
            // made by folding First/Last into one "Active" range rather than
            // adding a 12th column; 11 is already wide for A4 portrait.
            PdfTable(
                "Activity",
                listOf("Name", "Role", "Runs", "Hours", "kWh", "BD raised",
                       "BD handled", "PM closed", "QC", "Reads", "Active"),
                listOf(2.2f, 1.3f, 0.8f, 0.9f, 1f, 1f, 1.1f, 1.1f, 0.7f, 0.8f, 2f),
                r.people.people.map {
                    listOf(it.name, it.role, it.runs.toString(),
                           num(it.runHours), num(it.runKwh),
                           it.breakdownsRaised.toString(), it.breakdownsHandled.toString(),
                           it.pmClosed.toString(), it.qcDecisions.toString(),
                           it.readingsFiled.toString(),
                           activeRange(it.firstSeen, it.lastSeen))
                },
            ),
        ),
    )

    private fun assets(r: SummaryReport) = PdfSection(
        title = "5. Asset register",
        lines = listOf(
            "${r.assets.total} assets · ${r.assets.addedInPeriod} added this period · " +
                "${r.assets.beforeTracking} added before tracking began",
        ),
        tables = listOf(
            PdfTable("By plant", listOf("Plant", "Count"), listOf(3f, 1f),
                     r.assets.byBuilding.map { listOf(it.label, it.count.toString()) }),
            PdfTable("By category", listOf("Category", "Count"), listOf(3f, 1f),
                     r.assets.byCategory.map { listOf(it.label, it.count.toString()) }),
            PdfTable(
                "Added this period",
                listOf("Asset", "Name", "Plant", "Category", "Added on"),
                listOf(1.3f, 2.4f, 1f, 1.6f, 1.2f),
                r.assets.addedRows.map {
                    listOf(it.assetId, it.assetName, it.building ?: "—",
                           it.category ?: "—", it.addedOn)
                },
            ),
        ),
    )

    private fun hours(v: Double?): String = if (v == null) "—" else "${num(v)} h"

    /**
     * One decimal place, always with a `.` separator.
     *
     * `"%.1f".format(v)` uses the device's default locale, so the same report
     * would print `283.7` in en-IN and `283,7` in de-DE. A controlled document
     * should not change shape with a phone setting, so the locale is pinned.
     */
    private fun num(v: Double): String = String.format(Locale.US, "%.1f", v)

    /** "25 Aug 2026 – 28 Aug 2026" collapsed to one cell; "—" when never seen. */
    private fun activeRange(first: String?, last: String?): String = when {
        first.isNullOrBlank() && last.isNullOrBlank() -> "—"
        first == last -> first ?: last ?: "—"
        else -> "${first ?: "?"} – ${last ?: "?"}"
    }
}
