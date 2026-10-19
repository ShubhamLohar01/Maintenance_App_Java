package com.candorfoods.factoryops.excel

import com.candorfoods.factoryops.util.excel.Cell
import com.candorfoods.factoryops.util.excel.Sheet
import com.candorfoods.factoryops.util.excel.Xlsx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

/**
 * The .xlsx is hand-built rather than produced by a library, so these tests read the file
 * back apart — an xlsx is a zip of XML parts, and a missing or malformed part is the
 * difference between Excel opening it and Excel calling it corrupt.
 */
class XlsxTest {

    private fun parts(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
            }
        }
    }

    private val sheet = Sheet(
        name = "Diesel",
        headers = listOf("Date", "Plant", "Qty"),
        rows = listOf(
            listOf(Cell.date("2024-01-01"), Cell.Text("W-202"), Cell.Num(420.0)),
            listOf(Cell.date("2024-01-02"), Cell.Text("A-185"), Cell.Empty),
        ),
    )

    // ─── The package itself ───────────────────────────────────────────────────────

    @Test fun `the workbook contains every part Excel requires`() {
        val names = parts(Xlsx.toBytes(sheet)).keys
        listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/worksheets/sheet1.xml",
        ).forEach { assertTrue(it in names, "missing part: $it (have: $names)") }
    }

    @Test fun `every part is well-formed enough to declare itself XML`() {
        parts(Xlsx.toBytes(sheet)).forEach { (name, body) ->
            assertTrue(body.startsWith("<?xml"), "$name should be XML")
            assertFalse(body.contains("null"), "$name should not leak a literal null")
        }
    }

    // ─── Cell typing — the whole reason not to ship CSV ────────────────────────────

    @Test fun `numbers are written as numbers, not text`() {
        val body = parts(Xlsx.toBytes(sheet))["xl/worksheets/sheet1.xml"]!!
        assertTrue(body.contains("""<c r="C2"><v>420.0</v></c>"""), body)
        // No inlineStr on that cell — that would make SUM fail in Excel.
        assertFalse(body.contains("""<c r="C2" t="inlineStr""""), body)
    }

    @Test fun `text cells are inline strings`() {
        val body = parts(Xlsx.toBytes(sheet))["xl/worksheets/sheet1.xml"]!!
        assertTrue(body.contains("""t="inlineStr"><is><t xml:space="preserve">W-202"""), body)
    }

    @Test fun `a blank cell stays blank rather than becoming zero`() {
        val body = parts(Xlsx.toBytes(sheet))["xl/worksheets/sheet1.xml"]!!
        assertTrue(body.contains("""<c r="C3"/>"""), "empty cell should be self-closing: $body")
        assertEquals(Cell.Empty, Cell.of(null as Double?))
    }

    /**
     * Excel's epoch is 1899-12-30 — it keeps Lotus 1-2-3's non-existent 29 Feb 1900.
     * Both anchors below are published Excel serials, so they pin the off-by-one.
     */
    @Test fun `dates use Excel's own day serial`() {
        assertEquals(2L, Xlsx.serial(LocalDate.of(1900, 1, 1)))
        assertEquals(45292L, Xlsx.serial(LocalDate.of(2024, 1, 1)))
    }

    @Test fun `a date cell carries the date style so it does not show as a number`() {
        val body = parts(Xlsx.toBytes(sheet))["xl/worksheets/sheet1.xml"]!!
        assertTrue(body.contains("""<c r="A2" s="2"><v>45292</v></c>"""), body)
    }

    @Test fun `an unparseable date degrades to text instead of throwing`() {
        assertEquals(Cell.Text("25/12/2026"), Cell.date("25/12/2026"))
        assertEquals(Cell.Empty, Cell.date(null))
        assertEquals(Cell.Empty, Cell.date("  "))
    }

    // ─── Structure ────────────────────────────────────────────────────────────────

    @Test fun `the header row is bold and comes first`() {
        val body = parts(Xlsx.toBytes(sheet))["xl/worksheets/sheet1.xml"]!!
        assertTrue(body.contains("""<row r="1"><c r="A1" s="1" t="inlineStr"><is><t xml:space="preserve">Date"""), body)
    }

    @Test fun `the header row is frozen`() {
        val body = parts(Xlsx.toBytes(sheet))["xl/worksheets/sheet1.xml"]!!
        assertTrue(body.contains("""state="frozen""""), body)
    }

    @Test fun `column references roll over past Z`() {
        assertEquals("A1", Xlsx.ref(0, 1))
        assertEquals("Z9", Xlsx.ref(25, 9))
        assertEquals("AA1", Xlsx.ref(26, 1))
        assertEquals("AB2", Xlsx.ref(27, 2))
    }

    // ─── Things that would corrupt the file ───────────────────────────────────────

    @Test fun `xml-special characters are escaped`() {
        val out = Xlsx.toBytes(
            sheet.copy(rows = listOf(listOf(Cell.Text("""Ram & Co <"leak">"""), Cell.Empty, Cell.Empty)))
        )
        val body = parts(out)["xl/worksheets/sheet1.xml"]!!
        assertTrue(body.contains("Ram &amp; Co &lt;&quot;leak&quot;&gt;"), body)
    }

    @Test fun `control characters are stripped rather than written raw`() {
        assertFalse(Xlsx.escape("badvalue").contains(''))
        assertTrue(Xlsx.escape("keeps\ttabs").contains('\t'))
    }

    @Test fun `sheet names are sanitised and truncated to Excel's limit`() {
        assertEquals("HT Cold Room UV", Xlsx.safeSheetName("HT/Cold Room:UV"))
        assertEquals(31, Xlsx.safeSheetName("x".repeat(50)).length)
        assertEquals("Sheet1", Xlsx.safeSheetName("  "))
    }

    @Test fun `file names are filesystem-safe and end in xlsx`() {
        assertEquals("Main_meter_reading_2026-08-22.xlsx", Xlsx.fileName("Main meter reading", "2026-08-22"))
        assertEquals("HT_Cold_Room_UV_2026-08-22.xlsx", Xlsx.fileName("HT / Cold Room / UV", "2026-08-22"))
    }

    @Test fun `an export with no rows still produces a valid workbook`() {
        val empty = Sheet("Empty", listOf("Date"), emptyList())
        val names = parts(Xlsx.toBytes(empty)).keys
        assertTrue("xl/worksheets/sheet1.xml" in names)
    }
}
