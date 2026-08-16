package com.mapconductor.googlemaps

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

/**
 * 地図のタップ処理。
 *
 * カスケード（marker → circle → groundImage → polyline → polygon → map）は
 * コアの [com.mapconductor.core.controller.BaseMapViewController.dispatchTap] が回す。
 *
 * ## なぜメインスレッドへ寄せるか
 *
 * マーカーのヒットテストはアイコン矩形で判定するため座標を画面へ投影する。
 * Google Maps の `Projection` は UI スレッドから触る前提なので、カスケード全体を
 * メインで回す（android-for-tomtom も同じ理由でメインに寄せている）。
 *
 * ## ネイティブのマーカークリックとの関係
 *
 * ネイティブの `Marker` として描かれたマーカーは `OnMarkerClickListener` が先に
 * 拾うので、ここへは来ない（`MarkerOptions` に clickable 相当が無く、タップしても
 * `OnMapClickListener` が発火しないため。[com.mapconductor.core.marker.dispatchNativeMarkerClick] 参照）。
 * ここで拾うのは**タイル描画されたマーカー**で、それは
 * [GoogleMapViewController.dispatchMarkerTap] が担う。
 */
internal fun GoogleMapViewController.handleMapClick(position: LatLng) {
    val touchPosition = position.toGeoPoint()
    mainCoroutine.launch { dispatchTap(touchPosition) }
}
