package com.ch.track.data

import android.content.Context

class DraftStore(context: Context) {
    private val sp = context.getSharedPreferences("trace_draft", Context.MODE_PRIVATE)

    fun saveActiveSession(startTs: Long, activityType: String) {
        sp.edit().putLong("start_ts", startTs).putString("activity", activityType).apply()
    }

    fun clearActiveSession() {
        sp.edit().clear().apply()
    }

    fun hasActiveSession(): Boolean = sp.getLong("start_ts", 0L) > 0L
}
