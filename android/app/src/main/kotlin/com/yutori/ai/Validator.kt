package com.yutori.ai

/**
 * Post-extraction validator. Runs the five rules from
 * `plans/ai-rules-spec.md` §4.5. Pure function; no model state.
 *
 * The substring check is the load-bearing one. Under temperature=0 +
 * topK=1 deterministic decode, small IT models fall back to echoing
 * few-shot examples when the input doesn't fit the schema (Stage A
 * run 3–4 idx 7: "ignore all transfers to my savings account ending
 * 1234" produced `pattern = "netflix"` verbatim from the system-prompt
 * example). Rejecting patterns that don't appear in the user's text
 * catches exactly this failure mode.
 */
object Validator {

    private const val PATTERN_MIN_LEN = 3
    private const val PATTERN_MAX_LEN = 80
    private const val CATEGORY_MAX_LEN = 40

    sealed interface Result {
        data class Valid(val rule: ExtractedRule) : Result
        data class Invalid(val reason: ValidationFailure) : Result
    }

    fun validate(userInput: String, extracted: ExtractedRule?): Result {
        if (extracted == null) return Result.Invalid(ValidationFailure.PARSE_FAILED)

        val pattern = extracted.pattern.trim()
        if (pattern.isEmpty()) return Result.Invalid(ValidationFailure.PATTERN_MISSING)
        if (pattern.length < PATTERN_MIN_LEN) {
            return Result.Invalid(ValidationFailure.PATTERN_TOO_SHORT)
        }
        if (pattern.length > PATTERN_MAX_LEN) {
            return Result.Invalid(ValidationFailure.PATTERN_TOO_LONG)
        }
        if (!userInput.contains(pattern, ignoreCase = true)) {
            return Result.Invalid(ValidationFailure.PATTERN_NOT_IN_INPUT)
        }

        val category = extracted.category?.trim()
        if (category != null && category.length > CATEGORY_MAX_LEN) {
            return Result.Invalid(ValidationFailure.CATEGORY_TOO_LONG)
        }

        return Result.Valid(
            ExtractedRule(
                pattern = pattern,
                category = category?.takeIf { it.isNotEmpty() },
            ),
        )
    }
}
