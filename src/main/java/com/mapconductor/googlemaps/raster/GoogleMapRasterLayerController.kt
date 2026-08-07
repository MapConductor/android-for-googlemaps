package com.mapconductor.googlemaps.raster

import com.google.android.gms.maps.model.TileOverlay
import com.mapconductor.core.controller.OverlayControllerInterface
import com.mapconductor.core.raster.RasterLayerController
import com.mapconductor.core.raster.RasterLayerEntityInterface
import com.mapconductor.core.raster.RasterLayerManager
import com.mapconductor.core.raster.RasterLayerManagerInterface
import com.mapconductor.core.raster.RasterLayerState

interface GoogleMapRasterLayerControllerInterface :
    OverlayControllerInterface<
        RasterLayerState,
        RasterLayerEntityInterface<TileOverlay>,
    >

internal class GoogleMapRasterLayerController(
    rasterLayerManager: RasterLayerManagerInterface<TileOverlay> = RasterLayerManager(),
    renderer: GoogleMapRasterLayerOverlayRenderer,
) : RasterLayerController<TileOverlay>(rasterLayerManager, renderer),
    GoogleMapRasterLayerControllerInterface
