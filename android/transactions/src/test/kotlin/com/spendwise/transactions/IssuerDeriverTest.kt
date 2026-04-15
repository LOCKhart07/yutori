package com.spendwise.transactions

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class IssuerDeriverTest {

    @Test
    fun `KOTAKB sender resolves to Kotak`() {
        IssuerDeriver.fromSender("JD-KOTAKB-S") shouldBe "Kotak"
        IssuerDeriver.fromSender("VK-KOTAKB-S") shouldBe "Kotak"
    }

    @Test
    fun `KOTAKD sender also resolves to Kotak`() {
        IssuerDeriver.fromSender("JD-KOTAKD-S") shouldBe "Kotak"
    }

    @Test
    fun `AXISBK sender resolves to Axis`() {
        IssuerDeriver.fromSender("AX-AXISBK-S") shouldBe "Axis"
    }

    @Test
    fun `ICICI sender resolves to ICICI`() {
        IssuerDeriver.fromSender("JD-ICICIT-S") shouldBe "ICICI"
        IssuerDeriver.fromSender("VM-ICICIB-S") shouldBe "ICICI"
    }

    @Test
    fun `HDFC sender resolves to HDFC`() {
        IssuerDeriver.fromSender("VK-HDFCBK-S") shouldBe "HDFC"
    }

    @Test
    fun `SBI-prefixed senders resolve to SBI`() {
        IssuerDeriver.fromSender("VA-SBIUPI-S") shouldBe "SBI"
        IssuerDeriver.fromSender("AX-SBIINB-S") shouldBe "SBI"
    }

    @Test
    fun `PAYTMM resolves to Paytm`() {
        IssuerDeriver.fromSender("AD-PAYTMM-S") shouldBe "Paytm"
    }

    @Test
    fun `VJSBNK resolves to Vasai Janata Bank`() {
        IssuerDeriver.fromSender("JM-VJSBNK-S") shouldBe "Vasai Janata Bank"
    }

    @Test
    fun `blnkit resolves to Blinkit`() {
        IssuerDeriver.fromSender("JK-blnkit-S") shouldBe "Blinkit"
    }

    @Test
    fun `unknown sender returns null`() {
        IssuerDeriver.fromSender("VK-RANDOM-S").shouldBeNull()
        IssuerDeriver.fromSender("+919876543210").shouldBeNull()
        IssuerDeriver.fromSender("").shouldBeNull()
    }
}
