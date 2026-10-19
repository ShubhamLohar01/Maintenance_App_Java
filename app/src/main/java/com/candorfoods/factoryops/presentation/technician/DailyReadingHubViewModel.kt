package com.candorfoods.factoryops.presentation.technician

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.model.a185.A185_PLANT
import com.candorfoods.factoryops.domain.model.w202.W202_PLANT
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.util.canonicalPlant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Drives the Daily Reading hub — currently just whether to show the A-185-only readings card. */
@HiltViewModel
class DailyReadingHubViewModel @Inject constructor(
    getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {

    /**
     * The HT/Cold/UV card shows for A-185 users, plus every HEAD regardless of their own
     * plant — a HEAD oversees both plants and is the only role that can delete a day's
     * readings, so gating them on location would make that power unreachable.
     *
     * plantId comes straight from `mt_users.location` and is normally the unhyphenated
     * "A185", so it MUST go through [canonicalPlant] — comparing it to "A-185" directly
     * was hiding the card from every A-185 technician.
     */
    val showA185: StateFlow<Boolean> = getCurrentUser()
        .map { canonicalPlant(it?.plantId) == A185_PLANT || it?.role == UserRole.HEAD }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Main Meter / Solar are W-202's own meters, so they show for W-202 users — plus every
     * HEAD, for the same reason [showA185] does: a HEAD oversees both plants and is the only
     * role that can delete a day's reading, which gating on location would make unreachable.
     */
    val showW202: StateFlow<Boolean> = getCurrentUser()
        .map { canonicalPlant(it?.plantId) == W202_PLANT || it?.role == UserRole.HEAD }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
