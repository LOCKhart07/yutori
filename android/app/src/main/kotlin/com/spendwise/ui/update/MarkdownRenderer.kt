package com.spendwise.ui.update

// Lightweight parser for the subset of GitHub-flavored markdown that
// git-cliff-generated release bodies use. Not a CommonMark impl —
// headings (`#`/`##`/`###`), unordered bullets (`-` or `*`), inline
// backtick code, and plain paragraphs. Everything else falls through
// as plain text.
object MarkdownRenderer {
    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) {
                blocks += MdBlock.Paragraph(parseInline(paragraph.toString().trim()))
            }
            paragraph.clear()
        }

        source.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flushParagraph()
                line.startsWith("### ") -> {
                    flushParagraph()
                    blocks += MdBlock.Heading(level = 3, text = parseInline(line.removePrefix("### ")))
                }
                line.startsWith("## ") -> {
                    flushParagraph()
                    blocks += MdBlock.Heading(level = 2, text = parseInline(line.removePrefix("## ")))
                }
                line.startsWith("# ") -> {
                    flushParagraph()
                    blocks += MdBlock.Heading(level = 1, text = parseInline(line.removePrefix("# ")))
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flushParagraph()
                    blocks += MdBlock.Bullet(parseInline(line.drop(2)))
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(line)
                }
            }
        }
        flushParagraph()
        return blocks
    }

    // Extracts inline `code` spans — the only inline formatting we
    // render. Unmatched backticks fall through as literal text.
    private fun parseInline(text: String): InlineText {
        if (!text.contains('`')) return InlineText(text, emptyList())
        val spans = mutableListOf<InlineText.Span>()
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '`') {
                val close = text.indexOf('`', startIndex = i + 1)
                if (close == -1) {
                    out.append(c)
                    i += 1
                    continue
                }
                val start = out.length
                out.append(text, i + 1, close)
                spans += InlineText.Span(kind = InlineText.SpanKind.Code, start = start, end = out.length)
                i = close + 1
            } else {
                out.append(c)
                i += 1
            }
        }
        return InlineText(out.toString(), spans)
    }
}

sealed interface MdBlock {
    data class Heading(val level: Int, val text: InlineText) : MdBlock
    data class Bullet(val text: InlineText) : MdBlock
    data class Paragraph(val text: InlineText) : MdBlock
}

data class InlineText(val text: String, val spans: List<Span>) {
    data class Span(val kind: SpanKind, val start: Int, val end: Int)
    enum class SpanKind { Code }
}
