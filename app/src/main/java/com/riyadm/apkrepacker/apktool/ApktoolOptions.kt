package com.riyadm.apkrepacker.apktool

import android.content.Context
import androidx.preference.PreferenceManager

/** What to decode from the APK. Mirrors apktool's `-r` / `-s` switches. */
enum class DecodeMode {
    /** Resources and smali (default `apktool d`). */
    ALL,

    /** Resources only, keep classes*.dex as-is (`apktool d -s`). */
    RESOURCES_ONLY,

    /** Smali only, keep resources.arsc and binary XML as-is (`apktool d -r`). */
    SOURCES_ONLY,

    /** Nothing decoded, just unpack the files. */
    NONE,
}

/** Options for decompiling an APK, one field per apktool `d` flag. */
data class DecodeOptions(
    val mode: DecodeMode = DecodeMode.ALL,
    /** `--only-main-classes`: only disassemble classes*.dex in the APK root. */
    val onlyMainClasses: Boolean = true,
    /** `--no-debug-info`: skip .line/.local/.param debug directives. */
    val noDebugInfo: Boolean = false,
    /** `--no-assets`: don't extract assets/. */
    val noAssets: Boolean = false,
    /** `--force-manifest`: decode AndroidManifest.xml even when resources are skipped. */
    val forceManifest: Boolean = false,
    /** `--keep-broken-res`: keep going when a resource can't be decoded. */
    val keepBrokenResources: Boolean = false,
    /** `--match-original`/analysis mode: keeps output closer to the original, may not rebuild. */
    val analysisMode: Boolean = false,
    /** `-f`: delete the output folder first if it already exists. */
    val force: Boolean = true,
    /**
     * For an app installed as split APKs: merge its config splits (native libraries of the ABI
     * split, resources of the density/language splits) into the project. See [SplitApks].
     */
    val mergeSplits: Boolean = true,
)

/** Options for rebuilding a project, one field per apktool `b` flag. */
data class BuildOptions(
    /** `--debuggable`: set android:debuggable="true" in the manifest. */
    val debuggable: Boolean = false,
    /** `--net-sec-conf`: add a network security config that trusts user CAs. */
    val netSecConf: Boolean = false,
    /** `--copy-original`: copy the original AndroidManifest.xml and META-INF. */
    val copyOriginal: Boolean = false,
    /** `--no-crunch`: don't crunch PNGs. */
    val noCrunch: Boolean = false,
    /** `-f`: rebuild everything, ignoring cached build outputs. */
    val force: Boolean = false,
    /** Sign the rebuilt APK (v1 + v2/v3 per the existing signature preference). */
    val sign: Boolean = true,
    /**
     * Remove android:isSplitRequired / requiredSplitTypes / splitTypes and the Play split meta-data
     * from the manifest, so the base of a split app installs on its own. See [SplitApks].
     */
    val removeSplitRequirement: Boolean = true,
)

/** Persists the default decode/build options in the app's default SharedPreferences. */
object ApktoolOptionsStore {
    const val KEY_DECODE_MODE = "pref_apktool_decode_mode"
    const val KEY_ONLY_MAIN_CLASSES = "pref_apktool_only_main_classes"
    const val KEY_NO_DEBUG_INFO = "pref_apktool_no_debug_info"
    const val KEY_NO_ASSETS = "pref_apktool_no_assets"
    const val KEY_FORCE_MANIFEST = "pref_apktool_force_manifest"
    const val KEY_KEEP_BROKEN_RES = "pref_apktool_keep_broken_res"
    const val KEY_ANALYSIS_MODE = "pref_apktool_analysis_mode"
    const val KEY_MERGE_SPLITS = "pref_apktool_merge_splits"

    const val KEY_BUILD_DEBUGGABLE = "pref_apktool_build_debuggable"
    const val KEY_BUILD_NET_SEC_CONF = "pref_apktool_build_net_sec_conf"
    const val KEY_BUILD_COPY_ORIGINAL = "pref_apktool_build_copy_original"
    const val KEY_BUILD_NO_CRUNCH = "pref_apktool_build_no_crunch"
    const val KEY_BUILD_FORCE = "pref_apktool_build_force"
    const val KEY_BUILD_SIGN = "pref_apktool_build_sign"
    const val KEY_BUILD_REMOVE_SPLIT_REQUIREMENT = "pref_apktool_build_remove_split_requirement"

    @JvmStatic
    fun loadDecodeOptions(context: Context): DecodeOptions {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val defaults = DecodeOptions()
        val mode = prefs.getString(KEY_DECODE_MODE, null)
            ?.let { name -> DecodeMode.entries.firstOrNull { it.name == name } }
            ?: defaults.mode
        return DecodeOptions(
            mode = mode,
            onlyMainClasses = prefs.getBoolean(KEY_ONLY_MAIN_CLASSES, defaults.onlyMainClasses),
            noDebugInfo = prefs.getBoolean(KEY_NO_DEBUG_INFO, defaults.noDebugInfo),
            noAssets = prefs.getBoolean(KEY_NO_ASSETS, defaults.noAssets),
            forceManifest = prefs.getBoolean(KEY_FORCE_MANIFEST, defaults.forceManifest),
            keepBrokenResources = prefs.getBoolean(KEY_KEEP_BROKEN_RES, defaults.keepBrokenResources),
            analysisMode = prefs.getBoolean(KEY_ANALYSIS_MODE, defaults.analysisMode),
            mergeSplits = prefs.getBoolean(KEY_MERGE_SPLITS, defaults.mergeSplits),
        )
    }

    @JvmStatic
    fun saveDecodeOptions(context: Context, options: DecodeOptions) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY_DECODE_MODE, options.mode.name)
            .putBoolean(KEY_ONLY_MAIN_CLASSES, options.onlyMainClasses)
            .putBoolean(KEY_NO_DEBUG_INFO, options.noDebugInfo)
            .putBoolean(KEY_NO_ASSETS, options.noAssets)
            .putBoolean(KEY_FORCE_MANIFEST, options.forceManifest)
            .putBoolean(KEY_KEEP_BROKEN_RES, options.keepBrokenResources)
            .putBoolean(KEY_ANALYSIS_MODE, options.analysisMode)
            .putBoolean(KEY_MERGE_SPLITS, options.mergeSplits)
            .apply()
    }

    @JvmStatic
    fun loadBuildOptions(context: Context): BuildOptions {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val defaults = BuildOptions()
        return BuildOptions(
            debuggable = prefs.getBoolean(KEY_BUILD_DEBUGGABLE, defaults.debuggable),
            netSecConf = prefs.getBoolean(KEY_BUILD_NET_SEC_CONF, defaults.netSecConf),
            copyOriginal = prefs.getBoolean(KEY_BUILD_COPY_ORIGINAL, defaults.copyOriginal),
            noCrunch = prefs.getBoolean(KEY_BUILD_NO_CRUNCH, defaults.noCrunch),
            force = prefs.getBoolean(KEY_BUILD_FORCE, defaults.force),
            sign = prefs.getBoolean(KEY_BUILD_SIGN, defaults.sign),
            removeSplitRequirement = prefs.getBoolean(KEY_BUILD_REMOVE_SPLIT_REQUIREMENT, defaults.removeSplitRequirement),
        )
    }

    @JvmStatic
    fun saveBuildOptions(context: Context, options: BuildOptions) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(KEY_BUILD_DEBUGGABLE, options.debuggable)
            .putBoolean(KEY_BUILD_NET_SEC_CONF, options.netSecConf)
            .putBoolean(KEY_BUILD_COPY_ORIGINAL, options.copyOriginal)
            .putBoolean(KEY_BUILD_NO_CRUNCH, options.noCrunch)
            .putBoolean(KEY_BUILD_FORCE, options.force)
            .putBoolean(KEY_BUILD_SIGN, options.sign)
            .putBoolean(KEY_BUILD_REMOVE_SPLIT_REQUIREMENT, options.removeSplitRequirement)
            .apply()
    }
}
