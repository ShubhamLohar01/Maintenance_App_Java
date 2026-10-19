package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail
import com.candorfoods.factoryops.domain.model.ChecklistRecordItem
import com.candorfoods.factoryops.util.pdf.PdfForm
import com.candorfoods.factoryops.util.pdf.PreventiveRow
import com.candorfoods.factoryops.util.pdf.docMetaFor
import com.candorfoods.factoryops.util.pdf.fits
import com.candorfoods.factoryops.util.pdf.flattenPreventive
import com.candorfoods.factoryops.util.pdf.pdfFileName
import com.candorfoods.factoryops.util.pdf.pdfFormFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChecklistPdfModelTest {

    @Test fun `docMetaFor returns the controlled-copy metadata per form`() {
        assertEquals("CFPLA.C4.F.50a", docMetaFor(PdfForm.PREVENTIVE_MONTHLY).documentNo)
        assertEquals("06", docMetaFor(PdfForm.PREVENTIVE_MONTHLY).issueNo)
        assertFalse(docMetaFor(PdfForm.PREVENTIVE_MONTHLY).landscape)
        assertEquals("CFPLA.C4.F.50b", docMetaFor(PdfForm.PREVENTIVE_QUARTERLY).documentNo)
        assertEquals("CFPLA.C4.F.06", docMetaFor(PdfForm.BREAKDOWN).documentNo)
        assertTrue(docMetaFor(PdfForm.BREAKDOWN).landscape)
    }

    @Test fun `pdfFormFor maps formType string ignoring case`() {
        assertEquals(PdfForm.PREVENTIVE_QUARTERLY, pdfFormFor("QUARTERLY"))
        assertEquals(PdfForm.PREVENTIVE_QUARTERLY, pdfFormFor("quarterly"))
        assertEquals(PdfForm.PREVENTIVE_MONTHLY, pdfFormFor("MONTHLY"))
        assertEquals(PdfForm.PREVENTIVE_MONTHLY, pdfFormFor("anything else"))
    }

    @Test fun `pdfFileName builds per-form names and sanitizes the date`() {
        assertEquals("Preventive_50a_Monthly_2026-06-26_rec123.pdf",
            pdfFileName(PdfForm.PREVENTIVE_MONTHLY, "2026-06-26", 123))
        assertEquals("Preventive_50b_Quarterly_2026-06-26_rec5.pdf",
            pdfFileName(PdfForm.PREVENTIVE_QUARTERLY, "2026-06-26", 5))
        assertEquals("Breakdown_F06_2026-06-26.pdf",
            pdfFileName(PdfForm.BREAKDOWN, "2026-06-26", null))
        assertEquals("Breakdown_F06_2026-06-26-x.pdf",
            pdfFileName(PdfForm.BREAKDOWN, "2026/06/26 x", null))
    }

    @Test fun `fits is true only when the row stays above the page bottom`() {
        assertTrue(fits(cursorY = 100f, rowHeight = 20f, pageBottom = 130f))
        assertTrue(fits(cursorY = 100f, rowHeight = 30f, pageBottom = 130f))
        assertFalse(fits(cursorY = 120f, rowHeight = 20f, pageBottom = 130f))
    }

    @Test fun `flattenPreventive groups section, equipment and checkpoint rows in order`() {
        val detail = ChecklistRecordDetail(
            id = 1, formType = "MONTHLY", status = "SUBMITTED", docNo = "CFPLA.C4.F.50a",
            checklistDate = "2026-06-26", doneBy = "A", checkedBy = "B", verifiedBy = "C",
            remarks = "", createdAt = "",
            items = listOf(
                ChecklistRecordItem(section = "Lower Basement", equipment = "Lift 1 Old", srNo = 1, equipmentDate = "", checkpoint = "Doors", status = "OK", remarks = ""),
                ChecklistRecordItem(section = "Lower Basement", equipment = "Lift 1 Old", srNo = 1, equipmentDate = "", checkpoint = "Switches", status = "NOT_OK", remarks = "loose"),
                ChecklistRecordItem(section = "Service Floor", equipment = "Disel Generator", srNo = 15, equipmentDate = "", checkpoint = "Valve", status = "OK", remarks = ""),
            ),
        )
        val rows = flattenPreventive(detail)
        assertEquals(PreventiveRow.SectionHeader("LOWER BASEMENT"), rows[0])
        assertEquals(PreventiveRow.EquipmentHeader("1. Lift 1 Old"), rows[1])
        assertEquals(PreventiveRow.Checkpoint("Doors", "OK", ""), rows[2])
        assertEquals(PreventiveRow.Checkpoint("Switches", "NOT OK", "loose"), rows[3])
        assertEquals(PreventiveRow.SectionHeader("SERVICE FLOOR"), rows[4])
        assertEquals(PreventiveRow.EquipmentHeader("15. Disel Generator"), rows[5])
        assertEquals(PreventiveRow.Checkpoint("Valve", "OK", ""), rows[6])
    }

    @Test fun `flattenPreventive uses a placeholder for blank checkpoints`() {
        val detail = ChecklistRecordDetail(
            id = 1, formType = "MONTHLY", status = "DRAFT", docNo = "", checklistDate = "",
            doneBy = "", checkedBy = "", verifiedBy = "", remarks = "", createdAt = "",
            items = listOf(ChecklistRecordItem(section = "S", equipment = "E", srNo = 1, equipmentDate = "", checkpoint = "  ", status = "OK", remarks = "")),
        )
        val rows = flattenPreventive(detail)
        assertEquals(PreventiveRow.Checkpoint("General check", "OK", ""), rows[2])
    }
}
