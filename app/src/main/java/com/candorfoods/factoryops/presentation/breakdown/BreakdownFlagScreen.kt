package com.candorfoods.factoryops.presentation.breakdown

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.domain.model.Severity
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.components.LoadingOverlay
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import androidx.core.content.ContextCompat
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme
import com.candorfoods.factoryops.util.BreakdownPhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownFlagScreen(
    onBack: () -> Unit,
    viewModel: BreakdownFlagViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is BreakdownFlagUiEvent.FlagRaised     -> onBack()
                is BreakdownFlagUiEvent.ShowSnackbar   -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LoadingOverlay(isLoading = uiState.isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.breakdown_flag_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.breakdown_back)) } }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.breakdown_severity), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Severity.values().forEach { sev ->
                        val selected = uiState.severity == sev
                        val color = when (sev) {
                            Severity.CRITICAL -> FactoryTheme.colors.statusStopped
                            Severity.MAJOR    -> FactoryTheme.colors.statusFlagged
                            Severity.MINOR    -> MaterialTheme.colorScheme.primary
                        }
                        FilterChip(
                            selected = selected,
                            onClick  = { viewModel.onSeverityChanged(sev) },
                            label    = { Text(sev.name) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.2f),
                                selectedLabelColor     = color
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value         = uiState.description,
                    onValueChange = viewModel::onDescriptionChanged,
                    label         = { Text(stringResource(R.string.breakdown_description_label)) },
                    placeholder   = { Text(stringResource(R.string.breakdown_description_placeholder)) },
                    minLines      = 4,
                    modifier      = Modifier.fillMaxWidth(),
                    isError       = uiState.error != null
                )

                if (uiState.error != null) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                BeforePhotoCard(
                    machineId  = viewModel.machineId,
                    localPath  = uiState.beforePhotoLocalPath,
                    onPicked   = viewModel::onPhotoSelected,
                )

                PrimaryButton(
                    text     = stringResource(R.string.breakdown_submit),
                    onClick  = viewModel::onSubmit,
                    style    = PrimaryButtonStyle.Danger,
                    size     = PrimaryButtonSize.Primary,
                    loading  = uiState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The operator's optional photo of the fault.
 *
 * This card used to be inert text reading "available once permissions granted" —
 * there was no click handler at all, so tapping it did nothing. Everything below
 * the UI was already built: the ViewModel has onPhotoSelected, and the repository
 * uploads the file as a multipart `before_photo`.
 *
 * IT MUST HAND BACK AN ABSOLUTE FILE PATH, NOT A URI. BreakdownRepositoryImpl's
 * photoPart() does `File(localPath)` and returns null when that file does not
 * exist, so passing a `content://` string (which is what a naive
 * GetContent()/TakePicture() wiring yields) would drop the photo silently: the
 * flag submits fine, with no image and no error. Hence the gallery branch copies
 * the picked bytes into our own file first.
 */
@Composable
private fun BeforePhotoCard(
    machineId: String,
    localPath: String?,
    onPicked: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingFile
        // Only keep it if the camera actually wrote something. A cancelled capture
        // leaves a zero-byte file, which photoPart would reject anyway.
        if (success && file != null && file.length() > 0L) onPicked(file.absolutePath)
        else file?.delete()
        pendingFile = null
    }

    fun startCamera() {
        val file = BreakdownPhotoStorage.createBeforePhotoFile(context, machineId)
        pendingFile = file
        cameraLauncher.launch(BreakdownPhotoStorage.fileToShareUri(context, file))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startCamera() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val file = BreakdownPhotoStorage.createBeforePhotoFile(context, machineId)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file.takeIf { it.length() > 0L }?.absolutePath
                }.getOrNull()
            }
            onPicked(path)
        }
    }

    FactoryCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.breakdown_photo_optional),
                style = MaterialTheme.typography.titleSmall,
            )

            if (localPath != null) {
                Text(
                    stringResource(R.string.breakdown_photo_attached, localPath.takeLast(20)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) startCamera()
                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                    }) { Text(stringResource(R.string.breakdown_photo_retake)) }
                    OutlinedButton(onClick = { onPicked(null) }) {
                        Text(stringResource(R.string.breakdown_photo_remove))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.breakdown_photo_capture_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = FactoryTheme.colors.onSurfaceMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) startCamera()
                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                    }) {
                        Icon(
                            Icons.Default.PhotoCamera, contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.breakdown_photo_take))
                    }
                    // A second way in: on a device whose camera app misbehaves, an
                    // operator can still attach a shot they already took.
                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text(stringResource(R.string.breakdown_photo_choose))
                    }
                }
            }
        }
    }
}
