package com.candorfoods.factoryops.presentation.tickets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.tickets.RaisedTicket
import com.candorfoods.factoryops.domain.repository.RaisedTicketRepository
import com.candorfoods.factoryops.domain.repository.TicketActionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RaisedTicketDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val ticket: RaisedTicket? = null,
    val isActing: Boolean = false,
    /** Transient banner: a 409, or a failed write. Cleared on dismiss. */
    val actionMessage: String? = null,
    val showResolveDialog: Boolean = false,
    val correctiveAction: String = "",
) {
    /** The server rejects a blank note with 400, so the button is disabled first. */
    val canSubmitResolve: Boolean get() = correctiveAction.isNotBlank() && !isActing
}

/**
 * One ticket: acknowledge it, then resolve it with a corrective action.
 *
 * The ticket id is pushed in by the screen (NavGraph parses the route argument)
 * rather than injected, keeping SavedStateHandle out of the ViewModel and its
 * tests — the same choice ItAssetListViewModel makes for its category.
 */
@HiltViewModel
class RaisedTicketDetailViewModel @Inject constructor(
    private val repository: RaisedTicketRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RaisedTicketDetailUiState())
    val uiState: StateFlow<RaisedTicketDetailUiState> = _uiState.asStateFlow()

    private var ticketId: Int? = null

    fun onTicketId(id: Int) {
        if (ticketId == id) return
        ticketId = id
        load()
    }

    fun onRetry() = load()

    fun onDismissMessage() = _uiState.update { it.copy(actionMessage = null) }

    fun onAcknowledge() {
        val id = ticketId ?: return
        act { repository.acknowledge(id) }
    }

    fun onResolveClicked() =
        _uiState.update { it.copy(showResolveDialog = true, correctiveAction = "", actionMessage = null) }

    fun onResolveDismiss() = _uiState.update { it.copy(showResolveDialog = false) }

    fun onCorrectiveActionChange(text: String) =
        _uiState.update { it.copy(correctiveAction = text) }

    fun onResolveSubmit() {
        val id = ticketId ?: return
        val note = _uiState.value.correctiveAction.trim()
        if (note.isBlank()) return
        _uiState.update { it.copy(showResolveDialog = false) }
        act { repository.resolve(id, note) }
    }

    /**
     * Runs a write and folds the outcome back into state.
     *
     * A Conflict means another technician got there first. The ticket is reloaded
     * so the screen immediately shows who has it — leaving the stale buttons up
     * would invite the user to tap again and fail again.
     */
    private fun act(block: suspend () -> TicketActionResult) {
        // Synchronous so the button shows its spinner and disables on the tap,
        // not one dispatch later — otherwise a fast double tap fires twice.
        _uiState.update { it.copy(isActing = true, actionMessage = null) }
        viewModelScope.launch {
            when (val r = block()) {
                is TicketActionResult.Success -> _uiState.update {
                    it.copy(isActing = false, ticket = r.ticket, actionMessage = null)
                }
                is TicketActionResult.Conflict -> {
                    _uiState.update { it.copy(isActing = false, actionMessage = r.message) }
                    load(silent = true)
                }
                is TicketActionResult.Failure -> _uiState.update {
                    it.copy(isActing = false, actionMessage = r.message)
                }
            }
        }
    }

    /** `silent` keeps the current ticket on screen instead of flashing a spinner. */
    private fun load(silent: Boolean = false) {
        val id = ticketId ?: return
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = repository.get(id)) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, ticket = r.data, error = null)
                }
                is Result.Error -> _uiState.update {
                    // A failed silent refresh must not wipe the ticket the user is
                    // looking at; only a first load turns into a full error screen.
                    if (silent) it.copy(isLoading = false)
                    else it.copy(isLoading = false, error = r.message)
                }
                is Result.Loading -> Unit
            }
        }
    }
}
