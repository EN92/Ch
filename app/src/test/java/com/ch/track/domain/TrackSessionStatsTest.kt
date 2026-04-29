package com.ch.track.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSessionStatsTest {
    @Test
    fun paceFormatting_shouldReturnFallback_whenInvalid() {
        assertEquals("--:--", formatPace(0))
    }

    @Test
    fun calculateDistance_shouldBePositive_forMovingPoints() {
        val points = listOf(
            TrackPoint(39.9, 116.3, 0, 2f, 8f),
            TrackPoint(39.901, 116.301, 3, 2f, 8f)
        )
        assertTrue(calculateDistanceMeters(points) > 0f)
    }
}
