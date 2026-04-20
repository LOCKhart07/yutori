package com.yutori.aispike

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

// The whole point of Stage A: exercise LiteRT-LM's structured-output
// path, not free-form generation. The model's job is to call
// add_recipient_rule with the right arguments; the runtime parses the
// call, invokes this function, and we capture the arguments.
//
// Classification strings mirror :classifier's Classification enum
// (UPI_PAYMENT, CC_BILL_PAYMENT, SELF_TRANSFER, INCOMING_CREDIT, NON_FINANCIAL).
// The spike accepts whatever the model emits and records it — no
// validation here; that's Stage C.
class RuleExtractionTool : ToolSet {

    // Each call gets recorded with its raw JSON-ish argument shape so
    // the harness can show what the model actually produced.
    private val _calls = mutableListOf<ExtractedRule>()
    val calls: List<ExtractedRule> get() = _calls.toList()

    fun reset() {
        _calls.clear()
    }

    @Tool(
        description = "Register a recipient rule that reclassifies matching SMS transactions. " +
            "Use this whenever the user describes how to categorize payments to or from a specific merchant, UPI handle, or account.",
    )
    fun addRecipientRule(
        @ToolParam(description = "A merchant name, UPI handle, or substring to match (e.g. 'cred', 'swiggy', '1234@oksbi').")
        pattern: String,
        @ToolParam(description = "How pattern matches: LITERAL (exact substring), PREFIX (starts with), or REGEX.")
        patternKind: String,
        @ToolParam(description = "The classification to apply. One of: UPI_PAYMENT, CC_BILL_PAYMENT, SELF_TRANSFER, INCOMING_CREDIT, NON_FINANCIAL.")
        classification: String,
        @ToolParam(description = "Optional free-text note explaining the rule, for the user's own reference.")
        note: String? = null,
    ): Map<String, Any> {
        _calls.add(
            ExtractedRule(
                pattern = pattern,
                patternKind = patternKind,
                classification = classification,
                note = note,
            ),
        )
        return mapOf("ok" to true)
    }
}

data class ExtractedRule(
    val pattern: String,
    val patternKind: String,
    val classification: String,
    val note: String?,
    // Parser-mode-only: the user's free-text category label (e.g. "food",
    // "credit card bill"). TOOL_CALL mode leaves this null because the
    // @Tool schema still uses the old fields; the two modes represent
    // different architectures under evaluation.
    val category: String? = null,
)
