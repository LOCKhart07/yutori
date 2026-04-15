package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class KnownNonFinSenderTest {

    private val expected = ParseResult(
        classification = Classification.NON_FINANCIAL,
        amount = null,
        currency = "INR",
        merchant = null,
        last4 = null,
        category = null,
        pattern = "known_non_fin_sender",
    )

    @Test
    fun `ISATHI Sanchar Saathi promo matches`() {
        val result = knownNonFinSender(
            SmsInput(
                sender = "JX-ISATHI-G",
                body = "कोणी तुमच्या नावाचा वापर करून मोबाईल कनेक्शन मिळवत आहे का? " +
                    "तपासण्यासाठी संचार साथी डाउनलोड करा",
            ),
        )
        result shouldBe expected
    }

    @Test
    fun `JIOINF JioHome bill notice matches`() {
        val result = knownNonFinSender(
            SmsInput(
                sender = "JZ-JIOINF-S",
                body = "Your bill dated 16-02-2026 for JioHome connection having " +
                    "Jio number 000000000000 is due for payment on 22-02-2026 .",
            ),
        )
        result shouldBe expected
    }

    @Test
    fun `VIJAYS reward points notice matches`() {
        val result = knownNonFinSender(
            SmsInput(
                sender = "JD-VIJAYS-S",
                body = "Dear Guest, Congratulations on redeeming 300 MyVS Reward " +
                    "points on your recent shopping at VIJAY SALES. Enjoy the benefits! TnC",
            ),
        )
        result shouldBe expected
    }

    @Test
    fun `sender not in list returns null`() {
        knownNonFinSender(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "Any body",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `case mismatch on uppercase-only entry returns null`() {
        // "isathi" is lowercase, the substring list has only "ISATHI" — no match.
        knownNonFinSender(
            SmsInput(
                sender = "JX-isathi-G",
                body = "promo body",
            ),
        ).shouldBeNull()
    }
}
