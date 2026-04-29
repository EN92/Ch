package com.ch.track.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.ch.track.domain.TrackPoint

@Composable
fun MultiMapTrackView(vendor: MapVendor, points: List<TrackPoint>, modifier: Modifier = Modifier) {
    when (vendor) {
        MapVendor.GOOGLE -> GoogleMapTrackView(points = points, modifier = modifier)
        MapVendor.MAPBOX -> MapboxTrackView(points = points, modifier = modifier)
        MapVendor.AMAP, MapVendor.BAIDU -> {
            Box(modifier = modifier.background(Color(0xFF12171F))) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (points.size > 1) {
                        val minLat = points.minOf { it.lat }
                        val maxLat = points.maxOf { it.lat }
                        val minLon = points.minOf { it.lon }
                        val maxLon = points.maxOf { it.lon }

                        fun map(p: TrackPoint): Offset {
                            val x = ((p.lon - minLon) / (maxLon - minLon + 1e-9) * size.width).toFloat()
                            val y = ((1 - (p.lat - minLat) / (maxLat - minLat + 1e-9)) * size.height).toFloat()
                            return Offset(x, y)
                        }

                        for (i in 1 until points.size) {
                            drawLine(
                                color = Color(0xFF4FC3F7),
                                start = map(points[i - 1]),
                                end = map(points[i]),
                                strokeWidth = 4f
                            )
                        }
                    }
                }
                Text(
                    text = "${vendor.name} 轨迹预览（SDK待接入）",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}
