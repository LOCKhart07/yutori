package com.spendwise.backup

import com.spendwise.database.dao.TransactionDao
import com.spendwise.database.entities.TransactionEntity

/**
 * Proactive reclassify per settings-spec decision S1: when a user
 * adds a UPI handle to one of their own accounts, walk past
 * transactions whose merchant matches and reclassify them as
 * SELF_TRANSFER.
 *
 * Only reclassifies from UPI_PAYMENT / INCOMING_CREDIT — never
 * CC_TRANSACTION or other classifications where SELF_TRANSFER
 * wouldn't make sense (§2.3 of business-logic-spec).
 *
 * Returns the count of transactions reclassified.
 */
object ReclassifyOnRuleAdd {

    suspend fun forNewUpiHandles(
        newHandles: List<String>,
        accountId: Long,
        transactionDao: TransactionDao,
    ): Int {
        if (newHandles.isEmpty()) return 0
        var reclassified = 0
        for (handle in newHandles.distinct()) {
            val matches = transactionDao.findBySelfTransferCandidateMerchant(handle)
            for (tx in matches) {
                // Skip anything that's already been reclassified away
                // from UPI_PAYMENT / INCOMING_CREDIT. (Shouldn't happen
                // given the DAO filter, but defensive.)
                if (tx.classification != "UPI_PAYMENT" &&
                    tx.classification != "INCOMING_CREDIT"
                ) continue

                transactionDao.update(
                    tx.copy(
                        classification = "SELF_TRANSFER",
                        classificationOriginal = tx.classificationOriginal
                            ?: tx.classification,
                        budgetEffect = "DROP",
                        accountId = tx.accountId ?: accountId,
                        category = null,   // SELF_TRANSFER has no category
                    ),
                )
                reclassified++
            }
        }
        return reclassified
    }
}
