package com.candorfoods.factoryops.domain.model

/** One row of `mt_floor_utility_readings` — a floor's meter + system reading for one day. */
data class FloorReadingHistoryEntry(
    val building: String,
    val floor: String,
    val readingDate: String,   // ISO yyyy-MM-dd
    val systemReadingKwh: Double,
    val meterReading: Double?,
)
