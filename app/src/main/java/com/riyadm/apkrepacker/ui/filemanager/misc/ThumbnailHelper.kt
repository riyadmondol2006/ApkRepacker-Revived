/*
 * Copyright (C) 2014 George Venios
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.riyadm.apkrepacker.ui.filemanager.misc

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.Uri.decode
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.assist.ImageScaleType.EXACTLY
import com.nostra13.universalimageloader.core.assist.ViewScaleType
import com.nostra13.universalimageloader.core.decode.BaseImageDecoder
import com.nostra13.universalimageloader.core.decode.ImageDecoder
import com.nostra13.universalimageloader.core.decode.ImageDecodingInfo
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils.getViewIntentFor
import com.riyadm.apkrepacker.ui.filemanager.utils.Utils
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.Theme
import com.riyadm.apkrepacker.utils.ViewUtils
import com.riyadm.apkrepacker.utils.common.DLog
import com.sdsmdg.harjot.vectormaster.VectorMasterDrawable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException


object ThumbnailHelper {
    private const val FADE_IN_DURATION = 400

    private var sDecoder: ImageDecoder? = null
    private var sDefaultImageOptionsBuilder: DisplayImageOptions.Builder? = null
    private var sProjectMode = false

    @JvmStatic
    fun requestIcon(holder: FileHolder, imageView: ImageView, projectMode: Boolean) {
        sProjectMode = projectMode
        val uri = Uri.fromFile(holder.file)
        val options = defaultOptionsBuilder()
                .extraForDownloader(holder)
                .build()

        ImageLoader.getInstance().displayImage(decode(uri.toString()), imageView, options)
    }

    /**
     * Unfortunately getting the default is not straightforward..
     * See https://groups.google.com/forum/#!topic/android-developers/UkfP70MtjGA
     */
    private fun getAssociatedAppIconDrawable(holder: FileHolder, context: Context): Drawable? {
        val pm = context.packageManager
        val intent = getViewIntentFor(holder, context)
        var icon: Drawable? = null

        // Contrary to queryIntentActivities documentation, the first item IS NOT the same
        // as the one returned by resolveActivity.
        val resolveInfo = pm.resolveActivity(intent, MATCH_DEFAULT_ONLY)
        if (!FileUtils.isResolverActivity(resolveInfo)) {
            icon = resolveInfo!!.loadIcon(pm)
        } else if (holder.mimeType != "*/*") {
            val lri = pm.queryIntentActivities(intent,
                    MATCH_DEFAULT_ONLY)
            if (lri != null && lri.size > 0) {
                // Again, contrary to documentation, best match is actually the last item.
                icon = lri[lri.size - 1].loadIcon(pm)
            }
        }

        return icon
    }

    private fun getApkIconDrawable(holder: FileHolder, context: Context): Drawable? {
        val pm = context.packageManager
        val path = holder.file.path
        val pInfo = pm.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
        if (pInfo != null) {
            val aInfo = pInfo.applicationInfo!!

            // Bug in SDK versions >= 8. See here:
            // http://code.google.com/p/android/issues/detail?id=9151
            aInfo.sourceDir = path
            aInfo.publicSourceDir = path

            return aInfo.loadIcon(pm)
        }

        return null
    }

    private fun getVectorIconDrawable(holder: FileHolder, context: Context): Drawable? {
        val isLight = !Theme.getInstance(context).currentTheme.isDark
        val vectorDrawable = VectorMasterDrawable(context, holder.file, isLight)
        if (vectorDrawable.isVector) {
            return vectorDrawable
        }

        return null
    }

    @JvmStatic
    fun imageDecoder(context: Context): ImageDecoder {
        if (sDecoder == null) {
            sDecoder = object : ImageDecoder {
                private val internalDecoder = BaseImageDecoder(false)

                @Throws(IOException::class)
                override fun decode(idi: ImageDecodingInfo): Bitmap? {
                    val holder = idi.extraForDownloader as FileHolder
                    var bitmap: Bitmap? = null

                    if (!holder.file.isDirectory) {
                        val fileType = FileUtil.FileType.getFileType(holder.file)
                        when (fileType) {
                            FileUtil.FileType.IMAGE -> try {
                                val info = ImageDecodingInfo(
                                        idi.imageKey, idi.imageUri,
                                        idi.originalImageUri, idi.targetSize,
                                        ViewScaleType.CROP,
                                        idi.downloader, defaultOptionsBuilder().build()
                                )
                                val bmp = internalDecoder.decode(info)

                                bitmap = bmp
                                /*// Make bmp round
                                int targetHeight = idi.getTargetSize().getHeight();
                                int targetWidth = idi.getTargetSize().getWidth();
                                int radius = targetWidth / 2;
                                bitmap = Bitmap.createBitmap(targetWidth, targetHeight, ARGB_8888);
                                BitmapShader shader = new BitmapShader(bmp, CLAMP, CLAMP);
                                Canvas canvas = new Canvas(bitmap);
                                Paint paint = new Paint();
                                paint.setAntiAlias(true);
                                paint.setShader(shader);
                                canvas.drawCircle(radius, radius, radius, paint);*/

                                //    bmp.recycle();
                            } catch (ex: FileNotFoundException) {
                                DLog.e(ex)
                                return null
                                // Fail silently.
                            }
                            FileUtil.FileType.APK -> {
                                val drawable = getApkIconDrawable(holder, context)
                                if (drawable != null) {
                                    bitmap = Utils.bitmapFrom(drawable)
                                }
                            }
                            FileUtil.FileType.TTF -> bitmap = textToBitmap(context, holder.file)
                            FileUtil.FileType.XML -> if (sProjectMode) {
                                val vectorIconDrawable = getVectorIconDrawable(holder, context)
                                if (vectorIconDrawable != null) {
                                    bitmap = (vectorIconDrawable as BitmapDrawable).bitmap
                                }
                            }
                            else -> {
                            }
                        }
                    }

                    return bitmap
                }
            }
        }

        return sDecoder!!
    }

    private fun textToBitmap(context: Context, file: File): Bitmap {
        val tv = TextView(context)
        tv.text = "Aa"
        tv.setTextColor(if (!Theme.getInstance(context).currentTheme.isDark) Color.BLACK else Color.LTGRAY)
        tv.typeface = Typeface.createFromFile(file)
        tv.gravity = Gravity.CENTER
        tv.textSize = 20f
        tv.setPadding(0, ViewUtils.dpToPxOffset(5f, context), 0, 0)

        val bitmap = Bitmap.createBitmap(ViewUtils.dpToPxOffset(32f, context), ViewUtils.dpToPxOffset(32f, context), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        tv.layout(0, 0, ViewUtils.dpToPxOffset(32f, context), ViewUtils.dpToPxOffset(32f, context))
        tv.draw(canvas)
        return bitmap
    }

    private fun defaultOptionsBuilder(): DisplayImageOptions.Builder {
        if (sDefaultImageOptionsBuilder == null) {
            sDefaultImageOptionsBuilder = DisplayImageOptions.Builder()
                    .displayer(FadeInBitmapDisplayer(FADE_IN_DURATION, true, true, false))
                    .cacheInMemory(true)
                    .cacheOnDisk(false)
                    .delayBeforeLoading(125)
                    .imageScaleType(EXACTLY)
        }

        return sDefaultImageOptionsBuilder!!
    }

}
