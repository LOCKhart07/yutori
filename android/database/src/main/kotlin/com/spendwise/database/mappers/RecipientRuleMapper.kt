package com.spendwise.database.mappers

import com.spendwise.classifier.PatternKind
import com.spendwise.classifier.RecipientRule
import com.spendwise.classifier.RuleSource
import com.spendwise.database.entities.RecipientRuleEntity
import com.spendwise.parser.Classification

object RecipientRuleMapper {

    fun toDomain(entity: RecipientRuleEntity): RecipientRule = RecipientRule(
        id = entity.id,
        pattern = entity.pattern,
        patternKind = PatternKind.valueOf(entity.patternKind),
        reclassifyAs = Classification.valueOf(entity.reclassifyAs),
        accountId = entity.accountId,
        source = RuleSource.valueOf(entity.source),
        isEnabled = entity.isEnabled,
        note = entity.note,
    )

    fun toEntity(rule: RecipientRule): RecipientRuleEntity = RecipientRuleEntity(
        id = rule.id,
        pattern = rule.pattern,
        patternKind = rule.patternKind.name,
        reclassifyAs = rule.reclassifyAs.name,
        accountId = rule.accountId,
        source = rule.source.name,
        note = rule.note,
        isEnabled = rule.isEnabled,
    )
}
