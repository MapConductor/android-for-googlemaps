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
    private var controller: GoogleMapViewControllerInterface? = null

    internal fun setController(controller: GoogleMapViewControllerInterface) {
        this.controller = controller
//        _mapDesignType.let {
//            controller.setMapDesignType(it)
//        }
        attachController(controller)
    }

    internal fun onMapDesignTypeChange(value: GoogleMapDesignType) {
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
        bundle.putInt("id", state.mapDesignType.id)
    }

    override fun createState(
        stateId: String,
        mapDesignBundle: Bundle?,
        cameraPosition: MapCameraPosition,
    ): GoogleMapViewState =
        GoogleMapViewState(
            id = stateId,
            mapDesignType =
                GoogleMapDesign.Create(
                    id = mapDesignBundle?.getInt("id") ?: GoogleMapDesign.Normal.id,
                ),
            cameraPosition = cameraPosition,
        )

    override fun getStateId(state: GoogleMapViewState): String = state.id
}

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
