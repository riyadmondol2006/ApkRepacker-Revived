package com.riyadm.patchengine.rules

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.android.apksig.ApkVerifier
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.patchengine.utils.HexUtil
import com.riyadm.patchengine.utils.IOUtil
import org.apache.commons.io.IOUtils
import org.apache.commons.io.IOUtils.closeQuietly
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class PatchRuleReviseSig : PatchRule() {

    private val targetList: MutableList<String> = ArrayList()

    @Throws(IOException::class)
    override fun parseFrom(linedReader: LinedReader, iPatchContext: IPatchContext) {
        val br = linedReader
        val logger = iPatchContext
        this.startLine = br.currentLine
        var line = br.readLine()
        while (line != null) {
            val line2 = line.trim { it <= ' ' }
            if (strEnd != line2) {
                if (!super.parseAsKeyword(line2, br)) {
                    if (TARGET == line2) {
                        this.targetList.add(br.readLine()!!.trim { it <= ' ' })
                    } else {
                        logger.error(R.string.patch_error_cannot_parse, br.currentLine, line2)
                    }
                }
                line = br.readLine()
            } else {
                return
            }
        }
    }

    override fun executeRule(projectHelper: ProjectHelper, zipFile: ZipFile, iPatchContext: IPatchContext): String? {
        val logger = iPatchContext
        val packageName = projectHelper.getApkPackage() ?: ""
        val hexRSA = getHexRSA(projectHelper.getApkPath())
            ?: getHexCertificate(projectHelper.getApkPath())
            ?: getHexInstalledCertificate(projectHelper.getContext(), packageName)
        if (hexRSA == null) {
            logger.error(R.string.general_error, "No signing certificate found in the APK or in the installed app")
            return null
        }
        val targetFile = logger.getDecodeRootPath() + "/" + this.targetList[0]
        try {
            IOUtil.writeToFile(
                targetFile,
                readFileContent(targetFile).replace("%PACKAGE_NAME%", packageName).replace("%RSA_DATA%", hexRSA)
            )
            return null
        } catch (e: Exception) {
            logger.error(R.string.patch_error_write_to, targetFile)
            return null
        }
    }

    /**
     * %RSA_DATA% for a v1-signed APK: its signature block (the .RSA, .DSA or .EC file in META-INF). The
     * patch's smali reads it back with CertificateFactory, which takes that PKCS#7 block or a
     * plain X.509 certificate alike, so the two fallbacks below give a certificate.
     */
    private fun getHexRSA(apkPath: String?): String? {
        var zfile: ZipFile? = null
        try {
            zfile = ZipFile(apkPath)
            val entries = zfile.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (!e.isDirectory && V1_SIGNATURE_BLOCK.matches(e.name)) {
                    BufferedInputStream(zfile.getInputStream(e)).use { input ->
                        val output = ByteArrayOutputStream()
                        IOUtils.copy(input, output)
                        return HexUtil.bytesToHexString(output.toByteArray())
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            closeQuietly(zfile)
        }
        return null
    }

    /**
     * The signer certificate of an APK signed without v1 (APK Signature Scheme v2/v3 only, as
     * apps with minSdk 24+ usually are). v2 first: with key rotation it holds the original key,
     * the one PackageInfo.signatures reports.
     */
    private fun getHexCertificate(apkPath: String?): String? {
        if (apkPath == null) return null
        return try {
            val result = ApkVerifier.Builder(File(apkPath)).build().verify()
            val certificate = result.v2SchemeSigners.firstOrNull()?.certificate
                ?: result.v3SchemeSigners.firstOrNull()?.certificate
                ?: result.signerCertificates.firstOrNull()
            HexUtil.bytesToHexString(certificate?.encoded)
        } catch (e: Exception) {
            DLog.w(TAG, "Can't read the APK's signature: $e")
            null
        }
    }

    /**
     * The installed app's original certificate: a project made from split APKs merged into one has
     * no signature of its own.
     */
    @Suppress("DEPRECATION")
    private fun getHexInstalledCertificate(context: Context?, packageName: String): String? {
        if (context == null || packageName.isEmpty()) return null
        return try {
            val pm = context.packageManager
            val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                when {
                    info == null -> null
                    info.hasMultipleSigners() -> info.apkContentsSigners.firstOrNull()
                    else -> info.signingCertificateHistory.firstOrNull()
                }
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            }
            HexUtil.bytesToHexString(signature?.toByteArray())
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        return !this.targetList.isEmpty()
    }

    override fun isSmaliNeeded(): Boolean {
        return true
    }

    companion object {
        private const val TAG = "PatchRuleReviseSig"
        private const val TARGET = "TARGET:"
        private val V1_SIGNATURE_BLOCK = Regex("META-INF/[^/]+\\.(RSA|DSA|EC)", RegexOption.IGNORE_CASE)
        private const val strEnd = "[/SIGNATURE_REVISE]"
    }
}
