package com.yutori.ai

import org.json.JSONObject

/**
 * Extracts `{pattern, category}` from a JSON blob. Tolerant to
 * preamble, markdown code fences, and trailing prose — scans for the
 * first `{` and the matching `}` (respecting string escapes) and
 * parses whatever's between.
 *
 * Lifted verbatim from the Stage A spike (`ai-rules-stage-a` branch,
 * `android/ai-spike/.../StructuredOutputParser.kt`) after validation
 * across gemma-3-270m-it, gemma-3-1b-it, and gemma-4-E2B-it — all
 * three scored 10/10 parse-rate on the 10-prompt fixture when the
 * system prompt asked for JSON output.
 *
 * The validator (see `Validator`) is the real safety net; this parser
 * only extracts what the model emitted. It does not judge semantics.
 */
object StructuredOutputParser {

    fun parse(text: String): ExtractedRule? {
        val json = findJsonObject(text) ?: return null
        val pattern = json.optString("pattern").trim()
            .takeIf { it.isNotEmpty() }
            ?: return null
        val category = json.optString("category").trim().takeIf { it.isNotEmpty() }
        return ExtractedRule(pattern = pattern, category = category)
    }

    private fun findJsonObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var i = start
        var inString = false
        var escape = false
        while (i < text.length) {
            val c = text[i]
            when {
                escape -> escape = false
                inString && c == '\\' -> escape = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) {
                        return runCatching {
                            JSONObject(text.substring(start, i + 1))
                        }.getOrNull()
                    }
                }
            }
            i++
        }
        return null
    }
}
