package com.yutori.database.mappers

import com.yutori.classifier.PatternKind
import com.yutori.classifier.RecipientRule
import com.yutori.classifier.RuleSource
import com.yutori.database.entities.RecipientRuleEntity
import com.yutori.parser.Classification

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
