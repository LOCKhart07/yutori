package com.spendwise.ui.update

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class MarkdownRendererTest {
    @Test
    fun `headings at three levels`() {
        val blocks = MarkdownRenderer.parse(
            """
            # One
            ## Two
            ### Three
            """.trimIndent(),
        )
        blocks shouldHaveSize 3
        val h1 = blocks[0].shouldBeInstanceOf<MdBlock.Heading>()
        h1.level shouldBe 1
        h1.text.text shouldBe "One"
        blocks[1].shouldBeInstanceOf<MdBlock.Heading>().level shouldBe 2
        blocks[2].shouldBeInstanceOf<MdBlock.Heading>().level shouldBe 3
    }

    @Test
    fun `dash and star bullets both parse`() {
        val blocks = MarkdownRenderer.parse(
            """
            - one
            * two
            """.trimIndent(),
        )
        blocks shouldHaveSize 2
        blocks[0].shouldBeInstanceOf<MdBlock.Bullet>().text.text shouldBe "one"
        blocks[1].shouldBeInstanceOf<MdBlock.Bullet>().text.text shouldBe "two"
    }

    @Test
    fun `paragraph lines collapse until blank`() {
        val blocks = MarkdownRenderer.parse(
            """
            one
            two

            three
            """.trimIndent(),
        )
        blocks shouldHaveSize 2
        blocks[0].shouldBeInstanceOf<MdBlock.Paragraph>().text.text shouldBe "one\ntwo"
        blocks[1].shouldBeInstanceOf<MdBlock.Paragraph>().text.text shouldBe "three"
    }

    @Test
    fun `inline backticks become code spans and are stripped from text`() {
        val blocks = MarkdownRenderer.parse("install `gradle wrapper`")
        blocks shouldHaveSize 1
        val para = blocks[0].shouldBeInstanceOf<MdBlock.Paragraph>()
        para.text.text shouldBe "install gradle wrapper"
        para.text.spans shouldHaveSize 1
        para.text.spans[0].kind shouldBe InlineText.SpanKind.Code
        para.text.text.substring(para.text.spans[0].start, para.text.spans[0].end) shouldBe "gradle wrapper"
    }

    @Test
    fun `unclosed backtick is kept literal`() {
        val blocks = MarkdownRenderer.parse("open `unclosed")
        val para = blocks[0].shouldBeInstanceOf<MdBlock.Paragraph>()
        para.text.text shouldBe "open `unclosed"
        para.text.spans shouldHaveSize 0
    }

    @Test
    fun `bullet between headings flushes correctly`() {
        val blocks = MarkdownRenderer.parse(
            """
            ## Added
            - first
            - second
            ## Fixed
            - third
            """.trimIndent(),
        )
        blocks shouldHaveSize 5
        blocks[0].shouldBeInstanceOf<MdBlock.Heading>().text.text shouldBe "Added"
        blocks[1].shouldBeInstanceOf<MdBlock.Bullet>()
        blocks[3].shouldBeInstanceOf<MdBlock.Heading>().text.text shouldBe "Fixed"
    }

    @Test
    fun `empty source yields no blocks`() {
        MarkdownRenderer.parse("") shouldHaveSize 0
        MarkdownRenderer.parse("\n\n\n") shouldHaveSize 0
    }
}
