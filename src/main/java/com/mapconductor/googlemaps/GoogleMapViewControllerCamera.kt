package com.mapconductor.googlemaps

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap.CancelableCallback
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.VisibleRegion
import kotlinx.coroutines.launch

// カメラの適用と、Google Maps からのカメライベントの受け口。
// Google Maps だけは範囲制限もズーム制限もネイティブ API があるので、
// 他プロバイダのような停止時クランプは要らない。
internal fun GoogleMapViewController.handleMoveCamera(position: MapCameraPosition) {
    mainCoroutine.launch {
        val dstCameraPosition = position.toCameraPosition()
        val cameraUpdate = CameraUpdateFactory.newCameraPosition(dstCameraPosition)
        holder.map.moveCamera(cameraUpdate)
    }
}

internal fun GoogleMapViewController.handleAnimateCamera(
    position: MapCameraPosition,
    duration: Long,
) {
    val dstCameraPosition = position.toCameraPosition()
    mainCoroutine.launch {
        val cameraUpdate = CameraUpdateFactory.newCameraPosition(dstCameraPosition)
        holder.map.animateCamera(
            cameraUpdate,
            duration.toInt(),
            object : CancelableCallback {
                override fun onCancel() {
                    emitCameraMoveEnd(getMapCameraPosition())
                }

                override fun onFinish() {
                    emitCameraMoveEnd(getMapCameraPosition())
                }
            },
        )
    }
}

internal fun GoogleMapViewController.handleFitBounds(
    bounds: GeoRectBounds,
    padding: Int,
) {
    val latLngBounds = bounds.toLatLngBounds() ?: return
    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(latLngBounds, padding)
    mainCoroutine.launch {
        holder.map.moveCamera(cameraUpdate)
    }
}

internal fun GoogleMapViewController.handleCameraRestriction(restriction: CameraRestriction?) {
    // Google Maps の統一ズームはネイティブズームそのものなので変換不要。
    mainCoroutine.launch {
        holder.map.setLatLngBoundsForCameraTarget(restriction?.bounds?.toLatLngBounds())
        // preference をクリアするには null 相当（下限 = 通常の最小値）を渡す必要があるため
        // resetMinMaxZoomPreference で一旦解除してから設定する。
        holder.map.resetMinMaxZoomPreference()
        restriction?.minZoom?.let { holder.map.setMinZoomPreference(it.toFloat()) }
        restriction?.maxZoom?.let { holder.map.setMaxZoomPreference(it.toFloat()) }
    }
}

internal fun GoogleMapViewController.handleCameraMove() {
    val mapCameraPosition = getMapCameraPosition()
    defaultCoroutine.launch {
        emitCameraPosition(mapCameraPosition)
    }
    emitCameraMove(getMapCameraPosition())
}

internal fun GoogleMapViewController.handleCameraIdle() {
    val mapCameraPosition = getMapCameraPosition()
    defaultCoroutine.launch { markerController.onCameraChanged(mapCameraPosition) }
    emitCameraMoveEnd(getMapCameraPosition())
}

internal fun GoogleMapViewController.handleCameraMoveStarted(p0: Int) {
    emitCameraMoveStart(getMapCameraPosition())
}

internal fun GoogleMapViewController.handleCameraMoveCanceled() {
    emitCameraMoveEnd(getMapCameraPosition())
}

internal fun GoogleMapViewController.getMapCameraPosition(): MapCameraPosition {
    val camera = holder.map.cameraPosition.toMapCameraPosition()
    holder.map.projection.visibleRegion.let {
        val visibleRegion =
            VisibleRegion(
                bounds = it.latLngBounds.toGeoRectBounds(),
                nearLeft = it.nearLeft.toGeoPoint(),
                nearRight = it.nearRight.toGeoPoint(),
                farLeft = it.farLeft.toGeoPoint(),
                farRight = it.farRight.toGeoPoint(),
            )
        return camera.copy(visibleRegion = visibleRegion)
    }
}
