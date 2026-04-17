package com.yutori.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Settings → Send feedback sheet (#113). Owns the title /
 * description strings and the submit state machine.
 *
 * States are intentionally small: Editing (before tap) and After
 * (Sending / Success / Error). The description + title survive
 * errors so the user never loses what they typed.
 */
class FeedbackViewModel(
    private val reporter: IssueReporter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedbackUiState())
    val state: StateFlow<FeedbackUiState> = _state.asStateFlow()

    private var inFlight: Job? = null

    fun setTitle(value: String) {
        _state.value = _state.value.copy(title = value, phase = Phase.Editing)
    }

    fun setDescription(value: String) {
        _state.value = _state.value.copy(description = value, phase = Phase.Editing)
    }

    fun submit() {
        val current = _state.value
        if (current.title.isBlank()) return
        if (current.phase is Phase.Sending) return
        inFlight?.cancel()
        _state.value = current.copy(phase = Phase.Sending)
        inFlight = viewModelScope.launch {
            val body = buildBody(current.description)
            val result = withContext(ioDispatcher) {
                reporter.submit(title = current.title.trim(), body = body)
            }
            _state.value = when (result) {
                is SubmitResult.Success ->
                    _state.value.copy(phase = Phase.Sent(result.number, result.htmlUrl))
                is SubmitResult.NoToken ->
                    _state.value.copy(phase = Phase.Failed(
                        "This build isn't configured to send feedback.",
                    ))
                is SubmitResult.Network ->
                    _state.value.copy(phase = Phase.Failed(
                        "Couldn't send. Check connection.",
                    ))
                is SubmitResult.Http ->
                    _state.value.copy(phase = Phase.Failed(
                        "GitHub rejected the request (${result.code}).",
                    ))
                SubmitResult.BadResponse ->
                    _state.value.copy(phase = Phase.Failed(
                        "Sent, but GitHub returned an unexpected response.",
                    ))
            }
        }
    }

    /** Reset after a successful send — caller dismisses the sheet. */
    fun reset() {
        inFlight?.cancel()
        _state.value = FeedbackUiState()
    }

    class Factory(private val reporter: IssueReporter) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedbackViewModel(reporter) as T
    }

    private fun buildBody(description: String): String {
        val trimmed = description.trim()
        val context = FeedbackContext.render()
        return if (trimmed.isEmpty()) {
            context
        } else {
            "$trimmed\n\n$context"
        }
    }
}

data class FeedbackUiState(
    val title: String = "",
    val description: String = "",
    val phase: Phase = Phase.Editing,
) {
    val canSend: Boolean get() = title.isNotBlank() && phase !is Phase.Sending
}

sealed interface Phase {
    data object Editing : Phase
    data object Sending : Phase
    data class Sent(val number: Int, val htmlUrl: String) : Phase
    data class Failed(val message: String) : Phase
}
