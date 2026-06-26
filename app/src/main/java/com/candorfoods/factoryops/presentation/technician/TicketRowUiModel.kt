package com.candorfoods.factoryops.presentation.technician

import com.candorfoods.factoryops.domain.model.BreakdownFlag
import com.candorfoods.factoryops.domain.model.Criticality

data class TicketRowUiModel(
    val flag: BreakdownFlag,
    val machineCode: String,
    val machineName: String,
    val criticality: Criticality
)
