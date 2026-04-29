package com.ch.track.data

import com.ch.track.domain.ActivityType
import com.ch.track.domain.TrackPoint
import com.ch.track.domain.TrackSession
import com.ch.track.storage.PointEntity
import com.ch.track.storage.SessionEntity
import com.ch.track.storage.TrackDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrackRepository(
    private val dao: TrackDao? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val points = mutableListOf<TrackPoint>()
    private val sessions = mutableListOf<TrackSession>()

    private val _pointCount = MutableStateFlow(0)
    private val _history = MutableStateFlow<List<TrackSession>>(emptyList())

    fun addPoint(point: TrackPoint) {
        points.add(point)
        _pointCount.value = points.size
    }

    fun clearCurrentPoints() {
        points.clear()
        _pointCount.value = 0
    }

    fun currentPoints(): List<TrackPoint> = points.toList()

    suspend fun saveSession(session: TrackSession) {
        sessions.add(0, session)
        _history.value = sessions.toList()
        dao?.insertSession(
            SessionEntity(session.id, session.startTime, session.endTime, session.activityType.name, session.distanceMeters, session.avgPaceSecPerKm, session.isFavorite)
        )
        dao?.insertPoints(session.points.map {
            PointEntity(sessionId = session.id, lat = it.lat, lon = it.lon, time = it.time, speed = it.speed, accuracy = it.accuracy)
        })
    }

    fun observeDbHistory(): Flow<List<TrackSession>>? = dao?.observeSessions()?.map { rows ->
        rows.map { TrackSession(it.id, it.startTime, it.endTime, ActivityType.valueOf(it.activityType), emptyList(), it.distanceMeters, it.avgPaceSecPerKm, it.isFavorite) }
    }

    suspend fun loadSessionDetail(sessionId: String): TrackSession? {
        val s = dao?.sessionById(sessionId) ?: return sessions.firstOrNull { it.id == sessionId }
        val points = dao.pointsBySession(sessionId).map { TrackPoint(it.lat, it.lon, it.time, it.speed, it.accuracy) }
        return TrackSession(s.id, s.startTime, s.endTime, ActivityType.valueOf(s.activityType), points, s.distanceMeters, s.avgPaceSecPerKm, s.isFavorite)
    }

    fun pointCountFlow(): StateFlow<Int> = _pointCount.asStateFlow()
    fun historyFlow(): StateFlow<List<TrackSession>> = _history.asStateFlow()

    fun toggleFavorite(sessionId: String) {
        val updated = _history.value.map { if (it.id == sessionId) it.copy(isFavorite = !it.isFavorite) else it }
        _history.value = updated
        val session = updated.firstOrNull { it.id == sessionId } ?: return
        dao?.let { daoRef ->
            scope.launch { daoRef.updateFavorite(sessionId, session.isFavorite) }
        }
    }

    suspend fun clearHistory() {
        sessions.clear()
        _history.value = emptyList()
        dao?.clearSessions()
    }

    fun exportSessionAsGpx(session: TrackSession): String {
        val head = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="TraceMaster">
              <trk><name>${session.id}</name><trkseg>
        """.trimIndent()

        val body = session.points.joinToString("\n") {
            "<trkpt lat=\"${it.lat}\" lon=\"${it.lon}\"><time>${it.time}</time></trkpt>"
        }

        return "$head\n$body\n</trkseg></trk></gpx>"
    }
}
