package com.ch.track.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import com.ch.track.domain.TrackPoint

@Composable
fun AMapTrackView(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply { text = "高德地图SDK已接入，待配置Key后启用真实地图渲染（点数:${points.size}）" }
        }
    )
}
