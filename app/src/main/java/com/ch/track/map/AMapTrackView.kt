package com.ch.track.map

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.amap.api.maps.AMapOptions
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.PolylineOptions
import com.ch.track.domain.TrackPoint

@Composable
fun AMapTrackView(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx, AMapOptions()).apply {
                onCreate(Bundle())
                val map = this.map
                if (points.size > 1) {
                    map.addPolyline(
                        PolylineOptions().addAll(points.map { LatLng(it.lat, it.lon) }).width(10f)
                    )
                }
            }
        }
    )
}
