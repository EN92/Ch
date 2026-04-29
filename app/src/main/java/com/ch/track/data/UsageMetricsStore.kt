package com.ch.track.data

import android.content.Context

data class UsageMetrics(
    val startCount: Int,
    val finishCount: Int,
    val shareCount: Int
)

class UsageMetricsStore(context: Context) {
    private val sp = context.getSharedPreferences("usage_metrics", Context.MODE_PRIVATE)

    fun onStart() = sp.edit().putInt("start", sp.getInt("start", 0) + 1).apply()
    fun onFinish() = sp.edit().putInt("finish", sp.getInt("finish", 0) + 1).apply()
    fun onShare() = sp.edit().putInt("share", sp.getInt("share", 0) + 1).apply()

    fun snapshot(): UsageMetrics = UsageMetrics(
        startCount = sp.getInt("start", 0),
        finishCount = sp.getInt("finish", 0),
        shareCount = sp.getInt("share", 0)
    )
}
