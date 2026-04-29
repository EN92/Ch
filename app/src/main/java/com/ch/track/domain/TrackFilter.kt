package com.ch.track.domain

import kotlin.math.abs

class TrackFilter {
    private var last: TrackPoint? = null

    fun filter(raw: TrackPoint): TrackPoint? {
        val prev = last
        if (prev == null) {
            last = raw
            return raw
        }

        val dt = ((raw.time - prev.time).coerceAtLeast(1L)) / 1000f
        val estSpeed = distanceMeter(prev, raw) / dt

        if (estSpeed > 18f || raw.accuracy > 40f) {
            return null
        }

        val smooth = TrackPoint(
            lat = prev.lat * 0.75 + raw.lat * 0.25,
            lon = prev.lon * 0.75 + raw.lon * 0.25,
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
