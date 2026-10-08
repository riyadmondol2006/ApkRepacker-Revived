/*
 * Copyright (C) 2012 OpenIntents.org
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

package com.riyadm.apkrepacker.ui.filemanager.holder

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.text.format.DateUtils
import android.text.format.Formatter
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils
import com.riyadm.apkrepacker.ui.filemanager.utils.Utils
import com.riyadm.apkrepacker.utils.FileUtil
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat

class FileHolder : Parcelable, Comparable<FileHolder> {
    private val mFile: File
    private var mIcon: Drawable? = null
    private var mPreview: Drawable? = null
    private var mMimeType: String? = ""
    private val mExtension: String

    constructor(f: File, c: Context) {
        mFile = f
        mExtension = parseExtension()

        mMimeType = FileUtil.getMimeType(f)
        mIcon = Utils.getIconForFile(c, mMimeType, mFile)
    }

    /**
     * Fastest constructor as it takes everything ready.
     */
    constructor(f: File, m: String?, i: Drawable?) {
        mFile = f
        mIcon = i
        mExtension = parseExtension()
        mMimeType = m
    }

    private constructor(`in`: Parcel) {
        mFile = File(`in`.readString()!!)
        mMimeType = `in`.readString()
        mExtension = `in`.readString()!!
    }

    val file: File
        get() = mFile

    /**
     * Gets the icon representation of this file.
     * @return The icon.
     */
    var icon: Drawable?
        get() = mIcon
        set(icon) {
            mIcon = icon
        }

    /**
     * Get the preview for this file. E.g. if it's an image file, this is a scaled thumbnail.
     * @return The thumbnail of this file. May be null!
     */
    var preview: Drawable?
        get() = mPreview
        /**
         * See getPreview()
         * @param preview
         */
        set(preview) {
            this.mPreview = preview
        }

    /**
     * Use this method to get the best iconic representation for this holder.
     * @return The preview of this holder, if one exists, else the icon.
     */
    val bestIcon: Drawable?
        get() = if (mPreview != null) {
            mPreview
        } else {
            mIcon
        }

    /**
     * Shorthand for getFile().getName().
     * @return This file's name.
     */
    val name: String
        get() = mFile.name

    /**
     * Get the contained file's extension.
     */
    val extension: String
        get() = mExtension

    /**
     * @return The held item's mime type.
     */
    val mimeType: String?
        get() = mMimeType

    fun getFormattedModificationDate(c: Context?): CharSequence {
        return DateUtils.getRelativeDateTimeString(c, mFile.lastModified(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.YEAR_IN_MILLIS * 10, 0)
    }

    fun getFormattedHour(c: Context?): CharSequence {
        return DateUtils.getRelativeDateTimeString(c, mFile.lastModified(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.YEAR_IN_MILLIS * 10, 0)
    }

    /**
     * @param recursive Whether to return size of the whole tree below this file (Directories only).
     */
    fun getFormattedSize(c: Context?, recursive: Boolean): String {
        return Formatter.formatFileSize(c, getSizeInBytes(recursive))
    }

    private fun getSizeInBytes(recursive: Boolean): Long {
        return if (recursive && mFile.isDirectory)
            FileUtils.folderSize(mFile)
        else
            mFile.length()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mFile.absolutePath)
        dest.writeString(mMimeType)
        dest.writeString(mExtension)
    }

    override fun compareTo(other: FileHolder): Int {
        return mFile.compareTo(other.file)
    }

    /**
     * Parse the extension from the filename of the mFile member.
     */
    private fun parseExtension(): String {
        // Exclude the dot
        var ext = FileUtils.getExtension(mFile.path)
        if (ext.length > 0) {
            ext = ext.substring(1)
        }
        return ext
    }

    companion object {
        @JvmField
        val sTimeFormatter: DateFormat = SimpleDateFormat.getTimeInstance()

        @JvmField
        val CREATOR: Parcelable.Creator<FileHolder> = object : Parcelable.Creator<FileHolder> {
            override fun createFromParcel(`in`: Parcel): FileHolder {
                return FileHolder(`in`)
            }

            override fun newArray(size: Int): Array<FileHolder?> {
                return arrayOfNulls(size)
            }
        }
    }
}
