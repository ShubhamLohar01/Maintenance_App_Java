package com.candorfoods.factoryops.util.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Renders a checklist record to a PDF file matching the Candor controlled-copy format. */
class ChecklistPdfRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val headerFill = Color.parseColor("#F0F0F0")

    // Brand logo for the PDF header (res/drawable/candor_logo.jpg). Decoded once at native
    // pixel size (inScaled = false); null if it can't be decoded → header uses a text wordmark.
    private val logo: Bitmap? by lazy {
        runCatching {
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.candor_logo,
                BitmapFactory.Options().apply { inScaled = false },
            )
        }.getOrNull()
    }

    suspend fun renderPreventive(detail: ChecklistRecordDetail): File = withContext(Dispatchers.Default) {
        val form = pdfFormFor(detail.formType)
        val meta = docMetaFor(form)
        val doc = PdfDocument()
        try {
            val cv = CandorPdfCanvas(doc, landscape = meta.landscape, meta = meta, logo = logo)
            cv.startDocument()

            cv.gap()
            cv.textLine(
                "Date: ${detail.checklistDate.ifBlank { "—" }}     " +
                    "Done by: ${detail.doneBy.ifBlank { "—" }}",
                bold = true,
            )
            cv.textLine(
                "Checked by: ${detail.checkedBy.ifBlank { "—" }}     " +
                    "Verified by: ${detail.verifiedBy.ifBlank { "—" }}",
            )
            if (detail.remarks.isNotBlank()) cv.textLine("Remarks: ${detail.remarks}")
            cv.gap()

            cv.row(listOf("Check point", "Status", "Remarks"), PREVENTIVE_WEIGHTS, bold = true, fill = headerFill)
            flattenPreventive(detail).forEach { r ->
                when (r) {
                    is PreventiveRow.SectionHeader   -> cv.banner(r.title)
                    is PreventiveRow.EquipmentHeader -> cv.row(listOf(r.title), listOf(1f), bold = true)
                    is PreventiveRow.Checkpoint      ->
                        cv.row(listOf(r.name, r.status, r.remarks), PREVENTIVE_WEIGHTS)
                }
            }
            cv.finish()
            writeToCache(doc, pdfFileName(form, detail.checklistDate, detail.id))
        } finally {
            doc.close()
        }
    }

    suspend fun renderBreakdown(data: BreakdownDocData): File = withContext(Dispatchers.Default) {
        val meta = docMetaFor(PdfForm.BREAKDOWN)
        val doc = PdfDocument()
        try {
            val cv = CandorPdfCanvas(doc, landscape = true, meta = meta, logo = logo)
            cv.startDocument()

            cv.gap()
            cv.row(BREAKDOWN_HEADERS, BREAKDOWN_WEIGHTS, bold = true, fill = headerFill)
            data.rows.forEach { r ->
                cv.row(
                    listOf(
                        r.srNo.toString(), r.date, r.location, r.machineName, r.equipmentModel,
                        r.problem, r.typeOfMaintenance, r.partOfMachine, r.reason,
                        r.durationStart, r.durationEnd, r.machineOperatorSign,
                        r.maintenancePersonSign, r.qcClearanceSign,
                    ),
                    BREAKDOWN_WEIGHTS,
                )
            }
            cv.gap()
            cv.textLine("Verified By: ${data.verifiedBy.ifBlank { "—" }}", bold = true)
            cv.finish()
            writeToCache(doc, pdfFileName(PdfForm.BREAKDOWN, data.rows.firstOrNull()?.date ?: "", null))
        } finally {
            doc.close()
        }
    }

    private fun writeToCache(doc: PdfDocument, fileName: String): File {
        val dir = File(context.cacheDir, "checklist_pdfs")
        if (!dir.exists() && !dir.mkdirs()) error("Failed to create PDF cache dir: $dir")
        val file = File(dir, fileName)
        file.outputStream().use { doc.writeTo(it) }
        return file
    }

    private companion object {
        val PREVENTIVE_WEIGHTS = listOf(3f, 1f, 2f)
        val BREAKDOWN_HEADERS = listOf(
            "Sr", "Date", "Location", "Machine", "Model/Sr No", "Problem", "Type",
            "Part", "Reason", "Start", "End", "Operator", "Maint.", "QC",
        )
        val BREAKDOWN_WEIGHTS = listOf(
            0.5f, 1.2f, 1.2f, 1.6f, 1.4f, 2.2f, 1.2f, 1.1f, 1.6f, 0.9f, 0.9f, 1.2f, 1.2f, 1.1f,
        )
    }
}
