package com.spendwise.transactions.forex

import com.spendwise.transactions.TransactionRow

/**
 * Applies a fetched exchange rate to a pending-forex [TransactionRow],
 * producing an updated row with INR values filled in.
 *
 * Pre-conditions enforced here:
 *   - [row.originalCurrency] must not be INR (nothing to convert).
 *   - [row.originalAmount] must be non-null (parser should guarantee).
 *   - [rateInrPerUnit] must be finite and positive.
 *
 * Per business-logic-spec.md §5, the conversion is irreversible once
 * written; future rate changes do not retroactively rewrite historical
 * transactions. Users can manually override (v1.1).
 */
object ForexConverter {

    fun apply(
        row: TransactionRow,
        rateInrPerUnit: Double,
        rateSource: String,
    ): TransactionRow {
        require(row.originalCurrency != "INR") {
            "row ${row.id} is already INR; nothing to convert"
        }
        val original = requireNotNull(row.originalAmount) {
            "row ${row.id} has null originalAmount — parser bug; cannot convert"
        }
        require(rateInrPerUnit.isFinite() && rateInrPerUnit > 0.0) {
            "rateInrPerUnit must be finite and positive (got $rateInrPerUnit)"
        }
        return row.copy(
            inrAmount = original * rateInrPerUnit,
            exchangeRate = rateInrPerUnit,
            rateSource = rateSource,
        )
    }

    /**
     * Quick predicate: is this row still awaiting conversion?
     * Used by the worker to skip rows that have already been resolved
     * or manually overridden.
     */
    fun isPending(row: TransactionRow): Boolean =
        row.originalCurrency != "INR" &&
            (row.inrAmount == null || row.rateSource == "pending")
}
