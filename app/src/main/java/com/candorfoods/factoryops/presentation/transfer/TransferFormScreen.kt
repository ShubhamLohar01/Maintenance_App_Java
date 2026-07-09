package com.candorfoods.factoryops.presentation.transfer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.candorfoods.factoryops.util.TransferPhotoStorage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/** Machine Transfer form. Submits to POST /machine-transfers (multipart/form-data). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferFormScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: TransferFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val transferRecordedMessage = stringResource(R.string.transfer_form_transfer_recorded)

    LaunchedEffect(state.submittedOk) {
        if (state.submittedOk) {
            snackbarHostState.showSnackbar(transferRecordedMessage)
            viewModel.consumeSubmitResult()
            onSubmitted()
        }
    }
    LaunchedEffect(state.submitError) {
        state.submitError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSubmitResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.transfer_form_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.transfer_form_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.transfer_form_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Edge-to-edge draws behind the keyboard; shrink the scroll viewport to
                // above the IME so the focused bottom field scrolls into view (not hidden).
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ─── Route ───────────────────────────────────────────────────────
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionTitle(stringResource(R.string.transfer_form_section_route))
                    Field(stringResource(R.string.transfer_form_date_label), state.date) { v -> viewModel.update { it.copy(date = v) } }
                    WarehouseSwapRow(
                        from = state.fromWarehouse,
                        to = state.toWarehouse,
                        onSwap = { viewModel.swapWarehouses() },
                    )
                }
            }

            // ─── Machine ─────────────────────────────────────────────────────
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionTitle(stringResource(R.string.transfer_form_section_machine))
                    MachineAutocompleteField(
                        value = state.machineName,
                        suggestions = state.machineSuggestions,
                        onValueChange = { v -> viewModel.onMachineNameChanged(v) },
                        onPick = { opt -> viewModel.onMachinePicked(opt) },
                    )
                    if (state.machineSuggestions.isEmpty() && state.machinesError == null) {
                        Text(
                            stringResource(R.string.transfer_form_loading_machines),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    if (state.machinesError != null) {
                        Text(
                            stringResource(R.string.transfer_form_machines_load_error, state.machinesError!!),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Field(stringResource(R.string.transfer_form_machine_code_label), state.machineCode) { v ->
                        viewModel.update { it.copy(machineCode = v) }
                    }
                    DropdownField(
                        label = stringResource(R.string.transfer_form_condition_label),
                        value = state.condition,
                        options = TransferFormViewModel.CONDITIONS,
                    ) { v -> viewModel.update { it.copy(condition = v) } }
                }
            }

            // ─── Proof photo ─────────────────────────────────────────────────
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionTitle(stringResource(R.string.transfer_form_section_proof))
                    ProofImagePicker(
                        imageUri = state.proofImageUri,
                        onPicked = { uri -> viewModel.update { it.copy(proofImageUri = uri) } },
                        onClear = { viewModel.update { it.copy(proofImageUri = null) } },
                    )
                }
            }

            // ─── Authorisation ───────────────────────────────────────────────
            FactoryCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionTitle(stringResource(R.string.transfer_form_section_authorisation))
                    Field(stringResource(R.string.transfer_form_reason_label), state.reason, singleLine = false) { v ->
                        viewModel.update { it.copy(reason = v) }
                    }
                    Field(stringResource(R.string.transfer_form_authorised_by_label), state.authorisedPerson) { v ->
                        viewModel.update { it.copy(authorisedPerson = v) }
                    }
                    Field(stringResource(R.string.transfer_form_remarks_label), state.remarks, singleLine = false) { v ->
                        viewModel.update { it.copy(remarks = v) }
                    }
                }
            }

            PrimaryButton(
                text = if (state.submitting) stringResource(R.string.transfer_form_submitting) else stringResource(R.string.transfer_form_submit),
                onClick = { viewModel.submit() },
                enabled = state.canSubmit && !state.submitting,
                size = PrimaryButtonSize.Hero,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

/** From → To direction for the only two warehouses; the arrow swaps them. */
@Composable
private fun WarehouseSwapRow(from: String, to: String, onSwap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        WarehouseBox(stringResource(R.string.transfer_form_warehouse_from), from, Modifier.weight(1f))
        FilledTonalIconButton(onClick = onSwap) {
            Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.transfer_form_switch_warehouses))
        }
        WarehouseBox(stringResource(R.string.transfer_form_warehouse_to), to, Modifier.weight(1f))
    }
}

@Composable
private fun WarehouseBox(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier,
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Read-only picker (From/To warehouse, Condition). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Editable field with live suggestions — type to search OR enter a name manually. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MachineAutocompleteField(
    value: String,
    suggestions: List<MachineOption>,
    onValueChange: (String) -> Unit,
    onPick: (MachineOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, suggestions) {
        if (value.isBlank()) suggestions
        else suggestions.filter { it.name.contains(value, ignoreCase = true) }
    }
    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.transfer_form_machine_name_label)) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filtered.take(8).forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.name)
                                // asset_id + building + floor disambiguate repeated names
                                Text(
                                    listOf(option.assetId, option.building, option.subLocation)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        },
                        onClick = {
                            onPick(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProofImagePicker(
    imageUri: String?,
    onPicked: (String) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? -> if (uri != null) onPicked(uri.toString()) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingUri
        if (success && uri != null) onPicked(uri.toString())
        pendingUri = null
    }

    // Capture a live photo into our FileProvider file, then keep the uri on success.
    fun startCamera() {
        val file = TransferPhotoStorage.createPhotoFile(context)
        val uri = TransferPhotoStorage.fileToShareUri(context, file)
        pendingUri = uri
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startCamera() }

    fun takePhoto() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (imageUri == null) {
        Button(onClick = { takePhoto() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.transfer_form_take_photo))
        }
        OutlinedButton(
            onClick = { galleryPicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.transfer_form_choose_gallery))
        }
    } else {
        AsyncImage(
            model = imageUri,
            contentDescription = stringResource(R.string.transfer_form_proof_photo_desc),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedButton(onClick = { takePhoto() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.transfer_form_retake))
            }
            OutlinedButton(onClick = { galleryPicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.transfer_form_gallery))
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.transfer_form_remove))
            }
        }
    }
}
