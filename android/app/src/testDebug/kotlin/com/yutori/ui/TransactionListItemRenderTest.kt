package com.yutori.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import com.yutori.ui.theme.YutoriTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.yutori.database.entities.TransactionEntity
import java.text.NumberFormat
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the `Index -1 out of bounds for length 0`
 * recomposition crash. The bug: `amountColor()` read
 * `MaterialTheme.colorScheme.xxx` conditionally inside a `when`, while
 * other branches returned literal `Color(...)`. Compose opens a group
 * per composable getter call — literal-only branches skip the open,
 * then the shared close pops past the bottom of the group stack on
 * recomposition. The fix: read the theme unconditionally above the
 * `when`.
 *
 * Uses Robolectric (not instrumented) because this project's emulator
 * is API 37, which Espresso can't drive (InputManager signature
 * change). `runComposeUiTest` + Robolectric gets us off-device.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class TransactionListItemRenderTest {

    @Test
    fun rendersAllBudgetEffectsWithoutCrashing() = runComposeUiTest {
        val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val effects = listOf("SPEND", "REFUND", "INCOME", "DROP", "UNKNOWN")

        setContent {
            YutoriTheme {
                Column {
                    effects.forEach { effect ->
                        TransactionListItem(
                            entity = tx(effect = effect, merchant = "Merchant-$effect"),
                            inr = inr,
                            onClick = {},
                        )
                    }
                }
            }
        }

        effects.forEach { effect ->
            onNodeWithText("Merchant-$effect").assertExists()
        }
    }

    private fun tx(
        effect: String,
        merchant: String,
    ) = TransactionEntity(
        id = 0,
        classification = "UPI_PAYMENT",
        classificationOriginal = null,
        budgetEffect = effect,
        inrAmount = 100.0,
        originalAmount = null,
        originalCurrency = "INR",
        rateSource = null,
        merchant = merchant,
        merchantKey = merchant.lowercase(),
        category = null,
        accountId = null,
        last4 = "3333",
        issuer = "Kotak",
        occurredAtMs = 1_700_000_000_000L,
        monthKey = "2026-04",
    )
}
