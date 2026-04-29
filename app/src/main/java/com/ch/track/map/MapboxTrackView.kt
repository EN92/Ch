package com.ch.track.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ch.track.domain.TrackPoint
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style

@Composable
fun MapboxTrackView(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                mapboxMap.loadStyle(
                    style(Style.MAPBOX_STREETS) {
                        if (points.size > 1) {
                            +geoJsonSource("track-source") {
                                geometry(
                                    LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })
                                )
                            }
                            +lineLayer("track-layer", "track-source") {
                                lineColor("#4FC3F7")
                                lineWidth(4.0)
                            }
                        }
                    }
                )
                points.lastOrNull()?.let {
                    mapboxMap.setCamera(
                        CameraOptions.Builder().center(Point.fromLngLat(it.lon, it.lat)).zoom(15.0).build()
                    )
                }
            }
        },
        update = { view ->
            // Keep minimal for MVP.
        }
    )
}
