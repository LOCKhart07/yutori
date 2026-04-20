package com.yutori.ai

/**
 * The public extraction entry point called by `DescribeRuleSheet`.
 * Wires together [LlmInvoker] → [StructuredOutputParser] → [Validator]
 * and returns a single [ExtractionResult]. No Android dependencies;
 * testable against a fake `LlmInvoker`.
 *
 * Prompt and fixtures are the exact ones that scored 10/10 parse and
 * 8–10/10 semantic correctness at the Deep tier (`gemma-4-E2B-it`) in
 * Stage A. See issue #64 comment 4281511141 for the data.
 */
class RuleExtractor(
    private val invoker: LlmInvoker,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) {

    suspend fun extract(userInput: String): ExtractionResult {
        val raw: String = runCatching {
            invoker.complete(systemPrompt, userInput)
        }.getOrElse { cause ->
            return ExtractionResult.ModelUnavailable(cause)
        }

        val parsed = StructuredOutputParser.parse(raw)
        return when (val result = Validator.validate(userInput, parsed)) {
            is Validator.Result.Valid -> ExtractionResult.Success(result.rule)
            is Validator.Result.Invalid -> ExtractionResult.ValidationFailed(result.reason)
        }
    }

    companion object {
        /**
         * Verbatim from Stage A — JSON-only output, three few-shot
         * examples. The bare-`KEY: value` scaffolding we tried first
         * scored 4/10 at the Deep tier vs 10/10 for this JSON prompt.
         */
        val DEFAULT_SYSTEM_PROMPT = """
            Extract the merchant pattern and category from the user's instruction.
            Respond with ONLY a JSON object, no markdown fencing, no prose, no code blocks:
            {"pattern": "<merchant name, UPI handle, or substring>", "category": "<category>"}

            User: anything from swiggy is food
            {"pattern": "swiggy", "category": "food"}

            User: treat cred as a credit card bill
            {"pattern": "cred", "category": "credit card bill"}

            User: netflix is entertainment
            {"pattern": "netflix", "category": "entertainment"}
        """.trimIndent()
    }
}
