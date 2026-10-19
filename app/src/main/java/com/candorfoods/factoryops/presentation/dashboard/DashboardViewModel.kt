package com.candorfoods.factoryops.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.BreakdownStatus
import com.candorfoods.factoryops.domain.model.PmWoStatus
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Supplies the signed-in user for the dashboard's top bar subtitle, plus the live
 * per-tile alert counts rendered as red corner badges. Counts observe Room, so they
 * update in place as the background workers / screens sync data in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    getCurrentUser: GetCurrentUserUseCase,
    breakdownRepository: BreakdownRepository,
    pmWoRepository: PmWorkOrderRepository,
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user -> _currentUser.update { user } }
        }
    }

    /** Tile badge -> live count. Zero (or missing) means no badge is drawn. */
    val badgeCounts: StateFlow<Map<TileBadge, Int>> = run {
        // Breakdowns that need a technician right now.
        val openBreakdowns = breakdownRepository.observeAllFlags().map { flags ->
            flags.count { it.status == BreakdownStatus.OPEN || it.status == BreakdownStatus.REOPENED }
        }
        // PM work orders assigned to ME that aren't submitted yet (technician tile).
        val myPmTasks = currentUser.flatMapLatest { user ->
            if (user == null) flowOf(0)
            else pmWoRepository.observeForTechnician(user.id, ACTIVE_PM_STATUSES).map { it.size }
        }
        // Submitted PMs awaiting the supervisor's decision.
        val pendingApprovals = pmWoRepository.observeByStatus(PmWoStatus.SUBMITTED).map { it.size }
        // QC inbox: breakdowns awaiting a QC decision + PMs in PENDING_QC.
        val pendingQc = combine(
            breakdownRepository.observeAllFlags().map { flags ->
                flags.count { it.status == BreakdownStatus.PENDING_QC && it.qcDecidedAt == null }
            },
            pmWoRepository.observePendingQc().map { it.size },
        ) { breakdowns, pms -> breakdowns + pms }

        combine(openBreakdowns, myPmTasks, pendingApprovals, pendingQc) { bd, pm, approvals, qc ->
            mapOf(
                TileBadge.OPEN_BREAKDOWNS to bd,
                TileBadge.MY_PM_TASKS to pm,
                TileBadge.PENDING_APPROVALS to approvals,
                TileBadge.PENDING_QC to qc,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    }

    private companion object {
        val ACTIVE_PM_STATUSES = listOf(
            PmWoStatus.NOTIFIED, PmWoStatus.ACKNOWLEDGED, PmWoStatus.IN_PROGRESS,
        )
    }
}
