package com.spendwise.parser.rules

import com.spendwise.parser.Category
import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput

/**
 * UPI handles that route to CC bill payments. See parser-spec.md §5.1 and §7.
 *
 * Matching the recipient VPA here lets us reclassify before UPI_PAYMENT fires.
 * Includes the `¡` encoding glitch seen in the wild for CRED's Axis handle.
 */
private val CC_BILL_MIDDLEMAN_VPA = Regex(
    """^cred\.club[@¡]axisb|@paytm(cc|postpaid|ccbill)|@ybl.*creditcard|@okhdfcbankcc""",
    RegexOption.IGNORE_CASE,
)

private val regex = Regex(
    """^Sent Rs\.(?<amt>[\d.]+) from Kotak Bank AC X(?<last4>\w+) to (?<merchant>\S+) on""",
)

/**
 * parser-spec.md §5.1 — Kotak UPI debit.
 *
 * Returns CC_BILL_PAYMENT when the merchant matches the middleman VPA
 * regex, UPI_PAYMENT otherwise.
 */
fun kotakUpiDebit(input: SmsInput): ParseResult? {
    if (!input.sender.contains("KOTAKB")) return null
    val match = regex.find(input.body) ?: return null
    val amt = match.groups["amt"]!!.value.replace(",", "").toDouble()
    val merchant = match.groups["merchant"]!!.value
    val last4 = match.groups["last4"]!!.value

    return if (CC_BILL_MIDDLEMAN_VPA.containsMatchIn(merchant)) {
        ParseResult(
            classification = Classification.CC_BILL_PAYMENT,
            amount = amt,
            merchant = merchant,
            last4 = last4,
            pattern = "kotak_upi_debit",
        )
    } else {
        ParseResult(
            classification = Classification.UPI_PAYMENT,
            amount = amt,
            merchant = merchant,
            last4 = last4,
            category = Category.UPI_TRANSFER,
            pattern = "kotak_upi_debit",
        )
    }
}
