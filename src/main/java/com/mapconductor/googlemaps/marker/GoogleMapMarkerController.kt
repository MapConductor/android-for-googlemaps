package com.mapconductor.googlemaps.marker

import com.mapconductor.core.controller.OnCameraChangeReceiverInterface
import com.mapconductor.core.controller.OverlayControllerInterface
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.marker.AbstractMarkerController
import com.mapconductor.core.marker.BitmapIcon
import com.mapconductor.core.marker.DefaultMarkerIcon
import com.mapconductor.core.marker.MarkerEntity
import com.mapconductor.core.marker.MarkerEntityInterface
import com.mapconductor.core.marker.MarkerHitTest
import com.mapconductor.core.marker.MarkerIngestionEngine
import com.mapconductor.core.marker.MarkerManager
import com.mapconductor.core.marker.MarkerOverlayRendererInterface
import com.mapconductor.core.marker.MarkerState
import com.mapconductor.core.marker.MarkerTileRenderer
import com.mapconductor.core.marker.MarkerTilingOptions
import com.mapconductor.core.marker.MarkerViewportSwitch
import com.mapconductor.core.raster.RasterLayerSource
import com.mapconductor.core.raster.RasterLayerState
import com.mapconductor.core.raster.TileScheme
import com.mapconductor.core.tileserver.TileServerRegistry
import com.mapconductor.googlemaps.GoogleMapActualMarker
import com.mapconductor.googlemaps.GoogleMapViewHolder
import java.util.UUID
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Callback interface for managing RasterLayer from MarkerController.
 * This is used to decouple the MarkerController from the RasterLayerController.
 */
fun interface MarkerTileRasterLayerCallback {
    /**
     * Called when the marker tile RasterLayer needs to be added, updated, or removed.
     * @param state The RasterLayerState to add/update, or null to remove
     */
    suspend fun onRasterLayerUpdate(state: RasterLayerState?)
}

interface GoogleMapMarkerControllerInterface:
    OverlayControllerInterface<MarkerState, MarkerEntityInterface<GoogleMapActualMarker>>

internal class GoogleMapMarkerController private constructor(
    renderer: GoogleMapMarkerRenderer,
    markerManager: MarkerManager<GoogleMapActualMarker>,
    private val markerTiling: MarkerTilingOptions,
) : AbstractMarkerController<GoogleMapActualMarker>(
    markerManager = markerManager,
    renderer = renderer,
), GoogleMapMarkerControllerInterface,
    OnCameraChangeReceiverInterface {
    private val defaultMarkerIcon: BitmapIcon = DefaultMarkerIcon().toBitmapIcon()
    private val tiledMarkerIds = LinkedHashSet<String>()

    private var lastKnownZoom: Double = 0.0

    // Tile rendering via RasterLayer
    private val tileServer = TileServerRegistry.get()
    private var markerTileRenderer: MarkerTileRenderer<GoogleMapActualMarker>? = null
    private var markerTileGroupId: String? = null
    private var markerTileRasterLayerState: RasterLayerState? = null
    private var rasterLayerCallback: MarkerTileRasterLayerCallback? = null
    private var cacheVersion: Int = 0

    /**
     * ビューポート内が少ないときだけタイルをやめてネイティブマーカーで描く切り替え器。
     *
     * レンダラ／マネージャ／semaphore を共有するので、コントローラと同じ排他の下で動く。
     */
    private val viewportSwitch =
        MarkerViewportSwitch(
            markerManager = markerManager,
            renderer = renderer,
            defaultMarkerIcon = defaultMarkerIcon,
            semaphore = semaphore,
            policy = markerTiling.viewport,
            setTileLayerVisible = ::setTileLayerVisible,
            invalidateTiles = ::updateRasterLayerSource,
        )

    /**
     * Sets the callback for RasterLayer operations.
     * This must be called before using tiled marker rendering.
     */
    internal fun setRasterLayerCallback(callback: MarkerTileRasterLayerCallback?) {
        rasterLayerCallback = callback
    }

    override fun find(position: GeoPointInterface): MarkerEntityInterface<GoogleMapActualMarker>? =
        find(position = position, zoom = lastKnownZoom)

    /**
     * ネイティブの marker click に乗らないマーカー（タイル描画されたもの）を、地図クリックから
     * 拾うためのヒットテスト。
     *
     * 判定は他プロバイダと同じ [MarkerHitTest]（アイコン矩形 + tapTolerance）。以前は
     * 「tapTolerance を metersPerPixel で距離へ換算した固定半径」で測地距離と比較していたため、
     * アイコンの大きさを一切見ておらず、大きいアイコン（幅広ラベルやクラスタ）は端をタップしても
     * 反応せず、小さいアイコンは離れていても反応していた。
     *
     * @param zoom 呼び出し側が握っているカメラのズーム。判定自体は画面座標で行うため使わないが、
     *   既存の呼び出し側シグネチャを保つために残している。
     */
    @Suppress("UNUSED_PARAMETER")
    fun find(
        position: GeoPointInterface,
        zoom: Double,
    ): MarkerEntityInterface<GoogleMapActualMarker>? {
        val nearest = markerManager.findNearest(position) ?: return null
        val touchScreen = renderer.holder.toScreenOffset(position) ?: return null
        val markerScreen = renderer.holder.toScreenOffset(nearest.state.position) ?: return null

        return if (MarkerHitTest.hitsIcon(touchScreen, markerScreen, nearest.state)) {
            nearest
        } else {
            null
        }
    }

    override suspend fun add(data: List<MarkerState>) {
        // ingest はタイル担当 entity を marker = null で登録し直すので、先に昇格を戻す。
        // semaphore は再入不可なので withPermit の外で呼ぶこと。
        viewportSwitch.retract()
        semaphore.withPermit {
            val tilingEnabled =
                markerTiling.enabled && data.size >= markerManager.minMarkerCount

            val result =
                withContext(Dispatchers.Default) {
                    MarkerIngestionEngine.ingest(
                        data = data,
                        markerManager = markerManager,
                        renderer = renderer,
                        defaultMarkerIcon = defaultMarkerIcon,
                        tilingEnabled = tilingEnabled,
                        tiledMarkerIds = tiledMarkerIds,
                        shouldTile = { state -> !state.draggable && state.getAnimation() == null },
                    )
                }

            if (result.tiledDataChanged) {
                syncTiledOverlay()
            } else if (result.hasTiledMarkers) {
                // Keep existing tile overlay if present.
                // (No per-zoom indexing needed; renderTile queries MarkerManager directly.)
                if (markerTileRenderer == null || markerTileRasterLayerState == null) {
                    syncTiledOverlay()
                }
            } else {
                removeTileOverlay()
            }
        }
        viewportSwitch.requestReapply()
    }

    override suspend fun update(state: MarkerState) {
        if (!markerManager.hasEntity(state.id)) return

        // 昇格中の 1 件なら先に取り下げる（下で marker = null 登録があるため）。
        // 昇格していないマーカー（ドラッグ中など）では何もしないので、ドラッグは素通りする。
        if (viewportSwitch.isPromoted(state.id)) viewportSwitch.release(state.id)

        val prevEntity = markerManager.getEntity(state.id) ?: return
        val currentFinger = state.fingerPrint()
        val prevFinger = prevEntity.fingerPrint
        if (currentFinger == prevFinger) return

        semaphore.withPermit {
            val tilingEnabled =
                markerTiling.enabled && markerManager.allEntities().size >= markerManager.minMarkerCount
            val wantsTiled = tilingEnabled && !state.draggable && state.getAnimation() == null
            val wasTiled = tiledMarkerIds.contains(state.id)
            val markerIcon = state.icon?.toBitmapIcon() ?: defaultMarkerIcon

            if (wantsTiled) {
                if (!wasTiled) {
                    prevEntity.marker?.let { renderer.onRemove(listOf(prevEntity)) }
                    tiledMarkerIds.add(state.id)
                }
                markerManager.updateEntity(
                    MarkerEntity(
                        marker = null,
                        state = state,
                        visible = prevEntity.visible,
                        isRendered = true,
                        // tiling を立てないと MarkerTileRenderer の絞り込みから漏れ、
                        // タイル昇格したのにタイルへ描かれないマーカーになる。
                        tiling = true,
                    ),
                )
                syncTiledOverlay()
                return
            }

            if (wasTiled) {
                tiledMarkerIds.remove(state.id)
            }

            val renderEntity =
                MarkerEntity(
                    marker = prevEntity.marker,
                    state = state,
                    visible = prevEntity.visible,
                    isRendered = true,
                )

            val markerParams =
                object : MarkerOverlayRendererInterface.ChangeParamsInterface<GoogleMapActualMarker> {
                    override val current: MarkerEntityInterface<GoogleMapActualMarker> = renderEntity
                    override val bitmapIcon: BitmapIcon = markerIcon
                    override val prev: MarkerEntityInterface<GoogleMapActualMarker> = prevEntity
                }
            val markers = renderer.onChange(listOf(markerParams))

            markers.firstOrNull()?.let { actualMarker ->
                markerManager.updateEntity(
                    MarkerEntity(
                        marker = actualMarker,
                        state = state,
                        visible = prevEntity.visible,
                        isRendered = true,
                    ),
                )

                if (prevFinger.animation != currentFinger.animation) {
                    state.getAnimation()?.let { renderer.onAnimate(markerManager.getEntity(state.id)!!) }
                }
            }

            renderer.onPostProcess()

            if (tiledMarkerIds.isNotEmpty()) {
                syncTiledOverlay()
            } else {
                removeTileOverlay()
            }
        }
        viewportSwitch.requestReapply()
    }

    override suspend fun clear() {
        viewportSwitch.destroy()
        semaphore.withPermit {
            val entities = markerManager.allEntities()
            val toRemove = entities.filter { it.marker != null }
            if (toRemove.isNotEmpty()) {
                renderer.onRemove(toRemove)
            }
            markerManager.clear()
            tiledMarkerIds.clear()
            removeTileOverlay()
        }
    }

    override suspend fun onCameraChanged(mapCameraPosition: MapCameraPosition) {
        // 判定と昇格は debounce したうえで切り替え器の中で走る（パン中は動かない）。
        viewportSwitch.onCameraChanged(mapCameraPosition)
        lastKnownZoom = mapCameraPosition.zoom

        // Also update the MarkerTileRenderer's camera zoom for fractional zoom support
    }

    /**
     * マーカータイルのラスターレイヤの表示だけを切り替える。
     *
     * source（URL）には触らない。触るとタイルを取り直すことになり、切り替えのたびに
     * タイルキャッシュを捨てるのと同じになる。
     */
    private suspend fun setTileLayerVisible(visible: Boolean) {
        val current = markerTileRasterLayerState ?: return
        if (current.visible == visible) return
        val newState = current.copy(visible = visible)
        markerTileRasterLayerState = newState
        rasterLayerCallback?.onRasterLayerUpdate(newState)
    }

    override fun destroy() {
        viewportSwitch.destroy()
        // Clean up tile server registration
        // Unregister this map's route only. Never stop the server here: it is
        // a process-wide singleton shared by all map controllers and overlay
        // modules; stopping it breaks tile loading for every other live map.
        markerTileGroupId?.let { groupId ->
            tileServer.unregister(groupId)
        }
        markerTileGroupId = null
        markerTileRenderer = null

        // Remove RasterLayer via callback
        (renderer as GoogleMapMarkerRenderer).coroutine.launch {
            rasterLayerCallback?.onRasterLayerUpdate(null)
        }
        markerTileRasterLayerState = null
        super.destroy()
    }

    /**
     * Updates the RasterLayer source URL to trigger a cache refresh.
     * Creates a new RasterLayerState to ensure proper change detection.
     */
    private suspend fun updateRasterLayerSource() {
        val groupId =
            markerTileGroupId ?: run {
                Log.w(TAG, "updateRasterLayerSource: groupId is null, skip")
                return
            }
        val tileRenderer =
            markerTileRenderer ?: run {
                Log.w(TAG, "updateRasterLayerSource: tileRenderer is null, skip")
                return
            }
        val oldState =
            markerTileRasterLayerState
                ?: run {
                    Log.w(TAG, "updateRasterLayerSource: rasterLayerState is null, skip")
                    return
                }
        cacheVersion = (cacheVersion + 1) and 0x7fffffff
        tileRenderer.invalidate()

        // Create a new state object so RasterLayerController can detect the change
        val newState =
            oldState.copy(
                source =
                    RasterLayerSource.UrlTemplate(
                        template = "${tileServer.urlTemplate(groupId, tileRenderer.tileSize)}?v=$cacheVersion",
                        tileSize = tileRenderer.tileSize,
                        maxZoom = 22,
                        scheme = TileScheme.XYZ,
                    ),
                id = oldState.id,
            )
        markerTileRasterLayerState = newState
        rasterLayerCallback?.onRasterLayerUpdate(newState)
    }

    private suspend fun syncTiledOverlay() {
        if (tiledMarkerIds.isEmpty()) {
            removeTileOverlay()
            return
        }
        if (!markerTiling.enabled) {
            removeTileOverlay()
            tiledMarkerIds.clear()
            return
        }

        // Ensure tile renderer + RasterLayer are created before updating the source.
        getOrCreateTileRenderer()
        updateRasterLayerSource()
    }

    private fun getOrCreateTileRenderer(): MarkerTileRenderer<GoogleMapActualMarker> {
        markerTileRenderer?.let {
            return it
        }

        val groupId = UUID.randomUUID().toString()
        markerTileGroupId = groupId

        val tileRenderer =
            MarkerTileRenderer(
                markerManager = markerManager,
                tileSize = 256,
                cacheSizeBytes = markerTiling.cacheSize,
                debugTileOverlay = markerTiling.debugTileOverlay,
                iconScaleCallback = markerTiling.iconScaleCallback,
            )
        markerTileRenderer = tileRenderer

        // Register with tile server
        tileServer.register(groupId, tileRenderer)

        // Create RasterLayerState
        markerTileRasterLayerState =
            RasterLayerState(
                id = "marker-tile-$groupId",
                source =
                    RasterLayerSource.UrlTemplate(
                        template = tileServer.urlTemplate(groupId, tileRenderer.tileSize),
                        tileSize = tileRenderer.tileSize,
                        maxZoom = 22,
                        scheme = TileScheme.XYZ,
                    ),
                opacity = 1.0f,
                visible = true,
            )

        return tileRenderer
    }

    private suspend fun removeTileOverlay() {
        markerTileGroupId?.let { groupId ->
            tileServer.unregister(groupId)
        }
        markerTileGroupId = null
        markerTileRenderer = null

        // Remove RasterLayer
        rasterLayerCallback?.onRasterLayerUpdate(null)
        markerTileRasterLayerState = null
    }

    companion object {
        private const val TAG = "GoogleMapMarkerController"

        fun create(
            holder: GoogleMapViewHolder,
            markerTiling: MarkerTilingOptions = MarkerTilingOptions.Default,
        ): GoogleMapMarkerController {
            val markerManager =
                MarkerManager.defaultManager<GoogleMapActualMarker>(
                    minMarkerCount = markerTiling.minMarkerCount,
                )
            val renderer =
                GoogleMapMarkerRenderer(
                    holder = holder,
                )
            val controller =
                GoogleMapMarkerController(
                    renderer = renderer,
                    markerManager = markerManager,
                    markerTiling = markerTiling,
                )
            controller.lastKnownZoom =
                holder.map.cameraPosition.zoom
                    .toDouble()
            return controller
        }
    }
}
