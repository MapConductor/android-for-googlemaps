package com.mapconductor.googlemaps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.mapconductor.compose.map.MapViewBase
import com.mapconductor.core.OnCameraMoveHandler
import com.mapconductor.core.OnMapEventHandler
import com.mapconductor.core.OnMapLoadedHandler
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MutableMapServiceRegistry
import com.mapconductor.core.marker.MarkerEventControllerInterface
import com.mapconductor.core.marker.MarkerOverlayRendererInterface
import com.mapconductor.core.marker.MarkerRenderingStrategyInterface
import com.mapconductor.core.marker.MarkerRenderingSupport
import com.mapconductor.core.marker.MarkerRenderingSupportKey
import com.mapconductor.core.marker.MarkerTilingOptions
import com.mapconductor.core.marker.StrategyMarkerController
import com.mapconductor.googlemaps.circle.GoogleMapCircleController
import com.mapconductor.googlemaps.circle.GoogleMapCircleOverlayRenderer
import com.mapconductor.googlemaps.groundimage.GoogleMapGroundImageController
import com.mapconductor.googlemaps.groundimage.GoogleMapGroundImageOverlayRenderer
import com.mapconductor.googlemaps.marker.GoogleMapMarkerController
import com.mapconductor.googlemaps.polygon.GoogleMapPolygonController
import com.mapconductor.googlemaps.polygon.GoogleMapPolygonOverlayRenderer
import com.mapconductor.googlemaps.polyline.GoogleMapPolylineController
import com.mapconductor.googlemaps.polyline.GoogleMapPolylineOverlayRenderer
import com.mapconductor.googlemaps.raster.GoogleMapRasterLayerController
import com.mapconductor.googlemaps.raster.GoogleMapRasterLayerOverlayRenderer
import okhttp3.OkHttpClient
import android.view.ViewGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun GoogleMapView(
    state: GoogleMapViewState,
    modifier: Modifier = Modifier,
    markerTiling: MarkerTilingOptions? = null,
    cameraRestriction: CameraRestriction? = null,
    sdkInitialize: (suspend (android.content.Context) -> Boolean)? = null,
    onMapLoaded: OnMapLoadedHandler? = null,
    onMapClick: OnMapEventHandler? = null,
    onMapLongClick: OnMapEventHandler? = null,
    onCameraMoveStart: OnCameraMoveHandler? = null,
    onCameraMove: OnCameraMoveHandler? = null,
    onCameraMoveEnd: OnCameraMoveHandler? = null,
    content: (@Composable GoogleMapViewScope.() -> Unit)? = null,
) {
    val scope = remember { GoogleMapViewScope() } // Use specific scope
    val context = LocalContext.current // Context will be available from MapViewBase too if needed
    val registry = remember { scope.buildRegistry() }
    val cameraState = remember { mutableStateOf<MapCameraPositionInterface?>(state.cameraPosition) }

    MapViewBase(
        state = state,
        cameraState = cameraState,
        modifier = modifier,
        viewProvider = {
            val cameraPosition =
                MapCameraPosition
                    .from(state.cameraPosition)
                    .toCameraPosition()

            val mapInitOptions =
                GoogleMapOptions()
                    .mapType(state.mapDesignType.getValue())
                    .camera(cameraPosition)

            MapView(context, mapInitOptions).apply {
                onCreate(null)
            }
        },
        holderProvider = { mapView ->

            suspendCancellableCoroutine { cont ->
                mapView.getMapAsync { map ->
                    val holder = GoogleMapViewHolder(mapView, map)
                    cont.resume(holder) { _, _, _ -> }
                }
            }
        },
        controllerProvider = { holder ->
            createGoogleMapViewController(
                holder = holder,
                markerTiling = markerTiling ?: MarkerTilingOptions.Default,
                serviceRegistry = state.serviceRegistry,
            ).also { mapController ->
                state.setController(mapController)
                mapController.setCameraMoveStartListener {
                    cameraState.value = it
                    state.updateCameraPosition(it)
                    onCameraMoveStart?.invoke(it)
                }
                mapController.setCameraMoveListener {
                    cameraState.value = it
                    state.updateCameraPosition(it)
                    onCameraMove?.invoke(it)
                }
                mapController.setCameraMoveEndListener {
                    cameraState.value = it
                    state.updateCameraPosition(it)
                    onCameraMoveEnd?.invoke(it)
                }
                mapController.setMapClickListener(onMapClick)
                mapController.setMapLongClickListener(onMapLongClick)
                mapController.setMapDesignTypeChangeListener(state::onMapDesignTypeChange)
                cameraRestriction?.let { mapController.setCameraRestriction(it) }
                // Post an initial camera update once the MapView is laid out
                holder.mapView.post { mapController.sendInitialCameraUpdate() }
            }
        },
        scope = scope,
        registry = registry,
        onMapLoaded = onMapLoaded,
        customDisposableEffect = { _, holderRef ->
            // Specific Google Maps DisposableEffect logic
            val lifecycle = LocalLifecycleOwner.current.lifecycle // Get lifecycle here
            DisposableEffect(lifecycle) {
                val stateId = state.id
                val observer =
                    object : DefaultLifecycleObserver {
                        override fun onResume(owner: LifecycleOwner) {
                            holderRef.value?.mapView?.onResume()
                        }

                        override fun onPause(owner: LifecycleOwner) {
                            holderRef.value?.mapView?.onPause()
                        }

                        override fun onDestroy(owner: LifecycleOwner) {
                            val activity = context.findActivity()
                            if (activity?.isChangingConfigurations == true) {
                                holderRef.value?.mapView?.let {
                                    (it.parent as? ViewGroup)?.removeView(it)
                                    it.onDestroy()
                                }
                            } else {
                                // Real teardown (not a configuration change): free the
                                // controller's overlay controllers (tile-server routes,
                                // marker managers) and cancel its coroutine scope.
                                GoogleMapViewControllerStore.get(stateId)?.destroy()
                                GoogleMapViewControllerStore.remove(stateId)
                            }
                        }
                    }
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }
        },
        sdkInitialize = {
            sdkInitialize?.invoke(context) ?: true
        },
        // Pass content if it needs to be rendered within the overlay providers in MapViewBase,
        // or handle it here if it's specific to GoogleMapView structure before calling MapViewBase.
        // For now, assuming content relates to overlay definitions.
        content = content, // This might need adjustment based on how overlays are handled
    )
}

/**
 * Creates the imperative controller graph used by both the Compose MapView and non-Compose hosts
 * such as React Native.
 *
 * @param serviceRegistry 登録先のサービスレジストリ。Compose からは `state.serviceRegistry` を渡す
 *   （react-sdk / ios-sdk と同じく持ち主は state）。React Native / Cordova のような非 Compose
 *   ホストは state を持たないので、自前のレジストリを渡す。
 */
fun createGoogleMapViewController(
    holder: GoogleMapViewHolder,
    markerTiling: MarkerTilingOptions = MarkerTilingOptions.Default,
    serviceRegistry: MutableMapServiceRegistry? = null,
): GoogleMapViewController {
    val rasterLayerController = getRasterLayerController(holder)
    val mapController =
        GoogleMapViewController(
            markerController = getMarkerController(holder, markerTiling),
            groundImageController = getGroundImageController(holder),
            polylineController = getPolylineController(holder),
            polygonController = getPolygonController(holder),
            circleController = getCircleController(holder),
            rasterLayerController = rasterLayerController,
            holder = holder,
        )

    serviceRegistry?.let { registry ->
        registry.put(
            MarkerRenderingSupportKey,
            object : MarkerRenderingSupport<GoogleMapActualMarker> {
                override val mapLoadedState = mapController.mapLoadedState

                override fun createMarkerRenderer(
                    strategy: MarkerRenderingStrategyInterface<GoogleMapActualMarker>,
                ): MarkerOverlayRendererInterface<GoogleMapActualMarker> = mapController.createMarkerRenderer()

                override fun createMarkerEventController(
                    controller: StrategyMarkerController<GoogleMapActualMarker>,
                    renderer: MarkerOverlayRendererInterface<GoogleMapActualMarker>,
                ): MarkerEventControllerInterface<GoogleMapActualMarker> =
                    mapController.createMarkerEventController(controller)

                override fun registerMarkerEventController(
                    controller: MarkerEventControllerInterface<GoogleMapActualMarker>,
                ) {
                    mapController.registerMarkerEventController(controller)
                }

                override fun onMarkerRenderingReady() {
                    mapController.onMarkerRenderingReady()
                }
            },
        )
    }
    return mapController
}

private fun getPolygonController(holder: GoogleMapViewHolder): GoogleMapPolygonController {
    val renderer =
        GoogleMapPolygonOverlayRenderer(
            holder = holder,
        )

    val controller =
        GoogleMapPolygonController(
            renderer = renderer,
        )
    return controller
}

private fun getGroundImageController(holder: GoogleMapViewHolder): GoogleMapGroundImageController {
    val renderer =
        GoogleMapGroundImageOverlayRenderer(
            holder = holder,
        )

    val controller =
        GoogleMapGroundImageController(
            renderer = renderer,
        )
    return controller
}

private fun getCircleController(holder: GoogleMapViewHolder): GoogleMapCircleController {
    val renderer =
        GoogleMapCircleOverlayRenderer(
            holder = holder,
        )

    val controller =
        GoogleMapCircleController(
            renderer = renderer,
        )
    return controller
}

private fun getPolylineController(holder: GoogleMapViewHolder): GoogleMapPolylineController {
    val renderer =
        GoogleMapPolylineOverlayRenderer(
            holder = holder,
        )

    val controller =
        GoogleMapPolylineController(
            renderer = renderer,
        )
    return controller
}

private fun getMarkerController(
    holder: GoogleMapViewHolder,
    markerTiling: MarkerTilingOptions,
) = GoogleMapMarkerController.create(
    holder = holder,
    markerTiling = markerTiling,
)

private fun getRasterLayerController(holder: GoogleMapViewHolder): GoogleMapRasterLayerController {
    // No disk cache: tiles are served from the local in-process tile server (no network latency),
    // and multiple OkHttpClient instances sharing the same cache directory can cause corruption.
    val okHttpClient = OkHttpClient.Builder().build()

    val renderer =
        GoogleMapRasterLayerOverlayRenderer(
            holder = holder,
            okHttpClient = okHttpClient,
        )
    return GoogleMapRasterLayerController(
        renderer = renderer,
    )
}
