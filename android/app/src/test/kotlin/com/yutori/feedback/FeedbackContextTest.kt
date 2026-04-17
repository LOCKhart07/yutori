package com.yutori.feedback

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.util.Locale

class FeedbackContextTest {

    @Test
    fun `renders a three-line block with app OS and device`() {
        val out = FeedbackContext.render(
            versionName = "0.5.1",
            versionCode = 52,
            commitSha = "abc1234",
            androidRelease = "14",
            sdkInt = 34,
            locale = Locale.forLanguageTag("en-IN"),
            deviceModel = "Pixel 7",
            deviceManufacturer = "google",
            deviceProduct = "panther",
        )

        out shouldBe """
            |---
            |App:     0.5.1 (52) · commit abc1234
            |Android: 14 (API 34), en-IN
            |Device:  Pixel 7 (google/panther)
        """.trimMargin()
    }

    @Test
    fun `blank device metadata collapses to a usable line`() {
        val out = FeedbackContext.render(
            versionName = "0.5.1",
            versionCode = 52,
            commitSha = "abc1234",
            androidRelease = "14",
            sdkInt = 34,
            locale = Locale.forLanguageTag("en-IN"),
            deviceModel = "",
            deviceManufacturer = "",
            deviceProduct = "",
        )

        // Fallbacks are rendered — no crash, no 'null' literals.
        out shouldContain "Device:  unknown"
        out shouldNotContain "null"
    }

    @Test
    fun `does not include DB facts or user data beyond the allowlist`() {
        val out = FeedbackContext.render(
            versionName = "0.5.1",
            versionCode = 52,
            commitSha = "abc1234",
            androidRelease = "14",
            sdkInt = 34,
            locale = Locale.forLanguageTag("en-IN"),
            deviceModel = "Pixel 7",
            deviceManufacturer = "google",
            deviceProduct = "panther",
        )

        // Allowlist check — anything that would imply DB reads must be
        // absent. This guards against a careless future edit.
        out shouldNotContain "transaction"
        out shouldNotContain "sms"
        out shouldNotContain "account"
        out shouldNotContain "merchant"
        out shouldNotContain "budget"
    }
}
