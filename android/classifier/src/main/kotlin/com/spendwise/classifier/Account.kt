package com.spendwise.classifier

/**
 * A user-registered account per plan §12.2. Passed to the classifier
 * as input; storage lives in the future Room `accounts` table.
 */
data class Account(
    val id: Long,
    val kind: AccountKind,
    val issuer: String,
    /**
     * Masked last-4 from SMS. Nullable because UPI-only accounts
     * (e.g. Paytm, PhonePe, bank UPI apps without a card) have no
     * last-4 to store — identity is issuer + UPI handles from
     * recipient_rules. See issue #6.
     */
    val last4: String?,
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
