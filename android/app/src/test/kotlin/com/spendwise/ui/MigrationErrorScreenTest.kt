package com.spendwise.ui

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

/**
 * Migration-error recovery screen (#9). The composable itself is
 * visual, but the clipboard payload — what `Copy error details`
 * produces — is load-bearing for the bug-report flow (#78). It needs
 * to include enough context that a developer reading the paste can
 * identify the failure.
 */
class MigrationErrorScreenTest {

    @Test
    fun `stack trace string contains exception class and message`() {
        val err = IllegalStateException("A migration from 3 to 4 is missing")
        val text = stackTraceString(err)

        text shouldContain "IllegalStateException"
        text shouldContain "A migration from 3 to 4 is missing"
    }

    @Test
    fun `stack trace string starts with the thrown type -- not a caller frame`() {
        val err = RuntimeException("boom")
        val text = stackTraceString(err)

        // printStackTrace formats "ClassName: message\n  at …" — first
        // line is the thrown type + message, not a stack frame.
        text shouldStartWith "java.lang.RuntimeException: boom"
    }

    @Test
    fun `stack trace string is multiline -- preserves frames`() {
        val err = IllegalArgumentException("bad arg")
        val text = stackTraceString(err)

        // At least the type line + one stack frame (the caller of this
        // test, i.e. the kotest harness or JUnit runner).
        val lines = text.lines().filter { it.isNotBlank() }
        (lines.size >= 2) shouldBe true
        lines[1] shouldContain "at "
    }

    @Test
    fun `stack trace string includes cause chain`() {
        val root = IllegalStateException("room migration failed")
        val wrapped = RuntimeException("db init failed", root)
        val text = stackTraceString(wrapped)

        // printStackTrace walks the cause chain with "Caused by:" lines.
        text shouldContain "db init failed"
        text shouldContain "Caused by: java.lang.IllegalStateException"
        text shouldContain "room migration failed"
    }
}
