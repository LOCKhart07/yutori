package com.yutori.database.dao

import androidx.room.ColumnInfo

/**
 * Grouped-aggregate result from [TransactionDao.aggregateSuggestionCandidates].
 * Not an entity — Room only uses it as a @Query result shape.
 */
data class MerchantAggregateRow(
    @ColumnInfo(name = "merchant_key")
    val merchantKey: String,

    @ColumnInfo(name = "match_count")
    val matchCount: Int,

    @ColumnInfo(name = "total_inr")
    val totalInr: Double,
)
