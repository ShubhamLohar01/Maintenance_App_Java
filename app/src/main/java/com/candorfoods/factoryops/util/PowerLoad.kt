package com.candorfoods.factoryops.util

/**
 * Parses the free-text `power_load` column from mt_asset_list into kilowatts.
 *
 * The new asset register stores power as raw text (e.g. "120Watt", "2.2 kW",
 * "1.5 HP") and only A-185 rows carry it — W-202 rows are blank. This returns
 * null whenever the value is absent, a placeholder ("NA", "-"), unit-less, or
 * otherwise unrecognizable; callers then fall back to showing the raw text.
 */
object PowerLoad {

    private val NUMBER = Regex("""\d+(?:\.\d+)?""")
    private const val HP_TO_KW = 0.7457

    /** Best-effort kW from raw power_load text, or null if not inferable. */
    fun parseKw(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim().lowercase()
        val value = NUMBER.find(text)?.value?.toDoubleOrNull() ?: return null
        return when {
            "kw" in text -> value                       // already kilowatts
            "hp" in text -> value * HP_TO_KW            // horsepower -> kW
            "watt" in text || text.endsWith("w") -> value / 1000.0  // watts -> kW
            else -> null                                // unit-less: don't guess
        }
    }
}
