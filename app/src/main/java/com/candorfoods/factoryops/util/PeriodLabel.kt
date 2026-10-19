package com.candorfoods.factoryops.util

import java.time.LocalDate

/**
 * The human label for the period a checklist record covers — "Sept 2026" for a
 * monthly sheet, "Jul–Sep 2026" for a quarterly one.
 *
 * Exists because an AUTO-GENERATED document has no signature date: pm_checklist_sync
 * writes checklist_date="" on purpose, since nobody has signed that sheet. The
 * register used to render that empty string and show "Date · #19" with a hole in
 * it. The period the document actually covers has always been on the wire as
 * checklist_date_from / checklist_date_to; this turns it into something readable.
 *
 * Pure and Android-free so it is unit-testable, matching ReadingSheets' precedent.
 */
object PeriodLabel {

    /** Three-letter months as the printed forms write them — note "Sept", not Java's "Sep". */
    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sept", "Oct", "Nov", "Dec",
    )

    /**
     * [from] and [to] are ISO yyyy-MM-dd, either possibly blank. [fallback] is shown
     * when neither parses — pass the record's own checklistDate, which is populated
     * on a human-submitted sheet even though an auto-generated one leaves it empty.
     */
    fun of(from: String?, to: String?, fallback: String? = null): String {
        val start = parse(from)
        val end = parse(to)

        return when {
            start != null && end != null -> range(start, end)
            start != null -> month(start)
            end != null -> month(end)
            !fallback.isNullOrBlank() -> parse(fallback)?.let(::month) ?: fallback.trim()
            else -> "—"
        }
    }

    private fun parse(iso: String?): LocalDate? {
        val raw = iso?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    private fun month(d: LocalDate) = "${MONTHS[d.monthValue - 1]} ${d.year}"

    private fun range(start: LocalDate, end: LocalDate): String = when {
        // One month — a monthly sheet, or a span that happens not to cross a boundary.
        start.year == end.year && start.monthValue == end.monthValue -> month(start)
        // Same year: name the year once, so "Jul–Sep 2026" rather than repeating it.
        start.year == end.year ->
            "${MONTHS[start.monthValue - 1]}–${MONTHS[end.monthValue - 1]} ${end.year}"
        // A quarter can straddle December, so both years have to be spelled out.
        else -> "${month(start)}–${month(end)}"
    }
}
