package com.riyadm.apkrepacker.utils

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import androidx.appcompat.content.res.AppCompatResources
import com.riyadm.apkrepacker.R

/**
 * The app's own logo for in-app display (About screen).
 *
 * `PackageManager.getApplicationIcon()` returns the adaptive launcher icon with the system's mask
 * (a circle on many phones) already applied, so putting it in a rounded square shows a circle
 * floating on whatever sits behind it. This builds the logo from the icon's two layers instead,
 * scaled so the icon's visible area (the central 72 of its 108 dp canvas) fills the whole view;
 * the view's own shape then cuts it into a clean rounded square.
 */
object AppLogo {

    /** Show the central 72dp of the 108dp adaptive canvas: each layer grows by a quarter of the view per side. */
    private const val SIDE_GROWTH = 0.25f

    @JvmStatic
    fun drawable(context: Context, sizePx: Int): Drawable? {
        val icon = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            val layers = listOfNotNull(icon.background, icon.foreground)
            if (layers.isNotEmpty()) {
                val inset = -(sizePx * SIDE_GROWTH).toInt()
                return LayerDrawable(layers.toTypedArray()).also { group ->
                    for (i in layers.indices) group.setLayerInset(i, inset, inset, inset, inset)
                }
            }
        }
        // Android 6 and 7 have no adaptive icons: the launcher PNG already is the whole logo.
        return icon
    }
}
