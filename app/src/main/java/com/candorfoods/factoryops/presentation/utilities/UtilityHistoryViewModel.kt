package com.candorfoods.factoryops.presentation.utilities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.utilities.UtilityRecord
import com.candorfoods.factoryops.domain.model.utilities.UtilityType
import com.candorfoods.factoryops.domain.repository.UtilitiesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.candorfoods.factoryops.util.excel.ReadingSheets
import com.candorfoods.factoryops.util.excel.SpreadsheetExporter
import javax.inject.Inject

data class UtilityHistoryState(
    val records: List<UtilityRecord> = emptyList(),
    val plantFilter: String? = null,       // null = All
    val isLoading: Boolean = false,
    val error: String? = null,
    /** One-shot status line from a download, shown as a snackbar then cleared. */
    val downloadMessage: String? = null,
    val isDownloading: Boolean = false,
)

@HiltViewModel
class UtilityHistoryViewModel @Inject constructor(
    private val repo: UtilitiesRepository,
    private val exporter: SpreadsheetExporter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Download the listed records as .xlsx. Exports what is on screen, filters included. */
    fun onDownload() {
        val st = _state.value
        if (st.isDownloading) return
        viewModelScope.launch {
            _state.update { it.copy(isDownloading = true) }
            val title = type.name.lowercase().replaceFirstChar { c -> c.uppercase() } + " consumption"
            val message = exporter.export(ReadingSheets.utility(title, st.records), title)
            _state.update { it.copy(isDownloading = false, downloadMessage = message) }
        }
    }

    fun onDownloadMessageShown() = _state.update { it.copy(downloadMessage = null) }

    val type: UtilityType = UtilityType.fromSlug(savedStateHandle.get<String>("util")) ?: UtilityType.DIESEL

    private val _state = MutableStateFlow(UtilityHistoryState())
    val state = _state.asStateFlow()

    init { load() }

    fun setFilter(plant: String?) {
        _state.update { it.copy(plantFilter = plant) }
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.list(type, _state.value.plantFilter)) {
                is Result.Success -> _state.update { it.copy(isLoading = false, records = r.data) }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading -> Unit
            }
        }
    }
}
