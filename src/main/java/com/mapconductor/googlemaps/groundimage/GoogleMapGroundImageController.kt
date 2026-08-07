package com.mapconductor.googlemaps.groundimage

import com.mapconductor.core.controller.OverlayControllerInterface
import com.mapconductor.core.groundimage.GroundImageController
import com.mapconductor.core.groundimage.GroundImageEntityInterface
import com.mapconductor.core.groundimage.GroundImageManager
import com.mapconductor.core.groundimage.GroundImageManagerInterface
import com.mapconductor.core.groundimage.GroundImageState
import com.mapconductor.googlemaps.GoogleMapActualGroundImage

interface GoogleMapGroundImageControllerInterface :
    OverlayControllerInterface<
        GroundImageState,
        GroundImageEntityInterface<GoogleMapActualGroundImage>,
    >

internal class GoogleMapGroundImageController(
    groundImageManager: GroundImageManagerInterface<GoogleMapActualGroundImage> = GroundImageManager(),
    renderer: GoogleMapGroundImageOverlayRenderer,
) : GroundImageController<GoogleMapActualGroundImage>(groundImageManager, renderer),
    GoogleMapGroundImageControllerInterface
