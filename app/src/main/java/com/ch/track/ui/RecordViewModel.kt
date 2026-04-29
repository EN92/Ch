package com.ch.track.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ch.track.core.TrackRecorder
import com.ch.track.data.TrackRepository
import com.ch.track.domain.ActivityType
import com.ch.track.domain.RecordStatus
import com.ch.track.domain.SamplingState
import com.ch.track.domain.TrackPoint
import com.ch.track.domain.TrackSession
import com.ch.track.domain.UserSettings
import com.ch.track.domain.calculateAvgPaceSecPerKm
import com.ch.track.domain.calculateDistanceMeters
import com.ch.track.domain.formatPace
import com.ch.track.location.LocationEngine
import com.ch.track.storage.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class RecordViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TrackRepository(AppDatabase.get(app).trackDao())
    private val recorder = TrackRecorder(repository)
    private val locationEngine = LocationEngine(app)

    val status: StateFlow<RecordStatus> = recorder.statusFlow()
    val sampling: StateFlow<SamplingState> = recorder.samplingFlow()
    val pointCount: StateFlow<Int> = repository.pointCountFlow()
    val history: StateFlow<List<TrackSession>> = repository.historyFlow()

    private val _settings = MutableStateFlow(UserSettings())
    private val _message = MutableStateFlow("准备开始")

    val settings: StateFlow<UserSettings> = _settings.asStateFlow()
    val message: StateFlow<String> = _message.asStateFlow()

    private var startTs: Long = 0L

    fun startOrPause() {
        when (status.value) {
            RecordStatus.STOPPED, RecordStatus.PAUSED -> {
                if (status.value == RecordStatus.STOPPED) {
                    repository.clearCurrentPoints()
                    startTs = System.currentTimeMillis()
                }
                recorder.start()
                recorder.updateSamplingBySpeed(if (_settings.value.powerSave) 0.8f else 2.4f)
                locationEngine.start(sampling.value.intervalMs) { lat, lon, speed, acc, time ->
                    repository.addPoint(TrackPoint(lat, lon, time, speed, acc))
                    recorder.updateSamplingBySpeed(speed)
                }
                _message.value = "录制中（真实GPS）"
            }
            RecordStatus.RECORDING -> {
                recorder.pause()
                locationEngine.stop()
                _message.value = "已暂停"
            }
        }
    }

    fun stop() {
        val points = repository.currentPoints()
        val distance = calculateDistanceMeters(points)
        val duration = ((System.currentTimeMillis() - startTs) / 1000).coerceAtLeast(1)
        val pace = calculateAvgPaceSecPerKm(distance, duration)
        val session = TrackSession(
            id = UUID.randomUUID().toString(),
            startTime = startTs,
            endTime = System.currentTimeMillis(),
            activityType = _settings.value.activityType,
            points = points,
            distanceMeters = distance,
            avgPaceSecPerKm = pace
        )
        viewModelScope.launch { repository.saveSession(session) }
        locationEngine.stop()
        recorder.stop()
        _message.value = "已结束并保存"
    }

    fun togglePowerMode() { _settings.value = _settings.value.copy(powerSave = !_settings.value.powerSave) }
    fun toggleUnit() { _settings.value = _settings.value.copy(unit = if (_settings.value.unit == "km") "mile" else "km") }
    fun toggleAutoPause() { _settings.value = _settings.value.copy(autoPause = !_settings.value.autoPause) }
    fun cycleActivityType() {
        val next = when (_settings.value.activityType) { ActivityType.RUN -> ActivityType.HIKE; ActivityType.HIKE -> ActivityType.RIDE; ActivityType.RIDE -> ActivityType.RUN }
        _settings.value = _settings.value.copy(activityType = next)
    }

    fun summary(): String {
        val distance = calculateDistanceMeters(repository.currentPoints())
        val pace = calculateAvgPaceSecPerKm(distance, 360)
        val distanceText = if (_settings.value.unit == "km") "%.2f km".format(distance / 1000f) else "%.2f mi".format(distance / 1609.34f)
        return "${_settings.value.activityType} · 距离 $distanceText · 配速 ${formatPace(pace)}"
    }

    fun shareText(): String = "TraceMaster | ${summary()}"
    fun exportLastGpx(): String = history.value.firstOrNull()?.let { repository.exportSessionAsGpx(it) } ?: "暂无可导出轨迹"
    fun clearHistory() { viewModelScope.launch { repository.clearHistory() }; _message.value = "历史已清空" }
}
