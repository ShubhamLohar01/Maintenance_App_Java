package com.candorfoods.factoryops.util

import java.util.UUID

fun String.Companion.uuid(): String = UUID.randomUUID().toString()

fun Double.toKwh(ratedKw: Double, loadFactor: Double): Double {
    val runHours = this / 3_600.0
    return ratedKw * runHours * loadFactor
}

fun Long.durationHours(endMillis: Long): Double = (endMillis - this) / 3_600_000.0

fun Long.isToday(): Boolean {
    val start = DateTimeFormatter.startOfDayMillis(System.currentTimeMillis())
    val end = start + 86_400_000L
    return this in start until end
}
