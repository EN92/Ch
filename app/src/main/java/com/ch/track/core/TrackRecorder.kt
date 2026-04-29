package com.ch.track.core

import com.ch.track.data.TrackRepository
import com.ch.track.domain.RecordStatus
import com.ch.track.domain.SamplingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrackRecorder(
    private val repository: TrackRepository
) {
    private val _recordStatus = MutableStateFlow(RecordStatus.STOPPED)
    private val _samplingState = MutableStateFlow(SamplingState.IDLE)

    fun statusFlow(): StateFlow<RecordStatus> = _recordStatus.asStateFlow()
    fun samplingFlow(): StateFlow<SamplingState> = _samplingState.asStateFlow()

    fun start() {
        _recordStatus.value = RecordStatus.RECORDING
    }

    fun pause() {
        _recordStatus.value = RecordStatus.PAUSED
        _samplingState.value = SamplingState.IDLE
    }

    fun stop() {
        _recordStatus.value = RecordStatus.STOPPED
        _samplingState.value = SamplingState.IDLE
    }

    fun updateSamplingBySpeed(speed: Float) {
        _samplingState.value = when {
            speed < 0.5f -> SamplingState.IDLE
            speed < 3.0f -> SamplingState.SLOW
            else -> SamplingState.FAST
        }
    }
}
