package com.candorfoods.factoryops.presentation.w202

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.w202.W202MeterKind
import com.candorfoods.factoryops.domain.model.w202.W202MeterReading
import com.candorfoods.factoryops.domain.repository.W202ReadingsRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.candorfoods.factoryops.util.excel.ReadingSheets
import com.candorfoods.factoryops.util.excel.SpreadsheetExporter
import javax.inject.Inject

data class W202HistoryState(
    val kind: W202MeterKind = W202MeterKind.MAIN,
    val isLoading: Boolean = true,
    val readings: List<W202MeterReading> = emptyList(),
    val error: String? = null,
    /** Delete is HEAD-only (the backend 403s anyone else); hide the affordance otherwise. */
    val canDelete: Boolean = false,
    /** Date awaiting delete confirmation; null = no dialog showing. */
    val pendingDelete: String? = null,
    val deleteError: String? = null,
    /** One-shot status line from a download, shown as a snackbar then cleared. */
    val downloadMessage: String? = null,
    val isDownloading: Boolean = false,
)

@HiltViewModel
class W202ReadingHistoryViewModel @Inject constructor(
    private val repo: W202ReadingsRepository,
    private val exporter: SpreadsheetExporter,
    getCurrentUser: GetCurrentUserUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Download the listed readings as .xlsx, named after this meter. */
    fun onDownload() {
        val st = _state.value
        if (st.isDownloading) return
        viewModelScope.launch {
            _state.update { it.copy(isDownloading = true) }
            val title = st.kind.name.lowercase().replaceFirstChar { c -> c.uppercase() } + " meter reading"
            val message = exporter.export(ReadingSheets.w202Meter(title, st.readings), title)
            _state.update { it.copy(isDownloading = false, downloadMessage = message) }
        }
    }

    fun onDownloadMessageShown() = _state.update { it.copy(downloadMessage = null) }

    private val kind = W202MeterKind.fromSlug(savedStateHandle.get<String>("kind"))

    private val _state = MutableStateFlow(W202HistoryState(kind = kind))
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            getCurrentUser().collect { user ->
                _state.update { it.copy(canDelete = user?.role == UserRole.HEAD) }
            }
        }
    }

    // ── delete (HEAD only) ───────────────────────────────────────────────────────
    fun askDelete(date: String) = _state.update { it.copy(pendingDelete = date, deleteError = null) }
    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    /** Confirm the pending delete, then reload so the list reflects the server. */
    fun confirmDelete() {
        val date = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            when (val r = repo.delete(kind, date)) {
                is Result.Success -> load()
                is Result.Error -> _state.update { it.copy(deleteError = r.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun clearDeleteError() = _state.update { it.copy(deleteError = null) }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.list(kind)) {   // newest first from the backend
                is Result.Success -> _state.update { it.copy(isLoading = false, readings = r.data) }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading -> Unit
            }
        }
    }
}
