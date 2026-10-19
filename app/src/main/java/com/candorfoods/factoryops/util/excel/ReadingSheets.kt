package com.candorfoods.factoryops.util.excel

import com.candorfoods.factoryops.domain.model.a185.A185Reading
import com.candorfoods.factoryops.domain.model.reports.FloorReadingReportRow
import com.candorfoods.factoryops.domain.model.utilities.UtilityRecord
import com.candorfoods.factoryops.domain.model.w202.W202MeterReading
import com.candorfoods.factoryops.domain.model.w202.isAcFloorKey

/**
 * Turns each Daily Reading section's records into a [Sheet].
 *
 * Kept away from the ViewModels on purpose: these are pure list-to-table mappings, so they
 * are unit-testable without Android, and adding a column never means touching a screen.
 *
 * Every sheet leads with the date and ends with the free-text remark, so the exports line
 * up when someone pastes several into one workbook.
 */
object ReadingSheets {

    /** Diesel / Gas / Water / Electricity — the utility record's own numeric + text keys. */
    fun utility(title: String, records: List<UtilityRecord>): Sheet {
        // The columns are whatever keys the records carry: the utility schema is
        // server-driven, so hardcoding them here would silently drop a new field.
        val numberKeys = records.flatMap { it.numbers.keys }.distinct().sorted()
        val textKeys = records.flatMap { it.texts.keys }.distinct().sorted()
        return Sheet(
            name = title,
            headers = listOf("Date", "Plant") + numberKeys.map(::humanise) + textKeys.map(::humanise),
            rows = records.map { r ->
                buildList {
                    add(Cell.date(r.readingDate))
                    add(Cell.of(r.plant))
                    numberKeys.forEach { add(Cell.of(r.numbers[it])) }
                    textKeys.forEach { add(Cell.of(r.texts[it])) }
                }
            },
        )
    }

    /** Main meter / Solar — kWh and kVAh opening/closing plus power factor. */
    fun w202Meter(title: String, records: List<W202MeterReading>): Sheet = Sheet(
        name = title,
        headers = listOf(
            "Date", "Plant", "kWh initial", "kWh final", "kWh consumption",
            "kVAh initial", "kVAh final", "kVAh consumption", "Power factor", "Remark",
        ),
        rows = records.map { r ->
            listOf(
                Cell.date(r.readingDate),
                Cell.of(r.plant),
                Cell.of(r.initialKwh),
                Cell.of(r.finalKwh),
                Cell.of(r.kwhConsumption),
                Cell.of(r.initialKvah),
                Cell.of(r.finalKvah),
                Cell.of(r.kvahConsumption),
                Cell.of(r.powerFactor),
                Cell.of(r.remark),
            )
        },
    )

    /** HT Panel / Cold Room / UV — three meters on one row per day. */
    fun a185(title: String, records: List<A185Reading>): Sheet = Sheet(
        name = title,
        headers = listOf(
            "Date", "Plant",
            "HT initial", "HT final", "HT consumption", "HT power factor",
            "Cold initial", "Cold final", "Cold consumption",
            "UV initial", "UV final", "UV consumption", "Remark",
        ),
        rows = records.map { r ->
            listOf(
                Cell.date(r.readingDate),
                Cell.of(r.plant),
                Cell.of(r.htInitial),
                Cell.of(r.htFinal),
                Cell.of(r.htConsumption),
                Cell.of(r.htPowerFactor),
                Cell.of(r.coldInitial),
                Cell.of(r.coldFinal),
                Cell.of(r.coldConsumption),
                Cell.of(r.uvInitial),
                Cell.of(r.uvFinal),
                Cell.of(r.uvConsumption),
                Cell.of(r.remark),
            )
        },
    )

    /**
     * Floor meter readings, actual against system-computed.
     *
     * The same table backs floor meters and AC units, so [acOnly] picks which half of it
     * this export is about — see isAcFloorKey. Pass null for both halves in one sheet,
     * which is what the merged Electric Consumption screen downloads; AC rows keep their
     * "AC · " prefix there so the two remain tellable apart.
     */
    fun floorReadings(
        title: String,
        rows: List<FloorReadingReportRow>,
        acOnly: Boolean?,
    ): Sheet {
        val wanted = if (acOnly == null) rows else rows.filter { isAcFloorKey(it.floor) == acOnly }
        return Sheet(
            name = title,
            headers = listOf(
                "Date", "Building",
                when (acOnly) { true -> "AC unit"; false -> "Floor"; null -> "Floor / AC unit" },
                "Meter reading (actual)", "System (kWh)", "Difference",
            ),
            rows = wanted
                .sortedWith(compareByDescending<FloorReadingReportRow> { it.readingDate }.thenBy { it.floor })
                .map { r ->
                    val difference = r.meterReading?.let { m -> r.dailyKwh?.let { s -> m - s } }
                    listOf(
                        Cell.date(r.readingDate),
                        Cell.of(r.building),
                        Cell.of(if (acOnly == true) r.floor.removePrefix(AC_PREFIX) else r.floor),
                        Cell.of(r.meterReading),
                        Cell.of(r.dailyKwh),
                        Cell.of(difference),
                    )
                },
        )
    }

    private const val AC_PREFIX = "AC · "

    /**
     * "qty_litres" / "qtyLitres" -> "Qty litres" — the utility keys are raw field names.
     *
     * Sentence case, not Title Case, to match the hand-written headers on the other
     * sheets ("Power factor", "kWh initial") so a workbook of several reads consistently.
     */
    internal fun humanise(key: String): String {
        val spaced = key.replace('_', ' ')
            .replace(Regex("([a-z0-9])([A-Z])")) { m -> m.groupValues[1] + " " + m.groupValues[2].lowercase() }
            .trim()
        return spaced.replaceFirstChar { it.uppercase() }
    }
}
