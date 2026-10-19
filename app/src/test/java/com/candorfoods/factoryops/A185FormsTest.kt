package com.candorfoods.factoryops

import com.candorfoods.factoryops.presentation.checklist.BreakdownFormSpec
import com.candorfoods.factoryops.presentation.checklist.ChecklistCatalog
import com.candorfoods.factoryops.presentation.checklist.ChecklistType
import com.candorfoods.factoryops.util.pdf.PdfForm
import com.candorfoods.factoryops.util.pdf.docMetaFor
import com.candorfoods.factoryops.util.pdf.pdfFormFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A-185's own Preventive (F.41) and Breakdown (F.61) forms: that a technician only ever
 * sees their own plant's documents, and that the transcribed catalog still matches the
 * paper form it was taken from.
 */
class A185FormsTest {

    // ─── Plant scoping ────────────────────────────────────────────────────────────

    @Test fun `a plant only sees its own preventive forms`() {
        assertEquals(
            listOf(ChecklistType.A185_QUARTERLY, ChecklistType.A185_MONTHLY),
            ChecklistType.forPlant("A185"),
        )
        assertEquals(
            listOf(ChecklistType.MONTHLY, ChecklistType.QUARTERLY),
            ChecklistType.forPlant("W202"),
        )
    }

    /**
     * plantId is `mt_users.location`, which is descriptive free text rather than a plant
     * code — a real A-185 technician logs in as "A-185-Koparkhairne". Matching only exact
     * spellings silently served them W-202's forms.
     */
    @Test fun `descriptive mt_users locations resolve to their plant`() {
        listOf(
            "A185", "a185", "A-185", " a-185 ",
            "A-185-Koparkhairne", "185-Koparkhairnae", "a185 koparkhairne",
        ).forEach { assertEquals("A185", ChecklistType.plantOf(it), "plantOf($it)") }

        listOf(
            "W202", "W-202", "W-202-Koparkhairne", "202-Koparkhairne",
        ).forEach { assertEquals("W202", ChecklistType.plantOf(it), "plantOf($it)") }

        // Unknown or missing falls back to W-202 rather than leaving a technician
        // with no forms at all.
        listOf(null, "", "QC", "Pune").forEach {
            assertEquals("W202", ChecklistType.plantOf(it), "plantOf($it)")
        }
        assertTrue(ChecklistType.forPlant(null).isNotEmpty())
    }

    @Test fun `a descriptive A185 location selects the A185 forms end to end`() {
        val plantId = "A-185-Koparkhairne"
        assertEquals(
            listOf(ChecklistType.A185_QUARTERLY, ChecklistType.A185_MONTHLY),
            ChecklistType.forPlant(plantId),
        )
        assertEquals("CFPLB.C4.F.41", ChecklistType.forPlant(plantId).first().docNo)
        assertEquals(BreakdownFormSpec.A185, BreakdownFormSpec.forPlant(plantId))
    }

    @Test fun `the breakdown record follows the same plant`() {
        assertEquals(BreakdownFormSpec.A185, BreakdownFormSpec.forPlant("A-185"))
        assertEquals(BreakdownFormSpec.W202, BreakdownFormSpec.forPlant("W202"))
        assertEquals(BreakdownFormSpec.W202, BreakdownFormSpec.forPlant(null))
        // Read-back resolves by the stored doc_no, not by who is looking at it.
        assertEquals(BreakdownFormSpec.A185, BreakdownFormSpec.forDocNo("CFPLB.C4.F.61"))
        assertEquals(BreakdownFormSpec.W202, BreakdownFormSpec.forDocNo("CFPLA.C4.F.06"))
        assertEquals(BreakdownFormSpec.W202, BreakdownFormSpec.forDocNo(null))
    }

    @Test fun `both plants use the same columns and differ only in the printed header`() {
        assertEquals("CFPLA.C4.F.06", BreakdownFormSpec.W202.docNo)
        assertEquals("CFPLB.C4.F.61", BreakdownFormSpec.A185.docNo)
        assertEquals(PdfForm.BREAKDOWN, BreakdownFormSpec.W202.pdfForm)
        assertEquals(PdfForm.BREAKDOWN_A185, BreakdownFormSpec.A185.pdfForm)
    }

    // ─── Transcription integrity (F.41) ───────────────────────────────────────────

    private val quarterly = ChecklistCatalog.forType(ChecklistType.A185_QUARTERLY)
    private val monthly = ChecklistCatalog.forType(ChecklistType.A185_MONTHLY)

    @Test fun `F41 splits into the paper form's quarterly and monthly blocks`() {
        assertEquals(listOf("Ground floor", "Mezzanine floor", "Packing area"), quarterly.map { it.name })
        assertEquals(listOf("Monthly", "Miscellaneous"), monthly.map { it.name })
    }

    @Test fun `F41 Sr numbers run 1 to 172 with the paper form's own gaps`() {
        val srs = (quarterly + monthly).flatMap { it.equipment }.mapNotNull { it.srNo }
        assertEquals(srs.sorted(), srs, "Sr numbers must stay in document order")
        assertEquals(srs.distinct(), srs, "no duplicate Sr numbers")
        // 41, 88 and 129 are absent from the controlled document itself.
        assertEquals(listOf(41, 88, 129), (1..172).filterNot { it in srs })
        assertEquals(169, srs.size)
    }

    @Test fun `the unnumbered Lab AC row is preserved`() {
        val lab = quarterly.flatMap { it.equipment }.single { it.name == "Lab AC" }
        assertNull(lab.srNo, "the paper form leaves this row's Sr cell blank")
        assertEquals(4, lab.checkpoints.size)
    }

    @Test fun `the quarterly and monthly blocks split at the paper boundary`() {
        assertEquals(163, quarterly.flatMap { it.equipment }.mapNotNull { it.srNo }.max())
        assertEquals(164, monthly.flatMap { it.equipment }.mapNotNull { it.srNo }.min())
        assertEquals(
            "Hydraulic press machine",
            quarterly.last().equipment.last().name,
        )
        assertEquals("Air curtain", monthly.first().equipment.single().name)
    }

    @Test fun `every F41 equipment has a name and the Miscellaneous block has no checkpoints`() {
        (quarterly + monthly).flatMap { it.equipment }.forEach {
            assertTrue(it.name.isNotBlank(), "unnamed equipment at Sr ${it.srNo}")
        }
        val misc = monthly.single { it.name == "Miscellaneous" }
        assertEquals(8, misc.equipment.size)
        assertTrue(misc.equipment.all { it.checkpoints.isEmpty() })
    }

    @Test fun `F41 carries the whole checkpoint list from the paper form`() {
        val total = (quarterly + monthly).flatMap { it.equipment }.sumOf { it.checkpoints.size }
        assertEquals(503, total)
    }

    // ─── PDF metadata ─────────────────────────────────────────────────────────────

    @Test fun `both A185 records map to their own controlled-copy header`() {
        assertEquals(PdfForm.PREVENTIVE_A185, pdfFormFor("A185_QUARTERLY"))
        assertEquals(PdfForm.PREVENTIVE_A185, pdfFormFor("A185_MONTHLY"))
        assertEquals(PdfForm.PREVENTIVE_QUARTERLY, pdfFormFor("QUARTERLY"))
        assertEquals(PdfForm.PREVENTIVE_MONTHLY, pdfFormFor("MONTHLY"))

        val f41 = docMetaFor(PdfForm.PREVENTIVE_A185)
        assertEquals("CFPLB.C4.F.41", f41.documentNo)
        assertEquals("04/08/2021", f41.issueDate)
        assertEquals("15/04/2026", f41.revisionDate)

        val f61 = docMetaFor(PdfForm.BREAKDOWN_A185)
        assertEquals("CFPLB.C4.F.61", f61.documentNo)
        assertEquals("01/11/2017", f61.issueDate)
        assertEquals("04", f61.issueNo)
        assertEquals("02/02/2026", f61.revisionDate)
        assertEquals("03", f61.revisionNo)
        assertTrue(f61.landscape)
    }

}
