package com.yutori.ui.update

// Lightweight parser for the subset of GitHub-flavored markdown that
// git-cliff-generated release bodies use. Not a CommonMark impl — but
// covers what we observe in practice: headings (`#`/`##`/`###`),
// unordered bullets (`-` / `*`), inline `code`, `**bold**`,
// `[text](url)` links, `---` horizontal rules, and raw HTML tags
// (git-cliff wraps extended notes in `<details>/<summary>`). Anything
// unrecognised falls through as plain text.
object MarkdownRenderer {
    private val HTML_TAG_RE = Regex("</?[A-Za-z][^>]*>")

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
            // Strip HTML tags inline — git-cliff wraps extended notes
            // in <details><summary>...</summary>...</details>. Dropping
            // the tags leaves the summary text as a plain line.
            val line = raw.trimEnd().replace(HTML_TAG_RE, "").trimEnd()
            when {
                line.isBlank() -> flushParagraph()
                // Horizontal rule separator between sections — render
                // as a block so the Composable can draw a divider.
                line == "---" || line == "***" || line == "___" -> {
                    flushParagraph()
                    blocks += MdBlock.Rule
                }
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

    // Extracts inline `code`, `**bold**`, and `[text](url)` spans.
    // Processed as a single left-to-right scan so spans don't overlap.
    // Unmatched tokens fall through as literal text. Links keep the
    // visible text and attach the URL to a Link span — the Composable
    // can style or make it clickable without re-parsing.
    private fun parseInline(text: String): InlineText {
        val spans = mutableListOf<InlineText.Span>()
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text[i] == '`' -> {
                    val close = text.indexOf('`', startIndex = i + 1)
                    if (close == -1) { out.append(text[i]); i += 1 } else {
                        val start = out.length
                        out.append(text, i + 1, close)
                        spans += InlineText.Span(InlineText.SpanKind.Code, start, out.length)
                        i = close + 1
                    }
                }
                text.startsWith("**", i) -> {
                    val close = text.indexOf("**", startIndex = i + 2)
                    if (close == -1) { out.append(text[i]); i += 1 } else {
                        val start = out.length
                        out.append(text, i + 2, close)
                        spans += InlineText.Span(InlineText.SpanKind.Bold, start, out.length)
                        i = close + 2
                    }
                }
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', startIndex = i + 1)
                    val openParen = if (closeBracket != -1 && closeBracket + 1 < text.length) {
                        if (text[closeBracket + 1] == '(') closeBracket + 1 else -1
                    } else -1
                    val closeParen = if (openParen != -1) text.indexOf(')', startIndex = openParen + 1) else -1
                    if (closeBracket == -1 || openParen == -1 || closeParen == -1) {
                        out.append(text[i]); i += 1
                    } else {
                        val start = out.length
                        // The bracketed portion may contain inline
                        // code (common shape: `[`hash`](url)`). Recurse
                        // so nested code spans still render.
                        val inner = parseInline(text.substring(i + 1, closeBracket))
                        val offset = out.length
                        out.append(inner.text)
                        inner.spans.forEach {
                            spans += InlineText.Span(
                                kind = it.kind,
                                start = offset + it.start,
                                end = offset + it.end,
                            )
                        }
                        val url = text.substring(openParen + 1, closeParen)
                        spans += InlineText.Span(
                            kind = InlineText.SpanKind.Link,
                            start = start,
                            end = out.length,
                            url = url,
                        )
                        i = closeParen + 1
                    }
                }
                else -> {
                    out.append(text[i]); i += 1
                }
            }
        }
        return InlineText(out.toString(), spans)
    }
}

sealed interface MdBlock {
    data class Heading(val level: Int, val text: InlineText) : MdBlock
    data class Bullet(val text: InlineText) : MdBlock
    data class Paragraph(val text: InlineText) : MdBlock
    data object Rule : MdBlock
}

data class InlineText(val text: String, val spans: List<Span>) {
    data class Span(val kind: SpanKind, val start: Int, val end: Int, val url: String? = null)
    enum class SpanKind { Code, Bold, Link }
}
