package com.candorfoods.factoryops.presentation.a185

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.a185.A185Reading
import com.candorfoods.factoryops.domain.repository.A185ReadingsRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.candorfoods.factoryops.util.excel.ReadingSheets
import com.candorfoods.factoryops.util.excel.SpreadsheetExporter
import javax.inject.Inject

data class A185HistoryState(
    val isLoading: Boolean = true,
    val readings: List<A185Reading> = emptyList(),
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
class A185ReadingHistoryViewModel @Inject constructor(
    private val repo: A185ReadingsRepository,
    private val exporter: SpreadsheetExporter,
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    /** Download the listed readings as .xlsx. */
    fun onDownload() {
        val st = _state.value
        if (st.isDownloading) return
        viewModelScope.launch {
            _state.update { it.copy(isDownloading = true) }
            val title = "HT Cold Room UV readings"
            val message = exporter.export(ReadingSheets.a185(title, st.readings), title)
            _state.update { it.copy(isDownloading = false, downloadMessage = message) }
        }
    }

    fun onDownloadMessageShown() = _state.update { it.copy(downloadMessage = null) }

    private val _state = MutableStateFlow(A185HistoryState())
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
            when (val r = repo.delete(date)) {
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
            when (val r = repo.list()) {   // newest first from the backend
                is Result.Success -> _state.update { it.copy(isLoading = false, readings = r.data) }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading -> Unit
            }
        }
    }
}
