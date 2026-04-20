package com.yutori.aispike

// Synthetic rule-intent prompts. Scrubbed of real handles / merchant
// names to honour the repo's PII rule. Each line is something a Yutori
// user might plausibly type into the "Describe this rule" box.
val FIXTURE_PROMPTS: List<String> = listOf(
    "anything from CRED is a credit-card bill payment",
    "swiggy and zomato are food",
    "payments to my own icici handle are self-transfers, not spend",
    "treat cheq@ybl as a cc bill, not upi spend",
    "flipkart purchases are shopping",
    "netflix is entertainment",
    "ignore all transfers to my savings account ending 1234",
    "uber and ola are transport",
    "amazon pay topups are self-transfers",
    "anything with 'salary' from my employer is income",
)
