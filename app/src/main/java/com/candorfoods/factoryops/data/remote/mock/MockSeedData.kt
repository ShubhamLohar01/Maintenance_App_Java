package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.dto.MachineDto
import com.candorfoods.factoryops.data.remote.dto.ProductionRunDto
import com.candorfoods.factoryops.domain.model.MachineType
import com.candorfoods.factoryops.domain.model.PmPlan
import com.candorfoods.factoryops.domain.model.PmTemplate
import com.candorfoods.factoryops.domain.model.PmTemplateItem
import com.candorfoods.factoryops.domain.model.PmTriggerType
import com.candorfoods.factoryops.domain.model.TradeType

object MockSeedData {

    val machines = listOf(
        MachineDto(
            id = "mach-001", code = "FLR1-ROAST-01", name = "Roaster Unit 1",
            location = "Floor 1 - Section A", building = "W-202", plantId = "PLANT-01",
            machineType = MachineType.ROASTER.name,
            ratedKw = 15.0, loadFactor = 0.75, loadFactorSource = "SPOT_MEASURED",
            criticality = "A", expectedRunHours = 8.0,
            currentStatus = "IDLE", updatedAt = System.currentTimeMillis()
        ),
        MachineDto(
            id = "mach-002", code = "FLR1-PACK-01", name = "Packaging Machine 1",
            location = "Floor 1 - Section B", building = "W-202", plantId = "PLANT-01",
            machineType = MachineType.PACKING_LINE.name,
            ratedKw = 5.0, loadFactor = 0.6, loadFactorSource = "ASSUMED",
            criticality = "B", expectedRunHours = 8.0,
            currentStatus = "IDLE", updatedAt = System.currentTimeMillis()
        ),
        MachineDto(
            id = "mach-003", code = "FLR2-GRIND-01", name = "Grinder Unit 1",
            location = "Floor 2 - Section A", building = "W-202", plantId = "PLANT-01",
            machineType = MachineType.OTHER.name,
            ratedKw = 22.0, loadFactor = 0.8, loadFactorSource = "SPOT_MEASURED",
            criticality = "A", expectedRunHours = 8.0,
            currentStatus = "IDLE", updatedAt = System.currentTimeMillis()
        ),
        MachineDto(
            id = "mach-004", code = "FLR2-COOL-01", name = "Cooling Unit 1",
            location = "Floor 2 - Section B", building = "W-202", plantId = "PLANT-01",
            machineType = MachineType.HVAC.name,
            ratedKw = 8.0, loadFactor = 0.65, loadFactorSource = "ASSUMED",
            criticality = "B", expectedRunHours = 6.0,
            currentStatus = "IDLE", updatedAt = System.currentTimeMillis()
        ),
        MachineDto(
            id = "mach-005", code = "WH1-CONV-01", name = "Conveyor Belt 1",
            location = "Warehouse 1", building = "A-185", plantId = "PLANT-01",
            machineType = MachineType.CONVEYOR.name,
            ratedKw = 3.0, loadFactor = 0.5, loadFactorSource = "ASSUMED",
            criticality = "C", expectedRunHours = 8.0,
            currentStatus = "IDLE", updatedAt = System.currentTimeMillis()
        ),
        MachineDto(
            id = "mach-006", code = "FLR2-COMP-01", name = "Compressor 1",
            location = "Floor 2 - Utility Room", building = "W-202", plantId = "PLANT-01",
            machineType = MachineType.COMPRESSOR.name,
            ratedKw = 11.0, loadFactor = 0.7, loadFactorSource = "ASSUMED",
            criticality = "A", expectedRunHours = 16.0,
            currentStatus = "IDLE", updatedAt = System.currentTimeMillis()
        ),
        MachineDto(
            id = "mach-007", code = "FLR1-MIX-01", name = "Mixer 1",
            location = "Floor 1 - Section C", building = "W-202", plantId = "PLANT-01",
            machineType = MachineType.MIXER.name,
            ratedKw = 7.5, loadFactor = 0.6, loadFactorSource = "ASSUMED",
            criticality = "B", expectedRunHours = 8.0,
            currentStatus = "IDLE", updatedAt = System.currentTimeMillis()
        )
    )

    // NOTE: Breakdown/QC sample flags were intentionally removed. Even in mock mode the app
    // no longer seeds any "seed-flag-*" rows — breakdowns/QC must reflect the real backend only.
    // See BreakdownRepositoryImpl.seedMockOpenFlagsIfEmpty(), which now always purges leftovers.

    fun generateHistoryRuns(machineId: String, operatorId: String): List<ProductionRunDto> {
        val now = System.currentTimeMillis()
        val dayMillis = 86_400_000L
        return (1..30).flatMap { daysAgo ->
            val dayStart = now - (daysAgo * dayMillis)
            val shiftStart = dayStart + 8 * 3_600_000L
            val shiftEnd = shiftStart + 7 * 3_600_000L + 45 * 60_000L
            val kwh = 15.0 * 7.75 * 0.75
            listOf(
                ProductionRunDto(
                    id = "run-hist-$machineId-$daysAgo",
                    machineId = machineId,
                    operatorId = operatorId,
                    startedAt = shiftStart,
                    scheduledEndAt = shiftStart + 8 * 3_600_000L,
                    endedAt = shiftEnd,
                    computedKwh = kwh,
                    syncStatus = "SYNCED",
                    flagStatus = null
                )
            )
        }
    }

    // ─── Phase 2C — Preventive Maintenance seed ─────────────────────────────────

    private const val DAY_MS = 86_400_000L
    private const val SUPERVISOR_USER_ID = "user-sup-001"
    private const val TECHNICIAN_USER_ID = "user-002"

    /**
     * 3 templates × N items each. Each template's items have synthetic IDs so they survive
     * destructive migrations stably.
     */
    val pmTemplatesWithItems: List<Pair<PmTemplate, List<PmTemplateItem>>> = run {
        val now = System.currentTimeMillis()

        val roaster = PmTemplate(
            id = "tmpl-roaster-monthly",
            name = "Roaster monthly PM",
            machineType = MachineType.ROASTER,
            description = "Monthly preventive maintenance for roaster units. Total estimated time ~60 minutes.",
            estimatedDurationMinutes = 60,
            defaultTradeType = TradeType.MECHANICAL,
            createdBy = SUPERVISOR_USER_ID,
            createdAt = now,
            updatedAt = now,
            isActive = true
        )
        val roasterItems = listOf(
            PmTemplateItem(
                id = "item-roaster-1", templateId = roaster.id, orderIndex = 0,
                title = "Check burner alignment",
                description = "Inspect burner orientation and flame consistency. Verify visually that the flame is uniform and not deflected.",
                expectedResult = "Flame is symmetrical, no yellow flickering, no soot buildup.",
                requiresPhoto = true, requiresMeasurement = false,
                measurementUnit = null, measurementMin = null, measurementMax = null
            ),
            PmTemplateItem(
                id = "item-roaster-2", templateId = roaster.id, orderIndex = 1,
                title = "Lubricate bearings — Servo Mesh 220",
                description = "Apply Servo Mesh 220 grease to drum bearings (left and right). Wipe excess.",
                expectedResult = "Smooth rotation under hand-spin. No metal-on-metal sound.",
                requiresPhoto = true, requiresMeasurement = false,
                measurementUnit = null, measurementMin = null, measurementMax = null
            ),
            PmTemplateItem(
                id = "item-roaster-3", templateId = roaster.id, orderIndex = 2,
                title = "Verify temperature sensor calibration",
                description = "Use reference probe and record drum-temperature reading at steady state.",
                expectedResult = "Sensor reading within ±2°C of reference probe.",
                requiresPhoto = false, requiresMeasurement = true,
                measurementUnit = "°C", measurementMin = -2.0, measurementMax = 2.0
            ),
            PmTemplateItem(
                id = "item-roaster-4", templateId = roaster.id, orderIndex = 3,
                title = "Clean exhaust filter",
                description = "Remove and clean exhaust filter. Replace if torn or saturated.",
                expectedResult = "Filter mesh clear, no visible blockage.",
                requiresPhoto = true, requiresMeasurement = false,
                measurementUnit = null, measurementMin = null, measurementMax = null
            )
        )

        val compressor = PmTemplate(
            id = "tmpl-compressor-quarterly",
            name = "Compressor quarterly PM",
            machineType = MachineType.COMPRESSOR,
            description = "Quarterly PM for screw and reciprocating compressors. ~75 minutes.",
            estimatedDurationMinutes = 75,
            defaultTradeType = TradeType.MECHANICAL,
            createdBy = SUPERVISOR_USER_ID,
            createdAt = now,
            updatedAt = now,
            isActive = true
        )
        val compressorItems = listOf(
            PmTemplateItem(
                id = "item-compressor-1", templateId = compressor.id, orderIndex = 0,
                title = "Check oil level", description = "Confirm oil level in sight glass.",
                expectedResult = "Oil between MIN and MAX marks.",
                requiresPhoto = true, requiresMeasurement = false,
                measurementUnit = null, measurementMin = null, measurementMax = null
            ),
            PmTemplateItem(
                id = "item-compressor-2", templateId = compressor.id, orderIndex = 1,
                title = "Drain condensate", description = "Drain water from receiver bottom valve.",
                expectedResult = "Clean air discharge once water clears.",
                requiresPhoto = true, requiresMeasurement = false,
                measurementUnit = null, measurementMin = null, measurementMax = null
            ),
            PmTemplateItem(
                id = "item-compressor-3", templateId = compressor.id, orderIndex = 2,
                title = "Verify pressure switch",
                description = "Cycle compressor and record cut-in / cut-out pressure.",
                expectedResult = "Cut-in 6-7 bar, cut-out 8-9 bar.",
                requiresPhoto = false, requiresMeasurement = true,
                measurementUnit = "bar", measurementMin = 6.0, measurementMax = 9.0
            ),
            PmTemplateItem(
                id = "item-compressor-4", templateId = compressor.id, orderIndex = 3,
                title = "Inspect belts for wear", description = "Visually inspect drive belts for cracks or glazing.",
                expectedResult = "No cracks, no fraying, tension within 10mm deflection.",
                requiresPhoto = true, requiresMeasurement = false,
                measurementUnit = null, measurementMin = null, measurementMax = null
            ),
            PmTemplateItem(
                id = "item-compressor-5", templateId = compressor.id, orderIndex = 4,
                title = "Tighten electrical connections", description = "Power off, open panel, torque-check all terminations.",
                expectedResult = "All terminations torqued to spec, no discoloration.",
                requiresPhoto = true, requiresMeasurement = false,
                measurementUnit = null, measurementMin = null, measurementMax = null
            )
        )

        val packingLine = PmTemplate(
            id = "tmpl-packing-monthly",
            name = "Packing line monthly PM",
            machineType = MachineType.PACKING_LINE,
            description = "Monthly PM for sealing-and-weighing packing lines. ~45 minutes.",
            estimatedDurationMinutes = 45,
            defaultTradeType = TradeType.MECHANICAL,
            createdBy = SUPERVISOR_USER_ID,
            createdAt = now,
            updatedAt = now,
            isActive = true
        )
        val packingItems = listOf(
            PmTemplateItem(
                id = "item-packing-1", templateId = packingLine.id, orderIndex = 0,
                title = "Check sealing temperature",
                description = "Record sealing-jaw temperature at steady state.",
                expectedResult = "Within target band 130–180°C.",
                requiresPhoto = false, requiresMeasurement = true,
                measurementUnit = "°C", measurementMin = 130.0, measurementMax = 180.0
            ),
            PmTemplateItem(
                id = "item-packing-2", templateId = packingLine.id, orderIndex = 1,
                title = "Verify weighing accuracy",
                description = "Place 100g calibration weight; record displayed reading.",
                expectedResult = "Display within ±0.5g of calibration weight.",
                requiresPhoto = false, requiresMeasurement = true,
                measurementUnit = "g", measurementMin = 99.5, measurementMax = 100.5
            ),
            PmTemplateItem(
                id = "item-packing-3", templateId = packingLine.id, orderIndex = 2,
                title = "Inspect conveyor belt tension",
                description = "Check belt tension and tracking.",
                expectedResult = "Belt centered, tension matches OEM spec, no slip.",
                requiresPhoto = true, requiresMeasurement = false,
                measurementUnit = null, measurementMin = null, measurementMax = null
            )
        )

        listOf(
            roaster      to roasterItems,
            compressor   to compressorItems,
            packingLine  to packingItems
        )
    }

    /**
     * 3 plans against existing machines.
     * Computed `lastCompletedAt = today − N days` so escalation tier compute lights up correctly:
     *   - Roaster: 25 days ago (will be due in 5 days, WO auto-generation triggers)
     *   - Packaging: 45 days ago (overdue 15 days)
     *   - Cooling reused as Compressor 90-day plan: 60 days ago (mid-cycle)
     */
    val pmPlans: List<PmPlan> = run {
        val now = System.currentTimeMillis()
        listOf(
            PmPlan(
                id = "plan-roaster-001",
                machineId = "mach-001",
                templateId = "tmpl-roaster-monthly",
                triggerType = PmTriggerType.TIME,
                triggerInterval = 30,
                lastCompletedAt = now - 25 * DAY_MS,
                lastCompletedAtHours = null,
                nextDueAt = (now - 25 * DAY_MS) + 30 * DAY_MS,
                assignedTechnicianId = TECHNICIAN_USER_ID,
                isActive = true,
                createdAt = now - 25 * DAY_MS
            ),
            PmPlan(
                id = "plan-packing-001",
                machineId = "mach-002",
                templateId = "tmpl-packing-monthly",
                triggerType = PmTriggerType.TIME,
                triggerInterval = 30,
                lastCompletedAt = now - 45 * DAY_MS,
                lastCompletedAtHours = null,
                nextDueAt = (now - 45 * DAY_MS) + 30 * DAY_MS, // already 15 days overdue
                assignedTechnicianId = TECHNICIAN_USER_ID,
                isActive = true,
                createdAt = now - 45 * DAY_MS
            ),
            PmPlan(
                id = "plan-cooling-quarterly-001",
                machineId = "mach-004",
                templateId = "tmpl-compressor-quarterly",
                triggerType = PmTriggerType.TIME,
                triggerInterval = 90,
                lastCompletedAt = now - 60 * DAY_MS,
                lastCompletedAtHours = null,
                nextDueAt = (now - 60 * DAY_MS) + 90 * DAY_MS,
                assignedTechnicianId = TECHNICIAN_USER_ID,
                isActive = true,
                createdAt = now - 60 * DAY_MS
            )
        )
    }
}
