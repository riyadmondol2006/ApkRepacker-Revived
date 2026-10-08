package com.riyadm.apkrepacker.ui.preferences

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * First-run setup: copies the default signing key out of the assets.
 *
 * apktool 3 needs no other setup: aapt2 ships as lib/<abi>/libaapt2.so and apktool extracts its
 * own android framework (see ApktoolEngine), so the old aapt/aapt2/android.jar assets are gone.
 */
class InitPreference {

    fun init(context: Context) {
        val helper = PreferenceHelper.getInstance(context)
        val outDir = context.filesDir
        if (!helper.isToolsInstalled || (!helper.isCustomSign && !helper.hasUsableKey())) {
            try {
                installDefaultKey(context.assets, outDir, helper)
                helper.isToolsInstalled = true
            } catch (e: IOException) {
                Log.e(TAG, e.message ?: e.toString())
            }
        }
        removeLegacyTools(outDir)
    }

    private fun PreferenceHelper.hasUsableKey(): Boolean =
        certPath.isUsableFile() && privateKeyPath.isUsableFile()

    private fun String?.isUsableFile(): Boolean = !isNullOrEmpty() && File(this).isFile

    /** aapt binaries copied by older versions; no longer used. */
    private fun removeLegacyTools(outDir: File) {
        File(outDir, "aapt").delete()
        File(outDir, "aapt2").delete()
    }

    @Throws(IOException::class)
    private fun installDefaultKey(assets: AssetManager, outDir: File, helper: PreferenceHelper) {
        val privateKey = copyAsset(assets, "key/testkey.pk8", File(outDir, "testkey.pk8"))
        helper.privateKeyPath = privateKey.absolutePath
        val cert = copyAsset(assets, "key/testkey.x509.pem", File(outDir, "testkey.x509.pem"))
        helper.certPath = cert.absolutePath
    }

    @Throws(IOException::class)
    private fun copyAsset(assets: AssetManager, name: String, target: File): File {
        assets.open(name).use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    private companion object {
        const val TAG = "InitPreference"
    }
}
