package com.ch.track.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ch.track.domain.TrackPoint
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun GoogleMapTrackView(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    val fallback = LatLng(39.9, 116.3)
    val center = points.lastOrNull()?.let { LatLng(it.lat, it.lon) } ?: fallback
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 15f)
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
        properties = MapProperties(isMyLocationEnabled = false)
    ) {
        if (points.size > 1) {
            Polyline(points = points.map { LatLng(it.lat, it.lon) })
        }
    }
}
