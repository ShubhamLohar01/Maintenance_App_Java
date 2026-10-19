package com.candorfoods.factoryops.domain.model.w202

/** These readings exist for W-202 only; the backend rejects any other plant. */
const val W202_PLANT = "W202"

/**
 * The two W-202 electricity meters. Both record the same six numbers, so they share one
 * screen, repository and endpoint — [slug] is what tells them apart on the wire and picks
 * the table (`mt_w202_meterreading` / `mt_w202solar_reading`).
 *
 * The differences are only these two flags:
 *  - MAIN's power factor is entered (0.98 standing value, overridable); SOLAR's is derived
 *    server-side as KWH ÷ KVAH consumption and is read-only on screen.
 *  - MAIN's openings carry forward from the previous reading day's closings, so the
 *    technician only takes the final reading. SOLAR's are entered each time.
 */
enum class W202MeterKind(val slug: String, val title: String) {
    MAIN("main", "Main Meter Reading"),
    SOLAR("solar", "Solar Energy Reading");

    val powerFactorEditable: Boolean get() = this == MAIN
    val carriesForward: Boolean get() = this == MAIN

    companion object {
        fun fromSlug(slug: String?): W202MeterKind =
            entries.firstOrNull { it.slug == slug } ?: MAIN
    }
}

/**
 * One day's reading for one meter — the row keyed on plant + date + kind. Both
 * consumptions are computed server-side, as is SOLAR's power factor; the app never sends
 * them, it only displays what comes back.
 */
data class W202MeterReading(
    val id: Int? = null,
    val kind: W202MeterKind,
    val plant: String = W202_PLANT,
    val readingDate: String,
    val initialKwh: Double? = null,
    val finalKwh: Double? = null,
    val kwhConsumption: Double? = null,   // server-computed: final - initial
    val initialKvah: Double? = null,
    val finalKvah: Double? = null,
    val kvahConsumption: Double? = null,  // server-computed: final - initial
    /** Entered for MAIN; server-derived and read-only for SOLAR. */
    val powerFactor: Double? = null,
    val remark: String? = null,
)

/**
 * Opening values for a fresh day's form (GET /w202-readings/prefill). For MAIN the two
 * Initial fields are carried from the previous reading day's Finals; for SOLAR they come
 * back null. Null openings are also what the very first entry sees, with no earlier day.
 */
data class W202Prefill(
    val readingDate: String,
    val sourceDate: String?,        // day the openings came from; null if none
    val initialKwh: Double?,
    val initialKvah: Double?,
    val powerFactor: Double?,       // MAIN's standing 0.98; null for SOLAR (derived)
    val alreadySaved: Boolean,      // this date already has a (possibly partial) saved row
)
