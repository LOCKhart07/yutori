package com.spendwise.database

import androidx.room.TypeConverter
import com.spendwise.classifier.AccountKind
import com.spendwise.classifier.BudgetEffect
import com.spendwise.classifier.PatternKind
import com.spendwise.classifier.RuleSource
import com.spendwise.parser.Category
import com.spendwise.parser.Classification
import com.spendwise.transactions.SourceRole

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
