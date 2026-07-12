package com.mapconductor.googlemaps.raster

import com.google.android.gms.maps.model.TileOverlay
import com.mapconductor.core.controller.OnCameraChangeReceiverInterface
import com.mapconductor.core.controller.OverlayControllerInterface
import com.mapconductor.core.polyline.PolylineEntityInterface
import com.mapconductor.core.polyline.PolylineState
import com.mapconductor.core.raster.RasterLayerController
import com.mapconductor.core.raster.RasterLayerEntityInterface
import com.mapconductor.core.raster.RasterLayerManager
import com.mapconductor.core.raster.RasterLayerManagerInterface
import com.mapconductor.core.raster.RasterLayerState
import com.mapconductor.googlemaps.GoogleMapActualPolyline

interface GoogleMapRasterLayerControllerInterface : OverlayControllerInterface<
    RasterLayerState,
    RasterLayerEntityInterface<TileOverlay>,
    >
internal class GoogleMapRasterLayerController(
    rasterLayerManager: RasterLayerManagerInterface<TileOverlay> = RasterLayerManager(),
    renderer: GoogleMapRasterLayerOverlayRenderer,
) : RasterLayerController<TileOverlay>(rasterLayerManager, renderer), GoogleMapRasterLayerControllerInterface
