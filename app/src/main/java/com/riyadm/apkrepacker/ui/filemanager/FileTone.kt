package com.riyadm.apkrepacker.ui.filemanager

import androidx.annotation.AttrRes
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
    }
}
