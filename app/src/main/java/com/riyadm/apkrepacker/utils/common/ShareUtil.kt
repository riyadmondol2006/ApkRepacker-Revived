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

package com.riyadm.apkrepacker.utils.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.riyadm.apkrepacker.utils.FileProvider
import java.io.File

/**
 * Created by Duy on 09-Aug-17.
 */
object ShareUtil {

    @JvmStatic
    fun shareImage(context: Context, file: File) {
        // The app's own provider (not androidx.core's FileProvider, which it isn't).
        val uri: Uri = FileProvider.getUriForFile(context, file)
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.clipData = ClipData.newRawUri(file.name, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = "image/*"
        context.startActivity(Intent.createChooser(intent, "Share image via"))
    }

    @Deprecated("")
    @JvmStatic
    fun shareText(text: String?, context: Context) {
        shareText(context, text)
    }

    @JvmStatic
    fun shareText(context: Context, text: CharSequence?) {
        try {
            val intent = Intent()
            intent.action = Intent.ACTION_SEND
            intent.putExtra(Intent.EXTRA_TEXT, text)
            intent.type = "text/plain"
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
        }
    }

    @JvmStatic
    fun shareApp(context: Activity, appId: String?) {
        val intent = Intent(Intent.ACTION_SEND)
        val url = String.format("http://play.google.com/store/apps/details?id=%s", appId)
        intent.putExtra(Intent.EXTRA_TEXT, url)
        intent.type = "text/plain"
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun shareThisApp(context: Activity) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, "http://play.google.com/store/apps/details?id=" +
                context.packageName)
        intent.type = "text/plain"
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
