package com.riyadm.apkrepacker.utils.qickedit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.apache.commons.io.output.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object IconGenerate {
    @JvmField
    val mSizes = intArrayOf(36, 48, 72, 96, 144, 192)
    @JvmField
    val mDens = arrayOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

    @JvmStatic
    fun generate(path: String?, bm: Bitmap?, name: String?) {
        val bitmap = createSquaredBitmap(bm)
        for (i in mSizes.indices) {
            val dir = File(path, mDens[i])
            if (!dir.exists())
                dir.mkdir()
            val temp = Bitmap.createScaledBitmap(bitmap, mSizes[i], mSizes[i], false)
            savebitmap(temp, File(dir, "$name.png"))
        }
    }

    @JvmStatic
    @Synchronized
    fun createSquaredBitmap(srcBmp: Bitmap?): Bitmap {
        val src = resizeBitmap(srcBmp, 192)
        val dim = Math.max(src.width, src.height)
        val dstBmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dstBmp)
        canvas.drawColor(Color.TRANSPARENT)
        canvas.drawBitmap(src, ((dim - src.width) / 2).toFloat(), ((dim - src.height) / 2).toFloat(), null)
        return dstBmp
    }

    @JvmStatic
    @Synchronized
    fun savebitmap(bmp: Bitmap, f: File): File? {
        return try {
            val bytes = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            f.createNewFile()
            val fo = FileOutputStream(f)
            fo.write(bytes.toByteArray())
            fo.close()
            //bmp.recycle();
            f
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    @Synchronized
    fun resizeBitmap(srcBmp: File, maxSize: Int): Bitmap {
        val bitmap = BitmapFactory.decodeFile(srcBmp.absolutePath)
        return resizeBitmap(bitmap, maxSize)
    }

    @JvmStatic
    @Synchronized
    fun resizeBitmap(srcBmp: Bitmap?, maxSize: Int): Bitmap {
        val outWidth: Int
        val outHeight: Int
        val inWidth = srcBmp!!.width
        val inHeight = srcBmp.height
        if (inWidth > inHeight) {
            outWidth = maxSize
            outHeight = inHeight * maxSize / inWidth
        } else {
            outHeight = maxSize
            outWidth = inWidth * maxSize / inHeight
        }
        //myBitmap.recycle();
        return Bitmap.createScaledBitmap(srcBmp, outWidth, outHeight, false)
    }

    @JvmStatic
    @Synchronized
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        val bitmap: Bitmap
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }
        bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @JvmStatic
    @Synchronized
    fun getDominantColor(drawable: Drawable?): Int {
        if (drawable == null)
            return Color.DKGRAY
        val bitmap = drawableToBitmap(drawable)
        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        val pixels = IntArray(size)
        @Suppress("DEPRECATION")
        val bitmap2 = bitmap.copy(Bitmap.Config.ARGB_4444, false)
        bitmap2.getPixels(pixels, 0, width, 0, 0, width, height)
        val colorMap = HashMap<Int, Int>()
        var color: Int
        var count: Int?
        for (pixel in pixels) {
            color = pixel
            count = colorMap[color]
            if (count == null)
                count = 0
            colorMap[color] = ++count
        }
        var dominantColor = 0
        var max = 0
        for ((key, value) in colorMap) {
            if (value > max) {
                max = value
                dominantColor = key
            }
        }
        return if (dominantColor == Color.TRANSPARENT) Color.WHITE else dominantColor
    }
}
