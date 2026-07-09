package com.candorfoods.factoryops.util.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

/**
 * Low-level drawing over a [PdfDocument]: the Candor header block, footer, and a simple
 * weighted-column table with automatic page breaks. Knows nothing about checklists — only
 * pages, cells, and text. One instance renders one document. Lifecycle:
 * [startDocument] -> row()/banner()/textLine()/gap() ... -> [finish].
 */
class CandorPdfCanvas(
    private val doc: PdfDocument,
    private val landscape: Boolean,
    private val meta: DocMeta,
    private val logo: Bitmap? = null,
) {
    // A4 at 72 dpi in points; landscape swaps the dimensions.
    private val pageWidth = if (landscape) 842 else 595
    private val pageHeight = if (landscape) 595 else 842
    private val margin = 24f
    private val contentLeft = margin
    private val contentRight = pageWidth - margin
    private val contentWidth = contentRight - contentLeft
    private val footerReserve = 26f
    private val pageBottom get() = pageHeight - margin - footerReserve

    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var cursorY = 0f
    private var pageNo = 0

    private val border = Paint().apply {
        color = Color.parseColor("#999999"); style = Paint.Style.STROKE; strokeWidth = 0.8f
    }
    private val text = Paint().apply { color = Color.BLACK; textSize = 8.5f; isAntiAlias = true }
    private val textBold = Paint().apply {
        color = Color.BLACK; textSize = 9f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
    }
    private val brand = Paint().apply {
        color = Color.parseColor("#C1272D"); textSize = 15f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
    }
    private val muted = Paint().apply { color = Color.parseColor("#555555"); textSize = 7.5f; isAntiAlias = true }

    fun startDocument() = newPage()

    private fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNo += 1
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create()
        val p = doc.startPage(info)
        page = p
        canvas = p.canvas
        cursorY = margin
        drawFooter()
        drawHeaderBlock()
    }

    private fun drawHeaderBlock() {
        val c = canvas ?: return
        val h = 56f
        val logoW = 110f
        val rightW = 165f
        val top = cursorY
        c.drawRect(contentLeft, top, contentRight, top + h, border)
        c.drawLine(contentLeft + logoW, top, contentLeft + logoW, top + h, border)
        c.drawLine(contentRight - rightW, top, contentRight - rightW, top + h, border)

        // Brand logo in the left cell, scaled to fit (aspect preserved). Falls back to a
        // styled "Candor Foods" wordmark only if the logo asset can't be decoded.
        val logoBmp = logo
        if (logoBmp != null && logoBmp.width > 0 && logoBmp.height > 0) {
            val pad = 8f
            val cellW = logoW - pad * 2
            val cellH = h - pad * 2
            val scale = minOf(cellW / logoBmp.width, cellH / logoBmp.height)
            val drawW = logoBmp.width * scale
            val drawH = logoBmp.height * scale
            val dl = contentLeft + pad + (cellW - drawW) / 2f
            val dt = top + pad + (cellH - drawH) / 2f
            c.drawBitmap(logoBmp, null, RectF(dl, dt, dl + drawW, dt + drawH), null)
        } else {
            c.drawText("Candor", contentLeft + 12f, top + 26f, brand)
            c.drawText("Foods", contentLeft + 12f, top + 44f, brand)
        }

        // Center block.
        val cx = contentLeft + logoW + 8f
        c.drawText("CANDOR FOODS PVT LTD", cx, top + 18f, textBold)
        c.drawText("Format: ${meta.formatTitle}", cx, top + 34f, text)
        c.drawText("Document No: ${meta.documentNo}", cx, top + 49f, text)

        // Right block — four metadata rows.
        val rx = contentRight - rightW + 5f
        val rowH = h / 4f
        c.drawText("Issue Date: ${meta.issueDate}", rx, top + 13f, muted)
        c.drawText("Issue No: ${meta.issueNo}", rx, top + 13f + rowH, muted)
        c.drawText("Revision Date: ${meta.revisionDate}", rx, top + 13f + rowH * 2, muted)
        c.drawText("Revision No: ${meta.revisionNo}", rx, top + 13f + rowH * 3, muted)

        cursorY = top + h + 8f
    }

    private fun drawFooter() {
        val c = canvas ?: return
        val y = pageHeight - margin - 6f
        c.drawText("Prepared By: FST", contentLeft, y, muted)
        val mid = "CONTROLLED COPY"
        c.drawText(mid, (pageWidth - muted.measureText(mid)) / 2f, y, muted)
        val appr = "Approved By: FSTL"
        c.drawText(appr, contentRight - muted.measureText(appr), y, muted)
    }

    /**
     * Draw one bordered table row. [weights] sets relative column widths (need not sum to 1).
     * Text wraps within each cell and the row grows to fit. Page-breaks (redrawing header +
     * footer) when the row would cross the bottom margin.
     */
    fun row(
        cells: List<String>,
        weights: List<Float>,
        bold: Boolean = false,
        fill: Int? = null,
        minHeight: Float = 15f,
    ) {
        val paint = if (bold) textBold else text
        val totalW = weights.sum()
        val colWidths = weights.map { contentWidth * (it / totalW) }
        val wrapped = cells.mapIndexed { i, s -> wrapText(s, colWidths[i] - 6f, paint) }
        val lineCount = wrapped.maxOf { it.size }.coerceAtLeast(1)
        val rowH = minHeight.coerceAtLeast(lineCount * (paint.textSize + 3f) + 6f)

        if (!fits(cursorY, rowH, pageBottom)) newPage()
        val c = canvas ?: return

        val top = cursorY
        if (fill != null) {
            val bg = Paint().apply { color = fill; style = Paint.Style.FILL }
            c.drawRect(contentLeft, top, contentRight, top + rowH, bg)
        }
        var x = contentLeft
        for (i in cells.indices) {
            val w = colWidths[i]
            c.drawRect(x, top, x + w, top + rowH, border)
            var ty = top + paint.textSize + 3f
            for (line in wrapped[i]) {
                c.drawText(line, x + 3f, ty, paint)
                ty += paint.textSize + 3f
            }
            x += w
        }
        cursorY = top + rowH
    }

    /** A full-width shaded banner row (section title). */
    fun banner(title: String) {
        row(listOf(title), listOf(1f), bold = true, fill = Color.parseColor("#E0E0E0"), minHeight = 17f)
    }

    /** A free-text line outside any table (summary fields, Verified By). */
    fun textLine(s: String, bold: Boolean = false) {
        val paint = if (bold) textBold else text
        val rowH = paint.textSize + 8f
        if (!fits(cursorY, rowH, pageBottom)) newPage()
        val c = canvas ?: return
        c.drawText(s, contentLeft, cursorY + paint.textSize + 2f, paint)
        cursorY += rowH
    }

    fun gap(h: Float = 6f) { cursorY += h }

    private fun wrapText(s: String, maxW: Float, paint: Paint): List<String> {
        if (s.isBlank()) return listOf("")
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (word in s.split(" ")) {
            val candidate = if (cur.isEmpty()) word else "$cur $word"
            if (paint.measureText(candidate) <= maxW) {
                cur = StringBuilder(candidate)
            } else {
                if (cur.isNotEmpty()) { lines.add(cur.toString()); cur = StringBuilder() }
                if (paint.measureText(word) > maxW) {
                    // Hard-break a single over-long word.
                    var chunk = StringBuilder()
                    for (ch in word) {
                        if (paint.measureText("$chunk$ch") > maxW && chunk.isNotEmpty()) {
                            lines.add(chunk.toString()); chunk = StringBuilder()
                        }
                        chunk.append(ch)
                    }
                    cur = chunk
                } else {
                    cur = StringBuilder(word)
                }
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines.ifEmpty { listOf("") }
    }

    fun finish() {
        page?.let { doc.finishPage(it) }
        page = null
        canvas = null
    }
}
