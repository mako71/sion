package com.fuyi.app.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TranslationPipeline(
    private val recognizer: VoskRecognizer,
    private val translator: JaZhTranslator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    enum class State { Idle, Preparing, Ready, Listening, Translating, Error }

    data class Segment(
        val japanese: String,
        val chinese: String,
        val isPartial: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _state = MutableStateFlow(State.Idle)
    val state = _state.asStateFlow()

    private val _segment = MutableSharedFlow<Segment>(replay = 0, extraBufferCapacity = 16)
    val segments = _segment.asSharedFlow()

    suspend fun prepare(): Boolean {
        _state.value = State.Preparing
        val voskReady = recognizer.initialize()
        if (!voskReady) { _state.value = State.Error; return false }
        val modelReady = translator.prepareModel()
        if (!modelReady) { _state.value = State.Error; return false }
        _state.value = State.Ready
        return true
    }

    fun start(audioFlow: Flow<ByteArray>) {
        _state.value = State.Listening
        scope.launch {
            try {
                recognizer.recognize(audioFlow).collect { text ->
                    if (text.startsWith("[partial]")) {
                        val japanese = text.removePrefix("[partial]")
                        if (japanese.isNotBlank()) {
                            _segment.emit(Segment(japanese = japanese, chinese = "", isPartial = true))
                        }
                    } else {
                        if (text.isNotBlank()) {
                            _state.value = State.Translating
                            val chinese = translator.translate(text) ?: ""
                            _segment.emit(Segment(japanese = text, chinese = translator.formatTranslated(chinese)))
                            _state.value = State.Listening
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = State.Error
            }
        }
    }

    fun stop() {
        scope.cancel()
        recognizer.release()
        _state.value = State.Idle
    }

    fun destroy() { stop() }
}
