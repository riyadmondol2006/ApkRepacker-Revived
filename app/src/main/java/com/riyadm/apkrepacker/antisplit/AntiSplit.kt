package com.riyadm.apkrepacker.antisplit

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.reandroid.apk.APKLogger
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ArchiveFile
import com.reandroid.archive.ZipEntryMap
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.riyadm.apkrepacker.apktool.ApktoolLogListener
import com.riyadm.apkrepacker.utils.SafeZip
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.logging.Level
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * AntiSplit: turns an app that is installed as split APKs (a base APK plus configuration and
 * feature splits), or a split-APK archive (.apks, .xapk, .apkm), into one ordinary APK that
 * installs and decompiles like any other.
 *
 * The merge is binary, done with ARSCLib: resource tables, manifests and dex files are combined
 * directly, nothing is decompiled, so it is fast and lossless. On top of ARSCLib this:
 * - picks the base APK itself (the one APK whose manifest has no `split`); ARSCLib only accepts a
 *   base that has a launcher activity, so service-only apps used to be merged around a guess;
 * - refuses inputs that can't belong together (two bases, other packages, other versions);
 * - removes every leftover split requirement from the manifest, so the APK installs on its own;
 * - warns about protections that stop a re-signed app from running (PairIP).
 *
 * The result is unsigned. Signing (see [com.riyadm.apkrepacker.service.AntiSplitService]) also
 * page-aligns the stored native libraries, which apps with `extractNativeLibs="false"` need.
 */
object AntiSplit {

    class AntiSplitException(message: String, cause: Throwable? = null) : IOException(message, cause)

    /** What a merge produced. */
    data class Result(
        val packageName: String,
        val versionName: String?,
        val versionCode: Int?,
        val minSdk: Int?,
        /** "base" followed by the split names, e.g. config.arm64_v8a. */
        val modules: List<String>,
        /** Things the user should know (for example an anti-tamper library). */
        val warnings: List<String>,
    )

    /** File types handled as split-APK archives (besides a .zip that holds APKs). */
    val ARCHIVE_EXTENSIONS = setOf("apks", "xapk", "apkm")

    // Framework attribute ids (android.R.attr), checked against the platform's public resources.
    private const val ATTR_EXTRACT_NATIVE_LIBS = 0x010104ea
    private const val ATTR_SPLIT_NAME = 0x01010549
    private const val ATTR_IS_FEATURE_SPLIT = 0x0101055b
    private const val ATTR_IS_SPLIT_REQUIRED = 0x01010591
    private const val ATTR_USE_EMBEDDED_DEX = 0x0101059e
    private const val ATTR_REQUIRED_SPLIT_TYPES = 0x0101064e
    private const val ATTR_SPLIT_TYPES = 0x0101064f

    /** `<meta-data>` the Play Store adds to split apps; they only make sense next to the splits. */
    private val SPLIT_META_DATA = setOf(
        "com.android.vending.splits.required",
        "com.android.vending.splits",
        "com.android.vending.derived.apk.id",
        "com.android.stamp.source",
        "com.android.stamp.type",
        "com.android.dynamic.apk.fused.modules",
    )

    /** Native library of Google Play's anti-tamper wrapper; such apps check their own signature. */
    private val PAIRIP_LIBRARY = Regex("""lib/[^/]+/libpairipcore\.so""")

    /**
     * The original JAR (v1) signature, copied over from the base APK. It no longer matches the
     * merged contents; left in, an "unsigned" APK would look signed and fail verification.
     */
    private val OLD_SIGNATURE = Regex("""META-INF/([^/]+\.(SF|RSA|DSA|EC)|SIG-[^/]+|MANIFEST\.MF)|stamp-cert-sha256""", RegexOption.IGNORE_CASE)

    // region inputs

    /** True when the app is installed as more than one APK. */
    @JvmStatic
    fun isSplit(info: ApplicationInfo?): Boolean = !info?.splitSourceDirs.isNullOrEmpty()

    /** The base and split APK files of an installed app (readable without any permission). */
    @JvmStatic
    @Throws(IOException::class)
    fun installedApks(context: Context, packageName: String): List<File> {
        val info = try {
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw AntiSplitException("$packageName is not installed", e)
        }
        val paths = listOfNotNull(info.sourceDir) + info.splitSourceDirs.orEmpty()
        return paths.map(::File).filter { it.isFile }
    }

    /** Whether [file] looks like a split-APK archive (.apks, .xapk, .apkm, or a .zip of APKs). */
    @JvmStatic
    fun isSplitArchive(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.ROOT)
        if (extension in ARCHIVE_EXTENSIONS) return true
        if (extension != "zip") return false
        return try {
            ZipFile(file).use { zip -> zip.entries().asSequence().count { it.name.isApkName() } >= 2 }
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Extracts the APKs inside a split-APK archive into [dir] and returns them. Only `.apk`
     * entries are taken (an .xapk also holds OBB data and icons), names are flattened and made
     * unique, and entry names that would leave [dir] are skipped.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun extractArchive(archive: File, dir: File, log: ApktoolLogListener?): List<File> {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create $dir")
        val extracted = ArrayList<File>()
        val used = HashSet<String>()
        try {
            ZipFile(archive).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory || !entry.name.isApkName()) continue
                    val base = File(entry.name.replace('\\', '/')).name
                    var name = base
                    var n = 2
                    while (!used.add(name.lowercase(Locale.ROOT))) name = base.removeSuffix(".apk") + "_$n.apk".also { n++ }
                    val target = SafeZip.resolve(dir, name) ?: continue
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it, 64 * 1024) } }
                    extracted += target
                }
            }
        } catch (e: ZipException) {
            // Newer APKMirror bundles are encrypted, which no tool can unpack without their app.
            throw AntiSplitException("Can't read ${archive.name}: it is damaged or encrypted (${e.message})", e)
        }
        if (extracted.isEmpty()) throw AntiSplitException("${archive.name} contains no APK files")
        log?.onLog(Level.INFO, "Unpacked ${extracted.size} APK(s) from ${archive.name}")
        return extracted
    }

    /**
     * The base APK among [apks] (the one without a `split` name), or null if there is none.
     * Used for "base APK only" on an archive.
     */
    @JvmStatic
    fun findBase(apks: List<File>): File? = apks.firstOrNull { file ->
        runCatching { ArchiveFile(file).use { archive -> readManifestInfo(archive.createZipEntryMap())?.let { it.split == null } } }
            .getOrNull() == true
    }

    // endregion

    // region merge

    /**
     * Merges [apks] (one base APK and its splits, in any order) into the unsigned APK [out].
     * @throws AntiSplitException with a message for the user when the inputs can't be merged.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun merge(apks: List<File>, out: File, log: ApktoolLogListener?): Result {
        if (apks.isEmpty()) throw AntiSplitException("No APK files to merge")
        val opened = ArrayList<Closeable>()
        try {
            val modules = apks.map { file -> openModule(file, log).also { opened += it.archive } }
            val inputs = check(modules)
            val warnings = ArrayList<String>()
            if (inputs.any { m -> m.module.zipEntryMap.toArray().any { PAIRIP_LIBRARY.matches(it.name) } }) {
                warnings += "This app is protected by Google Play's anti-tamper check (PairIP). A re-signed " +
                    "copy usually closes itself or refuses to run."
            }
            log?.onLog(Level.INFO, "Merging ${inputs.joinToString { it.name }} (${inputs.first().info.packageName})")

            ApkBundle().use { bundle ->
                bundle.setAPKLogger(ArscLogger(log))
                inputs.forEach { bundle.addModule(it.module) }
                bundle.mergeModules(false).use { merged ->
                    val manifest = merged.androidManifest
                        ?: throw AntiSplitException("The merged APK has no manifest")
                    cleanManifest(manifest, warnings, log)
                    merged.zipEntryMap.removeIf { OLD_SIGNATURE.matches(it.name) }
                    val tmp = File(out.parentFile, out.name + ".part")
                    try {
                        merged.writeApk(tmp)
                        if (out.exists() && !out.delete()) throw IOException("Cannot replace $out")
                        if (!tmp.renameTo(out)) throw IOException("Cannot write $out")
                    } finally {
                        tmp.delete()
                    }
                    val base = inputs.first().info
                    warnings.forEach { log?.onLog(Level.WARNING, it) }
                    log?.onLog(Level.INFO, "Merged APK: ${out.name} (${out.length() / 1024} KB)")
                    return Result(
                        packageName = base.packageName,
                        versionName = manifest.versionName,
                        versionCode = manifest.versionCode,
                        minSdk = manifest.minSdkVersion,
                        modules = inputs.map { it.name },
                        warnings = warnings,
                    )
                }
            }
        } finally {
            opened.forEach { runCatching { it.close() } }
        }
    }

    /** An APK opened for merging, with the facts read from its manifest. */
    private class Input(val name: String, val module: ExplicitBaseModule, val archive: ArchiveFile, val info: ManifestInfo)

    private class ManifestInfo(val packageName: String, val split: String?, val versionCode: Int?, val isFeature: Boolean)

    /**
     * ARSCLib decides which module is the base with a heuristic (it must have a launcher
     * activity). This module is told instead, from the `split` attribute of its manifest.
     */
    private class ExplicitBaseModule(name: String, entries: ZipEntryMap) : ApkModule(name, entries) {
        var base = false
        override fun isBaseModule(): Boolean = base
    }

    private class Opened(val file: File, val module: ExplicitBaseModule, val archive: ArchiveFile, val info: ManifestInfo?)

    private fun openModule(file: File, log: ApktoolLogListener?): Opened {
        val archive = try {
            ArchiveFile(file)
        } catch (e: Exception) {
            throw AntiSplitException("${file.name} is not a readable APK (${e.message})", e)
        }
        val module = ExplicitBaseModule(file.nameWithoutExtension, archive.createZipEntryMap())
        module.setCloseable(archive)
        module.setLoadDefaultFramework(false)
        val info = readManifestInfo(module.zipEntryMap, module)
        log?.onLog(Level.FINE, "${file.name}: ${info?.split ?: "base"}")
        return Opened(file, module, archive, info)
    }

    private fun readManifestInfo(entries: ZipEntryMap, module: ApkModule? = null): ManifestInfo? {
        val manifest = try {
            (module ?: ApkModule("probe", entries)).androidManifest
        } catch (e: Exception) {
            null
        } ?: return null
        val root = manifest.manifestElement ?: return null
        return ManifestInfo(
            packageName = manifest.packageName.orEmpty(),
            split = manifest.split?.takeIf { it.isNotEmpty() },
            versionCode = manifest.versionCode,
            isFeature = root.searchAttributeByResourceId(ATTR_IS_FEATURE_SPLIT)?.valueAsBoolean == true,
        )
    }

    /** Validates the set and returns it base first, each split once, with unique module names. */
    private fun check(opened: List<Opened>): List<Input> {
        opened.firstOrNull { it.info == null }?.let { throw AntiSplitException("${it.file.name} has no Android manifest, so it is not an APK") }
        val bases = opened.filter { it.info!!.split == null }
        when {
            bases.isEmpty() -> throw AntiSplitException("There is no base APK, only splits (${opened.joinToString { it.file.name }})")
            bases.size > 1 -> throw AntiSplitException("There is more than one base APK: ${bases.joinToString { it.file.name }}")
        }
        val base = bases.single()
        val packageName = base.info!!.packageName
        opened.firstOrNull { it.info!!.packageName != packageName }?.let {
            throw AntiSplitException("${it.file.name} belongs to ${it.info!!.packageName}, not to $packageName")
        }
        // Android itself refuses splits of another version, so a mixed set can't be right.
        opened.firstOrNull { it.info!!.versionCode != base.info.versionCode }?.let {
            throw AntiSplitException("${it.file.name} is version ${it.info!!.versionCode}, the base is ${base.info.versionCode}")
        }
        val splits = opened.filter { it !== base }.distinctBy { it.info!!.split }
        if (splits.isEmpty()) throw AntiSplitException("${base.file.name} has no split APKs, so there is nothing to merge")
        base.module.base = true
        return listOf(Input("base", base.module, base.archive, base.info)) +
            splits.map { Input(it.info!!.split!!, it.module, it.archive, it.info) }
    }

    /**
     * Removes what ties the merged APK to splits that no longer exist. ARSCLib already removes the
     * common markers; this is the safety net for the rest (feature splits, uses-split, splitName).
     */
    private fun cleanManifest(manifest: AndroidManifestBlock, warnings: MutableList<String>, log: ApktoolLogListener?) {
        var removed = 0
        val root = manifest.manifestElement
        if (root != null) {
            for (id in intArrayOf(ATTR_REQUIRED_SPLIT_TYPES, ATTR_SPLIT_TYPES, ATTR_IS_SPLIT_REQUIRED, ATTR_IS_FEATURE_SPLIT)) {
                if (root.searchAttributeByResourceId(id) != null) {
                    root.removeAttributesWithId(id)
                    removed++
                }
            }
            if (root.searchAttributeByName("split") != null) {
                root.removeAttributesWithName("split")
                removed++
            }
            removed += root.removeChildren { it.name == "uses-split" }
        }
        val application = manifest.applicationElement
        if (application != null) {
            if (application.searchAttributeByResourceId(ATTR_IS_SPLIT_REQUIRED) != null) {
                application.removeAttributesWithId(ATTR_IS_SPLIT_REQUIRED)
                removed++
            }
            removed += application.removeChildren {
                it.name == "meta-data" && AndroidManifestBlock.getAndroidNameValue(it) in SPLIT_META_DATA
            }
            // Components of a feature split point at it by name; their code is now in this APK.
            for (element in application.descendants()) {
                if (element.searchAttributeByResourceId(ATTR_SPLIT_NAME) != null) {
                    element.removeAttributesWithId(ATTR_SPLIT_NAME)
                    removed++
                }
            }
            // ARSCLib writes dex files compressed, and Android won't install an app that asks for
            // embedded (uncompressed) dex with compressed dex files.
            val embeddedDex = application.searchAttributeByResourceId(ATTR_USE_EMBEDDED_DEX)
            if (embeddedDex != null && embeddedDex.valueAsBoolean) {
                application.removeAttributesWithId(ATTR_USE_EMBEDDED_DEX)
                removed++
                warnings += "useEmbeddedDex was turned off so the merged APK can be installed."
            }
        }
        manifest.refresh()
        val extract = manifest.isExtractNativeLibs
        log?.onLog(Level.INFO, "Manifest cleaned: $removed leftover split setting(s) removed" +
            if (extract == false) "; native libraries stay uncompressed (extractNativeLibs=false)" else "")
    }

    private fun ResXmlElement.removeChildren(predicate: (ResXmlElement) -> Boolean): Int {
        val before = elementsCount
        removeElementsIf { predicate(it as ResXmlElement) }
        return before - elementsCount
    }

    private fun ResXmlElement.descendants(): List<ResXmlElement> {
        val result = ArrayList<ResXmlElement>()
        val iterator = recursiveElements()
        while (iterator.hasNext()) (iterator.next() as? ResXmlElement)?.let(result::add)
        return result
    }

    /** Forwards ARSCLib's progress to the app's log, at debug level (it is chatty). */
    private class ArscLogger(private val log: ApktoolLogListener?) : APKLogger {
        override fun logMessage(msg: String?) {
            if (msg != null) log?.onLog(Level.FINE, msg)
        }

        override fun logError(msg: String?, tr: Throwable?) {
            log?.onLog(Level.WARNING, listOfNotNull(msg, tr?.message).joinToString(": "))
        }

        override fun logVerbose(msg: String?) = Unit
    }

    // endregion

    private fun String.isApkName(): Boolean = lowercase(Locale.ROOT).endsWith(".apk")

}
