package com.riyadm.apkrepacker.filepicker

import java.util.Locale

/** One row of the picker: a file, a directory or the synthetic "parent folder" entry. */
class FileListItem(
    var name: String,
    var path: String,
    var time: Long,
    var isDirectory: Boolean,
    var isMarked: Boolean = false,
    /** True for the synthetic "..." row that navigates one level up. */
    var isParentRow: Boolean = false,
) : Comparable<FileListItem> {

    /** Parent row first, then directories, then files, each case-insensitive by name. */
    override fun compareTo(other: FileListItem): Int = when {
        isParentRow != other.isParentRow -> if (isParentRow) -1 else 1
        isDirectory != other.isDirectory -> if (isDirectory) -1 else 1
        else -> name.lowercase(Locale.getDefault()).compareTo(other.name.lowercase(Locale.getDefault()))
    }

    override fun toString(): String =
        "FileListItem(name=$name, path=$path, time=$time, isDirectory=$isDirectory, isMarked=$isMarked)"
}
