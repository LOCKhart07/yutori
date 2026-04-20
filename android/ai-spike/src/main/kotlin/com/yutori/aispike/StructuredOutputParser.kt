package com.yutori.aispike

import org.json.JSONObject

// Extracts (pattern, category) from a JSON blob. Tolerant to preamble
// and markdown fencing — scans for the first `{` and the matching `}`
// and parses whatever's between.
object StructuredOutputParser {

    fun parse(text: String): ExtractedRule? {
        val json = findJsonObject(text) ?: return null
        val pattern = json.optString("pattern").trim().takeIf { it.isNotBlank() } ?: return null
        val category = json.optString("category").trim().takeIf { it.isNotBlank() }
        return ExtractedRule(
            pattern = pattern,
            patternKind = "LITERAL",
            classification = "",
            note = null,
            category = category,
        )
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
                        return runCatching { JSONObject(text.substring(start, i + 1)) }.getOrNull()
                    }
                }
            }
            i++
        }
        return null
    }
}
