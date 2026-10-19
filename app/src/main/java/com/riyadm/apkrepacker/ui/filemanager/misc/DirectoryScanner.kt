/*
 * Copyright (C) 2012 OpenIntents.org
 * Copyright (C) 2014-2015 George Venios
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
import android.os.Environment
import android.os.Handler
import android.os.SystemClock
import com.riyadm.apkrepacker.ui.filemanager.holder.DirectoryHolder
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils
import com.riyadm.apkrepacker.ui.filemanager.utils.Utils
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.utils.PreferenceUtils
import com.riyadm.apkrepacker.utils.common.DLog
import java.io.File
import java.util.Collections
import java.util.Locale


class DirectoryScanner(
        directory: File,
        context: Context,
        handler: Handler,
        filterFiletype: String,
        filterMimetype: String,
        writeableOnly: Boolean,
        directoriesOnly: Boolean
) : Thread("Directory Scanner") {
    private val currentDirectory: File = directory

    private var running = false
    private var cancelled = false
    private val mSdCardPath: String = Environment.getExternalStorageDirectory().absolutePath
    private val mContext: Context = context

    //private MimeTypes mMimeTypes;
    private val handler: Handler = handler
    private val mFilterFiletype: String = filterFiletype
    private val mFilterMimetype: String = filterMimetype
    private val mWriteableOnly: Boolean = writeableOnly
    private val mDirectoriesOnly: Boolean = directoriesOnly

    // Scan related variables.
    private var totalCount = 0
    private var progress = 0
    private var operationStartTime: Long = 0
    private var noMedia = false
    private var displayHidden = false
    private var files: Array<File>? = null
    private val TAG = "DIRSCANNER"

    /**
     * We keep all these three instead of one, so that sorting is done separately on each.
     */
    private var listDir: MutableList<FileHolder>? = null
    private var listFile: MutableList<FileHolder>? = null
    private var listSdCard: MutableList<FileHolder>? = null

    private fun init() {
        DLog.d(TAG, "Scanning directory $currentDirectory")

        if (cancelled) {
            DLog.d(TAG, "Scan aborted")
            return
        }

        totalCount = 0
        progress = 0
        files = currentDirectory.listFiles()
        noMedia = false
        displayHidden = PreferenceHelper.getInstance(mContext).isShowHiddenFiles

        operationStartTime = SystemClock.uptimeMillis()

        if (files == null) {
            DLog.d(TAG, "Returned null - inaccessible directory?")
        } else {
            totalCount = files!!.size
        }
        DLog.d(TAG, "Total count=$totalCount")

        /** Directory container */
        listDir = ArrayList(totalCount)
        /** File container */
        listFile = ArrayList(totalCount)
        /** External storage container */
        listSdCard = ArrayList(3)
    }

    override fun run() {
        running = true
        init()

        // Scan files
        if (files != null) {
            for (currentFile in files!!) {
                if (cancelled) {
                    DLog.d(TAG, "Scan aborted while checking files")
                    return
                }

                progress++
                updateProgress(progress, totalCount)

                // It's the noMedia file. Raise the flag.
                if (currentFile.name.equals(FileUtils.NOMEDIA_FILE_NAME, ignoreCase = true))
                    noMedia = true

                //If the user doesn't want to display hidden files and the file is hidden, ignore this file.
                if (!displayHidden && currentFile.isHidden) {
                    continue
                }

                // One stat per entry, here on the scanner thread: the list rows, the diff and the
                // sort below all read these cached attributes instead of the disk.
                val holder = FileHolder(currentFile, mContext).loadAttributes()

                // It's a directory. Handle it.
                if (holder.isDirectory) {
                    // It's the sd card.
                    if (currentFile.absolutePath == mSdCardPath) {
                        holder.icon = Utils.getSdCardIcon(mContext)
                        listSdCard!!.add(holder)
                    }
                    // It's a normal directory.
                    else {
                        listDir!!.add(holder)
                    }
                    // It's a file. Handle it too :P
                } else {
                    val fileName = currentFile.name

                    // Get the file's mimetype.
                    val mimetype = holder.mimeType
                    val filetype = FileUtils.getExtension(fileName)

                    val fileTypeAllowed = mFilterFiletype.isEmpty() || filetype.equals(mFilterFiletype, ignoreCase = true)
                    val mimeTypeAllowed = (mFilterMimetype.isEmpty()
                            || mFilterMimetype.contentEquals("*/*")
                            || mimetype!!.contentEquals(mFilterMimetype))
                    if (!mDirectoriesOnly && fileTypeAllowed && mimeTypeAllowed) {
                        listFile!!.add(holder)
                    }
                }
            }
        }

        DLog.d(TAG, "Sorting results...")

        // The sort dialog stores its choice's position (name, date, size); the comparators use
        // their own codes. Reading the position as a code sorted "date" by name and never by date.
        val sortBy = when (PreferenceUtils.getInteger(mContext, "pref_sort", 0)) {
            1 -> Comparators.LAST_MODIFIED
            2 -> Comparators.SIZE
            else -> Comparators.NAME
        }
        // Newest first for dates, A to Z / smallest first otherwise.
        val ascending = sortBy != Comparators.LAST_MODIFIED

        // Sort lists
        if (!cancelled) {
            Collections.sort(listSdCard!!)
            Comparators.sort(listDir!!, sortBy, ascending, directories = true)
            Comparators.sort(listFile!!, sortBy, ascending, directories = false)
        }

        // Return lists
        if (!cancelled) {
            DLog.d(TAG, "Sending data back to main thread")

            val contents = DirectoryHolder()

            contents.listDir = listDir
            contents.listFile = listFile
            contents.listSdCard = listSdCard
            contents.noMedia = noMedia

            val msg = handler.obtainMessage(MESSAGE_SHOW_DIRECTORY_CONTENTS)
            msg.obj = contents
            msg.sendToTarget()
        }

        running = false
    }

    private fun updateProgress(progress: Int, maxProgress: Int) {
        // Only update the progress bar every n steps...
        if ((progress % PROGRESS_STEPS) == 0) {
            // Also don't update for the first second.
            val curTime = SystemClock.uptimeMillis()

            if (curTime - operationStartTime < 1000L) {
                return
            }

            // Okay, send an update.
            val msg = handler.obtainMessage(MESSAGE_SET_PROGRESS)
            msg.arg1 = progress
            msg.arg2 = maxProgress
            msg.sendToTarget()
        }
    }

    fun cancel() {
        cancelled = true
    }

    fun getNoMedia(): Boolean {
        return noMedia
    }

    fun isRunning(): Boolean {
        return running
    }

    companion object {
        /**
         * List of contents is ready.
         */
        const val MESSAGE_SHOW_DIRECTORY_CONTENTS = 500    // List of contents is ready, obj = DirectoryHolder
        const val MESSAGE_SET_PROGRESS = 501    // Set progress bar, arg1 = current value, arg2 = max value

        // Update progress bar every n files
        private const val PROGRESS_STEPS = 50
    }
}

/**
 * The container class for all comparators.
 */
internal object Comparators {
    const val NAME = 1
    const val SIZE = 2
    const val LAST_MODIFIED = 3
    private const val EXTENSION = 4

    /**
     * Sorts [list] (stably) as [getForFile] / [getForDirectory] order it, but computes each
     * holder's key once instead of in every comparison (n log n lowercased names otherwise).
     */
    @JvmStatic
    fun sort(list: MutableList<FileHolder>, comparator: Int, ascending: Boolean, directories: Boolean) {
        val kind = if (directories) {
            when (comparator) {
                NAME, SIZE, EXTENSION -> NAME
                LAST_MODIFIED -> LAST_MODIFIED
                else -> 0
            }
        } else {
            comparator
        }
        val locale = Locale.getDefault()
        val key: (FileHolder) -> Comparable<*> = when (kind) {
            NAME -> { holder -> holder.name.lowercase(locale) }
            SIZE -> { holder -> holder.size }
            EXTENSION -> { holder -> holder.extension }
            LAST_MODIFIED -> { holder -> holder.lastModified }
            else -> {
                Collections.sort(list, null)
                return
            }
        }
        val keyed = list.map { KeyedHolder(key(it), it) }
        val order = if (ascending) KeyedHolder.ORDER else Comparator { a, b -> KeyedHolder.ORDER.compare(b, a) }
        val sorted = keyed.sortedWith(order)
        for (i in sorted.indices) list[i] = sorted[i].holder
    }

    private class KeyedHolder(val key: Comparable<*>, val holder: FileHolder) {
        companion object {
            @Suppress("UNCHECKED_CAST")
            val ORDER: Comparator<KeyedHolder> = Comparator { a, b -> (a.key as Comparable<Any>).compareTo(b.key) }
        }
    }

    @JvmStatic
    fun getForFile(comparator: Int, ascending: Boolean): Comparator<FileHolder>? {
        return when (comparator) {
            NAME -> NameComparator(ascending)
            SIZE -> SizeComparator(ascending)
            EXTENSION -> ExtensionComparator(ascending)
            LAST_MODIFIED -> LastModifiedComparator(ascending)
            else -> null
        }
    }

    @JvmStatic
    fun getForDirectory(comparator: Int, ascending: Boolean): Comparator<FileHolder>? {
        return when (comparator) {
            NAME -> NameComparator(ascending)
            SIZE -> NameComparator(ascending) //Not a bug! Getting directory's size is very slow
            EXTENSION -> NameComparator(ascending) // Sorting by name as folders don't have extensions.
            LAST_MODIFIED -> LastModifiedComparator(ascending)
            else -> null
        }
    }
}

internal abstract class FileHolderComparator(asc: Boolean) : Comparator<FileHolder> {
    private var ascending = true

    init {
        ascending = asc
    }

    constructor() : this(true)

    override fun compare(f1: FileHolder, f2: FileHolder): Int {
        return comp((if (ascending) f1 else f2), (if (ascending) f2 else f1))
    }

    protected abstract fun comp(f1: FileHolder, f2: FileHolder): Int
}

internal class NameComparator(asc: Boolean) : FileHolderComparator(asc) {

    override fun comp(f1: FileHolder, f2: FileHolder): Int {
        return f1.name.lowercase(Locale.getDefault()).compareTo(f2.name.lowercase(Locale.getDefault()))
    }
}

internal class SizeComparator(asc: Boolean) : FileHolderComparator(asc) {

    override fun comp(f1: FileHolder, f2: FileHolder): Int {
        return java.lang.Long.compare(f1.size, f2.size)
    }
}

internal class ExtensionComparator(asc: Boolean) : FileHolderComparator(asc) {

    override fun comp(f1: FileHolder, f2: FileHolder): Int {
        return f1.extension.compareTo(f2.extension)
    }
}

internal class LastModifiedComparator(asc: Boolean) : FileHolderComparator(asc) {

    override fun comp(f1: FileHolder, f2: FileHolder): Int {
        return java.lang.Long.compare(f1.lastModified, f2.lastModified)
    }
}
