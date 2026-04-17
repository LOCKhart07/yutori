package com.yutori.transactions.internal

import com.yutori.transactions.SourceRole

/**
 * Maps a parser rule name (the `pattern` field on a `sms_log` row) to
 * the [SourceRole] of the contributing event. See §4.4.
 *
 * When a new parser rule is added, add its mapping here. The fallback
 * ([SourceRole.DUPLICATE_NOTIF]) errs on the side of "secondary
 * source" — the worst that can happen is a non-primary row gets a
 * wrong role label, which is cosmetic; the authoritative fields
 * (amount, classification, occurred_at) still flow from the actual
 * primary.
 */
internal object RoleAssigner {

    fun roleFor(parserPattern: String): SourceRole = when (parserPattern) {
        // Bank-side rails — these SMSes report "money left my account"
        // or "money arrived in my account", so they're the canonical
        // authority on the event.
        "kotak_upi_debit",
        "kotak_cc_spend",
        "axis_cc_spend",
        "kotak_upi_credit",
        "kotak_neft_credit",
        "axis_savings_upi_credit",
        "axis_savings_cc_bill_debit",
        "axis_cashback",
        "vjsbnk_interest" -> SourceRole.BANK_DEBIT

        // Payment-gateway rails — know the payment succeeded but don't
        // always know the funding account.
        "icici_eazypay",
        "paytm_sebi_credit" -> SourceRole.GATEWAY

        // CC issuer's "payment received" notification — settles a bill;
        // a different event shape than a spend debit. Dropped from
        // budget anyway (§4.1), but kept here for completeness.
        "axis_cc_bill_received",
        "kotak_cc_bill_received" -> SourceRole.CC_PAYMENT_RECEIPT

        // Merchant-side confirmation (refund processed, e-receipt).
        "blinkit_refund" -> SourceRole.MERCHANT_ACK

        else -> SourceRole.DUPLICATE_NOTIF
    }
}
