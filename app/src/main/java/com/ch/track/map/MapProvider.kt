package com.ch.track.map

import com.ch.track.domain.TrackPoint

enum class MapVendor { GOOGLE, MAPBOX, AMAP, BAIDU }

interface MapProvider {
    val vendor: MapVendor
    fun renderSummary(points: List<TrackPoint>): String
}

class GoogleMapProvider : MapProvider {
    override val vendor = MapVendor.GOOGLE
    override fun renderSummary(points: List<TrackPoint>) = "GoogleMap轨迹(${points.size}点)"
}

class MapboxProvider : MapProvider {
    override val vendor = MapVendor.MAPBOX
    override fun renderSummary(points: List<TrackPoint>) = "Mapbox轨迹(${points.size}点)"
}

class AMapProvider : MapProvider {
    override val vendor = MapVendor.AMAP
    override fun renderSummary(points: List<TrackPoint>) = "高德轨迹(${points.size}点)"
}

class BaiduMapProvider : MapProvider {
    override val vendor = MapVendor.BAIDU
    override fun renderSummary(points: List<TrackPoint>) = "百度轨迹(${points.size}点)"
}

object MapProviderFactory {
    fun create(vendor: MapVendor): MapProvider = when (vendor) {
        MapVendor.GOOGLE -> GoogleMapProvider()
        MapVendor.MAPBOX -> MapboxProvider()
        MapVendor.AMAP -> AMapProvider()
        MapVendor.BAIDU -> BaiduMapProvider()
    }
}
