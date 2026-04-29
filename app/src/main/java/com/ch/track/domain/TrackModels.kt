package com.ch.track.domain

import com.ch.track.map.MapVendor

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val time: Long,
    val speed: Float,
    val accuracy: Float
)

enum class ActivityType { RUN, HIKE, RIDE }

data class TrackSession(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val activityType: ActivityType,
    val points: List<TrackPoint>,
    val distanceMeters: Float,
    val avgPaceSecPerKm: Int,
    val isFavorite: Boolean = false
)

data class UserSettings(
    val unit: String = "km",
    val powerSave: Boolean = false,
    val autoPause: Boolean = true,
    val activityType: ActivityType = ActivityType.RUN,
    val smoothFactor: Float = 0.22f,
    val maxJumpSpeed: Float = 9f,
    val mapVendor: MapVendor = MapVendor.GOOGLE,
    val cloudSync: Boolean = false,
    val aiInsight: Boolean = false
)

enum class SamplingState(val intervalMs: Long) {
    IDLE(10_000),
    SLOW(3_000),
    FAST(1_000)
}

enum class RecordStatus {
    STOPPED,
    RECORDING,
    PAUSED
}
