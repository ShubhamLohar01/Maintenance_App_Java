package com.candorfoods.factoryops.presentation.qc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs the QC landing chooser — just the signed-in user for the header line. */
@HiltViewModel
class QcLandingViewModel @Inject constructor(
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {
    val user: StateFlow<User?> = getCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
