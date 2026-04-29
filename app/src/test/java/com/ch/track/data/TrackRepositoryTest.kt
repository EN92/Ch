package com.ch.track.data

import com.ch.track.domain.ActivityType
import com.ch.track.domain.TrackPoint
import com.ch.track.domain.TrackSession
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRepositoryTest {
    @Test
    fun exportGpx_shouldContainTrackPoint() {
        val repo = TrackRepository()
        val session = TrackSession(
            id = "s1",
            startTime = 1L,
            endTime = 2L,
            activityType = ActivityType.RUN,
            points = listOf(TrackPoint(1.0, 2.0, 3L, 1f, 1f)),
            distanceMeters = 100f,
            avgPaceSecPerKm = 300
        )
        val gpx = repo.exportSessionAsGpx(session)
        assertTrue(gpx.contains("trkpt"))
        assertTrue(gpx.contains("TraceMaster"))
    }
}
