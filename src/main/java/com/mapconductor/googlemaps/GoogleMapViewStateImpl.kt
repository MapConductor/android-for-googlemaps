package com.mapconductor.googlemaps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.mapconductor.compose.map.BaseMapViewSaver
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MapPaddings
import com.mapconductor.core.map.MapViewState
import com.mapconductor.core.map.MapViewStateBundle
import com.mapconductor.core.map.MapViewStateInterface
import java.util.UUID
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface GoogleMapViewStateInterface : MapViewStateInterface<GoogleMapDesignType>

class GoogleMapViewState(
    override val id: String,
    mapDesignType: GoogleMapDesignType,
    cameraPosition: MapCameraPosition = MapCameraPosition.Default,
) : MapViewState<GoogleMapDesignType>(cameraPosition),
    GoogleMapViewStateInterface {
    // Map padding
    private val _padding = MutableStateFlow(MapPaddings.Zeros)
    val padding: StateFlow<MapPaddings> = _padding.asStateFlow()

    private var _mapDesignType: GoogleMapDesignType = mapDesignType

    override var mapDesignType: GoogleMapDesignType
        set(value) {
            _mapDesignType = value
            this.controller?.setMapDesignType(value)
        }
        get() = _mapDesignType

    // Read through the base so that letting go of the controller there (MapViewBase
    // does it when the map is torn down) leaves nothing behind here.
    private val controller: GoogleMapViewControllerInterface?
        get() = attachedMapController as? GoogleMapViewControllerInterface

    internal fun setController(controller: GoogleMapViewControllerInterface) {
        // コントローラが覚えているデザインを state のものにしておく。既定の None のままだと、
        // 変更リスナーを繋いだ時点で None が state へ返ってきて、復元したデザインを潰す。
        // 地図は state の mapType で作られているので、地図の見た目は変わらない。
        controller.setMapDesignType(_mapDesignType)
        attachController(controller)
    }

    internal fun onMapDesignTypeChange(value: GoogleMapDesignType) {
        // 地図から読み戻した値は mapType（Int）から作った定義済みデザインで、attributionRules を
        // 持たない。同じ mapType なら、アプリが渡した（あるいは復元した）デザインを残す。
        if (value.getValue() == _mapDesignType.getValue()) return
        _mapDesignType = value
    }

    /** 戻り型をこのプロバイダのホルダーへ絞る（アプリが `?.map` を取れる形を保つため）。 */
    override fun getMapViewHolder(): GoogleMapViewHolder? = super.getMapViewHolder() as? GoogleMapViewHolder

    internal fun updateCameraPosition(cameraPosition: MapCameraPosition) {
        setCameraPositionInternal(cameraPosition)
    }
}

// GoogleMapViewSaver implementation
class GoogleMapViewSaver : BaseMapViewSaver<GoogleMapViewState>() {
    override fun saveMapDesign(
        state: GoogleMapViewState,
        bundle: Bundle,
    ) {
        val design = state.mapDesignType
        bundle.putInt("id", design.id)
        // 定義済み（Normal など）以外は Custom として戻す。id だけだと Custom の
        // attributionRules が落ち、定義に無い id は None になってしまう。
        bundle.putBoolean(KEY_CUSTOM, design !== GoogleMapDesign.toMapDesignType(design.id))
        MapViewStateBundle.putAttributionRules(bundle, design.attributionRules)
    }

    override fun createState(
        stateId: String,
        mapDesignBundle: Bundle?,
        cameraPosition: MapCameraPosition,
    ): GoogleMapViewState {
        val id = mapDesignBundle?.getInt("id", GoogleMapDesign.Normal.id) ?: GoogleMapDesign.Normal.id
        val design =
            if (mapDesignBundle?.getBoolean(KEY_CUSTOM) == true) {
                GoogleMapDesign.Custom(
                    id = id,
                    attributionRules = MapViewStateBundle.getAttributionRules(mapDesignBundle),
                )
            } else {
                GoogleMapDesign.Create(id)
            }
        return GoogleMapViewState(
            id = stateId,
            mapDesignType = design,
            cameraPosition = cameraPosition,
        )
    }

    override fun getStateId(state: GoogleMapViewState): String = state.id
}

// 公開 API の門番（javap）にコンパニオンの定数が載らないよう、ファイル private にする。
private const val KEY_CUSTOM = "custom"

@Composable
fun rememberGoogleMapViewState(
    mapDesign: GoogleMapDesign = GoogleMapDesign.Normal,
    cameraPosition: MapCameraPositionInterface = MapCameraPosition.Default,
): GoogleMapViewState {
    val stateId by rememberSaveable {
        val uuid = UUID.randomUUID().toString()
        mutableStateOf(uuid)
    }
    val state =
        rememberSaveable(
            stateSaver = GoogleMapViewSaver().createSaver(),
        ) {
            mutableStateOf(
                GoogleMapViewState(
                    id = stateId,
                    mapDesignType = mapDesign,
                    cameraPosition = MapCameraPosition.from(cameraPosition),
                ),
            )
        }

    return state.value
}
