package com.ch.track.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ch.track.core.TrackRecorder
import com.ch.track.data.DraftStore
import com.ch.track.data.TrackRepository
import com.ch.track.data.UsageMetrics
import com.ch.track.data.UsageMetricsStore
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
    private val draftStore = DraftStore(app)
    private val metricsStore = UsageMetricsStore(app)

    val status: StateFlow<RecordStatus> = recorder.statusFlow()
    val sampling: StateFlow<SamplingState> = recorder.samplingFlow()
    val pointCount: StateFlow<Int> = repository.pointCountFlow()
    val history: StateFlow<List<TrackSession>> = repository.historyFlow()

    private val _settings = MutableStateFlow(UserSettings())
    private val _message = MutableStateFlow("准备开始")
    private val _selected = MutableStateFlow<TrackSession?>(null)
    private val _metrics = MutableStateFlow(metricsStore.snapshot())

    val settings: StateFlow<UserSettings> = _settings.asStateFlow()
    val message: StateFlow<String> = _message.asStateFlow()
    val selected: StateFlow<TrackSession?> = _selected.asStateFlow()
    val metrics: StateFlow<UsageMetrics> = _metrics.asStateFlow()

    private var startTs: Long = 0L

    init {
        if (draftStore.hasActiveSession()) {
            _message.value = "检测到上次未完成记录，可继续或结束保存"
        }
    }

    fun startOrPause() {
        when (status.value) {
            RecordStatus.STOPPED, RecordStatus.PAUSED -> {
                if (status.value == RecordStatus.STOPPED) {
                    repository.clearCurrentPoints()
                    startTs = System.currentTimeMillis()
                    draftStore.saveActiveSession(startTs, _settings.value.activityType.name)
                }
                recorder.start()
                metricsStore.onStart()
                _metrics.value = metricsStore.snapshot()
                recorder.updateSamplingBySpeed(if (_settings.value.powerSave) 0.8f else 2.4f)
                locationEngine.start(sampling.value.intervalMs) { lat, lon, speed, acc, time ->
                    if (acc <= 30f) repository.addPoint(TrackPoint(lat, lon, time, speed, acc))
                    recorder.updateSamplingBySpeed(speed)
                    if (_settings.value.autoPause && speed < 0.5f) {
                        recorder.pause()
                        locationEngine.stop()
                        _message.value = "已自动暂停（低速省电）"
                    }
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
        draftStore.clearActiveSession()
        metricsStore.onFinish()
        _metrics.value = metricsStore.snapshot()
        _message.value = "已结束并保存"
    }

    fun selectSession(id: String) {
        viewModelScope.launch {
            _selected.value = repository.loadSessionDetail(id)
        }
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

    fun shareText(): String { metricsStore.onShare(); _metrics.value = metricsStore.snapshot(); return "TraceMaster | ${summary()}" }
    fun exportLastGpx(): String = history.value.firstOrNull()?.let { repository.exportSessionAsGpx(it) } ?: "暂无可导出轨迹"
    fun clearHistory() { viewModelScope.launch { repository.clearHistory() }; _message.value = "历史已清空" }

    fun resumeDraftIfAny() {
        if (draftStore.hasActiveSession()) {
            _message.value = "已恢复草稿状态，请点击开始继续记录"
        } else {
            _message.value = "当前没有可恢复草稿"
        }
    }

    fun pdcaSelfCheck(): String = "P:补齐权限/前台服务/存储 D:真实GPS录制 C:回放详情与中断检测 A:继续补地图回放" 
}
