package com.yutori.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the Settings → Send feedback sheet (#113). Owns the title /
 * description strings and produces the [FeedbackDraft] the screen
 * hands to the mail client via [FeedbackMailer].
 *
 * There is no async/network state anymore — composing a `mailto:`
 * draft is synchronous. The only post-tap state is [Phase.Failed],
 * used when no mail app can handle the intent. Typed content always
 * survives so the user never loses what they wrote.
 */
class FeedbackViewModel : ViewModel() {

    private val _state = MutableStateFlow(FeedbackUiState())
    val state: StateFlow<FeedbackUiState> = _state.asStateFlow()

    fun setTitle(value: String) {
        _state.value = _state.value.copy(title = value, phase = Phase.Editing)
    }

    fun setDescription(value: String) {
        _state.value = _state.value.copy(description = value, phase = Phase.Editing)
    }

    /**
     * Subject + body for the mailto draft. Caller must gate on
     * [FeedbackUiState.canSend] (title non-blank) before using this.
     */
    fun draft(): FeedbackDraft {
        val current = _state.value
        return FeedbackDraft(
            subject = current.title.trim(),
            body = buildBody(current.description),
        )
    }

    /** No installed app could handle the `ACTION_SENDTO` intent. */
    fun onNoEmailApp() {
        _state.value = _state.value.copy(
            phase = Phase.Failed(
                "No email app found. Email ${FeedbackMailer.RECIPIENT} directly.",
            ),
        )
    }

    /** Reset after the draft is handed off — caller dismisses the sheet. */
    fun reset() {
        _state.value = FeedbackUiState()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedbackViewModel() as T
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

data class FeedbackDraft(val subject: String, val body: String)

data class FeedbackUiState(
    val title: String = "",
    val description: String = "",
    val phase: Phase = Phase.Editing,
) {
    val canSend: Boolean get() = title.isNotBlank()
}

sealed interface Phase {
    data object Editing : Phase
    data class Failed(val message: String) : Phase
}
