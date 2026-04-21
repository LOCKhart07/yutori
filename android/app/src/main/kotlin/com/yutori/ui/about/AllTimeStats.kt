package com.yutori.ui.about

import com.yutori.database.dao.SmsLogDao
import com.yutori.database.dao.TransactionDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lifetime stats shown in the easter-egg dialog (#79). Computed on
 * dialog-open from the full sms_log + transactions tables — cheap on
 * personal-scale data (thousands of rows, not millions).
 */
data class AllTimeStats(
    val smsProcessed: Int,
    val transactions: Int,
    /** First ingested SMS's local-date month, or null if sms_log is empty. */
    val firstTrackedMonth: YearMonth?,
    val monthsTracked: Int,
    /** Sum of SPEND-effect `inr_amount` across all time, in rupees. */
    val lifetimeSpendInr: Double,
    /** Count of local dates with SMS activity but no SPEND transaction. */
    val zeroSpendDays: Int,
) {
    companion object {
        suspend fun load(
            transactionDao: TransactionDao,
            smsLogDao: SmsLogDao,
            zone: ZoneId = ZoneId.systemDefault(),
        ): AllTimeStats {
            val smsCount = smsLogDao.count()
            val txCount = transactionDao.countAll()
            val earliestMs = smsLogDao.earliestReceivedAtMs()
            val monthsTracked = transactionDao.countDistinctMonths()
            val lifetimeSpend = transactionDao.sumLifetimeSpend()

            val smsMs = smsLogDao.allReceivedAtMs()
            val spendMs = transactionDao.allSpendOccurredAtMs()
            val zeroSpendDays = computeZeroSpendDays(smsMs, spendMs, zone)

            val firstMonth = earliestMs?.let {
                YearMonth.from(LocalDate.ofInstant(Instant.ofEpochMilli(it), zone))
            }
            return AllTimeStats(
                smsProcessed = smsCount,
                transactions = txCount,
                firstTrackedMonth = firstMonth,
                monthsTracked = monthsTracked,
                lifetimeSpendInr = lifetimeSpend,
                zeroSpendDays = zeroSpendDays,
            )
        }

        internal fun computeZeroSpendDays(
            smsReceivedAtMs: List<Long>,
            spendOccurredAtMs: List<Long>,
            zone: ZoneId,
        ): Int {
            if (smsReceivedAtMs.isEmpty()) return 0
            val smsDates = smsReceivedAtMs
                .mapTo(HashSet()) { LocalDate.ofInstant(Instant.ofEpochMilli(it), zone) }
            val spendDates = spendOccurredAtMs
                .mapTo(HashSet()) { LocalDate.ofInstant(Instant.ofEpochMilli(it), zone) }
            return (smsDates - spendDates).size
        }
    }
}

private val MONTH_FMT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

internal fun YearMonth?.formatShort(): String = this?.format(MONTH_FMT) ?: "—"

/**
 * Indian short-form INR: `₹500`, `₹12.4K`, `₹9.4L`, `₹1.2Cr`. Used
 * only in the easter-egg stats dialog — everywhere else uses the
 * locale-aware [com.yutori.ui.formatAmount].
 */
internal fun formatInrShort(rupees: Double): String {
    val abs = kotlin.math.abs(rupees)
    val sign = if (rupees < 0) "-" else ""
    return when {
        abs < 1_000.0 -> "₹$sign${abs.toLong()}"
        abs < 100_000.0 -> "₹$sign${trimTrailingZero(abs / 1_000.0)}K"
        abs < 10_000_000.0 -> "₹$sign${trimTrailingZero(abs / 100_000.0)}L"
        else -> "₹$sign${trimTrailingZero(abs / 10_000_000.0)}Cr"
    }
}

private fun trimTrailingZero(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.ENGLISH, "%.1f", rounded)
    }
}
