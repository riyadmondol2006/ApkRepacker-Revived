package com.jaredrummler.android.colorpicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors

internal object DrawingUtils {

    /** Above this alpha a check mark is still readable on top of white/black; below it the swatch is mostly transparent. */
    const val ALPHA_THRESHOLD = 165

    fun dpToPx(context: Context, dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)

    fun themeColor(context: Context, @AttrRes attr: Int, @ColorInt fallback: Int): Int =
        MaterialColors.getColor(context, attr, fallback)

    /** Black or white, whichever reads better on top of [color] (a swatch shows a user colour, so it can't be themed). */
    @ColorInt
    fun contrastOn(@ColorInt color: Int): Int =
        if (Color.alpha(color) <= ALPHA_THRESHOLD || ColorUtils.calculateLuminance(color or 0xFF000000.toInt()) >= 0.65) {
            Color.BLACK
        } else {
            Color.WHITE
        }

    private val patternCache = HashMap<Int, Bitmap>()

    /** Light checkerboard drawn behind translucent colours. */
    fun alphaShader(cellPx: Int): Shader {
        val cell = cellPx.coerceAtLeast(1)
        val bitmap = patternCache.getOrPut(cell) {
            Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                val paint = Paint()
                paint.color = 0xFFFFFFFF.toInt()
                canvas.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), paint)
                paint.color = 0xFFCBCBCB.toInt()
                canvas.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), paint)
                canvas.drawRect(cell.toFloat(), cell.toFloat(), cell * 2f, cell * 2f, paint)
            }
        }
        return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}
