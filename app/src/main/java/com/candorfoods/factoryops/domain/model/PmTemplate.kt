package com.candorfoods.factoryops.domain.model

data class PmTemplate(
    val id: String,
    val name: String,
    val machineType: MachineType,
    val description: String,
    val estimatedDurationMinutes: Int,
    val defaultTradeType: TradeType,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean,
    val items: List<PmTemplateItem> = emptyList()
)

data class PmTemplateItem(
    val id: String,
    val templateId: String,
    val orderIndex: Int,
    val title: String,
    /** "Remarks" in the plan editor — supervisor guidance for the item (was "instruction"). */
    val description: String
)
