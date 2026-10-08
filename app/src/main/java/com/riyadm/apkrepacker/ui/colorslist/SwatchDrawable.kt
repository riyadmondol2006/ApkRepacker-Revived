package com.riyadm.apkrepacker.ui.colorslist

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.BitmapShader
import android.graphics.drawable.Drawable
import android.view.View
import com.google.android.material.color.MaterialColors
import com.riyadm.apkrepacker.R

/**
 * A user's color drawn over a checkerboard, so translucent colors read as translucent. The color itself is real
 * content and stays literal; the checkerboard tones come from the theme.
 */
class SwatchDrawable(private val checkerLight: Int, private val checkerDark: Int, private val cellPx: Int) : Drawable() {

    private val checkerPaint = Paint().apply { shader = checkerShader() }
    private val colorPaint = Paint()

    var color: Int = 0
        set(value) {
            field = value
            colorPaint.color = value
            invalidateSelf()
        }

    private fun checkerShader(): Shader {
        val tile = Bitmap.createBitmap(cellPx * 2, cellPx * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        val paint = Paint()
        paint.color = checkerLight
        canvas.drawRect(0f, 0f, tile.width.toFloat(), tile.height.toFloat(), paint)
        paint.color = checkerDark
        canvas.drawRect(0f, 0f, cellPx.toFloat(), cellPx.toFloat(), paint)
        canvas.drawRect(cellPx.toFloat(), cellPx.toFloat(), tile.width.toFloat(), tile.height.toFloat(), paint)
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, checkerPaint)
        canvas.drawRect(bounds, colorPaint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        /** A swatch whose checkerboard uses the surface tones of [view]'s theme. */
        fun create(view: View): SwatchDrawable = SwatchDrawable(
            MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHighest),
            MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerLow),
            view.resources.getDimensionPixelSize(R.dimen.space_2),
        )

        /** `#AARRGGBB` as stored in `colors.xml`. */
        fun toStoredHex(color: Int): String = String.format("#%08X", color)

        /** What the hex field shows: `#RRGGBB` when opaque. */
        fun toDisplayHex(color: Int): String =
            if (color ushr 24 == 0xFF) String.format("#%06X", color and 0xFFFFFF) else toStoredHex(color)

        /** Parses `#RGB`, `#ARGB`, `#RRGGBB` or `#AARRGGBB`; null when invalid. */
        fun parseHex(text: String): Int? {
            val digits = text.trim().removePrefix("#")
            if (digits.isEmpty() || !digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            val expanded = if (digits.length == 3 || digits.length == 4) digits.map { "$it$it" }.joinToString("") else digits
            return when (expanded.length) {
                6 -> (0xFF000000L or expanded.toLong(16)).toInt()
                8 -> expanded.toLong(16).toInt()
                else -> null
            }
        }
    }
}
