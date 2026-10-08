/*
 * Copyright (C) 2012 OpenIntents.org
 * Copyright (C) 2014-2016 George Venios
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

package com.riyadm.apkrepacker.ui.filemanager.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Parcelable
import android.text.TextUtils
import android.text.TextUtils.isEmpty
import android.util.DisplayMetrics
import android.view.View
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.makeMeasureSpec
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.MainActivity
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.common.DLog
import java.io.File
import java.util.Arrays
import java.util.regex.Pattern


object Utils {

    /** Same semantics as java.lang.String.split(regex) (trailing empty strings removed). */
    private fun split(str: String, regex: String): Array<String> {
        return Pattern.compile(regex).split(str)
    }

    @JvmStatic
    fun getLastPathSegment(path: String): String {
        val segments = split(path, "/")

        return if (segments.isNotEmpty())
            segments[segments.size - 1]
        else
            ""
    }

    /**
     * Launch the home activity.
     *
     * @param act The currently displayed activity.
     */
    @JvmStatic
    fun showHome(act: Activity) {
        val intent = Intent(act, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        act.startActivity(intent)
    }

    @JvmStatic
    fun viewUri(context: Context, uri: String, fallbackUrl: String?) {
        val i = Intent(Intent.ACTION_VIEW)
        try {
            i.data = Uri.parse(uri)
            context.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            if (isEmpty(fallbackUrl)) {
                /*Logger.log(e);
                makeText(context, R.string.application_not_available, LENGTH_SHORT).show();*/
            } else {
                viewUri(context, fallbackUrl!!, null)
            }
        }
    }

    /**
     * Creates a home screen shortcut.
     *
     * @param fileHolder The [File] to create the shortcut to.
     */
    @JvmStatic
    fun createShortcut(fileHolder: FileHolder, context: Context) {
        val shortcutIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT")
        shortcutIntent.putExtra("duplicate", false)
        shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, fileHolder.name)
        try {
            shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON,
                    bitmapFrom(context.resources.displayMetrics,
                            fileHolder.bestIcon!!))
        } catch (ex: Exception) {
            DLog.e(ex)
            val icon: Parcelable = Intent.ShortcutIconResource.fromContext(
                    context.applicationContext, R.mipmap.ic_launcher)
            shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon)
        } finally {
            val onClickIntent = Intent(Intent.ACTION_VIEW)
            if (fileHolder.file.isDirectory)
                onClickIntent.data = Uri.fromFile(fileHolder.file)
            else
                onClickIntent.setDataAndType(Uri.fromFile(fileHolder.file), fileHolder.mimeType)
            onClickIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

            shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, onClickIntent)
            context.sendBroadcast(shortcutIntent)
        }
    }

    @JvmStatic
    fun bitmapFrom(metrics: DisplayMetrics, drawable: Drawable): Bitmap {
        val result = Bitmap.createBitmap(
                metrics,
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888)
        val c = Canvas(result)
        drawable.draw(c)
        return result
    }

    @JvmStatic
    fun bitmapFrom(drawable: Drawable): Bitmap {
        val result = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888)
        val c = Canvas(result)
        drawable.setBounds(0, 0, c.width, c.height)
        drawable.draw(c)
        return result
    }

    /**
     * Creates an activity picker to send a file.
     *
     * @param fHolder A [FileHolder] containing the [File] to send.
     * @param context [Context] in which to create the picker.
     */
    @JvmStatic
    fun sendFile(fHolder: FileHolder, context: Context) {
        val filename = fHolder.name

        var i = Intent()
        i.action = Intent.ACTION_SEND
        i.type = fHolder.mimeType
        i.putExtra(Intent.EXTRA_SUBJECT, filename)
        val uri = FileUtils.getUri(fHolder)
        i.putExtra(Intent.EXTRA_STREAM, uri)
        // The receiver reads the file through our content provider.
        i.clipData = ClipData.newRawUri(filename, uri)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        i = Intent.createChooser(i, context.getString(R.string.action_send))

        try {
            context.startActivity(i)
        } catch (e: ActivityNotFoundException) {
        }
    }

    @JvmStatic
    fun getIconForFile(c: Context, mimeType: String?, f: File): Drawable? {
        return ContextCompat.getDrawable(c, FileUtil.getImageResource(f))
        /*if (f.isDirectory()) {
            return getFolderIcon(c);
        } else {
            MimeTypes mimeTypes = ((App) c.getApplicationContext()).getMimeTypes();
            return mimeTypes.getIcon(c, mimeType);
        }*/
    }

    @JvmStatic
    fun getSdCardIcon(c: Context): Drawable? {
        return c.getDrawable(R.drawable.ic_sd_card)
    }


    /**
     * Get the directory index where two files are intersected last.
     * Effectively the last common directory in two files' paths. <br></br>
     * This method seems kind of silly, if anyone has a more elegant solution, please lmk.
     */
    @JvmStatic
    fun lastCommonDirectoryIndex(file1: File, file2: File): Int {
        val sep = if (File.separatorChar == '\\') "\\\\" else File.separator
        val parts1 = split(file1.absolutePath, sep)
        val parts2 = split(file2.absolutePath, sep)
        var index = 0

        for (part1 in parts1) {
            if (parts2.size <= index) {
                // Reached the end of the second input.
                // The first input was fully contained in it.
                if (index > 0)
                // If the index has been incremented, it is now
                // outside the second path's bounds.
                    index--
                // If it was 0, it meant that the second input was just the root.
                break
            } else if (part1 != parts2[index]) {
                // Found a difference between the two paths.
                // Last common directory was the previous one.
                index--
                break
            } else if (parts1[parts1.size - 1] != part1) {
                // The end of the first path has not been reached.
                index++
            }
        }

        return index
    }

    /**
     * @return 1 if file1 is above file2, -1 otherwise. 0 on errors
     */
    @JvmStatic
    fun getNavigationDirection(file1: File?, file2: File?): Int {
        if (file1 == null || file2 == null
                || file1.absolutePath == "" || file2.absolutePath == ""
                || file1.absolutePath == file2.absolutePath)
            return 0

        var result = -1
        if (file2.absolutePath.startsWith(file1.absolutePath)) {
            result = 1
        }
        return result
    }


    @JvmStatic
    fun isAPK(mimeType: String?): Boolean {
        return "application/vnd.android.package-archive" == mimeType
    }

    @JvmStatic
    fun isImage(mimeType: String): Boolean {
        val type = split(mimeType, "/")[0]
        return "image" == type
    }


    /**
     * @param initialDirPath The directory on which the app was launched.
     * @param currentDirPath The current directory's absolute path.
     * @return Whether the back button should exit the app.
     */
    @JvmStatic
    fun backWillExit(initialDirPath: String, currentDirPath: String): Boolean {
        // Count tree depths
        val dir = split(currentDirPath, "/")
        val dirTreeDepth = dir.size

        val init = split(initialDirPath, "/")
        val initTreeDepth = init.size

        return if (dirTreeDepth > initTreeDepth) {
            false
        } else {
            currentDirPath == initialDirPath || currentDirPath == "/"
        }
    }

    @JvmStatic
    fun downDir(levels: Int, oldPath: String): String {
        var levels = levels
        val splitterPathArray = split(oldPath, "/")
        levels = splitterPathArray.size - levels
        var splitedPathList: List<String> = Arrays.asList(*splitterPathArray)
        splitedPathList = splitedPathList.subList(0, levels)
        return TextUtils.join("/", splitedPathList)
    }

    @JvmStatic
    fun measureExactly(pixels: Int): Int {
        return makeMeasureSpec(pixels, EXACTLY)
    }

    @JvmStatic
    fun getLastChild(group: ViewGroup): View? {
        return getChildAtFromEnd(group, 0)
    }

    @JvmStatic
    fun getChildAtFromEnd(group: ViewGroup, i: Int): View? {
        val childCount = group.childCount
        if (childCount == 0) {
            return null
        }

        return group.getChildAt(childCount - (i + 1))
    }

    @JvmStatic
    fun <T> firstDifferentItemIndex(c1: Collection<T>,
                                    c2: Collection<T>): Int {
        var lastSameIndex = -1
        val i1 = c1.iterator()
        val i2 = c2.iterator()
        while (i1.hasNext() && i2.hasNext() &&
                i1.next() == i2.next()) {
            lastSameIndex++
        }

        if (c1.size - 1 == lastSameIndex && c2.size - 1 == lastSameIndex) {
            return -1   // Same size and same elements
        }
        return lastSameIndex + 1
    }
}
