package com.ch.track.domain

import kotlin.math.abs

data class FilterProfile(
    val maxJumpSpeed: Float,
    val maxAccuracy: Float,
    val smoothFactor: Double
)

class TrackFilter {
    private var last: TrackPoint? = null

    fun filter(raw: TrackPoint, profile: FilterProfile): TrackPoint? {
        val prev = last
        if (prev == null) {
            last = raw
            return raw
        }

        val dt = ((raw.time - prev.time).coerceAtLeast(1L)) / 1000f
        val estSpeed = distanceMeter(prev, raw) / dt

        if (estSpeed > profile.maxJumpSpeed || raw.accuracy > profile.maxAccuracy) {
            return null
        }

        val alpha = profile.smoothFactor
        val smooth = TrackPoint(
            lat = prev.lat * (1 - alpha) + raw.lat * alpha,
            lon = prev.lon * (1 - alpha) + raw.lon * alpha,
            time = raw.time,
            speed = (prev.speed * 0.6f + raw.speed * 0.4f),
            accuracy = raw.accuracy
        )
        last = smooth
        return smooth
    }

    private fun distanceMeter(a: TrackPoint, b: TrackPoint): Float {
        val dLat = abs((b.lat - a.lat) * 111_000.0).toFloat()
        val dLon = abs((b.lon - a.lon) * 111_000.0).toFloat()
        return kotlin.math.sqrt(dLat * dLat + dLon * dLon)
    }

    fun reset() { last = null }
}
