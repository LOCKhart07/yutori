package com.yutori.transactions

/**
 * Role of a single `sms_log` row's contribution to a merged
 * transaction. See business-logic-spec.md §4.4 and §4.5.
 *
 * Ordering here is significant — [priority] lower = more authoritative.
 * [BANK_DEBIT] is the canonical truth about money leaving an account,
 * so it always wins the primary slot when available.
 */
enum class SourceRole(val priority: Int) {
    BANK_DEBIT(1),
    GATEWAY(2),
    CC_PAYMENT_RECEIPT(3),
    MERCHANT_ACK(4),
    DUPLICATE_NOTIF(5),
}
