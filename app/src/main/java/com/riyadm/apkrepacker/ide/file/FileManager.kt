/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.riyadm.apkrepacker.ide.file

import android.content.Context

import com.jecelyin.common.utils.SysUtils

import java.io.File
import java.io.IOException

/**
 * Created by Duy on 25-Apr-18.
 */

class FileManager(private val context: Context) {

    fun createNewFile(fileName: String): File? {
        val file = File(getApplicationDir(), fileName)
        file.parentFile!!.mkdirs()
        try {
            file.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun createNewTempFile(fileName: String): File? {
        val file = File(SysUtils.getCacheDir(context), fileName)
        file.parentFile!!.mkdirs()
        try {
            file.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun getApplicationDir(): File {
        val path = SysUtils.getAppStoragePath(context)
        return File(path)
    }
}
