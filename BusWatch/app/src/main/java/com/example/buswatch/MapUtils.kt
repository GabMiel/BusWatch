package com.example.buswatch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.example.buswatch.common.R as CommonR

object MapUtils {
    fun getScaledDrawable(context: Context, drawableRes: Int, widthDp: Int, heightDp: Int): Drawable? {
        val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return null
        return getScaledDrawable(context, drawable, widthDp, heightDp)
    }
    
    fun getScaledDrawable(context: Context, drawable: Drawable?, widthDp: Int, heightDp: Int): Drawable? {
        if (drawable == null) return null
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.toDrawable(context.resources)
    }

    fun setupMarkerOnMap(context: Context, map: MapView?, point: GeoPoint, iconRes: Int) {
        if (map == null) return
        map.overlays.clear()
        map.controller.setZoom(17.0)
        map.controller.setCenter(point)
        
        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        val size = if (iconRes == CommonR.drawable.ic_stop_marker_red) 36 else 32
        marker.icon = getScaledDrawable(context, iconRes, size, size)
        map.overlays.add(marker)
        map.invalidate()
    }
}
