package com.mapconductor.googlemaps.polyline

import com.mapconductor.core.controller.OverlayControllerInterface
import com.mapconductor.core.polyline.PolylineController
import com.mapconductor.core.polyline.PolylineEntityInterface
import com.mapconductor.core.polyline.PolylineManager
import com.mapconductor.core.polyline.PolylineManagerInterface
import com.mapconductor.core.polyline.PolylineState
import com.mapconductor.googlemaps.GoogleMapActualPolyline

interface GoogleMapPolylineControllerInterface : OverlayControllerInterface<
    PolylineState,
    PolylineEntityInterface<GoogleMapActualPolyline>>
internal class GoogleMapPolylineController(
    polylineManager: PolylineManagerInterface<GoogleMapActualPolyline> = PolylineManager(),
    renderer: GoogleMapPolylineOverlayRenderer,
) : PolylineController<GoogleMapActualPolyline>(polylineManager, renderer), GoogleMapPolylineControllerInterface
