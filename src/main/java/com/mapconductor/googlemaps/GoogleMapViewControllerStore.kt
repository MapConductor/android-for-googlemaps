package com.mapconductor.googlemaps

import com.mapconductor.core.map.StaticHolder
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

object GoogleMapViewControllerStore : StaticHolder<GoogleMapViewController>()

internal fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
