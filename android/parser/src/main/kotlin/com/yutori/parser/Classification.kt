package com.yutori.parser

/**
 * Classification assigned to an SMS by the parser.
 *
 * See parser-spec.md §3 and business-logic-spec.md §2.4 for the
 * classification → budget_effect mapping.
 *
 * `SELF_TRANSFER` is reserved but never produced by the raw parser —
 * only by the downstream classifier layer (see parser-spec.md §9).
 */
enum class Classification {
    CC_TRANSACTION,
    CC_BILL_PAYMENT,
    UPI_PAYMENT,
    DEBIT_CARD,
    ATM_WITHDRAWAL,
    REFUND,
    CASHBACK,
    INCOMING_CREDIT,
    OTP,
    BALANCE_ALERT,
    NON_FINANCIAL,
    SELF_TRANSFER,
    UNMATCHED,
}

/**
 * Canonical human-readable name for a [Classification]. Used by every UI
 * surface that shows a classification to the user. See
 * plans/classification-display-spec.md.
 */
val Classification.displayName: String
    get() = when (this) {
        Classification.CC_TRANSACTION -> "CC transaction"
        Classification.CC_BILL_PAYMENT -> "CC bill payment"
        Classification.UPI_PAYMENT -> "UPI payment"
        Classification.DEBIT_CARD -> "Debit card"
        Classification.ATM_WITHDRAWAL -> "ATM withdrawal"
        Classification.REFUND -> "Refund"
        Classification.CASHBACK -> "Cashback"
        Classification.INCOMING_CREDIT -> "Incoming credit"
        Classification.OTP -> "OTP"
        Classification.BALANCE_ALERT -> "Balance alert"
        Classification.NON_FINANCIAL -> "Non-financial"
        Classification.SELF_TRANSFER -> "Self-transfer"
        Classification.UNMATCHED -> "Unmatched"
    }
