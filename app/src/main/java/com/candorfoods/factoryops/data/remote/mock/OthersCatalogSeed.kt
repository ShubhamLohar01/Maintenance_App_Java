package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.dto.MtMachineDto

/**
 * In-memory seed of the OTHERS slice of the asset register (everything whose
 * sub_category is not "Production Equipment" — infrastructure, fixtures, IT,
 * furniture, …). Mock-only; the Admin "+" button mutates this list and it is
 * lost on app kill. Mirrors the new `mt_asset_list` shape: building + asset_id,
 * free-text sub_category/sub_location, and raw power_load text (populated on
 * A-185 rows only — W-202 rows leave it null, as in production data).
 */
object OthersCatalogSeed {
    val entries: MutableList<MtMachineDto> = mutableListOf(
        // ---- W-202 (no power_load in source data) ----
        MtMachineDto("W202-0101", "W-202", "AC", "HVAC", "Service Floor"),
        MtMachineDto("W202-0102", "W-202", "Split AC", "HVAC", "1st Floor"),
        MtMachineDto("W202-0103", "W-202", "Tube light", "Lighting", "Lower Basement"),
        MtMachineDto("W202-0104", "W-202", "Exhaust fan", "Ventilation", "2nd Floor"),
        MtMachineDto("W202-0105", "W-202", "Fly catcher", "Pest Control", "1st Floor"),
        MtMachineDto("W202-0106", "W-202", "Deep freezer", "Refrigeration", "Lower Basement"),
        MtMachineDto("W202-0107", "W-202", "Drain pump", "Plumbing", "Upper Basement"),
        MtMachineDto("W202-0108", "W-202", "CCTV camera", "Security", "Service Floor"),
        MtMachineDto("W202-0109", "W-202", "Office chair", "Furniture", "2nd Floor"),
        MtMachineDto("W202-0110", "W-202", "Stabilizer", "Power", "Service Floor"),

        // ---- A-185 (power_load present as raw text) ----
        MtMachineDto("A185-0101", "A-185", "AC indoor unit", "HVAC", "2nd Floor", powerLoad = "1200Watt"),
        MtMachineDto("A185-0102", "A-185", "LED flood light", "Lighting", "Terrace", powerLoad = "100Watt"),
        MtMachineDto("A185-0103", "A-185", "Baking oven", "Kitchen", "1st Floor", powerLoad = "9000W"),
        MtMachineDto("A185-0104", "A-185", "RO plant", "Water", "Terrace", powerLoad = "3 kW"),
        MtMachineDto("A185-0105", "A-185", "Drain pump", "Plumbing", "Lower Basement", powerLoad = "1.5 HP"),
        MtMachineDto("A185-0106", "A-185", "D.G. set", "Power", "Terrace", powerLoad = "125kVA"),
        MtMachineDto("A185-0107", "A-185", "Fly catcher", "Pest Control", "Service Floor", powerLoad = "30W"),
        MtMachineDto("A185-0108", "A-185", "CCTV camera", "Security", "1st Floor", powerLoad = "NA"),
    )

    /** Next sequential asset_id for Admin-added entries, prefixed by building (e.g. A185-9001). */
    private var nextAdminSeq: Int = 9000

    fun nextAdminId(building: String): String {
        nextAdminSeq += 1
        val prefix = building.replace("-", "").ifBlank { "NEW" }
        return "%s-%04d".format(prefix, nextAdminSeq)
    }
}
