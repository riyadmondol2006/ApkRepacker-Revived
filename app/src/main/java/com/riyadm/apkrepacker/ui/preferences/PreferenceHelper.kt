package com.riyadm.apkrepacker.ui.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jecelyin.common.utils.DLog
import com.jecelyin.common.utils.StringUtils
import com.riyadm.apkrepacker.apktool.ApktoolOptionsStore
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_AUTO_THEME
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_CERT_PATH
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_CONFIRM_BUILD
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_COPY_ORIGINAL_FILES
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_DEBUG_MODE
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_DECODING_FOLDER
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_DECODING_MODE
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_EXTENSIONS
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_FILES_MODE
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_KEEP_SCREEN_ON
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_KEY_TYPE
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_MATCH_CASE
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_PRIVATE_KEY
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_PRIVATE_KEY_PATH
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_RECURSIVELY
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_REVERSE_TRANSLATED
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_SHOW_HIDDEN_FILES
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_SIGN_OUT_APK
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_SKIP_SUPPORT_LINES
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_SKIP_TRANSLATED
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_STORE_KEY
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_THEME
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_TOOLS_INSTALLED
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_USE_CUSTOM_SIGN
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_USE_REGEX
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_USE_V2_SIGNATURE
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_VERBOSE_MODE
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys.KEY_WHOLE_WORDS_ONLY
import org.json.JSONObject
import java.io.File
import java.util.WeakHashMap

/**
 * Typed, cached view over the app's default SharedPreferences.
 *
 * Every known key has a default; the cache is refreshed whenever the stored value changes, so
 * getters never touch disk. Interested parties can listen through
 * [registerOnSharedPreferenceChangeListener] (held weakly).
 */
class PreferenceHelper(private val context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val cache = HashMap<String, Any?>()
    private val listeners = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)

        cache[KEY_SHOW_HIDDEN_FILES] = false
        cache[KEY_DECODING_FOLDER] = defaultDecodingPath(context)
        cache[KEY_DECODING_MODE] = 0
        cache[KEY_SIGN_OUT_APK] = true
        cache[KEY_USE_CUSTOM_SIGN] = false
        cache[KEY_DEBUG_MODE] = false
        cache[KEY_VERBOSE_MODE] = false
        cache[KEY_COPY_ORIGINAL_FILES] = false
        cache[KEY_USE_V2_SIGNATURE] = true
        cache[KEY_TOOLS_INSTALLED] = false
        cache[KEY_PRIVATE_KEY_PATH] = ""
        cache[KEY_CERT_PATH] = ""
        cache[KEY_STORE_KEY] = ""
        cache[KEY_PRIVATE_KEY] = ""
        cache[KEY_USE_REGEX] = false
        cache[KEY_MATCH_CASE] = false
        cache[KEY_KEY_TYPE] = 0
        cache[KEY_WHOLE_WORDS_ONLY] = false
        cache[KEY_RECURSIVELY] = true
        cache[KEY_FILES_MODE] = true
        cache[KEY_EXTENSIONS] = defaultExtensionsJson()
        cache[KEY_KEEP_SCREEN_ON] = false
        cache[KEY_CONFIRM_BUILD] = false
        cache[KEY_THEME] = 0
        cache[KEY_AUTO_THEME] = false
        cache[KEY_SKIP_TRANSLATED] = false
        cache[KEY_SKIP_SUPPORT_LINES] = false
        cache[KEY_REVERSE_TRANSLATED] = false

        val stored = prefs.all
        cache.keys.toList().forEach { refresh(it, stored) }
        migrateDecodingFolder()
    }

    /**
     * Projects used to live in /sdcard/ApkRepacker. The default is now the app's own external files
     * dir (no storage permission, no FUSE overhead). A stored value equal to the old default is
     * switched to the new one; a folder the user picked is kept.
     */
    private fun migrateDecodingFolder() {
        val stored = prefs.getString(KEY_DECODING_FOLDER, null) ?: return
        if (stored.trimEnd('/') == legacyDecodingPath()) {
            val newPath = defaultDecodingPath(context)
            prefs.edit { putString(KEY_DECODING_FOLDER, newPath) }
            cache[KEY_DECODING_FOLDER] = newPath
        }
    }

    private fun bool(key: String) = cache[key] as Boolean

    val isCustomSign: Boolean get() = bool(KEY_USE_CUSTOM_SIGN)

    /** Whether to sign rebuilt apks: the build options' "sign" switch if set, else the old setting. */
    val isSignResultApk: Boolean
        get() = if (prefs.contains(ApktoolOptionsStore.KEY_BUILD_SIGN)) {
            prefs.getBoolean(ApktoolOptionsStore.KEY_BUILD_SIGN, true)
        } else {
            bool(KEY_SIGN_OUT_APK)
        }

    val isV2SignatureEnabled: Boolean get() = bool(KEY_USE_V2_SIGNATURE)
    val isDebugModeApk: Boolean get() = bool(KEY_DEBUG_MODE)
    val isVerboseModeApk: Boolean get() = bool(KEY_VERBOSE_MODE)
    val isCopyOriginalFiles: Boolean get() = bool(KEY_COPY_ORIGINAL_FILES)
    val isShowHiddenFiles: Boolean get() = bool(KEY_SHOW_HIDDEN_FILES)
    val isKeepScreenOn: Boolean get() = bool(KEY_KEEP_SCREEN_ON)
    val isConfirmBuild: Boolean get() = bool(KEY_CONFIRM_BUILD)

    /** Root folder of the app's work files: `projects/`, `output/`, `compile_log.txt`, `dictionary/`. */
    val decodingPath: String get() = cache[KEY_DECODING_FOLDER] as String

    /** Folder holding the decompiled projects (created if needed). */
    val projectsDir: File get() = File(decodingPath, "projects").also { it.mkdirs() }

    /** Folder receiving the rebuilt apks (created if needed). */
    val outputDir: File get() = File(decodingPath, "output").also { it.mkdirs() }

    val decodingMode: Int? get() = cache[KEY_DECODING_MODE] as Int?

    val currentTheme: Int get() = cache[KEY_THEME] as Int

    var certPath: String?
        get() = cache[KEY_CERT_PATH] as String?
        set(value) = putString(KEY_CERT_PATH, value)

    var privateKeyPath: String?
        get() = cache[KEY_PRIVATE_KEY_PATH] as String?
        set(value) = putString(KEY_PRIVATE_KEY_PATH, value)

    var privateKey: String?
        get() = cache[KEY_PRIVATE_KEY] as String?
        set(value) = putString(KEY_PRIVATE_KEY, value)

    var storeKey: String?
        get() = cache[KEY_STORE_KEY] as String?
        set(value) = putString(KEY_STORE_KEY, value)

    var keyType: Int?
        get() = cache[KEY_KEY_TYPE] as Int?
        set(mode) {
            val value = requireNotNull(mode) { "keyType must not be null" }
            prefs.edit { putInt(KEY_KEY_TYPE, value) }
            cache[KEY_KEY_TYPE] = value
        }

    var isToolsInstalled: Boolean
        get() = bool(KEY_TOOLS_INSTALLED)
        set(value) = putBoolean(KEY_TOOLS_INSTALLED, value)

    var isRegexMode: Boolean
        get() = bool(KEY_USE_REGEX)
        set(value) = putBoolean(KEY_USE_REGEX, value)

    var isMatchCaseMode: Boolean
        get() = bool(KEY_MATCH_CASE)
        set(value) = putBoolean(KEY_MATCH_CASE, value)

    var isWholeWordsOnlyMode: Boolean
        get() = bool(KEY_WHOLE_WORDS_ONLY)
        set(value) = putBoolean(KEY_WHOLE_WORDS_ONLY, value)

    var isRecursivelyMode: Boolean
        get() = bool(KEY_RECURSIVELY)
        set(value) = putBoolean(KEY_RECURSIVELY, value)

    var isFilesMode: Boolean
        get() = bool(KEY_FILES_MODE)
        set(value) = putBoolean(KEY_FILES_MODE, value)

    var isSkipTranslated: Boolean
        get() = bool(KEY_SKIP_TRANSLATED)
        set(value) = putBoolean(KEY_SKIP_TRANSLATED, value)

    var isSkipSupportLines: Boolean
        get() = bool(KEY_SKIP_SUPPORT_LINES)
        set(value) = putBoolean(KEY_SKIP_SUPPORT_LINES, value)

    var isReverseDictionary: Boolean
        get() = bool(KEY_REVERSE_TRANSLATED)
        set(value) = putBoolean(KEY_REVERSE_TRANSLATED, value)

    /** Extensions the "find in files" search looks at, with their enabled state. */
    var ext: MutableMap<String, Boolean>
        get() {
            val result = HashMap<String, Boolean>()
            try {
                val json = JSONObject(cache[KEY_EXTENSIONS] as String)
                json.keys().forEach { name -> result[name] = json.getBoolean(name) }
            } catch (e: Exception) {
                DLog.e("extensions", e)
            }
            return result
        }
        set(value) {
            val json = JSONObject(value as Map<*, *>).toString()
            prefs.edit { putString(KEY_EXTENSIONS, json) }
            cache[KEY_EXTENSIONS] = json
        }

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(this) { listeners[listener] = LISTENER_TOKEN }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == null) return
        refresh(key, sharedPreferences.all)
        val snapshot = synchronized(this) { listeners.keys.toList() }
        snapshot.forEach { it?.onSharedPreferenceChanged(sharedPreferences, key) }
    }

    fun getValue(key: String?): Any? = cache[key]

    private fun putString(key: String, value: String?) {
        prefs.edit { putString(key, value) }
        cache[key] = value
    }

    private fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
        cache[key] = value
    }

    /** Re-reads [key] from [stored], keeping the cached default when it isn't stored or has a foreign type. */
    private fun refresh(key: String, stored: Map<String, *>) {
        val current = cache[key] ?: return
        val input = stored[key]
        try {
            cache[key] = when (current) {
                is Int -> when (input) {
                    null -> current
                    is Int -> input
                    else -> StringUtils.toInt(input.toString())
                }
                is Boolean -> input as Boolean? ?: current
                else -> input as String? ?: current
            }
        } catch (e: Exception) {
            DLog.e("key = $key", e)
        }
    }

    companion object {
        private var instance: PreferenceHelper? = null
        private val LISTENER_TOKEN = Any()

        @JvmField
        val VALUE_EXT: MutableMap<String, Boolean> = HashMap()

        @JvmStatic
        fun getInstance(context: Context): PreferenceHelper =
            instance ?: synchronized(PreferenceHelper::class.java) {
                instance ?: PreferenceHelper(context.applicationContext).also { instance = it }
            }

        /** Default work folder: Android/data/<package>/files (app-owned, falls back to internal storage). */
        @JvmStatic
        fun defaultDecodingPath(context: Context): String =
            (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath

        @Suppress("DEPRECATION")
        private fun legacyDecodingPath(): String = Environment.getExternalStorageDirectory().path + "/ApkRepacker"

        private fun defaultExtensionsJson(): String {
            listOf("smali", "xml", "jpg", "png", "txt").forEach { VALUE_EXT[it] = true }
            return JSONObject(VALUE_EXT as Map<*, *>).toString()
        }
    }
}
