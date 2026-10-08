/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.androlib.res.decoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import brut.androlib.exceptions.AndrolibException
import brut.androlib.exceptions.NinePatchNotFoundException
import brut.androlib.res.data.LayoutBounds
import brut.androlib.res.data.NinePatchData
import brut.util.BinaryDataInputStream
import org.apache.commons.io.IOUtils
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteOrder

/**
 * Android port of apktool 3.0.3's ResNinePatchStreamDecoder: rebuilds the 1px 9-patch border of a
 * compiled .9.png from its npTc (and npLb) chunks.
 *
 * The upstream class draws with java.awt.image/javax.imageio, which don't exist on Android, so the
 * app's build strips it from apktool-lib (see app/build.gradle) and ships this one with the same
 * name, using android.graphics.Bitmap instead.
 */
class ResNinePatchStreamDecoder : ResStreamDecoder {

    @Throws(AndrolibException::class)
    override fun decode(input: InputStream, out: OutputStream) {
        try {
            val data = IOUtils.toByteArray(input)
            if (data.isEmpty()) {
                return
            }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                // Keep the exact color values of semi-transparent pixels.
                inPremultiplied = false
                inScaled = false
            }
            val src = BitmapFactory.decodeByteArray(data, 0, data.size, options)
                ?: throw AndrolibException("Could not decode image")
            val w = src.width
            val h = src.height

            val dst = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && src.colorSpace != null) {
                Bitmap.createBitmap(w + 2, h + 2, Bitmap.Config.ARGB_8888, true, src.colorSpace!!)
            } else {
                Bitmap.createBitmap(w + 2, h + 2, Bitmap.Config.ARGB_8888)
            }
            dst.isPremultiplied = false
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            dst.setPixels(pixels, 0, w, 1, 1, w, h)
            src.recycle()

            val np = findNinePatchData(data)
            drawHLine(dst, h + 1, np.paddingLeft + 1, w - np.paddingRight)
            drawVLine(dst, w + 1, np.paddingTop + 1, h - np.paddingBottom)

            val xDivs = np.xDivs
            if (xDivs.isEmpty()) {
                drawHLine(dst, 0, 1, w)
            } else {
                var i = 0
                while (i < xDivs.size) {
                    drawHLine(dst, 0, xDivs[i] + 1, xDivs[i + 1])
                    i += 2
                }
            }

            val yDivs = np.yDivs
            if (yDivs.isEmpty()) {
                drawVLine(dst, 0, 1, h)
            } else {
                var i = 0
                while (i < yDivs.size) {
                    drawVLine(dst, 0, yDivs[i] + 1, yDivs[i + 1])
                    i += 2
                }
            }

            // Some images optionally use optical inset/layout bounds.
            // https://developer.android.com/about/versions/android-4.3.html#OpticalBounds
            try {
                val lb = findLayoutBounds(data)
                for (i in 0 until lb.left) {
                    dst.setPixel(1 + i, h + 1, LayoutBounds.COLOR_TICK)
                }
                for (i in 0 until lb.right) {
                    dst.setPixel(w - i, h + 1, LayoutBounds.COLOR_TICK)
                }
                for (i in 0 until lb.top) {
                    dst.setPixel(w + 1, 1 + i, LayoutBounds.COLOR_TICK)
                }
                for (i in 0 until lb.bottom) {
                    dst.setPixel(w + 1, h - i, LayoutBounds.COLOR_TICK)
                }
            } catch (ignored: NinePatchNotFoundException) {
                // This chunk might not exist.
            }

            if (!dst.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw AndrolibException("Could not encode 9-patch image")
            }
            dst.recycle()
        } catch (ex: IOException) {
            // The file is not a valid image.
            throw AndrolibException(ex)
        } catch (ex: RuntimeException) {
            // Out-of-range divs/padding or a bitmap the platform can't handle: not a valid image.
            throw AndrolibException(ex)
        }
    }

    @Throws(NinePatchNotFoundException::class, IOException::class)
    private fun findNinePatchData(data: ByteArray): NinePatchData {
        val stream = BinaryDataInputStream(data, ByteOrder.BIG_ENDIAN)
        findChunk(stream, NinePatchData.MAGIC)
        return NinePatchData.read(stream)
    }

    @Throws(NinePatchNotFoundException::class, IOException::class)
    private fun findLayoutBounds(data: ByteArray): LayoutBounds {
        val stream = BinaryDataInputStream(data, ByteOrder.BIG_ENDIAN)
        findChunk(stream, LayoutBounds.MAGIC)
        return LayoutBounds.read(stream)
    }

    @Throws(NinePatchNotFoundException::class, IOException::class)
    private fun findChunk(stream: BinaryDataInputStream, magic: Int) {
        stream.skipBytes(8)
        while (true) {
            val size = try {
                stream.readInt()
            } catch (ignored: EOFException) {
                throw NinePatchNotFoundException()
            }
            if (stream.readInt() == magic) {
                return
            }
            stream.skipBytes(size + 4)
        }
    }

    private fun drawHLine(im: Bitmap, y: Int, x1: Int, x2: Int) {
        for (x in x1..x2) {
            im.setPixel(x, y, NinePatchData.COLOR_TICK)
        }
    }

    private fun drawVLine(im: Bitmap, x: Int, y1: Int, y2: Int) {
        for (y in y1..y2) {
            im.setPixel(x, y, NinePatchData.COLOR_TICK)
        }
    }
}
