package com.ch.track.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ch.track.domain.TrackPoint

@Composable
fun MultiMapTrackView(vendor: MapVendor, points: List<TrackPoint>, modifier: Modifier = Modifier) {
    when (vendor) {
        MapVendor.GOOGLE -> GoogleMapTrackView(points = points, modifier = modifier)
        MapVendor.MAPBOX -> MapboxTrackView(points = points, modifier = modifier)
        MapVendor.AMAP -> AMapTrackView(points = points, modifier = modifier)
        MapVendor.BAIDU -> BaiduMapTrackView(points = points, modifier = modifier)
    }
}
