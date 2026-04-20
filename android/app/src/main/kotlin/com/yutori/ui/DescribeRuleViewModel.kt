package com.yutori.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yutori.ai.ExtractionResult
import com.yutori.ai.RulePrefill
import com.yutori.ai.RuleExtractor
import com.yutori.ai.ValidationFailure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Drives the Describe-this-rule bottom sheet. Single-input, single-
 * output — user types text, taps Extract, gets either a Success event
 * (navigate to the edit form with [RulePrefill]) or an inline error
 * state.
 *
 * See `plans/ai-rules-spec.md` §6.3 and mockup §C.
 */
class DescribeRuleViewModel(
    private val extractor: RuleExtractor,
) : ViewModel() {

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _state = MutableStateFlow<SheetState>(SheetState.Idle)
    val state: StateFlow<SheetState> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onInputChanged(text: String) {
        _input.value = text
    }

    /** Optional seed text — used by the unsure-suggestion entry point. */
    fun seed(text: String) {
        _input.value = text
    }

    fun onExtract() {
        val prompt = _input.value.trim().ifEmpty { return }
        if (_state.value is SheetState.Extracting) return

        _state.value = SheetState.Extracting
        viewModelScope.launch {
            when (val result = extractor.extract(prompt)) {
                is ExtractionResult.Success -> {
                    _events.trySend(
                        Event.Extracted(
                            RulePrefill(
                                pattern = result.rule.pattern,
                                category = result.rule.category,
                            ),
                        ),
                    )
                    _state.value = SheetState.Idle
                }

                is ExtractionResult.ValidationFailed -> {
                    _state.value = SheetState.ValidationError(result.reason)
                }

                is ExtractionResult.ModelUnavailable -> {
                    _state.value = SheetState.ModelUnavailable(
                        detail = result.cause.message,
                    )
                }
            }
        }
    }

    fun onDismissError() {
        _state.value = SheetState.Idle
    }

    sealed interface SheetState {
        data object Idle : SheetState
        data object Extracting : SheetState
        data class ValidationError(val reason: ValidationFailure) : SheetState
        data class ModelUnavailable(val detail: String?) : SheetState
    }

    sealed interface Event {
        data class Extracted(val prefill: RulePrefill) : Event
    }

    class Factory(
        private val extractor: RuleExtractor,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DescribeRuleViewModel(extractor) as T
    }
}
