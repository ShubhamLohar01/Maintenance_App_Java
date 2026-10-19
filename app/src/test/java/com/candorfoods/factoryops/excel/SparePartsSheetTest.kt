package com.candorfoods.factoryops.excel

import com.candorfoods.factoryops.domain.model.spareparts.SparePart
import com.candorfoods.factoryops.presentation.spareparts.SparePartSearchResult
import com.candorfoods.factoryops.util.excel.Cell
import com.candorfoods.factoryops.util.excel.SparePartsSheet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Spare Parts stock export — one row per part, ready to sort and total in Excel. */
class SparePartsSheetTest {

    private val rows = listOf(
        SparePartSearchResult(
            part = SparePart(1, "V-Belt A42", "nos", 2, minQuantity = 3, lowStock = true),
            machineName = "Sealing Machine 2", warehouse = "W202", assetId = "W202-0014",
        ),
        SparePartSearchResult(
            part = SparePart(2, "Gasket", null, 9),
            machineName = "Hand Trolley", warehouse = "A185", assetId = null,
        ),
    )

    @Test
    fun `headers are the stock columns in order`() {
        assertEquals(
            listOf(
                "Warehouse", "Machine", "Asset ID", "Part", "Unit",
                "Quantity", "Min quantity", "Low stock",
            ),
            SparePartsSheet.stock(rows).headers,
        )
    }

    @Test
    fun `warehouse codes are humanised for the reader`() {
        val body = SparePartsSheet.stock(rows).rows
        assertEquals(Cell.Text("W-202"), body[0][0])
        assertEquals(Cell.Text("A-185"), body[1][0])
    }

    @Test
    fun `quantities are real numbers so Excel can total them`() {
        val body = SparePartsSheet.stock(rows).rows
        assertEquals(Cell.Num(2.0), body[0][5])
        assertEquals(Cell.Num(3.0), body[0][6])
    }

    @Test
    fun `low stock is flagged and an in-stock part is left blank`() {
        val body = SparePartsSheet.stock(rows).rows
        assertEquals(Cell.Text("LOW"), body[0][7])
        assertEquals(Cell.Text(""), body[1][7])
    }

    @Test
    fun `a part with no asset link or unit exports blank cells not nulls`() {
        val row = SparePartsSheet.stock(rows).rows[1]
        assertEquals(Cell.Text(""), row[2])   // asset id
        assertEquals(Cell.Text(""), row[4])   // unit
    }

    @Test
    fun `an empty list still produces headers so the caller can refuse it`() {
        val sheet = SparePartsSheet.stock(emptyList())
        assertTrue(sheet.rows.isEmpty())
        assertEquals(8, sheet.headers.size)
    }
}
