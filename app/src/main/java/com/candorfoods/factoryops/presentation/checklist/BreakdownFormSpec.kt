package com.candorfoods.factoryops.presentation.checklist

import com.candorfoods.factoryops.util.pdf.PdfForm

/**
 * Which plant's Breakdown Maintenance Record a technician is filling.
 *
 * Both plants use the same columns — W-202's CFPLA.C4.F.06 and A-185's CFPLB.C4.F.61
 * (Rev 03, 02/02/2026) are the same form under different controlled-copy numbers. So the
 * only things that vary are the document number shown on screen and the header block
 * printed on the exported PDF; the entry fields are identical.
 */
data class BreakdownFormSpec(
    val plant: String,
    val docNo: String,
    /** Controlled-copy header block used when exporting this plant's record. */
    val pdfForm: PdfForm,
) {
    companion object {
        val W202 = BreakdownFormSpec(
            plant = "W202",
            docNo = "CFPLA.C4.F.06",
            pdfForm = PdfForm.BREAKDOWN,
        )

        val A185 = BreakdownFormSpec(
            plant = "A185",
            docNo = "CFPLB.C4.F.61",
            pdfForm = PdfForm.BREAKDOWN_A185,
        )

        /** The record the signed-in technician's plant uses. Unknown plant → W-202. */
        fun forPlant(plantId: String?): BreakdownFormSpec =
            if (ChecklistType.plantOf(plantId) == "A185") A185 else W202

        /** The form a stored record belongs to, from its doc_no. Unknown → W-202's F.06. */
        fun forDocNo(docNo: String?): BreakdownFormSpec =
            if (docNo?.trim().equals(A185.docNo, ignoreCase = true)) A185 else W202
    }
}
