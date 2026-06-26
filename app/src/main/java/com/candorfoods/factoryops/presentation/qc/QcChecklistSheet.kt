package com.candorfoods.factoryops.presentation.qc

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.presentation.common.theme.TouchTarget
import com.candorfoods.factoryops.util.BreakdownPhotoStorage
import java.io.File

private val AmberBorder = Color(0xFFF59E0B)
private val DarkAmberText = Color(0xFF92400E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QcChecklistSheet(
    machineName: String,
    machineCode: String,
    ticketId: String,
    draft: QcChecklistDraft,
    isSubmitting: Boolean,
    error: String?,
    onChange: (QcChecklistDraft) -> Unit,
    onApprove: () -> Unit,
    onDisapprove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var showDisapproveDialog by remember { mutableStateOf(false) }
    var disapproveReason by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        val newPath = if (success && file != null && file.exists() && file.length() > 0) file.absolutePath else null
        onChange(draft.copy(afterPhotoLocalPath = newPath ?: draft.afterPhotoLocalPath))
        pendingFile = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = BreakdownPhotoStorage.createQcPhotoFile(context, ticketId)
            pendingFile = file
            cameraLauncher.launch(BreakdownPhotoStorage.fileToShareUri(context, file))
        }
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            val file = BreakdownPhotoStorage.createQcPhotoFile(context, ticketId)
            pendingFile = file
            cameraLauncher.launch(BreakdownPhotoStorage.fileToShareUri(context, file))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("QC checklist",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("$machineName · $machineCode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            // Section 1 — visual
            ChecklistSection(title = "1. Visual inspection") {
                PassFailRow(
                    label = "No foreign matter on machine surface",
                    value = draft.noForeignMatter,
                    onChange = { onChange(draft.copy(noForeignMatter = it)) }
                )
                PassFailRow(
                    label = "No oil leaks visible",
                    value = draft.noOilLeaks,
                    onChange = { onChange(draft.copy(noOilLeaks = it)) }
                )
                PassFailRow(
                    label = "All guards and covers in place",
                    value = draft.guardsInPlace,
                    onChange = { onChange(draft.copy(guardsInPlace = it)) }
                )
            }

            // Section 2 — hygiene
            ChecklistSection(title = "2. Hygiene (food-contact)") {
                ToggleRow(
                    label = "Food-contact machine?",
                    checked = draft.isFoodContact,
                    onChange = { onChange(draft.copy(isFoodContact = it)) }
                )
                if (draft.isFoodContact) {
                    PassFailRow(
                        label = "Food-contact surfaces sanitized",
                        value = draft.surfacesSanitized,
                        onChange = { onChange(draft.copy(surfacesSanitized = it)) }
                    )
                    PassFailRow(
                        label = "Cleaning agent residue test (visual)",
                        value = draft.cleaningResidueOk,
                        onChange = { onChange(draft.copy(cleaningResidueOk = it)) }
                    )
                } else {
                    Text("Section marked N/A — won't count toward decision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            // Section 3 — calibration
            ChecklistSection(title = "3. Calibration") {
                ToggleRow(
                    label = "Calibrated equipment?",
                    checked = draft.isCalibrated,
                    onChange = { onChange(draft.copy(isCalibrated = it)) }
                )
                if (draft.isCalibrated) {
                    PassFailRow(
                        label = "Calibration verified within tolerance",
                        value = draft.calibrationVerified,
                        onChange = { onChange(draft.copy(calibrationVerified = it)) }
                    )
                } else {
                    Text("Section marked N/A — won't count toward decision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            // Section 4 — function
            ChecklistSection(title = "4. Function test") {
                PassFailRow(
                    label = "Test run completed (60 sec minimum)",
                    value = draft.testRunCompleted,
                    onChange = { onChange(draft.copy(testRunCompleted = it)) }
                )
                PassFailRow(
                    label = "Output sample checked",
                    value = draft.outputSampleChecked,
                    onChange = { onChange(draft.copy(outputSampleChecked = it)) },
                    allowNa = true
                )
            }

            // After-QC photo (mandatory)
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("After-QC photo (required)", fontWeight = FontWeight.SemiBold)
                    val photo = draft.afterPhotoLocalPath
                    if (photo != null) {
                        AsyncImage(
                            model = File(photo),
                            contentDescription = "QC after photo preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        )
                        TextButton(onClick = { launchCamera() }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retake")
                        }
                    } else {
                        PrimaryButton(
                            text = "Capture QC photo",
                            onClick = { launchCamera() },
                            style = PrimaryButtonStyle.Outlined,
                            size = PrimaryButtonSize.Min,
                            icon = Icons.Default.PhotoCamera,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Optional notes
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { v ->
                    val limited = if (v.length > 500) v.substring(0, 500) else v
                    onChange(draft.copy(notes = limited))
                },
                label = { Text("Notes (optional, ${draft.notes.length}/500)") },
                placeholder = { Text("e.g. minor discoloration on guard but within spec") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            // Action buttons
            PrimaryButton(
                text = "Approve",
                onClick = onApprove,
                enabled = !isSubmitting && draft.canApprove,
                loading = isSubmitting,
                style = PrimaryButtonStyle.Success,
                size = PrimaryButtonSize.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            // Intentional one-off amber styling (Warning semantic) — see TicketDetailScreen.
            Button(
                onClick = {
                    disapproveReason = ""
                    showDisapproveDialog = true
                },
                enabled = !isSubmitting && draft.canDisapprove,
                modifier = Modifier.fillMaxWidth().height(TouchTarget.primary),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberBorder,
                    contentColor = Color.Black
                )
            ) {
                Text("Disapprove", style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold)
            }

            if (!draft.canApprove && !draft.canDisapprove) {
                Text("Capture the QC photo and answer at least one item.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDisapproveDialog) {
        AlertDialog(
            onDismissRequest = { showDisapproveDialog = false },
            title = { Text("Disapprove — reason") },
            text = {
                Column {
                    OutlinedTextField(
                        value = disapproveReason,
                        onValueChange = { v ->
                            val limited = if (v.length > 500) v.substring(0, 500) else v
                            disapproveReason = limited
                        },
                        label = { Text("Why is QC sending this back? (${disapproveReason.length}/500)") },
                        placeholder = { Text("e.g. sanitization residue still visible on rollers") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Required: 4–500 characters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisapproveDialog = false
                        onDisapprove(disapproveReason)
                    },
                    enabled = disapproveReason.trim().length in 4..500
                ) {
                    Text("Submit disapproval", color = FactoryTheme.colors.statusStopped, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisapproveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ChecklistSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PassFailRow(
    label: String,
    value: CheckResult,
    onChange: (CheckResult) -> Unit,
    allowNa: Boolean = false
) {
    val passColor = FactoryTheme.colors.statusRunning
    val failColor = FactoryTheme.colors.statusStopped
    val naColor = FactoryTheme.colors.statusFlagged
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.selectableGroup().fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ResultChip("Pass", value == CheckResult.PASS, passColor) {
                onChange(CheckResult.PASS)
            }
            ResultChip("Fail", value == CheckResult.FAIL, failColor) {
                onChange(CheckResult.FAIL)
            }
            if (allowNa) {
                ResultChip("N/A", value == CheckResult.NA, naColor) {
                    onChange(CheckResult.NA)
                }
            }
        }
    }
}

@Composable
private fun ResultChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
    val fg = if (selected) color else MaterialTheme.colorScheme.onSurface
    val border = if (selected) color else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(8.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
