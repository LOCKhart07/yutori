package com.yutori.parser.rules

import com.yutori.parser.Classification
import com.yutori.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PaytmSebiCreditTest {

    @Test
    fun `Paytm SEBI quarterly settlement`() {
        val result = paytmSebiCredit(
            SmsInput(
                sender = "AD-PAYTMM-S",
                body = "Rs. 100.00 successfully transferred on 03 January 2026 to your bank account as SEBI-mandated Quarterly settlement via Paytm Money Ltd. Transaction no: AXNH000000000000.",
            ),
        )!!
        result.classification shouldBe Classification.INCOMING_CREDIT
        result.amount shouldBe 100.00
        result.currency shouldBe "INR"
        result.merchant shouldBe "Paytm (SEBI settlement)"
        result.last4 shouldBe null
        result.category shouldBe null
        result.pattern shouldBe "paytm_sebi_credit"
    }

    @Test
    fun `Paytm SEBI monthly settlement`() {
        val result = paytmSebiCredit(
            SmsInput(
                sender = "AX-PAYTMM-S",
                body = "Rs. 50.00 successfully transferred on 07 March 2026 to your bank account as SEBI-mandated Monthly settlement via Paytm Money Ltd. Transaction no:  AXNH000000000001.",
            ),
        )!!
        result.amount shouldBe 50.00
        result.merchant shouldBe "Paytm (SEBI settlement)"
        result.pattern shouldBe "paytm_sebi_credit"
    }

    @Test
    fun `wrong sender returns null`() {
        paytmSebiCredit(
            SmsInput(
                sender = "AD-KOTAKB-S",
                body = "Rs. 100.00 successfully transferred on 03 January 2026 to your bank account as SEBI-mandated Quarterly settlement via Paytm Money Ltd.",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `non-SEBI Paytm body returns null`() {
        paytmSebiCredit(
            SmsInput(
                sender = "AD-PAYTMM-S",
                body = "Rs. 100.00 added to your Paytm Wallet.",
            ),
        ).shouldBeNull()
    }
}
