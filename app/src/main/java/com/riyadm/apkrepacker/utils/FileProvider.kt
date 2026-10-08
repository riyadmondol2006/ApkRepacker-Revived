package com.riyadm.apkrepacker.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.util.Arrays

class FileProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(p1: Uri, strArr: Array<String>?, p3: String?, p4: Array<String>?, p5: String?): Cursor {
        val file = decodeToFile(p1)
        val columns = strArr ?: COLUMNS
        val strArr3 = arrayOfNulls<String>(columns.size)
        val objArr = arrayOfNulls<Any>(columns.size)
        var idx = 0
        for (str in columns) {
            if ("_display_name" == str) {
                strArr3[idx] = "_display_name"
                objArr[idx++] = file.name
            } else if ("_size" == str) {
                strArr3[idx] = "_size"
                objArr[idx++] = file.length()
            }
        }
        val copyOf = Arrays.copyOf(strArr3, idx)
        val copyOf2 = Arrays.copyOf(objArr, idx)
        val matrixCursor = MatrixCursor(copyOf, 1)
        matrixCursor.addRow(copyOf2)
        return matrixCursor
    }

    override fun getType(p1: Uri): String? {
        val name = decodeToFile(p1).name
        val p = name.lastIndexOf('.')
        if (p == -1)
            return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(p + 1))
    }

    override fun insert(p1: Uri, p2: ContentValues?): Uri? {
        throw UnsupportedOperationException("No external inserts")
    }

    override fun delete(p1: Uri, p2: String?, p3: Array<String>?): Int {
        decodeToFile(p1).delete()
        return 0
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return ParcelFileDescriptor.open(decodeToFile(uri), modeToMode(mode))
    }


    override fun update(uri: Uri, contentValues: ContentValues?, str: String?, strArr: Array<String>?): Int {
        throw UnsupportedOperationException("No external updates")
    }

    companion object {
        const val FILE_PROVIDER_PREFIX = "content://com.riyadm.apkrepacker.fileprovider"

        /**
         * content:// Uri for [file] served by this provider (file:// Uris can't be shared since
         * API 24, and other apps can't read shared storage directly under scoped storage).
         * Grant access with Intent.FLAG_GRANT_READ_URI_PERMISSION.
         */
        @JvmStatic
        fun getUriForFile(context: Context, file: File): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority(context.packageName + ".fileprovider")
                .path(Base64.encodeToString(file.absolutePath.toByteArray(), Base64.NO_WRAP))
                .build()
        }
        private val COLUMNS = arrayOf("_display_name", "_size")

        private fun modeToMode(str: String?): Int {
            if ("r" == str) {
                return 268435456
            }
            if ("w" == str || "wt" == str) {
                return 738197504
            }
            if ("wa" == str) {
                return 704643072
            }
            if ("rw" == str) {
                return 939524096
            }
            if ("rwt" == str) {
                return 1006632960
            }
            throw IllegalArgumentException("Invalid mode: $str")
        }

        private fun decode(uri: Uri): String {
            val buf = Base64.decode(uri.path!!.substring(1), Base64.DEFAULT)
            return String(buf)
        }

        private fun decodeToFile(uri: Uri): File {
            val decode = decode(uri)
            return File(decode)
        }
    }
}
