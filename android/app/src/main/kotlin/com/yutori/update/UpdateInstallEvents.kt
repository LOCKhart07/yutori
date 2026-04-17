package com.yutori.update

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Process-level bridge between UpdateInstallResultReceiver and any
// ViewModel interested in install outcomes. The receiver is driven by
// the system and has no hold on the VM, so we relay through a hot
// SharedFlow. Success is mostly informational (the app process dies
// when the replacement install completes), Failure is the real signal
// — lets the VM unstick the Downloading dialog when the user cancels
// the confirmation UI or Android rejects the package.
object UpdateInstallEvents {
    private val _outcomes = MutableSharedFlow<Outcome>(extraBufferCapacity = 4)
    val outcomes: SharedFlow<Outcome> = _outcomes.asSharedFlow()

    fun publish(outcome: Outcome) { _outcomes.tryEmit(outcome) }

    sealed interface Outcome {
        data object Success : Outcome
        data class Failure(val status: Int, val message: String?) : Outcome
    }
}
