package com.riyadm.patchengine.interfaces

import com.riyadm.patchengine.ProjectHelper
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

interface IBeforeAddFile {

    @Throws(Exception::class)
    fun consumeAddedFile(projectHelper: ProjectHelper, zipFile: ZipFile, zipEntry: ZipEntry): Boolean
}
