package com.spendwise.classifier

/**
 * A user-registered account per plan §12.2. Passed to the classifier
 * as input; storage lives in the future Room `accounts` table.
 */
data class Account(
    val id: Long,
    val kind: AccountKind,
    val issuer: String,
    val last4: String,
    val displayName: String? = null,
    val isDefaultSpend: Boolean = false,
    val status: AccountStatus = AccountStatus.CONFIRMED,
)

enum class AccountStatus {
    CONFIRMED,
    SUGGESTED,
    DISMISSED,
}

enum class AccountKind {
    SAVINGS,
    CREDIT_CARD,
    INVESTMENT,
    OTHER,
}
