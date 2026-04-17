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
