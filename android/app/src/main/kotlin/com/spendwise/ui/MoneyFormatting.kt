package com.spendwise.ui

import java.text.NumberFormat
import kotlin.math.round

/**
 * Money formatter. `compact = true` rounds to nearest rupee and
 * strips decimals (clean column scan on dashboard, drill-down
 * headers, and tx list rows). `compact = false` keeps the two-
 * decimal precision the locale currency format produces (for
 * TransactionDetail where the exact amount matters).
 *
 * The receiver `NumberFormat` is not mutated — we strip the
 * `.00` tail from its output so the same shared formatter can be
 * reused across call sites.
 *
 * See issue #23.
 */
internal fun NumberFormat.formatAmount(value: Double, compact: Boolean = false): String {
    return if (compact) {
        format(round(value).toLong()).replace(Regex("\\.\\d+$"), "")
    } else {
        format(value)
    }
}
