package com.spendwise.parser.rules

import com.spendwise.parser.Classification
import com.spendwise.parser.ParseResult
import com.spendwise.parser.SmsInput
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OtpTest {

    @Test
    fun `Statiq OTP login message matches`() {
        val result = otp(
            SmsInput(
                sender = "CP-STATIQ-S",
                body = "Your OTP for logging into Statiq account is 169289. " +
                    "This OTP will expire in 5 minutes. ArTHlTdZI80. \nTeam Statiq!",
            ),
        )
        result shouldBe ParseResult(
            classification = Classification.OTP,
            amount = null,
            currency = "INR",
            merchant = null,
            last4 = null,
            category = null,
            pattern = "otp",
        )
    }

    @Test
    fun `Timezone one time password matches via alt phrasing`() {
        val result = otp(
            SmsInput(
                sender = "57575711",
                body = "Welcome to FUN! Your One Time Password for Timezone " +
                    "Membership is 404114, valid for 15 minutes",
            ),
        )
        result shouldBe ParseResult(
            classification = Classification.OTP,
            amount = null,
            currency = "INR",
            merchant = null,
            last4 = null,
            category = null,
            pattern = "otp",
        )
    }

    @Test
    fun `safety-tip body mentioning OTP is rejected by guard`() {
        otp(
            SmsInput(
                sender = "CP-BCCBnK-S",
                body = "Dear Customer, use digital channels available 24x7. " +
                    "Safety tips are on www.bccb.bank.in Do not share OTP, CVV, " +
                    "PIN, passwords or KYC details with anyone. -BCCB",
            ),
        ).shouldBeNull()
    }

    @Test
    fun `body with no OTP phrase returns null`() {
        otp(
            SmsInput(
                sender = "VK-AXISBK-S",
                body = "Payment of INR 1000 has been received towards your " +
                    "Axis Bank Credit Card XX1111 on 01-01-26",
            ),
        ).shouldBeNull()
    }
}
