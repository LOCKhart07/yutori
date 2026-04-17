package com.yutori.transactions.forex

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production forex client — hits https://open.er-api.com/v6/latest/{BASE}
 * (exchangerate-api.com's no-key free tier). Returns JSON of the form:
 *
 *     { "result":"success", "base_code":"USD",
 *       "rates": { "INR": 83.52, "EUR": 0.91, ... } }
 *
 * Parsed with a focused regex rather than pulling in a JSON lib — the
 * field we want is stable and deeply nested, but always on one line,
 * and the key is fixed as "INR".
 */
class ErApiForexRateClient(
    private val baseUrl: String = "https://open.er-api.com/v6/latest",
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) : ForexRateClient {

    override suspend fun fetch(currency: String): ForexFetchResult =
        withContext(Dispatchers.IO) { fetchBlocking(currency) }

    private fun fetchBlocking(currency: String): ForexFetchResult {
        val url = URL("$baseUrl/${currency.uppercase()}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code == 429) {
                return ForexFetchResult.Failure(ForexErrorKind.QUOTA_EXHAUSTED, "HTTP 429")
            }
            if (code !in 200..299) {
                return ForexFetchResult.Failure(
                    ForexErrorKind.TRANSIENT, "HTTP $code",
                )
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            return parseInrFromBody(body)
        } catch (e: Exception) {
            return ForexFetchResult.Failure(
                ForexErrorKind.TRANSIENT,
                "${e.javaClass.simpleName}: ${e.message}",
            )
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseInrFromBody(body: String): ForexFetchResult {
        // "INR": 83.52   (may have whitespace, may be integer or decimal)
        val match = INR_REGEX.find(body)
            ?: return ForexFetchResult.Failure(
                ForexErrorKind.CURRENCY_UNKNOWN,
                "no INR rate in response",
            )
        val rate = match.groupValues[1].toDoubleOrNull()
        if (rate == null || !rate.isFinite() || rate <= 0.0) {
            return ForexFetchResult.Failure(ForexErrorKind.TRANSIENT, "bad rate: $rate")
        }
        return ForexFetchResult.Success(rate)
    }

    companion object {
        private val INR_REGEX = Regex("\"INR\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")
    }
}
