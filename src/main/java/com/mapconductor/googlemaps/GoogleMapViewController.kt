package com.mapconductor.googlemaps

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.GoogleMap.OnCameraMoveCanceledListener
import com.google.android.gms.maps.GoogleMap.OnCameraMoveListener
import com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener
import com.google.android.gms.maps.GoogleMap.OnMapClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener
import com.google.android.gms.maps.model.LatLng
import com.mapconductor.core.circle.OnCircleEventHandler
import com.mapconductor.core.controller.BaseMapViewController
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.groundimage.GroundImageState
import com.mapconductor.core.groundimage.OnGroundImageEventHandler
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapUISettings
import com.mapconductor.core.marker.MarkerAnimationOverlayHost
import com.mapconductor.core.marker.MarkerEventControllerInterface
import com.mapconductor.core.marker.MarkerOverlayRendererInterface
import com.mapconductor.core.marker.OnMarkerEventHandler
import com.mapconductor.core.marker.StrategyMarkerController
import com.mapconductor.core.marker.dispatchGeoMarkerClick
import com.mapconductor.core.marker.dispatchNativeMarkerClick
import com.mapconductor.core.polygon.OnPolygonEventHandler
import com.mapconductor.core.polyline.OnPolylineEventHandler
import com.mapconductor.core.polyline.PolylineState
import com.mapconductor.googlemaps.circle.GoogleMapCircleController
import com.mapconductor.googlemaps.groundimage.GoogleMapGroundImageController
import com.mapconductor.googlemaps.marker.DefaultGoogleMapMarkerEventController
import com.mapconductor.googlemaps.marker.GoogleMapMarkerController
import com.mapconductor.googlemaps.marker.GoogleMapMarkerEventControllerInterface
import com.mapconductor.googlemaps.marker.GoogleMapMarkerRenderer
import com.mapconductor.googlemaps.marker.StrategyGoogleMapMarkerEventController
import com.mapconductor.googlemaps.polygon.GoogleMapPolygonController
import com.mapconductor.googlemaps.polyline.GoogleMapPolylineController
import com.mapconductor.googlemaps.raster.GoogleMapRasterLayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GoogleMapViewController internal constructor(
    override val holder: GoogleMapViewHolder,
    internal val markerController: GoogleMapMarkerController,
    internal val polylineController: GoogleMapPolylineController,
    internal val polygonController: GoogleMapPolygonController,
    internal val groundImageController: GoogleMapGroundImageController,
    internal val circleController: GoogleMapCircleController,
    internal val rasterLayerController: GoogleMapRasterLayerController,
    override val mainCoroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
    override val defaultCoroutine: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : BaseMapViewController(),
    GoogleMapViewControllerInterface,
    OnCameraMoveStartedListener,
    OnCameraMoveCanceledListener,
    OnCameraMoveListener,
    OnCameraIdleListener,
    OnMapClickListener,
    OnMarkerClickListener,
    OnMarkerDragListener,
    GoogleMap.OnMapLoadedCallback {
    internal val markerEventControllers = mutableListOf<GoogleMapMarkerEventControllerInterface>()
    private val _mapLoadedState = MutableStateFlow(false)
    val mapLoadedState: StateFlow<Boolean> = _mapLoadedState
    internal var markerClickListener: OnMarkerEventHandler? = null
    internal var markerDragStartListener: OnMarkerEventHandler? = null
    internal var markerDragListener: OnMarkerEventHandler? = null
    internal var markerDragEndListener: OnMarkerEventHandler? = null
    internal var markerAnimateStartListener: OnMarkerEventHandler? = null
    internal var markerAnimateEndListener: OnMarkerEventHandler? = null

    init {
        setupListeners()
        registerOverlayController(markerController)
        registerOverlayController(polygonController)
        registerOverlayController(polylineController)
        registerOverlayController(circleController)
        registerOverlayController(rasterLayerController)
        registerMarkerEventController(DefaultGoogleMapMarkerEventController(markerController))

        // Wire up the RasterLayer callback for marker tile rendering
        markerController.setRasterLayerCallback { state ->
            if (state != null) {
                rasterLayerController.upsert(state)
            } else {
                // Remove all marker tile layers
                val markerTileLayers =
                    rasterLayerController.rasterLayerManager
                        .allEntities()
                        .filter { it.state.id.startsWith("marker-tile-") }
                markerTileLayers.forEach { entity -> rasterLayerController.removeById(entity.state.id) }
            }
        }
    }

    override fun moveCamera(position: MapCameraPosition) = handleMoveCamera(position)

    override fun animateCamera(
        position: MapCameraPosition,
        duration: Long,
    ) = handleAnimateCamera(position, duration)

    override fun fitBounds(
        bounds: GeoRectBounds,
        padding: Int,
    ) = handleFitBounds(bounds, padding)

    override fun setCameraRestriction(restriction: CameraRestriction?) = handleCameraRestriction(restriction)

    override fun onCameraMove() = handleCameraMove()

    override fun onCameraIdle() = handleCameraIdle()

    override fun onCameraMoveStarted(p0: Int) = handleCameraMoveStarted(p0)

    override fun onCameraMoveCanceled() = handleCameraMoveCanceled()

    override fun onMapClick(position: LatLng) = handleMapClick(position)

    /**
     * タイル描画されたマーカーのヒットテスト。
     *
     * ネイティブの `Marker` として描かれたものは `OnMarkerClickListener` が先に消費するので
     * ここへは来ない（[com.mapconductor.core.marker.dispatchNativeMarkerClick]）。
     * 呼び出し元がメインスレッドなので `Projection` を触ってよい。
     */
    override fun dispatchMarkerTap(position: GeoPointInterface): Boolean =
        markerEventControllers.dispatchGeoMarkerClick(position)

    // 拡張ファイル（Camera / Gestures）からは基底クラスの protected へ触れないため、
    // ここで internal の入口を用意しておく。
    internal fun mapClickHandler(): ((GeoPoint) -> Unit)? = mapClickCallback

    internal fun emitCameraMoveStart(position: MapCameraPosition) {
        cameraMoveStartCallback?.invoke(position)
    }

    internal fun emitCameraMove(position: MapCameraPosition) {
        cameraMoveCallback?.invoke(position)
    }

    internal fun emitCameraMoveEnd(position: MapCameraPosition) {
        cameraMoveEndCallback?.invoke(position)
    }

    internal suspend fun emitCameraPosition(position: MapCameraPosition) {
        notifyMapCameraPosition(position)
    }

    fun setupListeners() {
        holder.map.setOnCameraMoveStartedListener(this)
        holder.map.setOnCameraMoveCanceledListener(this)
        holder.map.setOnCameraMoveListener(this)
        holder.map.setOnCameraIdleListener(this)
        holder.map.setOnMapClickListener(this)
        holder.map.setOnMapLoadedCallback(this)
        holder.map.setOnMarkerClickListener(this)
        holder.map.setOnMarkerDragListener(this)
    }

    override fun applyUISettings(settings: MapUISettings) {
        holder.map.uiSettings.apply {
            isScrollGesturesEnabled = settings.scrollGesture
            isZoomGesturesEnabled = settings.zoomGesture
            isRotateGesturesEnabled = settings.rotateGesture
            isTiltGesturesEnabled = settings.tiltGesture
        }
    }

    override suspend fun clearOverlays() {
        markerController.clear()
        groundImageController.clear()
        polylineController.clear()
        polygonController.clear()
        circleController.clear()
        rasterLayerController.clear()
    }

    override fun setMarkerAnimationOverlayHost(host: MarkerAnimationOverlayHost?) {
        (markerController.renderer as GoogleMapMarkerRenderer).animationOverlayHost = host
    }

    @Deprecated("Use CircleState.onClick instead.")
    override fun setOnCircleClickListener(listener: OnCircleEventHandler?) {
        this.circleController.clickListener = listener
    }

    @Deprecated("Use MarkerState.onDragStart instead.")
    override fun setOnMarkerDragStart(listener: OnMarkerEventHandler?) {
        markerDragStartListener = listener
        markerEventControllers.forEach { it.setDragStartListener(listener) }
    }

    @Deprecated("Use MarkerState.onDrag instead.")
    override fun setOnMarkerDrag(listener: OnMarkerEventHandler?) {
        markerDragListener = listener
        markerEventControllers.forEach { it.setDragListener(listener) }
    }

    @Deprecated("Use MarkerState.onDragEnd instead.")
    override fun setOnMarkerDragEnd(listener: OnMarkerEventHandler?) {
        markerDragEndListener = listener
        markerEventControllers.forEach { it.setDragEndListener(listener) }
    }

    @Deprecated("Use MarkerState.onAnimateStart instead.")
    override fun setOnMarkerAnimateStart(listener: OnMarkerEventHandler?) {
        markerAnimateStartListener = listener
        markerEventControllers.forEach { it.setAnimateStartListener(listener) }
    }

    @Deprecated("Use MarkerState.onAnimateEnd instead.")
    override fun setOnMarkerAnimateEnd(listener: OnMarkerEventHandler?) {
        markerAnimateEndListener = listener
        markerEventControllers.forEach { it.setAnimateEndListener(listener) }
    }

    @Deprecated("Use MarkerState.onClick instead.")
    override fun setOnMarkerClickListener(listener: OnMarkerEventHandler?) {
        markerClickListener = listener
        markerEventControllers.forEach { it.setClickListener(listener) }
    }

    override fun hasPolyline(state: PolylineState): Boolean =
        this.polylineController.polylineManager
            .hasEntity(state.id)

    override fun hasGroundImage(state: GroundImageState): Boolean =
        this.groundImageController.groundImageManager
            .hasEntity(state.id)

    @Deprecated("Use GroundImageState.onClick instead.")
    override fun setOnGroundImageClickListener(listener: OnGroundImageEventHandler?) {
        this.groundImageController.clickListener = listener
    }

    @Deprecated("Use PolylineState.onClick instead.")
    override fun setOnPolylineClickListener(listener: OnPolylineEventHandler?) {
        this.polylineController.clickListener = listener
    }

    @Deprecated("Use PolygonState.onClick instead.")
    override fun setOnPolygonClickListener(listener: OnPolygonEventHandler?) {
        this.polygonController.clickListener = listener
    }

    private var mapDesignType: GoogleMapDesignType = GoogleMapDesign.None
    private var mapDesignTypeChangeListener: GoogleMapDesignTypeChangeHandler? = null

    override fun setMapDesignType(value: GoogleMapDesignType) {
        mainCoroutine.launch {
            holder.map.mapType = value.getValue()
        }
        mapDesignType = value
        mapDesignTypeChangeListener?.invoke(value)
    }

    override fun setMapDesignTypeChangeListener(listener: GoogleMapDesignTypeChangeHandler) {
        mapDesignTypeChangeListener = listener
        listener(mapDesignType)
    }

    override fun onMapLoaded() {
        _mapLoadedState.value = true
        mapInitializedCallback?.invoke()
        mapInitializedCallback = null

        val mapDesignType = GoogleMapDesign.toMapDesignType(holder.map.mapType)
        mapDesignTypeChangeListener?.invoke(mapDesignType)
    }

    // Trigger an initial camera update after the view and map are ready
    private var initialCameraUpdateAttempts = 0

    fun sendInitialCameraUpdate() {
        val w = holder.mapView.width
        val h = holder.mapView.height
        if (w <= 0 || h <= 0) {
            if (initialCameraUpdateAttempts >= INITIAL_CAMERA_UPDATE_MAX_ATTEMPTS) return
            initialCameraUpdateAttempts += 1
            holder.mapView.post { sendInitialCameraUpdate() }
            return
        }
        initialCameraUpdateAttempts = 0
        val mapCameraPosition = getMapCameraPosition()
        defaultCoroutine.launch { notifyMapCameraPosition(mapCameraPosition) }
    }

    fun createMarkerRenderer(): MarkerOverlayRendererInterface<GoogleMapActualMarker> =
        GoogleMapMarkerRenderer(holder = holder)

    fun createMarkerEventController(
        controller: StrategyMarkerController<GoogleMapActualMarker>,
    ): MarkerEventControllerInterface<GoogleMapActualMarker> = StrategyGoogleMapMarkerEventController(controller)

    fun registerMarkerEventController(controller: MarkerEventControllerInterface<GoogleMapActualMarker>) {
        val typed = controller as? GoogleMapMarkerEventControllerInterface ?: return
        registerMarkerEventController(typed)
    }

    fun onMarkerRenderingReady() {
        sendInitialCameraUpdate()
    }

    companion object {
        private const val INITIAL_CAMERA_UPDATE_MAX_ATTEMPTS = 10
    }

    internal fun registerMarkerEventController(controller: GoogleMapMarkerEventControllerInterface) {
        if (markerEventControllers.contains(controller)) return
        markerEventControllers.add(controller)
        controller.setClickListener(markerClickListener)
        controller.setDragStartListener(markerDragStartListener)
        controller.setDragListener(markerDragListener)
        controller.setDragEndListener(markerDragEndListener)
        controller.setAnimateStartListener(markerAnimateStartListener)
        controller.setAnimateEndListener(markerAnimateEndListener)
    }

    /**
     * ネイティブのマーカークリック。
     *
     * Google Maps の `Marker` には clickable 相当の API が無く、マーカーをタップすると
     * SDK がイベントを消費して `OnMapClickListener` が発火しないため、他のオーバーレイ
     * （polygon / polyline / circle は `.clickable(false)` にして地図クリックへ寄せている）
     * と違い、ここだけネイティブのリスナーを使わざるを得ない。
     *
     * 判断はコアの [dispatchNativeMarkerClick] に一本化してある（TomTom も同じ経路）。
     * false を返すと SDK の既定動作（情報ウィンドウ＋カメラ移動）になるので、
     * アプリが `getMapViewHolder().map` へ直接追加したマーカーはそちらで処理される。
     */
    override fun onMarkerClick(marker: GoogleMapActualMarker): Boolean =
        markerEventControllers.dispatchNativeMarkerClick(marker.tag)

    override fun onMarkerDrag(marker: GoogleMapActualMarker) {
        val stateId = marker.tag as? String ?: return
        markerEventControllers.forEach { controller ->
            val entity = controller.getEntity(stateId) ?: return@forEach
            entity.state.position = marker.position.toGeoPoint()
            controller.dispatchDrag(entity.state)
            return
        }
    }

    override fun onMarkerDragEnd(marker: GoogleMapActualMarker) {
        val stateId = marker.tag as? String ?: return
        markerEventControllers.forEach { controller ->
            val entity = controller.getEntity(stateId) ?: return@forEach
            entity.state.position = marker.position.toGeoPoint()
            controller.dispatchDragEnd(entity.state)
            return
        }
    }

    override fun onMarkerDragStart(marker: GoogleMapActualMarker) {
        val stateId = marker.tag as? String ?: return
        markerEventControllers.forEach { controller ->
            val entity = controller.getEntity(stateId) ?: return@forEach
            entity.state.position = marker.position.toGeoPoint()
            controller.dispatchDragStart(entity.state)
            return
        }
    }
}
