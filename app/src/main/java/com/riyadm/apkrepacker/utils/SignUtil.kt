package com.riyadm.apkrepacker.utils

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.android.apksig.ApkSigner
import com.android.apksig.apk.ApkUtils
import com.android.apksig.util.DataSources
import androidx.preference.PreferenceManager
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys
import com.google.common.collect.ImmutableList
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ApktoolEngine
import com.riyadm.apkrepacker.apktool.ApktoolLogListener
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import org.apache.commons.io.IOUtils
import sun1.security.pkcs.PKCS8Key
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.logging.Level

class SignUtil private constructor() {
    private var privateKey: PrivateKey? = null
    private var certificate: X509Certificate? = null

    /**
     * Programmatic overrides of the scheme switches (null = the Settings switch). Used by the
     * on-device test harness. [schemesFor] still applies its safety rules on top.
     */
    var v1SigningEnabled: Boolean? = null
    var v2SigningEnabled: Boolean? = null
    var v3SigningEnabled: Boolean? = null
    var v4SigningEnabled: Boolean? = null

    /**
     * Signs [input] into [out] with the schemes from [schemesFor], reporting progress and every
     * scheme adjustment to [logger] (and logcat). With v4 on, also writes `<out>.idsig`; a stale
     * `.idsig` next to [out] is removed otherwise.
     */
    @Throws(Exception::class)
    fun sign(input: File, out: File, minSdk: Int, logger: ApktoolLogListener?): Boolean {
        msg?.let { report(logger, Level.WARNING, it) }
        if (out.exists()) {
            FileUtil.deleteFile(out)
        }
        val idsig = v4SignatureFile(out)
        if (idsig.exists()) idsig.delete()
        val schemes = schemesFor(input, minSdk)
        for (note in schemes.notes) report(logger, Level.WARNING, note)
        report(logger, Level.INFO, "Signature schemes: ${schemes.describe()} (minSdk ${schemes.minSdk}, targetSdk ${schemes.targetSdk ?: "?"})")
        val signer = newSigner(input, out, schemes, "ApkRepacker by Mr Ikso")
        report(logger, Level.INFO, String.format("Signing Apk: %s", input))
        return try {
            signer.sign()
            if (schemes.v4) report(logger, Level.INFO, "V4 signature written to ${idsig.path} (for adb install --incremental)")
            report(logger, Level.INFO, appContext?.getString(R.string.sign_done) ?: "Signing Done")
            true
        } catch (e: Exception) {
            report(logger, Level.WARNING, "Signature failed! " + ApktoolEngine.describe(e))
            e.printStackTrace()
            idsig.delete()
            false
        }
    }

    @Throws(Exception::class)
    fun sign(input: File, out: File, minSdk: Int): Boolean = sign(input, out, minSdk, null)

    /**
     * Effective signature schemes for one APK. [notes] explains every change made to the
     * requested switches, and warns about devices the signature won't install on.
     */
    data class Schemes(
        val v1: Boolean, val v2: Boolean, val v3: Boolean, val v4: Boolean,
        val minSdk: Int, val targetSdk: Int?, val notes: List<String> = emptyList(),
    ) {
        /** e.g. "v1 + v2 + v3". */
        fun describe(): String = listOfNotNull(
            "v1".takeIf { v1 }, "v2".takeIf { v2 }, "v3".takeIf { v3 }, "v4".takeIf { v4 },
        ).joinToString(" + ")
    }

    /**
     * Requested schemes: the v1/v2/v3/v4 switches in Settings (defaults on/on/on/off), or the
     * [v1SigningEnabled].. overrides when set. Safety rules on top, each one logged via [Schemes.notes]:
     * - none of v1/v2/v3 selected: sign with v1 + v2.
     * - targetSdk >= 30 (Android 11+ refuses to install it without v2+): v2 forced on if v2 and v3 are off.
     * - v4 needs a v2 or v3 signature to refer to: v2 forced on if v2 and v3 are off.
     * - the APK's minSdk is below what the chosen schemes cover (v1: any, v2: API 24, v3: API 28):
     *   kept as chosen, but warned about.
     * minSdk is the APK manifest's minSdkVersion when readable, else [fallbackMinSdk].
     */
    fun schemesFor(apk: File, fallbackMinSdk: Int): Schemes {
        var minSdk = fallbackMinSdk
        var targetSdk: Int? = null
        try {
            RandomAccessFile(apk, "r").use { raf ->
                val manifest = ApkUtils.getAndroidManifest(DataSources.asDataSource(raf))
                minSdk = ApkUtils.getMinSdkVersionFromBinaryAndroidManifest(manifest.duplicate())
                targetSdk = ApkUtils.getTargetSdkVersionFromBinaryAndroidManifest(manifest.duplicate())
            }
        } catch (e: Exception) {
            // Not a readable binary manifest: keep the caller's minSdk.
        }
        val prefs = appContext?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        fun pref(key: String, default: Boolean) = prefs?.getBoolean(key, default) ?: default
        var v1 = v1SigningEnabled ?: pref(PreferenceKeys.KEY_SIGN_V1, true)
        var v2 = v2SigningEnabled ?: pref(PreferenceKeys.KEY_USE_V2_SIGNATURE, true)
        val v3 = v3SigningEnabled ?: pref(PreferenceKeys.KEY_SIGN_V3, true)
        val v4 = v4SigningEnabled ?: pref(PreferenceKeys.KEY_SIGN_V4, false)
        val notes = ArrayList<String>()
        if (!v1 && !v2 && !v3) {
            v1 = true
            v2 = true
            notes.add("No v1/v2/v3 signature scheme is enabled: signing with v1 + v2")
        }
        val target = targetSdk
        if (target != null && target >= 30 && !v2 && !v3) {
            v2 = true
            notes.add("targetSdkVersion $target (Android 11+) requires an APK Signature Scheme v2 or v3 signature: v2 enabled")
        }
        if (v4 && !v2 && !v3) {
            v2 = true
            notes.add("v4 signing needs a v2 or v3 signature: v2 enabled")
        }
        if (!v1 && minSdk < 24) {
            val from = if (v2) "7.0 (API 24)" else "9 (API 28)"
            notes.add("v1 signing is off but minSdkVersion is $minSdk: the APK won't install on Android older than $from")
        } else if (!v1 && !v2 && minSdk < 28) {
            notes.add("Only v3 is enabled but minSdkVersion is $minSdk: the APK won't install on Android older than 9 (API 28)")
        }
        return Schemes(v1, v2, v3, v4, minSdk, targetSdk, notes)
    }

    private fun newSigner(input: File, out: File, schemes: Schemes, createdBy: String): ApkSigner {
        val signerConfig = ApkSigner.SignerConfig.Builder("CERT", privateKey, ImmutableList.of(certificate!!)).build()
        val builder = ApkSigner.Builder(ImmutableList.of(signerConfig))
            .setInputApk(input)
            .setOutputApk(out)
            .setCreatedBy(createdBy)
            .setMinSdkVersion(schemes.minSdk)
            .setV1SigningEnabled(schemes.v1)
            .setV2SigningEnabled(schemes.v2)
            // apksig enables v3 by default; set it explicitly so "v1 only" stays free of a v3 block.
            .setV3SigningEnabled(schemes.v3)
            // Stored native libraries aligned to 16 KB pages (Android 15+ 16 KB page devices);
            // other stored entries keep apksig's 4-byte alignment.
            .setLibraryPageAlignmentBytes(LIBRARY_PAGE_ALIGNMENT)
            .setV4SigningEnabled(schemes.v4)
        if (schemes.v4) builder.setV4SignatureOutputFile(v4SignatureFile(out))
        return builder.build()
    }

    fun interface LoadKeyCallback {
        fun call(signTool: SignUtil?)
    }

    companion object {
        private var preferenceHelper: PreferenceHelper? = null
        private var appContext: Context? = null

        /** Warning for the signing log (custom key missing, default key used instead). */
        private var msg: String? = null

        private val types = arrayOf("JKS", "PKCS12", "BKS")

        private const val TAG = "SignUtil"
        private const val LIBRARY_PAGE_ALIGNMENT = 16384

        /** Where the v4 signature of [apk] goes: `<apk>.idsig`, as `adb install --incremental` expects. */
        @JvmStatic
        fun v4SignatureFile(apk: File): File = File(apk.path + ".idsig")

        private fun report(logger: ApktoolLogListener?, level: Level, message: String) {
            logger?.onLog(level, message)
            if (level.intValue() >= Level.WARNING.intValue()) Log.w(TAG, message) else Log.i(TAG, message)
        }

        /** The app's built-in AOSP test key (assets/key/testkey.*), without touching preferences. */
        @JvmStatic
        @Throws(Exception::class)
        fun loadTestKey(context: Context): SignUtil {
            appContext = context.applicationContext
            val assets = context.assets
            val st = SignUtil()
            st.privateKey = assets.open("key/testkey.pk8").use { key -> PKCS8Key().apply { decode(key) } }
            st.certificate = assets.open("key/testkey.x509.pem").use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }
            return st
        }

        @JvmStatic
        fun loadKey(context: Context, callback: LoadKeyCallback) {
            val helper = PreferenceHelper.getInstance(context)
            preferenceHelper = helper
            appContext = context.applicationContext
            msg = null
            var custom = helper.isCustomSign
            if (custom) {
                val type = helper.keyType!!
                val keyPath = helper.privateKeyPath
                val certOrAlias = helper.certPath
                val storePass = helper.storeKey
                val keyPass = helper.privateKey
                try {
                    custom = if (type == 3)
                        loadKey(callback, keyPath!!, certOrAlias!!)
                    else
                        loadKey(context, callback, keyPath!!, type, certOrAlias!!, storePass!!, keyPass!!)
                } catch (e: Exception) {
                    error(context, keyPath)
                }
            }
            if (!custom) {
                try {
                    val st = SignUtil()
                    val cert = FileInputStream(helper.certPath)
                    val key = FileInputStream(helper.privateKeyPath)
                    val pkcs8 = PKCS8Key()
                    pkcs8.decode(key)

                    st.privateKey = pkcs8
                    st.certificate = CertificateFactory.getInstance("X.509").generateCertificate(cert) as X509Certificate
                    cert.close()
                    key.close()
                    callback.call(st)
                } catch (e: InvalidKeyException) {
                    e.printStackTrace()
                } catch (e: CertificateException) {
                    e.printStackTrace()
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        private fun error(context: Context, keyPath: String?) {
            val message = context.resources.getString(R.string.load_signature_file_fail, keyPath)
            AlertDialog.Builder(context)
                .setTitle(R.string.error)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }

        @Throws(Exception::class)
        private fun loadKey(
            context: Context, callback: LoadKeyCallback, keyPath: String, type: Int, alias: String,
            storePassText: String, keyPassText: String,
        ): Boolean {
            if (!exists(keyPath)) return false
            val keyType = types[type]
            val ks = KeyStore.getInstance(keyType)
            if (storePassText.isEmpty()) {
                showPasswd(context, callback, ks, keyPath, alias)
            } else {
                val storePass = storePassText.toCharArray()
                val keyPass = if (keyPassText.isEmpty()) storePass else keyPassText.toCharArray()
                loadKey(callback, ks, keyPath, alias, storePass, keyPass)
            }
            return true
        }

        private fun showPasswd(context: Context, callback: LoadKeyCallback, ks: KeyStore, keyPath: String, alias: String) {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_key_password, null)
            val storePass = view.findViewById<EditText>(R.id.storePass)
            val keyPass = view.findViewById<EditText>(R.id.keyPass)
            val msgView = view.findViewById<TextView>(R.id.msg)
            msgView.visibility = View.VISIBLE
            msgView.text = keyPath
            AlertDialog.Builder(context)
                .setTitle(R.string.enter_password)
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val storePassText = storePass.text.toString()
                    var keyPassText = keyPass.text.toString()
                    if (keyPassText.isEmpty()) keyPassText = storePassText
                    try {
                        loadKey(callback, ks, keyPath, alias, storePassText.toCharArray(), keyPassText.toCharArray())
                    } catch (e: Exception) {
                        error(context, keyPath)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        @Throws(Exception::class)
        private fun loadKey(
            callback: LoadKeyCallback, ks: KeyStore, keyPath: String, aliasName: String,
            storePass: CharArray, keyPass: CharArray,
        ) {
            FileInputStream(keyPath).use { ks.load(it, storePass) }
            val alias = if (aliasName.isEmpty()) ks.aliases().nextElement() else aliasName
            val prk = ks.getKey(alias, keyPass) as PrivateKey
            val cert = ks.getCertificate(alias) as X509Certificate
            val st = SignUtil()
            st.privateKey = prk
            st.certificate = cert
            callback.call(st)
        }

        @Throws(Exception::class)
        private fun loadKey(callback: LoadKeyCallback, keyPath: String, certPath: String): Boolean {
            if (!exists(keyPath)) return false
            if (!exists(certPath)) return false
            val data = FileInputStream(keyPath).use { IOUtils.toByteArray(it) }
            val cert = FileInputStream(certPath).use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }
            val spec = PKCS8EncodedKeySpec(data)
            val prk = KeyFactory.getInstance(cert.publicKey.algorithm).generatePrivate(spec)
            val st = SignUtil()
            st.privateKey = prk
            st.certificate = cert
            callback.call(st)
            return true
        }

        private fun exists(path: String): Boolean {
            if (File(path).exists()) return true
            msg = appContext?.getString(R.string.signature_file_missing, path)
                ?: "Signature file '$path' hasn't been found, using default signature!"
            return false
        }
    }
}
