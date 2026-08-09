package com.slandbar.app.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Estado do cronômetro/temporizador do painel. */
data class TimerState(
    val mode: TimerMode = TimerMode.STOPWATCH,
    val running: Boolean = false,
    val elapsedMs: Long = 0L,
    val targetMs: Long = 0L
) {
    val finished: Boolean get() = mode == TimerMode.TIMER && targetMs > 0 && elapsedMs >= targetMs
}

enum class TimerMode { STOPWATCH, TIMER }

/** Cronômetro e temporizador (foco/contagem regressiva) com precisão de 100 ms. */
class TimerController(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state

    private var ticker: Job? = null
    private var baseElapsedMs: Long = 0L
    private var startedAt: Long = 0L

    fun setMode(mode: TimerMode, targetSeconds: Int = 0) {
        stopTicker()
        _state.value = TimerState(
            mode = mode,
            running = false,
            elapsedMs = 0L,
            targetMs = targetSeconds * 1000L
        )
    }

    fun startStop() {
        val s = _state.value
        if (s.running) {
            pause()
        } else {
            if (s.finished) _state.value = s.copy(elapsedMs = 0L)
            baseElapsedMs = _state.value.elapsedMs
            startedAt = System.currentTimeMillis()
            _state.value = _state.value.copy(running = true)
            ticker = scope.launch {
                while (isActive) {
                    delay(100)
                    val now = System.currentTimeMillis() - startedAt + baseElapsedMs
                    _state.value = _state.value.copy(elapsedMs = now)
                    if (_state.value.finished) {
                        pause()
                        onFinished?.invoke()
                    }
                }
            }
        }
    }

    fun pause() {
        val s = _state.value
        if (!s.running) return
        stopTicker()
        _state.value = s.copy(
            running = false,
            elapsedMs = System.currentTimeMillis() - startedAt + baseElapsedMs
        )
    }

    fun reset() {
        stopTicker()
        _state.value = _state.value.copy(running = false, elapsedMs = 0L)
    }

    /** Chamado quando um temporizador chega a zero. */
    var onFinished: (() -> Unit)? = null

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    companion object {
        fun format(ms: Long): String {
            val totalSec = ms / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else String.format("%02d:%02d", m, s)
        }
    }
}
