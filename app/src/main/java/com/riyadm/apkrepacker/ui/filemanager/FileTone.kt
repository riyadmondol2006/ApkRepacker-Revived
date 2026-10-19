package com.riyadm.apkrepacker.ui.filemanager

import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.utils.FileUtil

/** Tonal color pair (container, on-container attributes) for a kind of file. */
data class FileTone(@AttrRes val container: Int, @AttrRes val onContainer: Int) {
    companion object {
        private val FOLDER = FileTone(R.attr.colorPrimaryContainer, R.attr.colorOnPrimaryContainer)
        private val PACKAGE = FileTone(R.attr.colorTertiaryContainer, R.attr.colorOnTertiaryContainer)
        private val MEDIA = FileTone(R.attr.colorSecondaryContainer, R.attr.colorOnSecondaryContainer)
        private val DOCUMENT = FileTone(R.attr.colorSurfaceContainerHighest, R.attr.colorOnSurfaceVariant)

        fun of(type: FileUtil.FileType): FileTone = when (type) {
            FileUtil.FileType.DIRECTORY -> FOLDER
            FileUtil.FileType.APK, FileUtil.FileType.APKS, FileUtil.FileType.DEX,
            FileUtil.FileType.ZIP, FileUtil.FileType.BAK -> PACKAGE
            FileUtil.FileType.IMAGE, FileUtil.FileType.VIDEO, FileUtil.FileType.AUDIO,
            FileUtil.FileType.TTF -> MEDIA
            else -> DOCUMENT
        }

        /** [FileUtil.getImageResource] for an already known [type] (that one stats the file again). */
        @DrawableRes
        fun iconOf(type: FileUtil.FileType): Int = when (type) {
            FileUtil.FileType.DIRECTORY -> R.drawable.ic_directory
            FileUtil.FileType.MISC_FILE -> R.drawable.ic_misc_file
            FileUtil.FileType.AUDIO -> R.drawable.ic_audio
            FileUtil.FileType.IMAGE -> R.drawable.ic_image
            FileUtil.FileType.VIDEO -> R.drawable.ic_video
            FileUtil.FileType.DOC -> R.drawable.ic_doc
            FileUtil.FileType.PPT -> R.drawable.ic_ppt
            FileUtil.FileType.XLS -> R.drawable.ic_xls
            FileUtil.FileType.PDF -> R.drawable.ic_pdf
            FileUtil.FileType.TXT -> R.drawable.ic_txt
            FileUtil.FileType.ZIP -> R.drawable.ic_zip
            FileUtil.FileType.DEX -> R.drawable.ic_txt
            FileUtil.FileType.SMALI, FileUtil.FileType.JS, FileUtil.FileType.JSON, FileUtil.FileType.XML -> R.drawable.ic_txt
            FileUtil.FileType.APK -> R.drawable.ic_txt
            FileUtil.FileType.APKS -> R.drawable.ic_zip
            else -> R.drawable.ic_misc_file
        }
    }
}
