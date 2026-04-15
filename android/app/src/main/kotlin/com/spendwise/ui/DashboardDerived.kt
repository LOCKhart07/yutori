package com.spendwise.ui

import com.spendwise.budget.MonthSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max

/**
 * Pacing numbers computed from a [MonthSnapshot] + the device clock.
 * Derived here rather than in [com.spendwise.budget.BudgetCalculator]
 * because the Dashboard-specific surfaces (daily-cap, projected-
 * surplus, banner grammar) aren't portable to CSV export or other
 * consumers of the month snapshot.
 *
 * Sign conventions mirror [MonthSnapshot]:
 *   - [dailyCapInr] positive = INR/day that keeps you on-budget
 *   - [dailyCapInr] null = already over budget OR no days left
 *   - [projectedSurplusInr] positive = under-spend; negative = over-spend
 *   - [projectedSurplusInr] null = no budget set
 */
data class DashboardDerived(
    val dayOfMonth: Int,
    val daysInMonth: Int,
    val daysLeft: Int,          // includes today
    val dailyBurnInr: Double,
    val dailyCapInr: Double?,
    val projectedSurplusInr: Double?,
    val banner: DashboardBanner,
    /** Where the user "should be" by today as a percent of the limit. Null when no budget. */
    val expectedPercentByNow: Double? = null,
    /** Bucket from `actual_pct − elapsed_pct`. Drives the hero card tint. */
    val pace: PaceBucket = PaceBucket.OnTrack,
) {
    companion object {
        fun from(snap: MonthSnapshot, monthKey: String, nowMs: Long): DashboardDerived {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone)
            val viewingCurrentMonth = monthKey == "%04d-%02d".format(today.year, today.monthValue)

            // Use the days-in-month of the *viewed* month, not today's
            // month. Matters when user navigates to a 28- or 31-day
            // month from a 30-day one.
            val viewedYm = runCatching { YearMonth.parse(monthKey) }
                .getOrDefault(YearMonth.from(today))
            val daysInMonth = viewedYm.lengthOfMonth()
            val dayOfMonth: Int
            val daysLeft: Int
            if (viewingCurrentMonth) {
                dayOfMonth = today.dayOfMonth
                daysLeft = daysInMonth - dayOfMonth + 1
            } else {
                // Past or future month — fully elapsed / not yet started.
                dayOfMonth = daysInMonth
                daysLeft = 0
            }

            val elapsedDays = max(dayOfMonth, 1)
            val dailyBurn = snap.netSpendInr / elapsedDays

            val hasBudget = snap.effectiveBudgetInr > 0.0
            val remaining = snap.effectiveBudgetInr - snap.netSpendInr

            val dailyCap = when {
                !hasBudget -> null
                daysLeft <= 0 -> null
                remaining < 0 -> null
                else -> remaining / daysLeft
            }

            val projectedSurplus = if (hasBudget) {
                snap.effectiveBudgetInr - (dailyBurn * daysInMonth)
            } else null

            val banner = computeBanner(
                hasBudget = hasBudget,
                percentUsed = snap.percentUsed,
                dayOfMonth = dayOfMonth,
                daysLeft = daysLeft,
                daysInMonth = daysInMonth,
                remaining = remaining,
                projectedSurplus = projectedSurplus,
                dailyCap = dailyCap,
            )

            val expectedPct = if (hasBudget) {
                100.0 * dayOfMonth / daysInMonth
            } else null
            val pace = computePace(
                hasBudget = hasBudget,
                actualPct = snap.percentUsed,
                expectedPct = expectedPct,
            )

            return DashboardDerived(
                dayOfMonth = dayOfMonth,
                daysInMonth = daysInMonth,
                daysLeft = daysLeft,
                dailyBurnInr = dailyBurn,
                dailyCapInr = dailyCap,
                projectedSurplusInr = projectedSurplus,
                banner = banner,
                expectedPercentByNow = expectedPct,
                pace = pace,
            )
        }

        /**
         * Pace bucket from `actual_pct − elapsed_pct`. Same colour grammar
         * as the on-screen banner, but driven by *pace* (used vs elapsed)
         * rather than raw percent.
         *
         *   - delta ≤ −10pp → Under (green): under-pace, surplus brewing
         *   - −10pp..+10pp  → OnTrack (neutral): pace ≈ budget
         *   - +10pp..+25pp  → OverPace (warn-orange)
         *   - delta > +25pp OR actual ≥ 100% → Over (red)
         *
         * Returns OnTrack when no budget is set (no signal to give).
         */
        internal fun computePace(
            hasBudget: Boolean,
            actualPct: Double,
            expectedPct: Double?,
        ): PaceBucket {
            if (!hasBudget || expectedPct == null) return PaceBucket.OnTrack
            if (actualPct >= 100.0) return PaceBucket.Over
            val delta = actualPct - expectedPct
            return when {
                delta <= -10.0 -> PaceBucket.Under
                delta <=  10.0 -> PaceBucket.OnTrack
                delta <=  25.0 -> PaceBucket.OverPace
                else           -> PaceBucket.Over
            }
        }

        private fun computeBanner(
            hasBudget: Boolean,
            percentUsed: Double,
            dayOfMonth: Int,
            daysLeft: Int,
            daysInMonth: Int,
            remaining: Double,
            projectedSurplus: Double?,
            dailyCap: Double?,
        ): DashboardBanner {
            if (!hasBudget) return DashboardBanner.NoBudget
            if (percentUsed >= 100.0) return DashboardBanner.Over(deficit = -remaining)

            val nearEndOfMonth = daysLeft in 1..3 && daysInMonth > 5
            val comfortable = projectedSurplus != null && projectedSurplus > 0.05 * (remaining + 1)

            // Last stretch with real runway → positive framing
            if (nearEndOfMonth && percentUsed < 85.0 && comfortable) {
                return DashboardBanner.OnTrack(projectedSurplus!!)
            }
            // Approaching cap (mid-month or late) → hot framing
            if (percentUsed >= 80.0 && dailyCap != null) {
                return DashboardBanner.Approaching(dailyCap)
            }
            // Very early, low burn → hint to hide daily-burn
            if (dayOfMonth <= 5 && percentUsed < 20.0) {
                return DashboardBanner.EarlyMonth
            }
            return DashboardBanner.Normal
        }
    }
}

enum class PaceBucket {
    Under,    // green — well under-pace, surplus brewing
    OnTrack,  // neutral — pace matches budget
    OverPace, // warn-orange — over-pace but not over-budget
    Over,     // red — over budget OR egregiously over-pace
}

sealed interface DashboardBanner {
    data object Normal : DashboardBanner
    data object NoBudget : DashboardBanner
    data object EarlyMonth : DashboardBanner   // no visible banner, but UI hides daily-burn
    data class Approaching(val dailyCapInr: Double) : DashboardBanner
    data class OnTrack(val projectedSurplusInr: Double) : DashboardBanner
    data class Over(val deficit: Double) : DashboardBanner
}
