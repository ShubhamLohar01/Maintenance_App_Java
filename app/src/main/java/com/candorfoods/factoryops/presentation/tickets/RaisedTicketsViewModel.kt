package com.candorfoods.factoryops.presentation.tickets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.tickets.RaisedTicket
import com.candorfoods.factoryops.domain.repository.RaisedTicketRepository
import com.candorfoods.factoryops.domain.repository.TicketScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RaisedTicketsUiState(
    val scope: TicketScope = TicketScope.LIVE,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val tickets: List<RaisedTicket> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && error == null && tickets.isEmpty()
}

/**
 * The Raised Tickets list: Live (open + in progress) and History (resolved).
 *
 * Both tabs come from the same endpoint with a different `scope`, and the list is
 * re-fetched on every tab switch rather than cached per tab — a technician
 * switching tabs is exactly when they want current data, and the queue is small.
 *
 * No polling: the screen refreshes on open and on pull-to-refresh. A background
 * poll would drain battery for a queue that sees a handful of tickets a day.
 */
@HiltViewModel
class RaisedTicketsViewModel @Inject constructor(
    private val repository: RaisedTicketRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RaisedTicketsUiState())
    val uiState: StateFlow<RaisedTicketsUiState> = _uiState.asStateFlow()

    init { load() }

    fun onScopeChange(scope: TicketScope) {
        if (_uiState.value.scope == scope) return
        _uiState.update { it.copy(scope = scope, tickets = emptyList(), error = null) }
        load()
    }

    fun onRetry() = load()

    /** Pull-to-refresh: keeps the current rows on screen while re-fetching. */
    fun onRefresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean = false) {
        // Set synchronously, not inside the coroutine: the pull-to-refresh
        // indicator should respond to the gesture immediately rather than after
        // the launch is dispatched.
        _uiState.update {
            it.copy(isLoading = !isRefresh, isRefreshing = isRefresh, error = null)
        }
        viewModelScope.launch {
            when (val r = repository.list(_uiState.value.scope)) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, tickets = r.data, error = null)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = r.message)
                }
                is Result.Loading -> Unit
            }
        }
    }
}
