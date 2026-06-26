package com.candorfoods.factoryops.presentation.qc

import kotlinx.serialization.Serializable

enum class CheckResult { PASS, FAIL, NA, UNANSWERED }

@Serializable
data class QcChecklistSnapshot(
    val visual: VisualSection,
    val hygiene: HygieneSection,
    val calibration: CalibrationSection,
    val function: FunctionSection,
    val notes: String?
)

@Serializable
data class VisualSection(
    val noForeignMatter: String,
    val noOilLeaks: String,
    val guardsInPlace: String
)

@Serializable
data class HygieneSection(
    val isFoodContact: Boolean,
    val surfacesSanitized: String,
    val cleaningResidueOk: String
)

@Serializable
data class CalibrationSection(
    val isCalibrated: Boolean,
    val calibrationVerified: String
)

@Serializable
data class FunctionSection(
    val testRunCompleted: String,
    val outputSampleChecked: String
)

data class QcChecklistDraft(
    val noForeignMatter: CheckResult = CheckResult.UNANSWERED,
    val noOilLeaks: CheckResult = CheckResult.UNANSWERED,
    val guardsInPlace: CheckResult = CheckResult.UNANSWERED,

    val isFoodContact: Boolean = true,
    val surfacesSanitized: CheckResult = CheckResult.UNANSWERED,
    val cleaningResidueOk: CheckResult = CheckResult.UNANSWERED,

    val isCalibrated: Boolean = false,
    val calibrationVerified: CheckResult = CheckResult.UNANSWERED,

    val testRunCompleted: CheckResult = CheckResult.UNANSWERED,
    val outputSampleChecked: CheckResult = CheckResult.UNANSWERED,

    val notes: String = "",
    val afterPhotoLocalPath: String? = null
) {
    /**
     * Approve eligibility: every applicable required item is PASS and the
     * after-QC photo is captured. N/A is allowed only on outputSampleChecked.
     */
    val canApprove: Boolean
        get() {
            if (afterPhotoLocalPath == null) return false

            // Visual — all 3 must be PASS.
            if (noForeignMatter != CheckResult.PASS) return false
            if (noOilLeaks != CheckResult.PASS) return false
            if (guardsInPlace != CheckResult.PASS) return false

            // Hygiene applies only when toggle is on.
            if (isFoodContact) {
                if (surfacesSanitized != CheckResult.PASS) return false
                if (cleaningResidueOk != CheckResult.PASS) return false
            }

            // Calibration applies only when toggle is on.
            if (isCalibrated) {
                if (calibrationVerified != CheckResult.PASS) return false
            }

            // Function — test run mandatory; output sample may be N/A.
            if (testRunCompleted != CheckResult.PASS) return false
            if (outputSampleChecked != CheckResult.PASS &&
                outputSampleChecked != CheckResult.NA) return false

            return true
        }

    /**
     * Disapprove eligibility: after-QC photo present (FSSAI/BRC needs visual
     * evidence even on rejection) and at least one item answered, so we don't
     * record an empty checklist.
     */
    val canDisapprove: Boolean
        get() {
            if (afterPhotoLocalPath == null) return false
            return listOf(
                noForeignMatter, noOilLeaks, guardsInPlace,
                surfacesSanitized, cleaningResidueOk,
                calibrationVerified, testRunCompleted, outputSampleChecked
            ).any { it != CheckResult.UNANSWERED }
        }

    fun toSnapshot(): QcChecklistSnapshot = QcChecklistSnapshot(
        visual = VisualSection(
            noForeignMatter = noForeignMatter.name,
            noOilLeaks = noOilLeaks.name,
            guardsInPlace = guardsInPlace.name
        ),
        hygiene = HygieneSection(
            isFoodContact = isFoodContact,
            surfacesSanitized = surfacesSanitized.name,
            cleaningResidueOk = cleaningResidueOk.name
        ),
        calibration = CalibrationSection(
            isCalibrated = isCalibrated,
            calibrationVerified = calibrationVerified.name
        ),
        function = FunctionSection(
            testRunCompleted = testRunCompleted.name,
            outputSampleChecked = outputSampleChecked.name
        ),
        notes = notes.takeIf { it.isNotBlank() }
    )
}
