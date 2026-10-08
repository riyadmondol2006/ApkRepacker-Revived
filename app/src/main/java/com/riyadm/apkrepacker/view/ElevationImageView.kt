package com.riyadm.apkrepacker.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import kotlin.math.min

/**
 * An image with a soft drop shadow that follows the image's own alpha (so icons with a
 * transparent background get a shaped shadow). The shadow is a blurred alpha mask of the
 * drawable, redrawn only when the drawable, size or matrix change.
 */
open class ElevationImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var clipShadow = false
    private var forceClip = false
    private var translucent = false
    private var blurShadow = true
    private var elevationPx = DEFAULT_ELEVATION_PX

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var shadow: Bitmap? = null
    private var shadowOffsetX = 0
    private var shadowOffsetY = 0
    private var shadowDrawable: Drawable? = null
    private var shadowWidth = 0
    private var shadowHeight = 0
    private val shadowMatrix = Matrix()

    fun setClipShadow(value: Boolean) {
        clipShadow = value
        invalidate()
    }

    fun setForceClip(value: Boolean) {
        forceClip = value
        updateParentClipping()
        invalidate()
    }

    fun isTranslucent(): Boolean = translucent

    fun setTranslucent(value: Boolean) {
        translucent = value
        dropShadow()
        invalidate()
    }

    fun isBlurShadow(): Boolean = blurShadow

    fun setBlurShadow(value: Boolean) {
        blurShadow = value
        dropShadow()
        invalidate()
    }

    override fun setElevation(elevation: Float) {
        elevationPx = elevation
        dropShadow()
        invalidate()
    }

    fun setElevationDp(elevation: Float) {
        setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, elevation, resources.displayMetrics))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateParentClipping()
    }

    override fun onDraw(canvas: Canvas) {
        if (!isInEditMode && !clipShadow) drawShadow(canvas)
        super.onDraw(canvas)
    }

    private fun drawShadow(canvas: Canvas) {
        val image = drawable ?: return
        val radius = blurRadius()
        if (radius <= 0f || width <= 0 || height <= 0) return

        val stale = shadow == null || shadowDrawable !== image || shadowWidth != width ||
            shadowHeight != height || shadowMatrix != imageMatrix
        if (stale) buildShadow(image, radius)
        val mask = shadow ?: return

        shadowPaint.color = Color.argb(shadowAlpha(), 0, 0, 0)
        // The mask is blurred outward by `radius`, and the light comes from above: nudge it down.
        canvas.withTranslation(shadowOffsetX - radius, shadowOffsetY - radius + radius / 2f) {
            drawBitmap(mask, 0f, 0f, shadowPaint)
        }
    }

    private fun buildShadow(image: Drawable, radius: Float) {
        dropShadow()
        val inset = radius.toInt()
        val source = createBitmap(width + 2 * inset, height + 2 * inset, Bitmap.Config.ARGB_8888)
        val sourceCanvas = Canvas(source)
        sourceCanvas.translate((paddingLeft + inset).toFloat(), (paddingTop + inset).toFloat())
        sourceCanvas.concat(imageMatrix)
        image.draw(sourceCanvas)

        val blur = Paint().apply { maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) }
        val offset = IntArray(2)
        shadow = source.extractAlpha(blur, offset)
        shadowOffsetX = offset[0]
        shadowOffsetY = offset[1]
        source.recycle()

        shadowDrawable = image
        shadowWidth = width
        shadowHeight = height
        shadowMatrix.set(imageMatrix)
    }

    private fun dropShadow() {
        shadow?.recycle()
        shadow = null
        shadowDrawable = null
    }

    private fun shadowAlpha(): Int = when {
        translucent -> 0x66
        blurShadow -> 0xCC
        else -> 0x4D
    }

    private fun blurRadius(): Float {
        val maxElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)
        return min(MAX_BLUR_PX * (elevationPx / maxElevation), MAX_BLUR_PX)
    }

    private fun updateParentClipping() {
        if (forceClip) (parent as? ViewGroup)?.clipChildren = false
    }

    override fun onDetachedFromWindow() {
        dropShadow()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DEFAULT_ELEVATION_PX = 20f
        const val MAX_BLUR_PX = 25f
    }
}
