package com.candorfoods.factoryops.util.excel

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One cell of an exported sheet.
 *
 * The distinction matters: a [Num] lands in Excel as a real number so SUM and AVERAGE work
 * without the reader retyping the column, and a [DateCell] lands as a real date rather than
 * a string that Excel re-parses (and mangles) per locale.
 */
sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Num(val value: Double) : Cell
    data class DateCell(val value: LocalDate) : Cell

    companion object {
        /** Blank cells are written as empty text — a missing reading is not a zero. */
        val Empty: Cell = Text("")

        /** null -> blank, so an unrecorded value never becomes 0.0 in a total. */
        fun of(value: Double?): Cell = value?.let(::Num) ?: Empty

        fun of(value: String?): Cell = Text(value.orEmpty())

        /** Parses ISO yyyy-MM-dd; anything else stays text rather than throwing. */
        fun date(iso: String?): Cell {
            val raw = iso?.trim().orEmpty()
            if (raw.isEmpty()) return Empty
            return runCatching { DateCell(LocalDate.parse(raw)) }.getOrElse { Text(raw) }
        }
    }
}

/** A sheet: a title, one header row, and the body. */
data class Sheet(
    val name: String,
    val headers: List<String>,
    val rows: List<List<Cell>>,
)

/**
 * Writes a real .xlsx — which is a zip of XML parts, so it needs no library and adds
 * nothing to the APK. Apache POI would do the same job at roughly 10 MB and a method-count
 * problem; everything below is about 150 lines.
 *
 * Deliberately minimal: inline strings (no sharedStrings part), three cell styles (plain,
 * bold header, date). Enough for a records export, and nothing that has to be maintained.
 */
object Xlsx {

    fun write(sheet: Sheet, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", CONTENT_TYPES)
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("xl/workbook.xml", workbookXml(sheet.name))
            zip.put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.put("xl/styles.xml", STYLES)
            zip.put("xl/worksheets/sheet1.xml", sheetXml(sheet))
        }
    }

    fun toBytes(sheet: Sheet): ByteArray =
        ByteArrayOutputStream().also { write(sheet, it) }.toByteArray()

    private fun ZipOutputStream.put(path: String, body: String) {
        putNextEntry(ZipEntry(path))
        write(body.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ─── Sheet body ───────────────────────────────────────────────────────────────

    private fun sheetXml(sheet: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="$NS_MAIN">""")
        // Freeze the header so a long export stays readable while scrolling.
        append("""<sheetViews><sheetView workbookViewId="0">""")
        append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
        append("""</sheetView></sheetViews>""")
        append(colsXml(sheet))
        append("<sheetData>")
        append(rowXml(1, sheet.headers.map { Cell.Text(it) }, styleOverride = STYLE_HEADER))
        sheet.rows.forEachIndexed { i, cells -> append(rowXml(i + 2, cells)) }
        append("</sheetData></worksheet>")
    }

    /** Widths from the longest value per column, so nothing opens as ####. */
    private fun colsXml(sheet: Sheet): String {
        val widths = sheet.headers.indices.map { c ->
            val longest = sheet.rows.maxOfOrNull { it.getOrNull(c).displayLength() } ?: 0
            (maxOf(longest, sheet.headers[c].length) + 2).coerceIn(10, 48)
        }
        if (widths.isEmpty()) return ""
        return widths.mapIndexed { i, w -> """<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""" }
            .joinToString("", prefix = "<cols>", postfix = "</cols>")
    }

    private fun Cell?.displayLength(): Int = when (this) {
        null, Cell.Empty -> 0
        is Cell.Text -> value.length
        is Cell.Num -> value.toString().length
        is Cell.DateCell -> 10
    }

    private fun rowXml(rowNum: Int, cells: List<Cell>, styleOverride: Int? = null): String =
        cells.mapIndexed { i, cell -> cellXml(ref(i, rowNum), cell, styleOverride) }
            .joinToString("", prefix = """<row r="$rowNum">""", postfix = "</row>")

    private fun cellXml(ref: String, cell: Cell, styleOverride: Int?): String = when (cell) {
        is Cell.Num -> """<c r="$ref"${style(styleOverride)}><v>${cell.value}</v></c>"""
        is Cell.DateCell ->
            """<c r="$ref" s="${styleOverride ?: STYLE_DATE}"><v>${serial(cell.value)}</v></c>"""
        is Cell.Text ->
            if (cell.value.isEmpty()) """<c r="$ref"${style(styleOverride)}/>"""
            else """<c r="$ref"${style(styleOverride)} t="inlineStr"><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>"""
    }

    private fun style(s: Int?): String = if (s == null) "" else """ s="$s""""

    /** A1-style reference: column letters are base-26 (A..Z, AA..). */
    internal fun ref(colIndex: Int, rowNum: Int): String {
        var n = colIndex
        val letters = StringBuilder()
        while (true) {
            letters.insert(0, ('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return "$letters$rowNum"
    }

    /**
     * Excel's day serial. Epoch is 1899-12-30, not 1900-01-01, because Excel keeps Lotus
     * 1-2-3's non-existent 29 Feb 1900 — 25569 is 1970-01-01 in that scheme.
     */
    internal fun serial(date: LocalDate): Long = date.toEpochDay() + 25569

    internal fun escape(s: String): String = buildString(s.length) {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            // Control characters are illegal in XML 1.0 and would corrupt the file.
            else -> if (ch.code < 0x20 && ch != '\n' && ch != '\t') append(' ') else append(ch)
        }
    }

    /** Excel refuses these in a sheet name, and caps it at 31 characters. */
    fun safeSheetName(raw: String): String {
        val cleaned = raw.map { if (it in "[]:*?/\\") ' ' else it }.joinToString("").trim()
        return cleaned.ifBlank { "Sheet1" }.take(31)
    }

    /**
     * Filesystem-safe file name with the .xlsx extension. Runs of separators collapse to a
     * single underscore, so "HT / Cold Room / UV" is HT_Cold_Room_UV, not HT___Cold_Room___UV.
     */
    fun fileName(title: String, stamp: String): String {
        val safe = title.map { if (it.isLetterOrDigit() || it == '-') it else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "export" }
        return "${safe}_$stamp.xlsx"
    }

    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private const val STYLE_HEADER = 1
    private const val STYLE_DATE = 2
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="$NS_REL/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="$NS_REL/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="$NS_REL/styles" Target="styles.xml"/>
</Relationships>"""

    // numFmtId 14 is the built-in short date; cellXfs index 2 is what STYLE_DATE points at.
    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="$NS_MAIN">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="1"><fill><patternFill patternType="none"/></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
</cellXfs>
</styleSheet>"""

    private fun workbookXml(sheetName: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="$NS_MAIN" xmlns:r="$NS_REL">
<sheets><sheet name="${escape(safeSheetName(sheetName))}" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
}
