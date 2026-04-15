package com.spendwise.classifier.internal

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MerchantKeyNormalizerTest {

    @Test
    fun `null input returns null`() {
        MerchantKeyNormalizer.normalize(null).shouldBeNull()
    }

    @Test
    fun `empty string returns null`() {
        MerchantKeyNormalizer.normalize("").shouldBeNull()
    }

    @Test
    fun `whitespace-only string returns null`() {
        MerchantKeyNormalizer.normalize("   ").shouldBeNull()
    }

    @Test
    fun `uppercase is lowercased`() {
        MerchantKeyNormalizer.normalize("ZOMATO") shouldBe "zomato"
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        MerchantKeyNormalizer.normalize("  foo  ") shouldBe "foo"
    }

    @Test
    fun `VPA handle is preserved intact`() {
        MerchantKeyNormalizer.normalize("alice@okhdfcbank") shouldBe "alice@okhdfcbank"
    }

    @Test
    fun `hyphen in VPA becomes space but dot and at are preserved`() {
        MerchantKeyNormalizer.normalize("goog-payments@axisbank") shouldBe "goog payments@axisbank"
    }

    @Test
    fun `runs of punctuation collapse to single space`() {
        // `.` is preserved (needed for VPAs like "foo@bank.com"); other
        // punctuation collapses to whitespace and then a single space.
        MerchantKeyNormalizer.normalize("Rs.,!? foo") shouldBe "rs. foo"
    }

    @Test
    fun `Kotak CC UPI tag ZOMAT expands to zomato`() {
        MerchantKeyNormalizer.normalize("UPI-000000000000-ZOMAT") shouldBe "zomato"
    }

    @Test
    fun `Kotak CC UPI tag DOMIN expands to dominos`() {
        MerchantKeyNormalizer.normalize("UPI-000000000000-DOMIN") shouldBe "dominos"
    }

    @Test
    fun `Kotak CC UPI unknown tag VINOD is kept lowercased`() {
        MerchantKeyNormalizer.normalize("UPI-000000000000-VINOD") shouldBe "vinod"
    }

    @Test
    fun `Kotak CC UPI tag KAWI is kept as kawi`() {
        MerchantKeyNormalizer.normalize("UPI-000000000000-KAWI") shouldBe "kawi"
    }

    @Test
    fun `multi-word merchants keep internal single space`() {
        MerchantKeyNormalizer.normalize("VIJAY SALES") shouldBe "vijay sales"
    }

    @Test
    fun `digits are preserved in the key`() {
        MerchantKeyNormalizer.normalize("7ELEVEN") shouldBe "7eleven"
    }

    @Test
    fun `mixed punctuation and whitespace runs collapse`() {
        MerchantKeyNormalizer.normalize("  FOO   ---   BAR  ") shouldBe "foo bar"
    }

    @Test
    fun `string of only punctuation returns null after collapse`() {
        MerchantKeyNormalizer.normalize("---!!!").shouldBeNull()
    }
}
