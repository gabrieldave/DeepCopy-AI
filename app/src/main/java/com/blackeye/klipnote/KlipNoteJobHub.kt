package com.blackeye.klipnote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Puente entre [KlipNoteWorkService] y la UI de [MainActivity].
 */
object KlipNoteJobHub {

    sealed class JobState {
        data object Idle : JobState()
        data class Running(val label: String) : JobState()
        data class Success(
            val transcript: String,
            val meta: TranscriptionResult?,
            val savedMedia: SavedMediaInfo?,
            val sourceLabel: String,
            val toastMessage: String
        ) : JobState()
        data class Failed(val message: String) : JobState()
        data object Cancelled : JobState()
    }

    private val _state = MutableStateFlow<JobState>(JobState.Idle)
    val state: StateFlow<JobState> = _state.asStateFlow()

    @Volatile
    var appInForeground: Boolean = false

    fun setRunning(label: String) {
        _state.value = JobState.Running(label)
    }

    fun setSuccess(
        transcript: String,
        meta: TranscriptionResult?,
        savedMedia: SavedMediaInfo?,
        sourceLabel: String,
        toastMessage: String
    ) {
        _state.value = JobState.Success(
            transcript = transcript,
            meta = meta,
            savedMedia = savedMedia,
            sourceLabel = sourceLabel,
            toastMessage = toastMessage
        )
    }

    fun setFailed(message: String) {
        _state.value = JobState.Failed(message)
    }

    fun setCancelled() {
        _state.value = JobState.Cancelled
    }

    fun consumeTerminal() {
        val cur = _state.value
        if (cur is JobState.Success || cur is JobState.Failed || cur is JobState.Cancelled) {
            _state.value = JobState.Idle
        }
    }

    fun isBusy(): Boolean = _state.value is JobState.Running
}
