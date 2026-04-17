package com.yutori.database

import androidx.room.TypeConverter
import com.yutori.classifier.AccountKind
import com.yutori.classifier.BudgetEffect
import com.yutori.classifier.PatternKind
import com.yutori.classifier.RuleSource
import com.yutori.parser.Category
import com.yutori.parser.Classification
import com.yutori.transactions.SourceRole

/**
 * Room TypeConverters for domain enums.
 *
 * Enums are stored as their `name` string — never ordinals
 * (schema.md note in §3). Reordering the enum at the source should
 * never silently re-map existing rows.
 *
 * Nullable variants are provided where the column accepts null.
 */
class EnumConverters {

    // --- Classification ---
    @TypeConverter
    fun classificationToString(value: Classification?): String? = value?.name

    @TypeConverter
    fun stringToClassification(value: String?): Classification? =
        value?.let { Classification.valueOf(it) }

    // --- Category ---
    @TypeConverter
    fun categoryToString(value: Category?): String? = value?.name

    @TypeConverter
    fun stringToCategory(value: String?): Category? =
        value?.let { Category.valueOf(it) }

    // --- BudgetEffect ---
    @TypeConverter
    fun budgetEffectToString(value: BudgetEffect?): String? = value?.name

    @TypeConverter
    fun stringToBudgetEffect(value: String?): BudgetEffect? =
        value?.let { BudgetEffect.valueOf(it) }

    // --- SourceRole ---
    @TypeConverter
    fun sourceRoleToString(value: SourceRole?): String? = value?.name

    @TypeConverter
    fun stringToSourceRole(value: String?): SourceRole? =
        value?.let { SourceRole.valueOf(it) }

    // --- PatternKind ---
    @TypeConverter
    fun patternKindToString(value: PatternKind?): String? = value?.name

    @TypeConverter
    fun stringToPatternKind(value: String?): PatternKind? =
        value?.let { PatternKind.valueOf(it) }

    // --- RuleSource ---
    @TypeConverter
    fun ruleSourceToString(value: RuleSource?): String? = value?.name

    @TypeConverter
    fun stringToRuleSource(value: String?): RuleSource? =
        value?.let { RuleSource.valueOf(it) }

    // --- AccountKind ---
    @TypeConverter
    fun accountKindToString(value: AccountKind?): String? = value?.name

    @TypeConverter
    fun stringToAccountKind(value: String?): AccountKind? =
        value?.let { AccountKind.valueOf(it) }
}
