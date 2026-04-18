package com.yutori.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Save-button gating for the per-tx notes editor (#27). The
 * composable itself is visual; the non-obvious rule is
 * "don't enable save when the user's edit is trim-equivalent to what
 * was already stored" — a whitespace-only delta is a no-op. And the
 * 500-char cap is measured on raw length, not trimmed length, so a
 * user can't smuggle a too-long note past the cap by padding it with
 * whitespace.
 */
class TransactionNotesTest {

    @Test
    fun `save disabled when no initial note and field is empty`() {
        noteSaveEnabled(text = "", initial = null) shouldBe false
    }

    @Test
    fun `save disabled when whitespace is trim-equivalent to null initial`() {
        noteSaveEnabled(text = "   ", initial = null) shouldBe false
    }

    @Test
    fun `save enabled when adding a note to a previously empty tx`() {
        noteSaveEnabled(text = "dentist", initial = null) shouldBe true
    }

    @Test
    fun `save enabled when clearing an existing note`() {
        noteSaveEnabled(text = "", initial = "old note") shouldBe true
    }

    @Test
    fun `save disabled when text equals existing note`() {
        noteSaveEnabled(text = "groceries", initial = "groceries") shouldBe false
    }

    @Test
    fun `save disabled when text only differs from initial by surrounding whitespace`() {
        noteSaveEnabled(text = "  groceries  ", initial = "groceries") shouldBe false
    }

    @Test
    fun `save disabled past max length even if trim would shrink it`() {
        // A 500-char body plus a trailing space makes raw length 501,
        // which must block save even though trim() would reduce it to
        // exactly the cap. Length is checked before trim for exactly
        // this reason.
        val body = "x".repeat(NOTE_MAX_LEN) + " "
        noteSaveEnabled(text = body, initial = null) shouldBe false
    }

    @Test
    fun `save enabled at exactly the max length`() {
        val body = "x".repeat(NOTE_MAX_LEN)
        noteSaveEnabled(text = body, initial = null) shouldBe true
    }

    @Test
    fun `payload trims surrounding whitespace`() {
        noteSavePayload("  dentist  ") shouldBe "dentist"
    }

    @Test
    fun `payload collapses blank text to null so cleared notes don't linger as empty string`() {
        noteSavePayload("") shouldBe null
        noteSavePayload("   ") shouldBe null
        noteSavePayload("\n\t") shouldBe null
    }

    @Test
    fun `payload preserves interior whitespace and newlines`() {
        noteSavePayload("line one\nline two") shouldBe "line one\nline two"
    }
}
