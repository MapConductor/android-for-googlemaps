package com.mapconductor.googlemaps.circle

import com.mapconductor.core.circle.CircleController
import com.mapconductor.core.circle.CircleEntityInterface
import com.mapconductor.core.circle.CircleManager
import com.mapconductor.core.circle.CircleManagerInterface
import com.mapconductor.core.circle.CircleState
import com.mapconductor.core.controller.OverlayControllerInterface
import com.mapconductor.googlemaps.GoogleMapActualCircle

interface GoogleMapCircleControllerInterface :
    OverlayControllerInterface<
        CircleState,
        CircleEntityInterface<GoogleMapActualCircle>,
    >

internal class GoogleMapCircleController(
    circleManager: CircleManagerInterface<GoogleMapActualCircle> = CircleManager(),
    renderer: GoogleMapCircleOverlayRenderer,
) : CircleController<GoogleMapActualCircle>(circleManager, renderer),
    GoogleMapCircleControllerInterface
