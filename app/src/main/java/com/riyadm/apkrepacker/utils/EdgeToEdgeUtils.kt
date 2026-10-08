package com.riyadm.apkrepacker.utils

import android.annotation.SuppressLint
import androidx.core.content.res.use
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.color.MaterialColors

/**
 * Edge-to-edge for every activity, the same way on every API level (it is enforced from targetSdk 35):
 * the window draws behind the status bar, navigation bar and display cutout, and the activity's
 * content view is padded by the system bar, cutout and IME insets, so app bars, navigation bars /
 * rails, FABs, lists and the editor stay clear of them.
 *
 * The padding is painted with Material 3 surface roles, so the bars read as part of the screen in
 * light, dark and dynamic themes alike:
 * - the status bar area takes `colorSurface` (the color app bars sit on),
 * - the navigation bar area and the side insets take `colorSurfaceContainer` (the color of the
 *   bottom navigation bar and the navigation rail, so they continue into the gesture area).
 * Bar icon contrast comes from the theme's `windowLightStatusBar` / `windowLightNavigationBar`.
 */
object EdgeToEdgeUtils {

    private val navigationScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

    /** Call from Activity.onCreate before setContentView. */
    @SuppressLint("ResourceType", "InlinedApi")
    @JvmStatic
    fun enable(activity: ComponentActivity) {
        val (lightStatus, lightNavigation) = activity.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowLightStatusBar, android.R.attr.windowLightNavigationBar)
        ).use { it.getBoolean(0, false) to it.getBoolean(1, false) }

        val transparent = Color.TRANSPARENT
        activity.enableEdgeToEdge(
            statusBarStyle = if (lightStatus) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent),
            // Before API 26 there are no light navigation bar icons; the dark scrim keeps white ones readable.
            navigationBarStyle = if (lightNavigation) SystemBarStyle.light(transparent, navigationScrim) else SystemBarStyle.dark(transparent),
        )
        // The IME is handled through insets (applyContentInsets); the window must not pan as well.
        activity.window.apply {
            val keep = attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()
            @Suppress("DEPRECATION")
            setSoftInputMode(keep or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    /**
     * Call once the content view is set: pads android.R.id.content by the insets and paints the bar
     * areas. The insets are consumed there, so views below must not pad themselves again.
     */
    @JvmStatic
    fun applyContentInsets(activity: ComponentActivity) {
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        val surface = MaterialColors.getColor(content, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT)
        val container = MaterialColors.getColor(content, com.google.android.material.R.attr.colorSurfaceContainer, surface)
        val background = SystemBarsBackground(statusColor = surface, navigationColor = container)
        content.background = background

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val padding = Insets.of(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            background.insets = padding
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(content)
    }

    /** Hides the status bar (swipe to peek it back) or shows it again. */
    @JvmStatic
    fun setStatusBarHidden(activity: ComponentActivity, hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    /** Paints the inset areas around the content: status color on top, navigation color on the other three sides. */
    private class SystemBarsBackground(statusColor: Int, navigationColor: Int) : Drawable() {
        private val statusPaint = Paint().apply { color = statusColor }
        private val navigationPaint = Paint().apply { color = navigationColor }

        var insets: Insets = Insets.NONE
            set(value) {
                if (value != field) {
                    field = value
                    invalidateSelf()
                }
            }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val i = insets
            val left = b.left.toFloat()
            val right = b.right.toFloat()
            val top = b.top.toFloat()
            val bottom = b.bottom.toFloat()
            val innerTop = top + i.top
            val innerBottom = bottom - i.bottom
            if (i.top > 0) canvas.drawRect(left, top, right, innerTop, statusPaint)
            if (i.bottom > 0) canvas.drawRect(left, innerBottom, right, bottom, navigationPaint)
            if (i.left > 0) canvas.drawRect(left, innerTop, left + i.left, innerBottom, navigationPaint)
            if (i.right > 0) canvas.drawRect(right - i.right, innerTop, right, innerBottom, navigationPaint)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
