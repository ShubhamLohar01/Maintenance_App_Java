package com.candorfoods.factoryops.presentation.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.MtUser
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.MtUserRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageUsersUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUser: User? = null,
    val users: List<MtUser> = emptyList(),
    val editing: MtUser? = null,        // set while editing; null = adding
    val showSheet: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val pendingDelete: MtUser? = null,  // non-null while the confirm dialog is open
    val isDeleting: Boolean = false,
    val message: String? = null,        // one-shot snackbar (e.g. a failed delete)
) {
    /** HEAD manages the directory; ADMIN also allowed (matches the backend). */
    val canManage: Boolean
        get() = currentUser?.role == UserRole.HEAD || currentUser?.role == UserRole.ADMIN
}

@HiltViewModel
class ManageUsersViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val mtUserRepository: MtUserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageUsersUiState())
    val uiState: StateFlow<ManageUsersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user -> _uiState.update { it.copy(currentUser = user) } }
        }
        load(initial = true)
    }

    fun onAddClicked() = _uiState.update { it.copy(showSheet = true, editing = null, saveError = null) }
    fun onEditClicked(user: MtUser) = _uiState.update { it.copy(showSheet = true, editing = user, saveError = null) }
    fun onSheetDismiss() = _uiState.update { it.copy(showSheet = false, editing = null, saveError = null) }

    fun onSubmit(user: MtUser) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            val editingId = _uiState.value.editing?.id
            val result = if (editingId != null && editingId != 0)
                mtUserRepository.update(user.copy(id = editingId))
            else
                mtUserRepository.add(user)
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, showSheet = false, editing = null) }
                    load(initial = false)
                }
                is Result.Error -> _uiState.update { it.copy(isSaving = false, saveError = result.message) }
                is Result.Loading -> _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onDeleteClicked(user: MtUser) = _uiState.update { it.copy(pendingDelete = user) }
    fun onDeleteDismiss() = _uiState.update { it.copy(pendingDelete = null) }

    fun onConfirmDelete() {
        val target = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            when (val r = mtUserRepository.delete(target.id)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isDeleting = false, pendingDelete = null) }
                    load(initial = false)
                }
                is Result.Error ->
                    // Keep the list on screen; report the failure via a snackbar.
                    _uiState.update { it.copy(isDeleting = false, pendingDelete = null, message = r.message) }
                is Result.Loading -> _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onRefresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = initial, error = null) }
            when (val r = mtUserRepository.list()) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, users = r.data) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }
}
