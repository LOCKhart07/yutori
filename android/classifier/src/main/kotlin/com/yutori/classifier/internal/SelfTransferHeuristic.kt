package com.yutori.classifier.internal

import com.yutori.classifier.RecipientRule
import com.yutori.parser.Classification

/**
 * Applies the self-transfer heuristic (business-logic-spec §2.3) and
 * more general recipient-rule reclassification (§2.2 step 4).
 *
 * Given the raw parser [Classification] and a matched [RecipientRule]
 * (possibly null), returns the final classification.
 *
 * Rules:
 * - No matched rule → raw classification unchanged.
 * - Matched rule with `reclassifyAs = SELF_TRANSFER` only takes effect
 *   when raw is `UPI_PAYMENT` or `INCOMING_CREDIT`. Applying it to, say,
 *   a `CC_TRANSACTION` would be nonsensical — self-transfer doesn't
 *   make sense over CC rails.
 * - Any other matched `reclassifyAs` is applied unconditionally. That
 *   covers user-added middleman rules (e.g. `UPI_PAYMENT →
 *   CC_BILL_PAYMENT`) and any future reclassification shapes.
 */
internal object SelfTransferHeuristic {

    fun apply(raw: Classification, matchedRule: RecipientRule?): Classification {
        if (matchedRule == null) return raw

        return when (matchedRule.reclassifyAs) {
            Classification.SELF_TRANSFER -> {
                if (raw == Classification.UPI_PAYMENT ||
                    raw == Classification.INCOMING_CREDIT
                ) {
                    Classification.SELF_TRANSFER
                } else {
                    raw
                }
            }
            else -> matchedRule.reclassifyAs
        }
    }
}
