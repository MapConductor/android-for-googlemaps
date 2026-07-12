package com.mapconductor.googlemaps.polygon

import com.mapconductor.core.controller.OverlayControllerInterface
import com.mapconductor.core.polygon.PolygonController
import com.mapconductor.core.polygon.PolygonEntityInterface
import com.mapconductor.core.polygon.PolygonManager
import com.mapconductor.core.polygon.PolygonManagerInterface
import com.mapconductor.core.polygon.PolygonState
import com.mapconductor.googlemaps.GoogleMapActualPolygon

interface GoogleMapPolygonControllerInterface : OverlayControllerInterface<
    PolygonState,
    PolygonEntityInterface<GoogleMapActualPolygon>>
internal class GoogleMapPolygonController(
    polygonManager: PolygonManagerInterface<GoogleMapActualPolygon> = PolygonManager(),
    renderer: GoogleMapPolygonOverlayRenderer,
) : PolygonController<GoogleMapActualPolygon>(polygonManager, renderer), GoogleMapPolygonControllerInterface
