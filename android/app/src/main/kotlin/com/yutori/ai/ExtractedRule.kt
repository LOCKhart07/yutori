package com.yutori.ai

/**
 * Output of a successful LLM rule extraction. The LLM's job is
 * narrowed to these two fields: `pattern` (the merchant substring) and
 * `category` (a free-text user-facing label). Classification stays
 * with the user's manual dropdown pick in `AddEditRecipientRule` — see
 * `plans/ai-rules-spec.md` §3.2 / §6.4.
 */
data class ExtractedRule(
    val pattern: String,
    val category: String?,
)

/**
 * The three terminal states of `RuleExtractor.extract`.
 *
 * - [Success] — parser + validator both passed; `rule` is safe to use.
 * - [ValidationFailed] — either the parser couldn't find a JSON object
 *   or the validator rejected the extracted rule. The sheet surfaces
 *   the same "Couldn't extract a rule from that" banner for every
 *   [ValidationFailure] variant; the sub-type is kept for telemetry
 *   and future-different-copy-per-reason.
 * - [ModelUnavailable] — the Engine refused to initialize or the
 *   inference call threw. The sheet surfaces "AI is unavailable on
 *   this device" and disables the toggle (spec §6.3 step 4).
 */
sealed interface ExtractionResult {
    data class Success(val rule: ExtractedRule) : ExtractionResult
    data class ValidationFailed(val reason: ValidationFailure) : ExtractionResult
    data class ModelUnavailable(val cause: Throwable) : ExtractionResult
}

/**
 * Specific validator-failure reasons. Single user-facing copy for all
 * of them per mockup §C3; kept typed here so telemetry can show a
 * histogram when we need to tune the prompt or length floors.
 */
enum class ValidationFailure {
    /** Parser couldn't find a JSON object in the model's output. */
    PARSE_FAILED,

    /** `pattern` was missing, empty, or whitespace-only in the JSON. */
    PATTERN_MISSING,

    /** `pattern` was shorter than the 3-char floor (spec §4.5 rule 2). */
    PATTERN_TOO_SHORT,

    /** `pattern` was longer than the 80-char cap (spec §4.5 rule 3). */
    PATTERN_TOO_LONG,

    /**
     * `pattern` didn't appear as a substring of the user's input text.
     * Guards against the "hallucinated-example-from-the-few-shot"
     * failure mode seen at Stage A runs 3–4 (spec §4.5 rule 4).
     */
    PATTERN_NOT_IN_INPUT,

    /** `category` was longer than the 40-char cap (spec §4.5 rule 5). */
    CATEGORY_TOO_LONG,
}
