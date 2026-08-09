package com.mapconductor.googlemaps

import com.google.android.gms.maps.model.LatLng
import com.mapconductor.core.circle.CircleEvent
import com.mapconductor.core.groundimage.GroundImageEvent
import com.mapconductor.core.marker.clickableOnly
import com.mapconductor.core.polygon.PolygonEvent
import com.mapconductor.core.polyline.PolylineEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 地図のタップ処理。
// タップは**マーカーが先**で、どのマーカーにも当たらなかったときだけ
// 地図のタップとして扱う（android の他プロバイダと同じ順序）。
internal fun GoogleMapViewController.handleMapClick(position: LatLng) {
    val touchPosition = position.toGeoPoint()
    val zoomSnapshot =
        holder.map.cameraPosition.zoom
            .toDouble()
    defaultCoroutine.launch {
        // マーカーのヒットテストはアイコン矩形で判定するため座標を画面へ投影する。
        // Google Maps の `Projection` は UI スレッドから触る前提なので、ここだけメインへ切り替える
        // （android-for-tomtom も同じ理由でメインに寄せている）。
        val markerEntity =
            withContext(mainCoroutine.coroutineContext) {
                markerController.find(touchPosition, zoomSnapshot)
            }
        // clickable=false のマーカーは「当たらなかった」ことにして次の層へ進める
        // （握り潰すと地図クリックも飛ばなくなる）。判定はコアの clickableOnly。
        markerEntity.clickableOnly()?.let { entity ->
            mainCoroutine.launch { markerController.dispatchClick(entity.state) }
            return@launch
        }

        circleController.find(touchPosition)?.let { entity ->
            val event =
                CircleEvent(
                    state = entity.state,
                    clicked = touchPosition,
                )
            mainCoroutine.launch {
                circleController.dispatchClick(event)
            }
            return@launch
        }

        groundImageController.find(touchPosition)?.let { entity ->
            val event =
                GroundImageEvent(
                    state = entity.state,
                    clicked = touchPosition,
                )
            mainCoroutine.launch {
                groundImageController.dispatchClick(event)
            }
            return@launch
        }

        polylineController.findWithClosestPoint(touchPosition)?.let { hitResult ->
            val event =
                PolylineEvent(
                    state = hitResult.entity.state,
                    clicked = hitResult.closestPoint,
                )
            mainCoroutine.launch {
                polylineController.dispatchClick(event)
            }
            return@launch
        }

        polygonController.find(touchPosition)?.let { entity ->
            val event =
                PolygonEvent(
                    state = entity.state,
                    clicked = touchPosition,
                )
            mainCoroutine.launch {
                polygonController.dispatchClick(event)
            }
            return@launch
        }

        mapClickHandler()?.let {
            mainCoroutine.launch { it(position.toGeoPoint()) }
        }
    }
}
