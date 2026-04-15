package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput

/**
 * parser-spec.md §5.6 — Axis savings debit feeding an Axis CC bill payment.
 *
 * Sender substring: "AXISBK".
 * Multi-line body:
 *   Debit INR <amt>
 *   Axis Bank A/c XX<last4>
 *   <ts>
 *   CreditCard Payment XX...
 *
 * `last4` extracted here is the *savings* account last4 (the source of funds),
 * matching the Python reference (§5.6 regex group).
 */
private val regex = Regex(
    """^Debit INR (?<amt>[\d.]+)\s*\n""" +
        """Axis Bank A/c XX(?<last4>\w+)\s*\n""" +
        """\S+ \S+\s*\n""" +
        """CreditCard Payment XX""",
)

fun axisSavingsCcBillDebit(input: SmsInput): ParseResult? {
    if ("AXISBK" !in input.sender) return null
    val m = regex.find(input.body) ?: return null
    val amt = m.groups["amt"]!!.value.replace(",", "").toDouble()
    val last4 = m.groups["last4"]!!.value
    return ParseResult(
        classification = Classification.CC_BILL_PAYMENT,
        amount = amt,
        currency = "INR",
        merchant = null,
        last4 = last4,
        category = null,
        pattern = "axis_savings_cc_bill_debit",
    )
}
