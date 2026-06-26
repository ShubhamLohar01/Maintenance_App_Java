package com.candorfoods.factoryops.presentation.qc

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Criticality

data class QcTicketRowUiModel(
    val flag: BreakdownFlag,
    val machineCode: String,
    val machineName: String,
    val machineLocation: String,
    val criticality: Criticality
)
