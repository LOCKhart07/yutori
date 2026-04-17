package com.yutori.transactions

/**
 * Derives a display-friendly issuer name from an SMS sender address.
 *
 * The parser captures a `last4` but has no first-class concept of
 * issuer — that's an ingestion-layer enrichment based on the DLT
 * header of the sender. This pure function keeps the mapping in one
 * place so the dashboard, the card drill-down, and the CSV export
 * all agree.
 *
 * Returns null when the sender doesn't match any known bank prefix.
 * Future parser rules should add their issuer mapping here.
 */
object IssuerDeriver {

    private val MAPPING: List<Pair<String, String>> = listOf(
        "KOTAKB" to "Kotak",
        "KOTAKD" to "Kotak",
        "AXISBK" to "Axis",
        "ICICI" to "ICICI",
        "HDFC" to "HDFC",
        "SBIUPI" to "SBI",
        "SBIINB" to "SBI",
        "SBIBNK" to "SBI",
        "SBI" to "SBI",
        "PAYTMM" to "Paytm",
        "VJSBNK" to "Vasai Janata Bank",
        "blnkit" to "Blinkit",
    )

    /** Returns the issuer name, or null if no mapping matches. */
    fun fromSender(sender: String): String? =
        MAPPING.firstOrNull { it.first in sender }?.second
}
