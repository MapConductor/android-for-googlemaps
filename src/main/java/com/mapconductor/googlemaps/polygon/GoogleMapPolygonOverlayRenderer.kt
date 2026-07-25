package com.mapconductor.googlemaps.polygon

import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolygonOptions
import com.mapconductor.core.ResourceProvider
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.polygon.AbstractPolygonOverlayRenderer
import com.mapconductor.core.polygon.PolygonEntityInterface
import com.mapconductor.core.polygon.PolygonState
import com.mapconductor.core.polygon.unionHoles
import com.mapconductor.core.spherical.createInterpolatePoints
import com.mapconductor.core.spherical.createLinearInterpolatePoints
import com.mapconductor.googlemaps.AdaptiveInterpolation
import com.mapconductor.googlemaps.GoogleMapActualPolygon
import com.mapconductor.googlemaps.GoogleMapViewHolder
import com.mapconductor.googlemaps.LatLngInterpolationCache
import com.mapconductor.googlemaps.toLatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Google Maps Android SDK は穴（inner rings）を [PolygonOptions.addHole] / [Polygon.setHoles] で
 * ネイティブに描画できるため、ラスタタイルマスクは使わずネイティブの穴で描画する。
 */
internal class GoogleMapPolygonOverlayRenderer(
    override val holder: GoogleMapViewHolder,
    override val coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AbstractPolygonOverlayRenderer<GoogleMapActualPolygon>() {
    private val interpolationCache = LatLngInterpolationCache(maxEntries = 64)

    private fun geodesicPoints(statePoints: List<GeoPointInterface>): List<LatLng> {
        val camera = holder.map.cameraPosition
        val maxSegmentLength =
            AdaptiveInterpolation.maxSegmentLengthMeters(
                zoom = camera.zoom,
                latitude = camera.target.latitude,
            )
        val key = AdaptiveInterpolation.cacheKey(AdaptiveInterpolation.pointsHash(statePoints), maxSegmentLength)
        interpolationCache.get(key)?.let { return it }

        val geoPoints = createInterpolatePoints(statePoints, maxSegmentLength = maxSegmentLength)
        val points = geoPoints.map { GeoPoint.from(it).toLatLng() }
        interpolationCache.put(key, points)
        return points
    }

    private fun toLatLngRing(
        statePoints: List<GeoPointInterface>,
        geodesic: Boolean,
    ): List<LatLng> =
        when (geodesic) {
            true -> geodesicPoints(statePoints)
            false -> createLinearInterpolatePoints(statePoints).map { GeoPoint.from(it).toLatLng() }
        }

    private fun toLatLngHoles(
        holes: List<List<GeoPointInterface>>,
        geodesic: Boolean,
    ): List<List<LatLng>> =
        holes
            .map { ring -> toLatLngRing(ring, geodesic) }
            .filter { it.size >= 3 }

    /**
     * 複数の穴が重なっている場合は結合（union）して重複を解消する。
     * 他プロバイダ（ArcGIS/Mapbox/MapLibre/HERE）と同じ [unionHoles] を用いる。
     */
    private fun resolveHoles(state: PolygonState): PolygonState =
        if (state.holes.size > 1) state.unionHoles() else state

    override suspend fun removePolygon(entity: PolygonEntityInterface<GoogleMapActualPolygon>) {
        coroutine.launch {
            entity.polygon.remove()
        }
    }

    override suspend fun createPolygon(state: PolygonState) =
        withContext(coroutine.coroutineContext) {
            val resolved = resolveHoles(state)
            val options =
                PolygonOptions()
                    .addAll(toLatLngRing(resolved.points, resolved.geodesic))
                    .apply { toLatLngHoles(resolved.holes, resolved.geodesic).forEach { addHole(it) } }
                    .strokeColor(resolved.strokeColor.toArgb())
                    .strokeWidth(ResourceProvider.dpToPx(resolved.strokeWidth).toFloat())
                    .fillColor(resolved.fillColor.toArgb())
                    .zIndex(resolved.zIndex.toFloat())
                    .clickable(false)
            holder.map.addPolygon(options).also {
                it.tag = state.id
            }
        }

    override suspend fun updatePolygonProperties(
        polygon: GoogleMapActualPolygon,
        current: PolygonEntityInterface<GoogleMapActualPolygon>,
        prev: PolygonEntityInterface<GoogleMapActualPolygon>,
    ): GoogleMapActualPolygon? =
        withContext(coroutine.coroutineContext) {
            val polygon = current.polygon
            val finger = current.fingerPrint
            val prevFinger = prev.fingerPrint
            if (
                finger.points != prevFinger.points ||
                finger.holes != prevFinger.holes ||
                finger.geodesic != prevFinger.geodesic
            ) {
                val resolved = resolveHoles(current.state)
                polygon.points = toLatLngRing(resolved.points, resolved.geodesic)
                polygon.holes = toLatLngHoles(resolved.holes, resolved.geodesic)
            }
            if (finger.strokeWidth != prevFinger.strokeWidth) {
                polygon.strokeWidth = ResourceProvider.dpToPx(current.state.strokeWidth).toFloat()
            }
            if (finger.strokeColor != prevFinger.strokeColor) {
                polygon.strokeColor = current.state.strokeColor.toArgb()
            }
            if (finger.fillColor != prevFinger.fillColor) {
                polygon.fillColor = current.state.fillColor.toArgb()
            }
            if (finger.zIndex != prevFinger.zIndex) {
                polygon.zIndex = current.state.zIndex.toFloat()
            }
            polygon
        }
}
