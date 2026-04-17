package com.yutori.transactions.internal

import com.yutori.transactions.SourceRole
import com.yutori.transactions.TransactionSource

/**
 * Decides whether a newly-merged source should become the primary
 * for its transaction, per business-logic-spec.md §4.3 step 2 and
 * §4.5 preference order ([SourceRole.priority]).
 *
 * A lower [SourceRole.priority] wins. Ties (same role) resolve in
 * favor of the existing primary — stability matters; we don't flip
 * flags without reason.
 */
internal object PrimarySelector {

    /**
     * Returns true iff [newRole] should replace the [currentPrimary]
     * as the transaction's primary source.
     */
    fun shouldPromote(
        newRole: SourceRole,
        currentPrimary: TransactionSource,
    ): Boolean = newRole.priority < currentPrimary.role.priority
}
