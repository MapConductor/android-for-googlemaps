package com.mapconductor.googlemaps

import android.graphics.PointF
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.map.MapViewHolderInterface
import android.graphics.Point

class GoogleMapViewHolder(
    override val mapView: MapView,
    override val map: GoogleMap,
) : MapViewHolderInterface<MapView, GoogleMap> {
    override fun toScreenOffset(position: GeoPointInterface): PointF? {
        val point =
            map.projection.toScreenLocation(
                GeoPoint.from(position).toLatLng(),
            )
        return PointF(point.x.toFloat(), point.y.toFloat())
    }

    override suspend fun fromScreenOffset(offset: PointF): GeoPoint? =
        map.projection
            .fromScreenLocation(
                Point(
                    offset.x.toInt(),
                    offset.y.toInt(),
                ),
            ).toGeoPoint()
}
