package com.ch.track.domain

import kotlin.math.max

private const val METER_IN_KM = 1000f

fun calculateDistanceMeters(points: List<TrackPoint>): Float {
    if (points.size < 2) return 0f
    var sum = 0f
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        val dLat = (cur.lat - prev.lat).toFloat() * 111_000f
        val dLon = (cur.lon - prev.lon).toFloat() * 111_000f
        sum += kotlin.math.sqrt(dLat * dLat + dLon * dLon)
    }
    return sum
}

fun calculateAvgPaceSecPerKm(distanceMeters: Float, durationSec: Long): Int {
    if (distanceMeters <= 0f || durationSec <= 0) return 0
    val km = distanceMeters / METER_IN_KM
    return max(1, (durationSec / km).toInt())
}

fun formatPace(secPerKm: Int): String {
    if (secPerKm <= 0) return "--:--"
    val min = secPerKm / 60
    val sec = secPerKm % 60
    return "%d:%02d /km".format(min, sec)
}
