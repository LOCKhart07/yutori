package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class KotakCcSpendTest {

    @Test
    fun `T10 - Kotak CC spend integer amount`() {
        val result = kotakCcSpend(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "INR 100 spent on Kotak Credit Card x3333 on 01-Jan-2026 at UPI-000000000000-KAWI. Avl limit is INR 10000",
            ),
        )!!
        result.classification shouldBe Classification.CC_TRANSACTION
        result.amount shouldBe 100.00
        result.currency shouldBe "INR"
        result.merchant shouldBe "UPI-000000000000-KAWI"
        result.last4 shouldBe "3333"
        result.category shouldBe null
        result.pattern shouldBe "kotak_cc_spend"
    }

    @Test
    fun `T11 - Kotak CC spend decimal amount`() {
        val result = kotakCcSpend(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "INR 50.00 spent on Kotak Credit Card x3333 on 01-JAN-2026 at UPI-000000000000-ZOMAT. Avl limit is INR 10000",
            ),
        )!!
        result.classification shouldBe Classification.CC_TRANSACTION
        result.amount shouldBe 50.00
        result.merchant shouldBe "UPI-000000000000-ZOMAT"
        result.last4 shouldBe "3333"
        result.pattern shouldBe "kotak_cc_spend"
    }

    @Test
    fun `wrong sender returns null`() {
        kotakCcSpend(
            SmsInput(
                sender = "VK-AXISBK",
                body = "INR 100 spent on Kotak Credit Card x3333 on 01-Jan-2026 at FOO. Avl limit is INR 10000",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `non-matching body returns null`() {
        kotakCcSpend(
            SmsInput(
                sender = "JD-KOTAKB-S",
                body = "Sent Rs.100.00 from Kotak Bank AC X0000 to foo@bar on 01-01-26",
            ),
        ).shouldBeNull()
    }
}
