package com.mapconductor.googlemaps

import com.google.android.gms.maps.model.CameraPosition
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.map.CameraBearing
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MapPaddings
import com.mapconductor.core.map.MapPaddingsInterface
import com.mapconductor.core.spherical.Spherical
import com.mapconductor.core.zoom.AbstractZoomAltitudeConverter
import com.mapconductor.googlemaps.zoom.ZoomAltitudeConverter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

private val converter = ZoomAltitudeConverter(AbstractZoomAltitudeConverter.DEFAULT_ZOOM0_ALTITUDE)

fun MapCameraPosition.toCameraPosition(): CameraPosition {
    if (this.tilt >= 0) {
        return CameraPosition
            .builder()
            .target(GeoPoint.from(position).toLatLng())
            .zoom(zoom.toFloat())
            .tilt(tilt.coerceIn(0.0, 60.0).toFloat())
            .bearing(CameraBearing.toNativeHeading(bearing).toFloat())
            .build()
    } else {
        // tilt < 0: 水平線より abs(tilt) 度上方を向く（仰角ビュー）
        // カメラ eye を固定したまま bearing 方向の前方を見る。
        // Google Maps は上向き pitch を表現できないため、地面ターゲットをカメラ真下から
        // bearing 方向に altitude * tan(|tilt|) メートル前方へ置き、同じ eye 位置・高さを再現する。
        val tiltAbsDeg = abs(tilt).coerceIn(0.0, 60.0)
        val tiltAbsRad = Math.toRadians(tiltAbsDeg)
        val altitude = converter.zoomLevelToAltitude(zoom, position.latitude, 0.0)
        val distanceForward = altitude * tan(tiltAbsRad)
        val target = Spherical.computeOffset(position, distanceForward, CameraBearing.toNativeHeading(bearing))
        val adjustedZoom = converter.altitudeToZoomLevel(altitude / cos(tiltAbsRad), target.latitude, 0.0)

        return CameraPosition
            .builder()
            .target(target.toLatLng())
            .zoom(adjustedZoom.toFloat())
            .tilt(tiltAbsDeg.toFloat())
            .bearing(CameraBearing.toNativeHeading(bearing).toFloat())
            .build()
    }
}

fun MapCameraPosition.Companion.from(position: MapCameraPositionInterface): MapCameraPosition =
    when (position) {
        is MapCameraPosition -> position
        else ->
            MapCameraPosition(
                position = position.position,
                zoom = position.zoom,
                bearing = position.bearing,
                tilt = position.tilt,
                paddings = position.paddings,
                visibleRegion = position.visibleRegion,
            )
    }

fun CameraPosition.toMapCameraPosition(paddings: MapPaddingsInterface = MapPaddings.Zeros): MapCameraPosition {
    val altitude =
        converter.zoomLevelToAltitude(
            zoomLevel = zoom.toDouble(),
            latitude = target.latitude,
            tilt = tilt.toDouble(),
        )
    val position = target.toGeoPoint().copy(altitude = altitude)
    return MapCameraPosition(
        position = position,
        zoom = zoom.toDouble(),
        bearing = CameraBearing.bearingFromNativeHeading(bearing.toDouble()),
        tilt = tilt.toDouble(),
        paddings = paddings,
        visibleRegion = null,
    )
}
