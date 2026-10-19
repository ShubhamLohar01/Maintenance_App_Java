package com.candorfoods.factoryops.domain.model.a185

/**
 * One day's A-185 HT Panel / Cold Room / UV meter readings — the `mt_a185_readings`
 * row (unique per plant + date). The three consumptions and the HT power factor are
 * computed server-side; the app never sends them, it only displays what comes back.
 */
data class A185Reading(
    val id: Int? = null,
    val plant: String = A185_PLANT,
    val readingDate: String,
    val htInitial: Double? = null,
    val htFinal: Double? = null,
    val htPowerFactor: Double? = null,   // server-set, fixed 0.99 (read-only)
    val htConsumption: Double? = null,   // server-computed: ht_final - ht_initial
    val coldInitial: Double? = null,
    val coldFinal: Double? = null,
    val coldConsumption: Double? = null, // server-computed
    val uvInitial: Double? = null,
    val uvFinal: Double? = null,
    val uvConsumption: Double? = null,   // server-computed
    val remark: String? = null,
)

/**
 * Opening values for a fresh day's form (GET /a185-readings/prefill): the three
 * Initial fields carried from the previous reading day's Finals, plus the fixed PF.
 * All openings are null on the very first entry (no earlier day).
 */
data class A185Prefill(
    val readingDate: String,
    val sourceDate: String?,        // day the openings came from; null if none
    val htPowerFactor: Double,      // 0.99
    val htInitial: Double?,
    val coldInitial: Double?,
    val uvInitial: Double?,
    val alreadySaved: Boolean,      // this date already has a (possibly partial) saved row
)

/** These readings exist for A-185 only; the backend rejects any other plant. */
const val A185_PLANT = "A185"
