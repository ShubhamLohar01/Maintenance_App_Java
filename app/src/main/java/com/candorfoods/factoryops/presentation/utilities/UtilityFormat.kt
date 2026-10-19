package com.candorfoods.factoryops.presentation.utilities

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Format a numeric value for display: rounded half-up to [decimals], trailing zeros
 * trimmed, plain notation. Null → "" (callers may swap in "—" for money like
 * cost_per_unit that is intentionally absent).
 */
fun formatUtilityNumber(value: Double?, decimals: Int): String =
    if (value == null) ""
    else BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
