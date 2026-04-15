package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VjsbnkInterestTest {

    @Test
    fun `VJSBNK quarterly savings interest credit`() {
        val result = vjsbnkInterest(
            SmsInput(
                sender = "JM-VJSBNK-S",
                body = "Rs.10.00 is Credited By Trf in A/c 006666 on 01/01/26 INT: 01/01-31/03. Clear balance Rs.1000.00. Vasai Janata Bank",
            ),
        )!!
        result.classification shouldBe Classification.INCOMING_CREDIT
        result.amount shouldBe 10.00
        result.currency shouldBe "INR"
        result.merchant shouldBe "Savings interest (VJSBNK)"
        result.last4 shouldBe "006666"
        result.category shouldBe null
        result.pattern shouldBe "vjsbnk_interest"
    }

    @Test
    fun `wrong sender returns null`() {
        vjsbnkInterest(
            SmsInput(
                sender = "JM-KOTAKB-S",
                body = "Rs.10.00 is Credited By Trf in A/c 006666 on 01/01/26 INT: 01/01-31/03.",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `non-interest body returns null`() {
        vjsbnkInterest(
            SmsInput(
                sender = "JM-VJSBNK-S",
                body = "Your account balance is Rs.1000.00. Vasai Janata Bank",
            ),
        ).shouldBeNull()
    }
}
