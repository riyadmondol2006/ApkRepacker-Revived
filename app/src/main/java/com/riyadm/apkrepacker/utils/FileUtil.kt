package com.riyadm.apkrepacker.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.CursorLoader
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import com.riyadm.apkrepacker.App
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.filepicker.Utility
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.utils.common.DLog
import java.io.BufferedInputStream
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.Date
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

object FileUtil {
    private const val TAG = "FileUtil"


    @JvmStatic
    fun isRoot(root: File?, current: File?): Boolean {
        return try {
            root!!.path == current!!.path
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun findPackage(projectDir: File?, currentFolder: File?): String {
        try {
            val path = currentFolder!!.path
            val pattern = Pattern.compile("smali(_classes[0-9]+)?")
            val matcher = pattern.matcher(path)
            //DLog.i(path);
            while (matcher.find()) {
                if (path.startsWith(projectDir!!.path)) {
                    var pkg = path.substring(projectDir.path.length + 2 + matcher.group().length)
                    pkg = pkg.replace(File.separator, ".")
                    DLog.i("pkg: $pkg")
                    return pkg
                } else {
                    DLog.i("Not found1")
                    return ""
                }
            }
            run {
                DLog.i("Not found2")
                return ""
            }
        } catch (e: Exception) {
            return ""
        }

    }

    @Suppress("KotlinConstantConditions")
    @JvmStatic
    fun genName(ctx: Context?, path: String?, name: String?, suff: String?, cnt: Int): String {
        val overwrite = true//Settings.getb(ctx, "overwrite_apk", true);
        if (overwrite) {
            return name + suff
        } else {
            try {
                var tn = name
                if (cnt > 0) {
                    tn = "$name($cnt)"
                }
                val check = File(path, tn + suff)
                return if (check.exists()) {
                    genName(ctx, path, name, suff, cnt + 1)
                } else {
                    tn + suff
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return name + suff
            }
        }
    }

    @Suppress("KotlinConstantConditions")
    @JvmStatic
    fun genNameApk(ctx: Context?, path: String?, name: String?, suff: String?, cnt: Int): String {
        val param = parseNameApk(name, suff)
        val overwrite = true// Settings.getb(ctx, "overwrite_apk", true);
        if (overwrite) {
            return param[0] + "." + param[1]
        } else {
            try {
                var tn = param[0]
                if (cnt > 0) {
                    tn = param[0] + "(" + cnt + ")"
                }
                val check = File(path, tn + "." + param[1])
                return if (check.exists()) {
                    genNameApk(ctx, path, name, suff, cnt + 1)
                } else {
                    tn + "." + param[1]
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return param[0] + "." + param[1]
            }
        }
    }

    @Suppress("UNUSED_VALUE", "SENSELESS_COMPARISON")
    @JvmStatic
    fun parseNameApk(name: String?, suff: String?): Array<String> {

        var filename = "out"
        var extension = "apk"

        val i = name!!.lastIndexOf('.')
        if (i > 0 && name != null) {
            filename = name.substring(0, i) + suff
            extension = name.substring(i + 1)
        }
        return arrayOf(name, extension)
    }


    @SuppressLint("DefaultLocale")
    @JvmStatic
    fun createBackupFile(packageMeta: PackageMeta, parent: String?): File? {
        val backupsDir = File(parent, "backup")
        if (!backupsDir.exists() && !backupsDir.mkdir()) {
            Log.e(TAG, "Unable to mkdir:$backupsDir")
            return null
        }

        var packageInfoPart = String.format("%s-v%s", packageMeta.label, packageMeta.versionName).replace('.', '_')
        if (packageInfoPart.length > 160)
            packageInfoPart = packageInfoPart.substring(0, 160)

        packageInfoPart = escapeFileName(packageInfoPart)

        return File(backupsDir, "$packageInfoPart.apk")/* String.format("%s-%d.apks", packageInfoPart, System.currentTimeMillis()));*/
    }

    @JvmStatic
    fun escapeFileName(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    }

    //---------------------------
    @JvmStatic
    @Throws(Exception::class)
    fun copyFile(src: File, path: File?): File {
        try {
            if (src.isDirectory) {
                if (src.path == path!!.path) throw Exception()
                val directory = createDirectory(path, src.name)
                for (file in src.listFiles()!!) copyFile(file, directory)
                return directory
            } else {
                val file = File(path, src.name)
                val channel = FileInputStream(src).channel
                channel.transferTo(0, channel.size(), FileOutputStream(file).channel)
                return file
            }
        } catch (e: Exception) {
            throw Exception(String.format("Error copying %s", src.name))
        }
    }

    //----------------------------------------------------------------------------------------------

    @JvmStatic
    @Throws(Exception::class)
    fun createDirectory(path: File?, name: String?): File {
        val directory = File(path, name)
        if (directory.mkdirs()) return directory
        if (directory.exists()) throw Exception(String.format("%s already exists", name))
        throw Exception(String.format("Error creating %s", name))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun deleteFile(file: File?): File {
        if (file!!.isDirectory) {
            for (child in file.listFiles()!!) {
                deleteFile(child)
            }
        }

        if (file.delete()) return file
        throw Exception(String.format("Error deleting %s", file.name))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun renameFile(file: File, name: String?): File {

        //  String extension = getExtension(file.getName());
        //  if (!extension.isEmpty()) name += "." + extension;
        val newFile = File(file.parent, name)
        if (file.renameTo(newFile)) return newFile
        throw Exception(String.format("Error renaming %s", file.name))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun unzip(zip: File): File {
        val directory = createDirectory(zip.parentFile, removeExtension(zip.name))
        val fileInputStream = FileInputStream(zip)
        val bufferedInputStream = BufferedInputStream(fileInputStream)
        ZipInputStream(bufferedInputStream).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                val buffer = ByteArray(1024)
                // Skip entries that would land outside the target directory ("../x").
                val file = SafeZip.resolve(directory, zipEntry.name)
                if (file == null) {
                    zipEntry = zipInputStream.nextEntry
                    continue
                }
                if (zipEntry.isDirectory) {
                    if (!file.mkdirs()) throw Exception("Error uncompressing")
                } else {
                    var count: Int
                    FileOutputStream(file).use { fileOutputStream ->
                        while (zipInputStream.read(buffer).also { count = it } != -1) {
                            fileOutputStream.write(buffer, 0, count)
                        }
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }
        }
        return directory
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun getInternalStorage(): File {
        //returns the path to the internal storage
        return Environment.getExternalStorageDirectory()
    }

    //----------------------------------------------------------------------------------------------

    @JvmStatic
    fun getExternalStorage(context: Context): File? {
        //returns the path to the external storage or null if it doesn't exist
        val path = Utility.getExternalStoragePath(context, true)
        return if (path != null) File(path) else null
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun getPublicDirectory(type: String?): File {
        //returns the path to the public directory of the given type
        return Environment.getExternalStoragePublicDirectory(type)
    }

    @JvmStatic
    fun getFileExtension(file: File): String {
        val fileName = file.name
        return if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
            fileName.substring(fileName.lastIndexOf(".") + 1)
        else ""
    }

    @JvmStatic
    fun getLastModified(file: File): String {

        //returns the last modified date of the given file as a formatted string
        return DateFormat.format("dd MMM yyy, HH:mm", Date(file.lastModified())).toString()
    }

    @JvmStatic
    fun getMimeType(file: File): String? {

        //returns the mime type for the given file or null iff there is none
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(getExtension(file.name))
    }

    @JvmStatic
    fun getName(file: File): String {

        //returns the name of the file hiding extensions of known file types
        return file.name
        /* switch (FileType.getFileType(file)) {

             case DIRECTORY:
                 return file.getName();

             case MISC_FILE:
                 return file.getName();

             default:
                 return removeExtension(file.getName());
         }*/
    }

    @JvmStatic
    fun getNameVithoutExt(file: File): String {

        //returns the name of the file hiding extensions of known file types
        //return file.getName();
        /* switch (FileType.getFileType(file)) {

             case DIRECTORY:
                 return file.getName();

             case MISC_FILE:
                 return file.getName();

             default:

         */
        return removeExtension(file.name)

    }

    @JvmStatic
    fun getCreateTime(file: File): String {
        val attributes: BasicFileAttributes?
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                return DateFormat.format("dd MMM yyy, HH:mm", Date(attributes.creationTime().toMillis())).toString()
                // FileTime time = attributes.creationTime();
                // SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyy HH:mm");
                // return dateFormat.format(time.toMillis());
            } catch (e: IOException) {

                e.printStackTrace()

            }

        } else {
            val lastmod = Date(file.lastModified())
            return lastmod.toString()
        }
        return ""
    }

    @JvmStatic
    fun getPath(file: File?): String? {

        //returns the path of the given file or null if the file is null

        return file?.path
    }

    @JvmStatic
    fun getSize(context: Context, file: File): String? {

        if (file.isDirectory) {
            //returns the size folder
            // return Formatter.formatShortFileSize(context,getFolderSize(file));
            val children = getChildren(file) ?: return null
            return context.resources.getString(R.string.items, children.size)
        } else {
            return Formatter.formatShortFileSize(context, file.length())
        }
    }

    @JvmStatic
    fun getFormatFolderSize(context: Context?, file: File): String? {
        if (file.isDirectory)
        //returns the size folder
            return Formatter.formatShortFileSize(context, getFolderSize(file))
        return null
    }

    @JvmStatic
    fun getFolderSize(dir: File): Long {
        if (dir.exists()) {
            var result: Long = 0
            val fileList = dir.listFiles()!!
            for (i in fileList.indices) {
                if (fileList[i].isDirectory) {
                    result += getFolderSize(fileList[i])
                } else {
                    result += fileList[i].length()
                }
            }
            return result
        }
        return 0
    }

    @JvmStatic
    fun getStorageUsage(context: Context): String {
        val internal = getInternalStorage()
        val external = getExternalStorage(context)
        var f = internal.freeSpace
        var t = internal.totalSpace
        if (external != null) {
            f += external.freeSpace
            t += external.totalSpace
        }
        val use = Formatter.formatShortFileSize(context, t - f)
        val tot = Formatter.formatShortFileSize(context, t)
        return String.format("%s used of %s", use, tot)
    }

    @JvmStatic
    fun isSameFile(file1: File, file2: File): Boolean {
        val file1Exists = file1.exists()
        if (file1Exists != file2.exists()) {
            return false
        }

        if (!file1Exists) {
            // two not existing files are equal
            return true
        }

        if (file1.length() != file2.length()) {
            // lengths differ, cannot be equal
            return false
        }

        try {
            if (file1.canonicalFile == file2.canonicalFile) {
                // same file
                return true
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    @JvmStatic
    fun getTitle(file: File): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun getExtension(filename: String): String {

        //returns the file extension or an empty string iff there is no extension
        return if (filename.contains(".")) filename.substring(filename.lastIndexOf(".") + 1) else ""
    }

    //----------------------------------------------------------------------------------------------

    @JvmStatic
    fun removeExtension(filename: String): String {
        val index = filename.lastIndexOf(".")
        return if (index != -1) filename.substring(0, index) else filename
    }

    @JvmStatic
    fun compareDate(file1: File, file2: File): Int {
        val lastModified1 = file1.lastModified()
        val lastModified2 = file2.lastModified()
        return lastModified2.compareTo(lastModified1)
    }

    //----------------------------------------------------------------------------------------------

    @JvmStatic
    fun compareName(file1: File, file2: File): Int {
        val name1 = file1.name
        val name2 = file2.name
        return name1.compareTo(name2, ignoreCase = true)
    }

    @JvmStatic
    fun compareSize(file1: File, file2: File): Int {
        val length1 = file1.length()
        val length2 = file2.length()
        return length2.compareTo(length1)
    }

    @JvmStatic
    fun getColorResource(file: File): Int {
        return when (FileType.getFileType(file)) {
            FileType.DIRECTORY -> R.color.directory
            FileType.MISC_FILE -> R.color.misc_file
            FileType.AUDIO -> R.color.audio
            FileType.IMAGE -> R.color.image
            FileType.VIDEO -> R.color.video
            FileType.DOC -> R.color.doc
            FileType.PPT -> R.color.ppt
            FileType.XLS -> R.color.xls
            FileType.PDF -> R.color.pdf
            FileType.TXT -> R.color.txt
            FileType.ZIP -> R.color.zip
            FileType.APK -> R.color.apk
            FileType.DEX -> R.color.dex
            FileType.SMALI -> R.color.smali
            FileType.XML -> R.color.xml
            else -> R.color.misc_file
        }
    }

    @JvmStatic
    fun getColorResourceSimple(file: File): Int {
        return when (FileType.getFileType(file)) {
            FileType.DIRECTORY -> R.color.directory
            FileType.MISC_FILE -> R.color.xml
            else -> R.color.xml
        }
    }

    //----------------------------------------------------------------------------------------------

    @JvmStatic
    fun getImageResource(file: File): Int {
        return when (FileType.getFileType(file)) {
            FileType.DIRECTORY -> R.drawable.ic_directory
            FileType.MISC_FILE -> R.drawable.ic_misc_file
            FileType.AUDIO -> R.drawable.ic_audio
            FileType.IMAGE -> R.drawable.ic_image
            FileType.VIDEO -> R.drawable.ic_video
            FileType.DOC -> R.drawable.ic_doc
            FileType.PPT -> R.drawable.ic_ppt
            FileType.XLS -> R.drawable.ic_xls
            FileType.PDF -> R.drawable.ic_pdf
            FileType.TXT -> R.drawable.ic_txt
            FileType.ZIP -> R.drawable.ic_zip
            FileType.DEX -> R.drawable.ic_txt
            FileType.SMALI, FileType.JS, FileType.JSON, FileType.XML -> R.drawable.ic_txt
            FileType.APK -> R.drawable.ic_txt
            FileType.APKS -> R.drawable.ic_zip
            else -> R.drawable.ic_misc_file
        }
    }

    /* public static boolean isStorage(File dir) {
         return dir == null || dir.equals(getInternalStorage()) || dir.equals(getExternalStorage());
     }*/

    //----------------------------------------------------------------------------------------------

    @JvmStatic
    fun getChildren(directory: File): Array<File>? {
        if (!directory.canRead())
            return null
        return if (showIsHidden()) {
            directory.listFiles(FileFilter { pathname -> pathname.exists() })
        } else {
            directory.listFiles(FileFilter { pathname -> pathname.exists() && !pathname.isHidden && pathname.name != "apktool.json" })
        }
    }

    private fun showIsHidden(): Boolean {
        return PreferenceHelper.getInstance(App.getContext()).isShowHiddenFiles
    }
    //----------------------------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    @SuppressLint("Range")
    @JvmStatic
    fun searchFilesName(context: Context, name: String): ArrayList<File> {
        val list = ArrayList<File>()
        val uri = MediaStore.Files.getContentUri("external")
        val data = arrayOf(MediaStore.Files.FileColumns.DATA)
        val cursor = CursorLoader(context, uri, data, null, null, null).loadInBackground()
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val file = File(cursor.getString(cursor.getColumnIndex(data[0])))
                if (file.exists() && file.name.startsWith(name)) list.add(file)
            }
            cursor.close()
        }
        return list
    }

    enum class FileType {

        DIRECTORY, MISC_FILE, AUDIO, IMAGE, VIDEO, TTF, DOC, PPT, XLS, PDF, TXT, ZIP, APK, DEX, BAK, APKS,
        XML, SMALI, JSON, HTML, HTM, INI, JS;

        companion object {
            @JvmStatic
            fun getFileType(file: File): FileType {

                if (file.isDirectory)
                    return DIRECTORY

                val mime = getMimeType(file)
                val ext = getExtension(file.name)
                return if (ext.startsWith("zip")) {
                    // Log.i("EXT", "returned zip");
                    ZIP
                } else if (ext.startsWith("apks")) {
                    //  Log.i("EXT", "returned apks file");
                    APKS
                } else if (ext.startsWith("apk")) {
                    // Log.i("EXT", "returned apk file");
                    APK
                } else if (ext.startsWith("dex")) {
                    // Log.i("EXT", "returned dex file");
                    DEX
                } else if (ext.startsWith("bak")) {
                    // Log.i("EXT", "returned bak file");
                    BAK
                }
                //текстовые файлы
                else if (ext.startsWith("xml")) {
                    XML
                } else if (ext.startsWith("ini")) {
                    INI
                } else if (ext.startsWith("smali")) {
                    SMALI
                } else if (ext.startsWith("json")) {
                    JSON
                } else if (ext.startsWith("html")) {
                    HTML
                } else if (ext.startsWith("htm")) {
                    HTM
                } else if (ext.startsWith("js")) {
                    JS
                } else if (ext.startsWith("ttf")) {
                    TTF
                } else if (mime == null)
                    MISC_FILE
                else if (mime.startsWith("audio"))
                    AUDIO
                else if (mime.startsWith("image"))
                    IMAGE
                else if (mime.startsWith("video"))
                    VIDEO
                else if (mime.startsWith("application/ogg"))
                    AUDIO
                else if (mime.startsWith("application/msword"))
                    DOC
                else if (mime.startsWith("application/vnd.ms-word"))
                    DOC
                else if (mime.startsWith("application/vnd.ms-powerpoint"))
                    PPT
                else if (mime.startsWith("application/vnd.ms-excel"))
                    XLS
                else if (mime.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml"))
                    DOC
                else if (mime.startsWith("application/vnd.openxmlformats-officedocument.presentationml"))
                    PPT
                else if (mime.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml"))
                    XLS
                else if (mime.startsWith("application/pdf"))
                    PDF
                else if (mime.startsWith("text"))
                    TXT
                else
                    MISC_FILE
            }
        }
    }

    @JvmStatic
    fun decodeBase64(input: String?): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(input, 0)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
