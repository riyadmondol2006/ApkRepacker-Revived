package com.riyadm.apkrepacker.model

import android.graphics.Bitmap
import com.riyadm.apkrepacker.utils.QickEditParams
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.apkrepacker.utils.qickedit.IconGenerate
import com.riyadm.apkrepacker.utils.qickedit.ManifestEditor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Repacks an APK while patching its manifest values and (optionally) launcher icon from [QickEditParams]. */
class QickEdit {

    private val ignoreStarts = arrayOf("assets/ugc", "assets/yandexnavi/fonts/tiles", "res/raw/netdisk", "assets/yandexnavi/fonts/", "res/raw/langid.data", "res/raw/joda", "assets/adp", "assets/js-modules/UNBUNDLE", "res/raw/estool", "res/raw/feature", "res/raw/irlocaldata", "assets/sound-strings/", "res/raw/selection", "res/raw/sb", "res/raw/guides", "res/raw/metadata.json", "res/raw/sm", "assets/cuisine-strings/", "res/raw/fill", "res/raw/transform", "assets/metadata.json", "res/raw/copic", "res/raw/layers", "res/raw/dav", "res/raw/test", "res/raw/timelapse", "res/raw/pulsar", "res/raw/cuscs", "res/raw/gtm", "res/raw/megviifacepp", "assets/countries-strings/", "assets/services/", "res/raw/ep", "assets/ABBYY.license", "res/raw/bnbp", "libs/", "res/raw/tw", "res/raw/bear.tiff", "res/raw/yvideo", "res/raw/spki")
    private val ignoreEnds = arrayOf(".jpg", ".jpeg", ".png", ".gif", ".wav", ".mp2", ".mp3", ".ogg", ".aac", ".mpg", ".mpeg", ".mid", ".midi", ".smf", ".jet", ".rtttl", ".imy", ".xmf", ".mp4", ".m4a", ".m4v", ".3gp", ".3gpp", ".3g2", ".3gpp2", ".amr", ".awb", ".wma", ".wmv", ".avc", ".der", ".pfx", ".kml", ".pic", ".bc", ".key", ".glsl", ".plugin", ".p12", ".dat", ".cer", ".pb", ".bks", ".woff2", ".res", "/thumbnail", ".binarypb", ".bin", ".dict", ".zip", ".pk8", ".mov", ".crt")
    private var zipFile: ZipFile? = null
    private var bitmap: Bitmap? = null
    private var iconName: String? = null

    fun build(from: File, to: File) {
        try {
            zipFile = ZipFile(from)
            var type: String? = null
            var name: String? = null
            bitmap = QickEditParams.getBitmap()
            iconName = QickEditParams.getIconName()
            val icon = iconName
            if (bitmap != null && icon != null) {
                val parts = icon.split("/").filter { it.isNotEmpty() }
                type = parts[0]
                name = parts[1]
                DLog.i("Generate new icon")
                IconGenerate.generate(to.parent, bitmap, name)
            }
            DLog.i("repack apk")
            repackApk(from, to, type, name, QickEditParams.getIconFiles())
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            zipFile?.close()
            zipFile = null
        }
    }

    private fun patchManifest(manifest: ZipEntry): File? {
        return try {
            val originalManifest = File.createTempFile("orig", "xml")
            val editedManifest = File.createTempFile("edited", "xml")
            zipFile!!.getInputStream(manifest).use { input ->
                FileOutputStream(originalManifest, false).use { input.copyTo(it) }
            }
            ManifestEditor(originalManifest).apply {
                setAppName(QickEditParams.getNewname())
                setPackageName(QickEditParams.getNewPackage())
                setVersionName(QickEditParams.getVersionName())
                setVersionCode(Integer.parseInt(QickEditParams.getVersionCode()))
                setMinimumSdk(QickEditParams.getMinimumSdk())
                setTargetSdk(QickEditParams.getTargetSdk())
                setInstallLocation(QickEditParams.getInstallLoacation())
                commit()
                FileOutputStream(editedManifest, false).use { writeTo(it) }
            }
            editedManifest
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    @Throws(IOException::class)
    private fun repackApk(srcApk: File, targetApk: File, type: String?, name: String?, iconFiles: Map<String, String>) {
        val dir = targetApk.parentFile
        ZipInputStream(FileInputStream(srcApk)).use { zin ->
            ZipOutputStream(FileOutputStream(targetApk)).use { zout ->
                zout.setLevel(Deflater.BEST_COMPRESSION)
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val entryName = entry.name
                    when {
                        // replaced launcher icon: every density's bitmap gets the generated PNG of that density
                        type != null && name != null && isIconBitmap(entryName, type, name, iconFiles) -> {
                            val density = iconFiles[entryName] ?: densityOf(entryName)
                            val icon = File(dir!!.absolutePath + File.separator + density + File.separator + name + ".png")
                            writeDeflated(zout, entryName, icon)
                        }
                        // patched manifest
                        entryName == MANIFEST -> {
                            DLog.i("patch manifest")
                            writeDeflated(zout, entryName, patchManifest(entry)!!)
                        }
                        else -> copyEntry(zin, zout, entry)
                    }
                    zin.closeEntry()
                }
            }
        }
    }

    /**
     * The icon's bitmaps: the files the resource table resolved ([iconFiles], which covers obfuscated
     * names) plus any res/<type>[-qualifiers]/<name>.png|webp|jpg. PNG bytes under a .webp/.jpg name
     * still decode, since Android sniffs the format. An adaptive icon (mipmap-anydpi-v26/<name>.xml)
     * is left alone: it is a binary XML of foreground/background layers that a bitmap cannot replace,
     * so on Android 8+ such an app keeps its old icon and only older releases show the new one.
     */
    private fun isIconBitmap(entryName: String, type: String, name: String, iconFiles: Map<String, String>): Boolean {
        if (entryName in iconFiles) return true
        val dirName = entryName.substringBeforeLast('/', "")
        if (dirName != "res/$type" && !dirName.startsWith("res/$type-")) return false
        val fileName = entryName.substringAfterLast('/')
        return fileName.substringBeforeLast('.') == name && fileName.substringAfterLast('.') in iconExtensions
    }

    /** The density qualifier of res/<type>-...-<density>-.../ (no density: the largest). */
    private fun densityOf(entryName: String): String {
        val qualifiers = entryName.substringBeforeLast('/').split('-')
        return IconGenerate.mDens.firstOrNull { it in qualifiers } ?: IconGenerate.mDens.last()
    }

    private fun writeDeflated(zout: ZipOutputStream, entryName: String, source: File) {
        val outEntry = ZipEntry(entryName).apply {
            compressedSize = source.length()
            method = ZipEntry.DEFLATED
        }
        zout.putNextEntry(outEntry)
        FileInputStream(source).use { it.copyTo(zout) }
        zout.flush()
        zout.closeEntry()
    }

    private fun copyEntry(zin: InputStream, zout: ZipOutputStream, entry: ZipEntry) {
        val entryName = entry.name
        val outEntry = ZipEntry(entryName)
        val size = entry.size
        val crc = entry.crc
        // What the source stores uncompressed stays so: Android 11+ refuses an app whose
        // resources.arsc is compressed, and stored native libraries are loaded straight from the APK.
        if ((entry.method == ZipEntry.STORED || isIgnore(entryName)) && crc >= 0 && size >= 0) {
            outEntry.method = ZipEntry.STORED
            outEntry.size = size
            outEntry.crc = crc
        } else {
            outEntry.method = ZipEntry.DEFLATED
        }
        outEntry.compressedSize = entry.size
        zout.putNextEntry(outEntry)
        zin.copyTo(zout as OutputStream)
        zout.flush()
        zout.closeEntry()
    }

    private fun isIgnore(name: String): Boolean {
        if (name.lowercase(Locale.getDefault()).startsWith("r/")) return true
        if (ignoreStarts.any { name.startsWith(it) }) return true
        return (name.startsWith("res/raw") || name.startsWith("assets/")) && ignoreEnds.any { name.endsWith(it) }
    }

    private companion object {
        const val MANIFEST = "AndroidManifest.xml"
        val iconExtensions = setOf("png", "webp", "jpg")
    }
}
