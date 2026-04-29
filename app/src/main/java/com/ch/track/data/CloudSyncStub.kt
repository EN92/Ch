package com.ch.track.data

import android.content.Context
import com.ch.track.domain.TrackSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CloudSyncStub(context: Context) {
    private val file = File(context.filesDir, "cloud_sync_stub.json")

    fun syncSessions(sessions: List<TrackSession>): String {
        val arr = JSONArray()
        sessions.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("distanceMeters", it.distanceMeters)
                put("avgPaceSecPerKm", it.avgPaceSecPerKm)
                put("endTime", it.endTime)
            })
        }
        file.writeText(arr.toString())
        return file.absolutePath
    }
}
