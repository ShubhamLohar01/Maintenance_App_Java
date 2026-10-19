package com.candorfoods.factoryops.util.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.domain.model.reports.SummaryReport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Renders the consolidated Summary Report to a PDF in the Candor controlled-copy
 * format. Draw-only: every formatting decision was already made by
 * [SummaryPdfModel], so this class walks sections and calls the canvas.
 */
class SummaryPdfRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val headerFill = Color.parseColor("#F0F0F0")

    private val logo: Bitmap? by lazy {
        runCatching {
            BitmapFactory.decodeResource(
                context.resources, R.drawable.candor_logo,
                BitmapFactory.Options().apply { inScaled = false },
            )
        }.getOrNull()
    }

    suspend fun render(report: SummaryReport): File = withContext(Dispatchers.Default) {
        val content = SummaryPdfModel.from(report)
        val meta = DocMeta(
            documentNo = "CFPL.MAINT.SUMMARY",
            formatTitle = "Maintenance Summary Report",
            issueNo = "01",
            revisionNo = "00",
            issueDate = report.periodFrom,
            revisionDate = report.periodTo,
            landscape = false,
        )
        val doc = PdfDocument()
        try {
            val cv = CandorPdfCanvas(doc, landscape = meta.landscape, meta = meta, logo = logo)
            cv.startDocument()

            cv.gap()
            cv.textLine(content.title, bold = true)
            cv.textLine(content.subtitle)
            cv.gap()

            // KPI band — two columns of label/value, so the headline numbers are
            // readable before anyone reaches a table.
            cv.banner("At a glance")
            content.kpis.chunked(2).forEach { pair ->
                val cells = pair.flatMap { listOf(it.label, it.value) }
                val padded = if (cells.size == 4) cells else cells + listOf("", "")
                cv.row(padded, listOf(2f, 1f, 2f, 1f))
            }
            cv.gap()

            content.sections.forEach { section ->
                cv.banner(section.title)
                section.lines.forEach { cv.textLine(it) }
                section.tables.forEach { table ->
                    if (table.rows.isEmpty()) {
                        cv.textLine("${table.caption}: none", bold = false)
                        return@forEach
                    }
                    cv.gap(3f)
                    cv.textLine(table.caption, bold = true)
                    cv.row(table.headers, table.weights, bold = true, fill = headerFill)
                    table.rows.forEach { cv.row(it, table.weights) }
                }
                cv.gap()
            }

            if (content.notes.isNotEmpty()) {
                cv.banner("Notes")
                content.notes.forEach { cv.textLine("• $it") }
            }
            cv.finish()
            writeToCache(doc, "Summary_Report_${report.periodFrom}_${report.periodTo}.pdf")
        } finally {
            doc.close()
        }
    }

    private fun writeToCache(doc: PdfDocument, fileName: String): File {
        val dir = File(context.cacheDir, "summary_pdfs")
        if (!dir.exists() && !dir.mkdirs()) error("Failed to create PDF cache dir: $dir")
        val file = File(dir, fileName)
        file.outputStream().use { doc.writeTo(it) }
        return file
    }
}
