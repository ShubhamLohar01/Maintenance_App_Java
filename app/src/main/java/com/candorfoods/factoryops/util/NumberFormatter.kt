package com.candorfoods.factoryops.util

import java.text.NumberFormat
import java.util.Locale

object NumberFormatter {
    private val indianLocale = Locale("en", "IN")
    private val currencyFormat = NumberFormat.getCurrencyInstance(indianLocale)
    private val numberFormat = NumberFormat.getNumberInstance(indianLocale)

    fun formatKwh(kwh: Double): String = "%.2f kWh".format(kwh)
    fun formatKw(kw: Double): String = "%.1f kW".format(kw)
    fun formatHours(hours: Double): String = "%.1f h".format(hours)
    fun formatCurrency(amount: Double): String = currencyFormat.format(amount)
    fun formatNumber(number: Int): String = numberFormat.format(number)
}
