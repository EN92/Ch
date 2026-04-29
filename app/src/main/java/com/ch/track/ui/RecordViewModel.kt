package com.ch.track.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ch.track.core.TrackRecorder
import com.ch.track.data.CloudSyncStub
import com.ch.track.data.DraftStore
import com.ch.track.data.TrackRepository
import com.ch.track.data.UsageMetrics
import com.ch.track.data.UsageMetricsStore
import com.ch.track.domain.ActivityType
import com.ch.track.domain.RecordStatus
import com.ch.track.domain.SamplingState
import com.ch.track.domain.TrackFilter
import com.ch.track.domain.FilterProfile
import com.ch.track.domain.TrackPoint
import com.ch.track.domain.TrackSession
import com.ch.track.domain.UserSettings
import com.ch.track.domain.calculateAvgPaceSecPerKm
import com.ch.track.domain.calculateDistanceMeters
import com.ch.track.domain.calculateSplitStats
import com.ch.track.domain.formatPace
import com.ch.track.location.LocationEngine
import com.ch.track.map.MapProviderFactory
import com.ch.track.map.MapVendor
import com.ch.track.storage.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class RecordViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TrackRepository(AppDatabase.get(app).trackDao())
    private val recorder = TrackRecorder(repository)
    private val locationEngine = LocationEngine(app)
    private val draftStore = DraftStore(app)
    private val cloudSyncStub = CloudSyncStub(app)
    private val trackFilter = TrackFilter()
    private val metricsStore = UsageMetricsStore(app)

    val status: StateFlow<RecordStatus> = recorder.statusFlow()
    val sampling: StateFlow<SamplingState> = recorder.samplingFlow()
    val pointCount: StateFlow<Int> = repository.pointCountFlow()
    val history: StateFlow<List<TrackSession>> = repository.historyFlow()

    private val _settings = MutableStateFlow(UserSettings())
    private val _message = MutableStateFlow("准备开始")
    private val _selected = MutableStateFlow<TrackSession?>(null)
    private val _metrics = MutableStateFlow(metricsStore.snapshot())
    private val _splitPreview = MutableStateFlow<List<String>>(emptyList())
    private val _recordSec = MutableStateFlow(0L)
    private val _distanceLive = MutableStateFlow(0f)
    private val _gpsQuality = MutableStateFlow("--")
    private val _countdown = MutableStateFlow(0)

    val settings: StateFlow<UserSettings> = _settings.asStateFlow()
    val message: StateFlow<String> = _message.asStateFlow()
    val selected: StateFlow<TrackSession?> = _selected.asStateFlow()
    val metrics: StateFlow<UsageMetrics> = _metrics.asStateFlow()
    val splitStats: StateFlow<List<String>> = _splitPreview.asStateFlow()
    val recordSec: StateFlow<Long> = _recordSec.asStateFlow()
    val distanceLive: StateFlow<Float> = _distanceLive.asStateFlow()
    val gpsQuality: StateFlow<String> = _gpsQuality.asStateFlow()
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    private val _historyFilter = MutableStateFlow<ActivityType?>(null)
    private val _favoritesOnly = MutableStateFlow(false)
    val historyFilter: StateFlow<ActivityType?> = _historyFilter.asStateFlow()
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    val filteredHistory: List<TrackSession>
        get() = history.value.filter { (_historyFilter.value == null || it.activityType == _historyFilter.value) && (!_favoritesOnly.value || it.isFavorite) }

    fun mapSummary(points: List<TrackPoint>): String = MapProviderFactory.create(_settings.value.mapVendor).renderSummary(points)

    private var startTs: Long = 0L
    private var tickerJob: Job? = null

    private fun currentProfile(): FilterProfile {
        val base = when (_settings.value.activityType) {
            ActivityType.RUN -> FilterProfile(9f, 25f, 0.22)
            ActivityType.HIKE -> FilterProfile(6f, 20f, 0.18)
            ActivityType.RIDE -> FilterProfile(18f, 35f, 0.30)
        }
        return base.copy(maxJumpSpeed = _settings.value.maxJumpSpeed, smoothFactor = _settings.value.smoothFactor.toDouble())
    }

    init {
        if (draftStore.hasActiveSession()) {
            _message.value = "检测到上次未完成记录，可继续或结束保存"
        }
        viewModelScope.launch {
            repository.observeDbHistory()?.collectLatest { dbHistory ->
                if (dbHistory.isNotEmpty()) {
                    _message.value = "已加载本地历史 ${dbHistory.size} 条"
                }
            }
        }
    }

    fun startWithCountdown(seconds: Int = 3) {
        viewModelScope.launch {
            for (i in seconds downTo 1) {
                _countdown.value = i
                _message.value = "即将开始记录: ${i}s"
                delay(1000)
            }
            _countdown.value = 0
            startOrPause()
        }
    }

    fun startOrPause() {
        when (status.value) {
            RecordStatus.STOPPED, RecordStatus.PAUSED -> {
                if (status.value == RecordStatus.STOPPED) {
                    repository.clearCurrentPoints()
                    trackFilter.reset()
                    startTs = System.currentTimeMillis()
                    draftStore.saveActiveSession(startTs, _settings.value.activityType.name)
                }
                recorder.start()
                startTicker()
                metricsStore.onStart()
                _metrics.value = metricsStore.snapshot()
                recorder.updateSamplingBySpeed(if (_settings.value.powerSave) 0.8f else 2.4f)
                locationEngine.start(sampling.value.intervalMs) { lat, lon, speed, acc, time ->
                    val profile = currentProfile()
                    trackFilter.filter(TrackPoint(lat, lon, time, speed, acc), profile)?.let {
                        repository.addPoint(it)
                        _distanceLive.value = calculateDistanceMeters(repository.currentPoints())
                    }
                    _gpsQuality.value = when { acc <= 8f -> "优"; acc <= 20f -> "良"; else -> "弱" }
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
                tickerJob?.cancel()
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
            avgPaceSecPerKm = pace,
            isFavorite = false
        )
        viewModelScope.launch {
            repository.saveSession(session)
            _splitPreview.value = calculateSplitStats(session.points).map { "第${it.kmIndex}公里 ${it.paceSecPerKm}s/km" }
            if (_settings.value.cloudSync) {
                val path = cloudSyncStub.syncSessions(history.value)
                _message.value = "已同步到云存根: $path"
            }
        }
        tickerJob?.cancel()
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
    fun updateSmoothFactor(value: Float) { _settings.value = _settings.value.copy(smoothFactor = value) }
    fun updateMaxJumpSpeed(value: Float) { _settings.value = _settings.value.copy(maxJumpSpeed = value) }

    fun cycleMapVendor() {
        val next = when (_settings.value.mapVendor) {
            MapVendor.GOOGLE -> MapVendor.MAPBOX
            MapVendor.MAPBOX -> MapVendor.AMAP
            MapVendor.AMAP -> MapVendor.BAIDU
            MapVendor.BAIDU -> MapVendor.GOOGLE
        }
        _settings.value = _settings.value.copy(mapVendor = next)
    }

    fun toggleCloudSync() { _settings.value = _settings.value.copy(cloudSync = !_settings.value.cloudSync) }
    fun toggleAiInsight() { _settings.value = _settings.value.copy(aiInsight = !_settings.value.aiInsight) }

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

    fun exportLastGpxToFile(): String {
        val session = history.value.firstOrNull() ?: return "暂无可导出轨迹"
        val gpx = repository.exportSessionAsGpx(session)
        val file = java.io.File(getApplication<Application>().filesDir, "track-${session.id.take(8)}.gpx")
        file.writeText(gpx)
        return "已导出到: ${file.absolutePath}"
    }
    fun clearHistory() { viewModelScope.launch { repository.clearHistory() }; _message.value = "历史已清空" }
    fun toggleFavorite(id: String) { repository.toggleFavorite(id) }
    fun toggleFavoritesOnly() { _favoritesOnly.value = !_favoritesOnly.value }

    fun cycleHistoryFilter() {
        _historyFilter.value = when (_historyFilter.value) {
            null -> ActivityType.RUN
            ActivityType.RUN -> ActivityType.HIKE
            ActivityType.HIKE -> ActivityType.RIDE
            ActivityType.RIDE -> null
        }
    }
    fun batteryScoreHint(): String = "续航评分: " + if (_settings.value.powerSave) "A" else "B"
    fun quickMark() { _message.value = "已打点: ${recordSec.value}s / %.2fkm".format(distanceLive.value / 1000f) }

    fun weeklySummary(): String {
        val sessions = history.value.take(7)
        val totalKm = sessions.sumOf { it.distanceMeters.toDouble() } / 1000.0
        val avgPace = sessions.map { it.avgPaceSecPerKm }.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0
        return "近7条: ${sessions.size}次 / %.1fkm / 均配${avgPace}s".format(totalKm)
    }

    fun resumeDraftIfAny() {
        if (draftStore.hasActiveSession()) {
            _message.value = "已恢复草稿状态，请点击开始继续记录"
        } else {
            _message.value = "当前没有可恢复草稿"
        }
    }

    fun splitPreview(): String = if (_splitPreview.value.isEmpty()) "P1分段统计: 暂无" else _splitPreview.value.joinToString(" | ")

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                _recordSec.value = ((System.currentTimeMillis() - startTs) / 1000).coerceAtLeast(0)
                delay(1000)
            }
        }
    }

    fun liveSummary(): String = "实时: %.2fkm / ${recordSec.value}s / GPS${gpsQuality.value}".format(distanceLive.value / 1000f)

    fun pdcaSelfCheck(): String = "P0:多地图+回放 P1:分段统计+导出 P2:云同步/AI开关" 
}
