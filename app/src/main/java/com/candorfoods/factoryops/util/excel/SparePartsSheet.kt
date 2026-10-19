package com.candorfoods.factoryops.util.excel

import com.candorfoods.factoryops.presentation.spareparts.SparePartSearchResult

/**
 * Turns the Spare Parts landing screen's rows into a [Sheet].
 *
 * Kept out of the ViewModel for the same reason as [ReadingSheets]: a pure list-to-table
 * mapping is unit-testable without Android, and adding a column never means touching a
 * screen.
 *
 * One row per PART, not per machine — a machine's parts are what someone counting stock
 * needs, and a nested layout cannot be sorted or filtered in Excel.
 */
object SparePartsSheet {

    /**
     * [rows] is whatever the screen is currently showing, already filtered by warehouse,
     * search text and the low-stock toggle. Exporting the filtered view rather than the
     * whole register is deliberate: the user narrowed the list on purpose, and re-deleting
     * rows in Excel to get back to it would be busywork.
     */
    fun stock(rows: List<SparePartSearchResult>, title: String = "Spare parts"): Sheet = Sheet(
        name = title,
        headers = listOf(
            "Warehouse", "Machine", "Asset ID", "Part", "Unit",
            "Quantity", "Min quantity", "Low stock",
        ),
        rows = rows.map { r ->
            listOf(
                // Displayed the way the factory labels its buildings, not the compact
                // storage code — this sheet is read by people, not re-imported.
                Cell.of(warehouseLabel(r.warehouse)),
                Cell.of(r.machineName),
                Cell.of(r.assetId),
                Cell.of(r.part.partName),
                Cell.of(r.part.unit),
                // Num, not text, so Excel can total a column without retyping it.
                Cell.of(r.part.quantity.toDouble()),
                Cell.of(r.part.minQuantity.toDouble()),
                Cell.of(if (r.part.lowStock) "LOW" else ""),
            )
        },
    )

    /** 'W202' -> 'W-202'. Unknown or missing values pass through unchanged. */
    private fun warehouseLabel(code: String?): String = when (code) {
        "W202" -> "W-202"
        "A185" -> "A-185"
        else -> code.orEmpty()
    }
}
