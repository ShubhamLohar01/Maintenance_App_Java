# Checklist PDF Download Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Download PDF" action to the Preventive checklist record detail and to the Breakdown (F.06) form that exports the record as a Candor-format PDF and opens the Android share sheet.

**Architecture:** Native `android.graphics.pdf.PdfDocument` + `Canvas` drawing (no new dependency). A pure model/transform layer (testable) feeds a low-level drawing helper (`CandorPdfCanvas`) driven by a domain-aware `ChecklistPdfRenderer`. The result `File` is shared via the app's existing FileProvider pattern (`PdfShareLauncher`). UI is wired through the existing ViewModel + one-shot state-flag pattern (`pdfToShare` / `onPdfShared()`).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Coroutines, JUnit5 (Jupiter). Spec: `docs/superpowers/specs/2026-06-26-checklist-pdf-download-design.md`.

---

## Notes for the implementer

- **Working dir / commands:** the Gradle wrapper lives at the repo root `d:\Maintenance module\FactoryOps`. Run all `./gradlew` commands from there (Git Bash). The app module is `:app`.
- **Git:** this project is **not** a git repository. The "Commit" steps below are written as logical checkpoints — if git is later initialized, run them as-is; otherwise treat each as "stop, the change is self-contained here."
- **Drawing is not unit-testable:** `android.graphics` is stubbed (returns 0 / no-ops) in JVM unit tests. Only the pure model/transform layer (Task 1) has unit tests. Drawing (Tasks 4–5) and wiring (Tasks 6–7) are verified by a successful build plus the manual on-device check in Task 8.
- **Package root:** `com.candorfoods.factoryops`. New code lives under `util/pdf/`.

---

## File structure

| File | Responsibility |
|---|---|
| `util/pdf/ChecklistPdfModel.kt` (new) | Pure types + transforms: `PdfForm`, `DocMeta`/`docMetaFor`, `pdfFormFor`, `PreventiveRow`/`flattenPreventive`, `BreakdownRow`/`BreakdownDocData`, `pdfFileName`, `fits`. No Android deps. |
| `util/pdf/CandorPdfCanvas.kt` (new) | Low-level drawing: header block, footer, table rows, page breaks. Android graphics only; no checklist knowledge. |
| `util/pdf/ChecklistPdfRenderer.kt` (new) | Domain-aware: `renderPreventive(detail)` / `renderBreakdown(data)` → `File` in `cacheDir/checklist_pdfs/`. Hilt `@Inject`, `@ApplicationContext`. |
| `util/pdf/PdfShareLauncher.kt` (new) | `share(context, file)` → FileProvider URI + ACTION_SEND chooser. |
| `res/xml/file_paths.xml` (edit) | Expose `checklist_pdfs/` cache dir to FileProvider. |
| `presentation/checklist/PreventiveDetailViewModel.kt` (edit) | Add export state + `exportPdf()`/`onPdfShared()`. |
| `presentation/checklist/PreventiveDetailScreen.kt` (edit) | TopAppBar download action + share effect + snackbar. |
| `presentation/checklist/BreakdownFormViewModel.kt` (edit) | Add export state + `canDownload` + `exportPdf()`/`onPdfShared()`. |
| `presentation/checklist/BreakdownFormScreen.kt` (edit) | TopAppBar download action + share effect (reuse existing snackbar). |
| `test/.../ChecklistPdfModelTest.kt` (new) | Unit tests for Task 1 pure logic. |

---

## Task 1: Pure PDF model + transforms (TDD)

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/util/pdf/ChecklistPdfModel.kt`
- Test: `src/test/java/com/candorfoods/factoryops/ChecklistPdfModelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/candorfoods/factoryops/ChecklistPdfModelTest.kt`:

```kotlin
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
        // unsafe characters in date become dashes
        assertEquals("Breakdown_F06_2026-06-26-x.pdf",
            pdfFileName(PdfForm.BREAKDOWN, "2026/06/26 x", null))
    }

    @Test fun `fits is true only when the row stays above the page bottom`() {
        assertTrue(fits(cursorY = 100f, rowHeight = 20f, pageBottom = 130f))
        assertTrue(fits(cursorY = 100f, rowHeight = 30f, pageBottom = 130f)) // exact fit
        assertFalse(fits(cursorY = 120f, rowHeight = 20f, pageBottom = 130f))
    }

    @Test fun `flattenPreventive groups section, equipment and checkpoint rows in order`() {
        val detail = ChecklistRecordDetail(
            id = 1, formType = "MONTHLY", status = "SUBMITTED", docNo = "CFPLA.C4.F.50a",
            checklistDate = "2026-06-26", doneBy = "A", checkedBy = "B", verifiedBy = "C",
            remarks = "", createdAt = "",
            items = listOf(
                ChecklistRecordItem("Lower Basement", "Lift 1 Old", 1, "", "Doors", "OK", ""),
                ChecklistRecordItem("Lower Basement", "Lift 1 Old", 1, "", "Switches", "NOT_OK", "loose"),
                ChecklistRecordItem("Service Floor", "Disel Generator", 15, "", "Valve", "OK", ""),
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
            items = listOf(ChecklistRecordItem("S", "E", 1, "", "  ", "OK", "")),
        )
        val rows = flattenPreventive(detail)
        assertEquals(PreventiveRow.Checkpoint("General check", "OK", ""), rows[2])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from repo root): `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.ChecklistPdfModelTest"`
Expected: FAIL — unresolved references (`PdfForm`, `docMetaFor`, etc.).

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/candorfoods/factoryops/util/pdf/ChecklistPdfModel.kt`:

```kotlin
package com.candorfoods.factoryops.util.pdf

import com.candorfoods.factoryops.domain.model.ChecklistRecordDetail

/** The three controlled-copy formats we can export. */
enum class PdfForm { PREVENTIVE_MONTHLY, PREVENTIVE_QUARTERLY, BREAKDOWN }

/** Static header metadata reproduced from the controlled-copy PDFs in `public/`. */
data class DocMeta(
    val documentNo: String,
    val formatTitle: String,
    val issueNo: String,
    val revisionNo: String,
    val issueDate: String,
    val revisionDate: String,
    val landscape: Boolean,
)

fun docMetaFor(form: PdfForm): DocMeta = when (form) {
    PdfForm.PREVENTIVE_MONTHLY -> DocMeta(
        documentNo = "CFPLA.C4.F.50a",
        formatTitle = "Preventive Maintenance Checklist- Monthly",
        issueNo = "06", revisionNo = "05",
        issueDate = "01/11/2027", revisionDate = "13/12/2025",
        landscape = false,
    )
    PdfForm.PREVENTIVE_QUARTERLY -> DocMeta(
        documentNo = "CFPLA.C4.F.50b",
        formatTitle = "Preventive Maintenance Checklist-Quarterly",
        issueNo = "05", revisionNo = "04",
        issueDate = "01/11/2027", revisionDate = "01/10/2025",
        landscape = false,
    )
    PdfForm.BREAKDOWN -> DocMeta(
        documentNo = "CFPLA.C4.F.06",
        formatTitle = "Breakdown Maintainance Record",
        issueNo = "04", revisionNo = "03",
        issueDate = "01/11/2017", revisionDate = "01/10/2025",
        landscape = true,
    )
}

/** Map a stored record's `formType` ("MONTHLY"/"QUARTERLY") to a [PdfForm]. */
fun pdfFormFor(formType: String): PdfForm = when (formType.trim().uppercase()) {
    "QUARTERLY" -> PdfForm.PREVENTIVE_QUARTERLY
    else        -> PdfForm.PREVENTIVE_MONTHLY
}

/** A flattened render row for the preventive body table. */
sealed interface PreventiveRow {
    data class SectionHeader(val title: String) : PreventiveRow
    data class EquipmentHeader(val title: String) : PreventiveRow
    data class Checkpoint(val name: String, val status: String, val remarks: String) : PreventiveRow
}

/**
 * Flatten a stored record into ordered render rows, mirroring the grouping used by
 * PreventiveDetailScreen: a SectionHeader when the section changes, an EquipmentHeader
 * when section/equipment/srNo changes, then one Checkpoint per item.
 */
fun flattenPreventive(detail: ChecklistRecordDetail): List<PreventiveRow> {
    val out = mutableListOf<PreventiveRow>()
    detail.items.forEachIndexed { index, item ->
        val prev = detail.items.getOrNull(index - 1)
        val newSection = prev == null || prev.section != item.section
        val newEquipment = newSection || prev?.equipment != item.equipment || prev?.srNo != item.srNo
        if (newSection && item.section.isNotBlank()) {
            out.add(PreventiveRow.SectionHeader(item.section.uppercase()))
        }
        if (newEquipment) {
            val name = buildString {
                if (item.srNo != null) append("${item.srNo}. ")
                append(item.equipment)
                if (item.equipmentDate.isNotBlank()) append("  ·  ${item.equipmentDate}")
            }
            out.add(PreventiveRow.EquipmentHeader(name))
        }
        out.add(
            PreventiveRow.Checkpoint(
                name = item.checkpoint.ifBlank { "General check" },
                status = if (item.status.trim().uppercase() == "OK") "OK" else "NOT OK",
                remarks = item.remarks,
            )
        )
    }
    return out
}

/** One F.06 register row, already trimmed/derived from the form entries. */
data class BreakdownRow(
    val srNo: Int,
    val date: String,
    val location: String,
    val machineName: String,
    val equipmentModel: String,
    val problem: String,
    val typeOfMaintenance: String,
    val partOfMachine: String,
    val reason: String,
    val durationStart: String,
    val durationEnd: String,
    val machineOperatorSign: String,
    val maintenancePersonSign: String,
    val qcClearanceSign: String,
)

/** Render input for the breakdown PDF (built by the ViewModel from on-screen entries). */
data class BreakdownDocData(
    val docNo: String,
    val verifiedBy: String,
    val rows: List<BreakdownRow>,
)

/** Build a filesystem-safe output filename per form. */
fun pdfFileName(form: PdfForm, date: String, recordId: Int?): String {
    val safeDate = date.ifBlank { "record" }.trim().replace(Regex("[^A-Za-z0-9_-]"), "-")
    return when (form) {
        PdfForm.PREVENTIVE_MONTHLY   -> "Preventive_50a_Monthly_${safeDate}_rec${recordId ?: 0}.pdf"
        PdfForm.PREVENTIVE_QUARTERLY -> "Preventive_50b_Quarterly_${safeDate}_rec${recordId ?: 0}.pdf"
        PdfForm.BREAKDOWN            -> "Breakdown_F06_${safeDate}.pdf"
    }
}

/** True when a row of [rowHeight] starting at [cursorY] fits above [pageBottom]. */
fun fits(cursorY: Float, rowHeight: Float, pageBottom: Float): Boolean =
    cursorY + rowHeight <= pageBottom
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.ChecklistPdfModelTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/util/pdf/ChecklistPdfModel.kt \
        src/test/java/com/candorfoods/factoryops/ChecklistPdfModelTest.kt
git commit -m "feat(pdf): add pure checklist PDF model + transforms"
```

---

## Task 2: Expose the PDF cache dir to FileProvider

**Files:**
- Modify: `src/main/res/xml/file_paths.xml`

- [ ] **Step 1: Add the cache path**

Replace the contents of `src/main/res/xml/file_paths.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="breakdown_after" path="breakdown_after/" />
    <files-path name="breakdown_qc"    path="breakdown_qc/" />
    <files-path name="transfer_photos" path="transfer_photos/" />
    <cache-path  name="checklist_pdfs" path="checklist_pdfs/" />
</paths>
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (resource is valid; nothing references it yet).

- [ ] **Step 3: Commit**

```bash
git add src/main/res/xml/file_paths.xml
git commit -m "feat(pdf): expose checklist_pdfs cache dir via FileProvider"
```

---

## Task 3: PdfShareLauncher

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/util/pdf/PdfShareLauncher.kt`

> No unit test — `FileProvider`/`Intent`/`startActivity` are Android framework calls verified by build + Task 8.

- [ ] **Step 1: Write the implementation**

Create `src/main/java/com/candorfoods/factoryops/util/pdf/PdfShareLauncher.kt`:

```kotlin
package com.candorfoods.factoryops.util.pdf

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Opens the Android share sheet for a generated PDF, reusing the app's FileProvider. */
object PdfShareLauncher {
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file.canonicalFile,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/util/pdf/PdfShareLauncher.kt
git commit -m "feat(pdf): add PdfShareLauncher (ACTION_SEND chooser)"
```

---

## Task 4: CandorPdfCanvas (low-level drawing)

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/util/pdf/CandorPdfCanvas.kt`

> No unit test — drawing is stubbed in JVM tests. Verified by build + Task 8. Uses `fits` from Task 1.

- [ ] **Step 1: Write the implementation**

Create `src/main/java/com/candorfoods/factoryops/util/pdf/CandorPdfCanvas.kt`:

```kotlin
package com.candorfoods.factoryops.util.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

/**
 * Low-level drawing over a [PdfDocument]: the Candor header block, footer, and a simple
 * weighted-column table with automatic page breaks. Knows nothing about checklists — only
 * pages, cells, and text. One instance renders one document. Lifecycle:
 * [startDocument] → row()/banner()/textLine()/gap() … → [finish].
 */
class CandorPdfCanvas(
    private val doc: PdfDocument,
    private val landscape: Boolean,
    private val meta: DocMeta,
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

        // Logo wordmark (no logo asset available; styled text stands in).
        c.drawText("Candor", contentLeft + 12f, top + 26f, brand)
        c.drawText("Foods", contentLeft + 12f, top + 44f, brand)

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
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/util/pdf/CandorPdfCanvas.kt
git commit -m "feat(pdf): add CandorPdfCanvas (header/footer/table + page breaks)"
```

---

## Task 5: ChecklistPdfRenderer

**Files:**
- Create: `src/main/java/com/candorfoods/factoryops/util/pdf/ChecklistPdfRenderer.kt`

> No unit test — drawing/IO is verified by build + Task 8. Uses Task 1 model + Task 4 canvas.

- [ ] **Step 1: Write the implementation**

Create `src/main/java/com/candorfoods/factoryops/util/pdf/ChecklistPdfRenderer.kt`:

```kotlin
package com.candorfoods.factoryops.util.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
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

    suspend fun renderPreventive(detail: ChecklistRecordDetail): File = withContext(Dispatchers.Default) {
        val form = pdfFormFor(detail.formType)
        val meta = docMetaFor(form)
        val doc = PdfDocument()
        val cv = CandorPdfCanvas(doc, landscape = meta.landscape, meta = meta)
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
    }

    suspend fun renderBreakdown(data: BreakdownDocData): File = withContext(Dispatchers.Default) {
        val meta = docMetaFor(PdfForm.BREAKDOWN)
        val doc = PdfDocument()
        val cv = CandorPdfCanvas(doc, landscape = true, meta = meta)
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
    }

    private fun writeToCache(doc: PdfDocument, fileName: String): File {
        val dir = File(context.cacheDir, "checklist_pdfs").apply { if (!exists()) mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
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
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/util/pdf/ChecklistPdfRenderer.kt
git commit -m "feat(pdf): add ChecklistPdfRenderer for preventive + breakdown"
```

---

## Task 6: Wire the Preventive detail download action

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/presentation/checklist/PreventiveDetailViewModel.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/presentation/checklist/PreventiveDetailScreen.kt`

### 6a — ViewModel

- [ ] **Step 1: Add export state + actions to the ViewModel**

In `PreventiveDetailViewModel.kt`:

Add imports (below the existing imports):

```kotlin
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import java.io.File
```

Replace the `PreventiveDetailUiState` data class with:

```kotlin
data class PreventiveDetailUiState(
    val isLoading: Boolean = true,
    val record: ChecklistRecordDetail? = null,
    val error: String? = null,
    val isExporting: Boolean = false,
    val pdfToShare: File? = null,
    val exportError: String? = null,
)
```

Add `pdfRenderer` to the constructor:

```kotlin
@HiltViewModel
class PreventiveDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val checklistRepository: ChecklistRepository,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {
```

Add these methods inside the class (after `load()`):

```kotlin
    fun exportPdf() {
        val record = _uiState.value.record ?: return
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }
            try {
                val file = pdfRenderer.renderPreventive(record)
                _uiState.update { it.copy(isExporting = false, pdfToShare = file) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isExporting = false, exportError = e.message ?: "Couldn't create PDF")
                }
            }
        }
    }

    fun onPdfShared() = _uiState.update { it.copy(pdfToShare = null) }

    fun onExportErrorShown() = _uiState.update { it.copy(exportError = null) }
```

### 6b — Screen

- [ ] **Step 2: Add the download action, share effect, and snackbar to the screen**

In `PreventiveDetailScreen.kt`:

Add imports:

```kotlin
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.util.pdf.PdfShareLauncher
```

Replace the `Scaffold(...) { padding -> ... }` block (the whole `Scaffold` call in `PreventiveDetailScreen`) with:

```kotlin
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.pdfToShare) {
        uiState.pdfToShare?.let { file ->
            PdfShareLauncher.share(context, file)
            viewModel.onPdfShared()
        }
    }
    LaunchedEffect(uiState.exportError) {
        uiState.exportError?.let {
            snackbarHostState.showSnackbar("Couldn't create PDF — $it")
            viewModel.onExportErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checklist Record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = viewModel::exportPdf,
                            enabled = uiState.record != null,
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download PDF")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> Text(
                    "Couldn't load — ${uiState.error}",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
                uiState.record != null -> RecordBody(uiState.record!!)
            }
        }
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/presentation/checklist/PreventiveDetailViewModel.kt \
        src/main/java/com/candorfoods/factoryops/presentation/checklist/PreventiveDetailScreen.kt
git commit -m "feat(pdf): add Download PDF action to preventive record detail"
```

---

## Task 7: Wire the Breakdown form download action

**Files:**
- Modify: `src/main/java/com/candorfoods/factoryops/presentation/checklist/BreakdownFormViewModel.kt`
- Modify: `src/main/java/com/candorfoods/factoryops/presentation/checklist/BreakdownFormScreen.kt`

### 7a — ViewModel

- [ ] **Step 1: Add export state + actions to the ViewModel**

In `BreakdownFormViewModel.kt`:

Add imports:

```kotlin
import com.candorfoods.factoryops.util.pdf.BreakdownDocData
import com.candorfoods.factoryops.util.pdf.BreakdownRow
import com.candorfoods.factoryops.util.pdf.ChecklistPdfRenderer
import java.io.File
```

Add the new fields to `BreakdownFormState` (and a `canDownload` derived flag). Replace the `BreakdownFormState` data class with:

```kotlin
data class BreakdownFormState(
    val entries: List<BreakdownEntryState> = emptyList(),
    val verifiedBy: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    // Machine register for the Machine Name search/dropdown.
    val machineOptions: List<MachineOption> = emptyList(),
    val machinesError: String? = null,
    // PDF export.
    val isExporting: Boolean = false,
    val pdfToShare: File? = null,
    val exportError: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && entries.isNotEmpty() && entries.all { it.isValid }

    /** At least one complete row is needed to produce a non-empty sheet. */
    val canDownload: Boolean
        get() = !isExporting && entries.any { it.isValid }
}
```

Add `pdfRenderer` to the constructor:

```kotlin
@HiltViewModel
class BreakdownFormViewModel @Inject constructor(
    private val checklistRepository: ChecklistRepository,
    private val getMachineMaster: GetMachineMasterUseCase,
    private val pdfRenderer: ChecklistPdfRenderer,
) : ViewModel() {
```

Add these methods inside the class (after `submit()` / `onSubmittedHandled()`):

```kotlin
    /** Build the F.06 PDF from the complete on-screen entries and emit it for sharing. */
    fun exportPdf() {
        val s = _state.value
        if (s.isExporting) return
        val rows = s.entries.filter { it.isValid }.mapIndexed { i, e ->
            BreakdownRow(
                srNo = i + 1,
                date = e.date.trim(),
                location = e.location.trim(),
                machineName = e.machineName.trim(),
                equipmentModel = e.equipmentModel.trim(),
                problem = e.problem.trim(),
                typeOfMaintenance = e.typeOfMaintenance.trim(),
                partOfMachine = e.partOfMachine.trim(),
                reason = e.reason.trim(),
                durationStart = e.durationStart.trim(),
                durationEnd = e.durationEnd.trim(),
                machineOperatorSign = e.machineOperatorSign.trim(),
                maintenancePersonSign = e.maintenancePersonSign.trim(),
                qcClearanceSign = e.qcClearanceSign.trim(),
            )
        }
        if (rows.isEmpty()) {
            _state.update { it.copy(exportError = "Add at least one entry (Date, Machine, Problem).") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, exportError = null) }
            try {
                val file = pdfRenderer.renderBreakdown(
                    BreakdownDocData(
                        docNo = "CFPLA.C4.F.06",
                        verifiedBy = s.verifiedBy.trim(),
                        rows = rows,
                    )
                )
                _state.update { it.copy(isExporting = false, pdfToShare = file) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isExporting = false, exportError = e.message ?: "Couldn't create PDF")
                }
            }
        }
    }

    fun onPdfShared() = _state.update { it.copy(pdfToShare = null) }

    fun onExportErrorShown() = _state.update { it.copy(exportError = null) }
```

### 7b — Screen

- [ ] **Step 2: Add the download action + share effect to the screen**

In `BreakdownFormScreen.kt`:

Add imports:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.util.pdf.PdfShareLauncher
```

Add a `context` handle next to the existing `snackbarHostState` (just after `val snackbarHostState = remember { SnackbarHostState() }`):

```kotlin
    val context = LocalContext.current
```

Add these two effects right after the existing `LaunchedEffect(state.error) { ... }` block:

```kotlin
    LaunchedEffect(state.pdfToShare) {
        state.pdfToShare?.let { file ->
            PdfShareLauncher.share(context, file)
            viewModel.onPdfShared()
        }
    }
    LaunchedEffect(state.exportError) {
        state.exportError?.let {
            snackbarHostState.showSnackbar("Couldn't create PDF — $it")
            viewModel.onExportErrorShown()
        }
    }
```

Add an `actions` block to the `TopAppBar` (it currently has only `title` and `navigationIcon`). Insert after the `navigationIcon = { ... },` line, before the `TopAppBar`'s closing `)`:

```kotlin
                actions = {
                    if (state.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = viewModel::exportPdf, enabled = state.canDownload) {
                            Icon(Icons.Default.Download, contentDescription = "Download PDF")
                        }
                    }
                },
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/candorfoods/factoryops/presentation/checklist/BreakdownFormViewModel.kt \
        src/main/java/com/candorfoods/factoryops/presentation/checklist/BreakdownFormScreen.kt
git commit -m "feat(pdf): add Download PDF action to breakdown F.06 form"
```

---

## Task 8: Full unit suite + manual on-device verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, including `ChecklistPdfModelTest`.

- [ ] **Step 2: Build and install the debug app**

Run: `./gradlew :app:installDebug` (with an emulator/device connected).
Expected: BUILD SUCCESSFUL, app installed.

- [ ] **Step 3: Manually verify all three documents**

Open the app and confirm each export opens the share sheet and produces a correct PDF (Save to Files, then open):

1. **Preventive Monthly (50a):** open a stored Monthly record → tap the Download icon (top bar) → share → open the PDF. Confirm: Candor header block (company name, `Format: Preventive Maintenance Checklist- Monthly`, `Document No: CFPLA.C4.F.50a`, Issue No 06 / Rev No 05), summary line (Date/Done by/Checked by/Verified by), section banners → equipment headers → checkpoint rows with OK / NOT OK + remarks, footer (`Prepared By: FST` · `CONTROLLED COPY` · `Approved By: FSTL`) on every page, multi-page flows correctly.
2. **Preventive Quarterly (50b):** repeat with a Quarterly record. Confirm `Document No: CFPLA.C4.F.50b`, Issue No 05 / Rev No 04.
3. **Breakdown (F.06):** open the Breakdown form, add ≥1 complete entry (Date, Machine, Problem), tap Download → share → open. Confirm landscape orientation, `Document No: CFPLA.C4.F.06`, the 14-column register with one row per entry, `Verified By:` line, footer.

Also confirm: the Download action is **disabled** on an empty Breakdown form (no complete entry), and a render failure surfaces the "Couldn't create PDF" snackbar rather than crashing.

- [ ] **Step 4: Commit (if any tweaks were needed during manual verification)**

```bash
git add -A
git commit -m "test(pdf): verify 50a/50b/F.06 export end-to-end"
```

---

## Self-review

**Spec coverage:**
- Preventive export from stored record → Tasks 1, 5, 6. ✅
- Breakdown export from on-screen form entries → Tasks 1, 5, 7. ✅
- Share-sheet delivery via FileProvider → Tasks 2, 3. ✅
- Faithful Candor format (header/table/footer, per-form metadata, portrait vs landscape) → Tasks 1, 4, 5. ✅
- Native PdfDocument, no new dependency → Tasks 4, 5 (no `build.gradle.kts`/catalog change). ✅
- One-shot `pdfToShare` / `onPdfShared()` pattern → Tasks 6, 7. ✅
- Off-main-thread render + error snackbar + disabled-empty → Tasks 5, 6, 7. ✅
- Filenames → Task 1 (`pdfFileName`), used in Task 5. ✅
- Testing strategy (pure logic unit-tested; drawing manual) → Tasks 1, 8. ✅

**Placeholder scan:** No TBD/TODO; every code step shows complete code; every command has an expected result.

**Type consistency:** `ChecklistPdfRenderer.renderPreventive(ChecklistRecordDetail)` / `renderBreakdown(BreakdownDocData)`; `BreakdownDocData(docNo, verifiedBy, rows)` + `BreakdownRow(...)` field names match between Task 1 (definition), Task 5 (consumption) and Task 7 (construction). `exportPdf()`/`onPdfShared()`/`onExportErrorShown()` and state fields `isExporting`/`pdfToShare`/`exportError` are named identically across ViewModel and Screen tasks. `fits(cursorY, rowHeight, pageBottom)` signature matches between Task 1 and Task 4. `PdfShareLauncher.share(context, file)` matches between Task 3 and Tasks 6/7.

**Out of scope (per spec):** stored-breakdown-record export, real logo asset, pixel-perfect blank grid, server-side generation.
