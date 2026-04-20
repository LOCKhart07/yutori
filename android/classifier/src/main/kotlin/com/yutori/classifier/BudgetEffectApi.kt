package com.yutori.classifier

import com.yutori.classifier.internal.BudgetEffectMapper
import com.yutori.parser.Classification

/** Public API for the canonical classification → budget effect mapping. */
fun budgetEffectForClassification(classification: Classification): BudgetEffect =
    BudgetEffectMapper.effectFor(classification)
