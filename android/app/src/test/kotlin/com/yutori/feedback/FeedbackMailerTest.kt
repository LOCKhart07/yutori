package com.yutori.feedback

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

/**
 * Encoding contract for the `mailto:` URI (the post-#71(a) feedback
 * path). The Android `intent()` wrapper is a one-line
 * `Intent(ACTION_SENDTO, Uri.parse(...))` over this — the percent-
 * encoding is the only logic worth pinning, and getting it wrong
 * (notably `+` vs `%20`) silently corrupts every report a user sends.
 */
class FeedbackMailerTest {

    @Test
    fun `targets the default recipient with subject and body query`() {
        val uri = FeedbackMailer.mailtoUri(subject = "Title", body = "Body")

        uri shouldBe "mailto:dsouzajenslee@gmail.com?subject=Title&body=Body"
    }

    @Test
    fun `spaces are percent-encoded as %20, never as plus`() {
        val uri = FeedbackMailer.mailtoUri(
            subject = "crash on save",
            body = "two words",
        )

        uri shouldBe
            "mailto:dsouzajenslee@gmail.com?subject=crash%20on%20save&body=two%20words"
        uri shouldContain "%20"
        // A literal '+' here would render as '+' in the user's draft.
        (uri.contains('+')) shouldBe false
    }

    @Test
    fun `query-breaking characters are escaped so they stay in the field`() {
        val uri = FeedbackMailer.mailtoUri(
            subject = "a & b = c?",
            body = "see #42",
        )

        // &, =, ?, # must not survive raw or they'd split the mailto query.
        uri shouldBe
            "mailto:dsouzajenslee@gmail.com?subject=a%20%26%20b%20%3D%20c%3F&body=see%20%2342"
    }

    @Test
    fun `newlines in the body become %0A`() {
        val uri = FeedbackMailer.mailtoUri(subject = "t", body = "line1\nline2")

        uri shouldBe "mailto:dsouzajenslee@gmail.com?subject=t&body=line1%0Aline2"
    }

    @Test
    fun `a literal plus is preserved as %2B, not collapsed to a space`() {
        val uri = FeedbackMailer.mailtoUri(subject = "c++", body = "1+1")

        uri shouldBe "mailto:dsouzajenslee@gmail.com?subject=c%2B%2B&body=1%2B1"
    }

    @Test
    fun `non-ascii is UTF-8 percent-encoded`() {
        val uri = FeedbackMailer.mailtoUri(subject = "café", body = "naïve")

        uri shouldBe "mailto:dsouzajenslee@gmail.com?subject=caf%C3%A9&body=na%C3%AFve"
    }

    @Test
    fun `recipient override is honoured`() {
        val uri = FeedbackMailer.mailtoUri(
            subject = "s",
            body = "b",
            recipient = "someone@example.com",
        )

        uri shouldStartWith "mailto:someone@example.com?"
    }

    @Test
    fun `default recipient constant is the published address`() {
        FeedbackMailer.RECIPIENT shouldBe "dsouzajenslee@gmail.com"
    }
}
