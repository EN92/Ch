package com.ch.track.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.BaiduMapOptions
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.OverlayOptions
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.ch.track.domain.TrackPoint

@Composable
fun BaiduMapTrackView(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx, BaiduMapOptions()).apply {
                val map = this.map
                if (points.size > 1) {
                    val latlngs = points.map { LatLng(it.lat, it.lon) }
                    val overlay: OverlayOptions = PolylineOptions().points(latlngs).width(8)
                    map.addOverlay(overlay)
                    map.animateMapStatus(MapStatusUpdateFactory.newLatLng(latlngs.last()))
                }
            }
        }
    )
}
