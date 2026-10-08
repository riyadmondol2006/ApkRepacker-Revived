package com.riyadm.apkrepacker.apktool

import android.content.Context
import android.os.Process
import brut.androlib.ApkBuilder
import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.androlib.meta.ApkInfo
import brut.androlib.res.Framework
import androidx.preference.PreferenceManager
import com.riyadm.apkrepacker.BuildConfig
import com.riyadm.apkrepacker.ui.preferences.PreferenceKeys
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.logging.Level

/**
 * Runs apktool 3 (org.apktool:apktool-lib) on Android.
 *
 * - aapt2 is the static build shipped as `lib/<abi>/libaapt2.so` (extracted to nativeLibraryDir).
 * - Frameworks live in `filesDir/framework`; apktool extracts its bundled android framework there
 *   as `1.apk` on first use.
 * - apktool's java.util.logging output is forwarded to the caller's [ApktoolLogListener].
 * - Every failure is reported as an [ApktoolException] with a readable message (including aapt2's
 *   error lines), never as a raw Throwable, so callers can't crash on an Error from the library.
 *
 * Operations are serialized: decode/build are memory hungry and share the log bridge.
 */
object ApktoolEngine {

    private const val AAPT2_LIB = "libaapt2.so"
    private const val FRAMEWORK_DIR = "framework"
    private const val FRAMEWORK_MARKER = ".apktool-version"
    private const val BUILD_OPTIONS_STAMP = ".apkrepacker-build-options"

    init {
        // apktool reads desktop-JVM system properties in static initializers (brut.util.OSDetection
        // calls System.getProperty("sun.arch.data.model").toLowerCase()); Android doesn't set them,
        // which made every resource decode/build fail with ExceptionInInitializerError.
        if (System.getProperty("sun.arch.data.model") == null) {
            System.setProperty("sun.arch.data.model", if (Process.is64Bit()) "64" else "32")
        }
        if (System.getProperty("os.name") == null) {
            System.setProperty("os.name", "Linux")
        }
        if (System.getProperty("user.home").isNullOrEmpty()) {
            System.setProperty("user.home", System.getProperty("java.io.tmpdir") ?: "/data/local/tmp")
        }
    }

    private val lock = Any()

    /** apktool-lib version, e.g. "3.0.3". */
    @JvmStatic
    val version: String
        get() = BuildConfig.APKTOOL_VERSION

    @JvmStatic
    fun frameworkDirectory(context: Context): File = File(context.filesDir, FRAMEWORK_DIR)

    @JvmStatic
    fun aaptBinary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, AAPT2_LIB)

    /** A [Config] with the app's aapt2, framework directory and thread count. */
    @JvmStatic
    fun newConfig(context: Context): Config {
        val config = Config(version)
        config.jobs = 1
        config.frameworkDirectory = prepareFrameworkDirectory(context).absolutePath
        config.aaptBinary = aaptBinary(context).absolutePath
        // Settings > Build > Verbose: apktool logs every file it processes and passes -v to aapt2.
        config.isVerbose = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PreferenceKeys.KEY_VERBOSE_MODE, false)
        return config
    }

    /**
     * Decodes [apk] into [outDir] (replaced if it exists). On failure the partial output is removed.
     * @return the decoded apk info (what apktool wrote to apktool.yml).
     */
    @JvmStatic
    @Throws(ApktoolException::class)
    fun decode(context: Context, apk: File, outDir: File, options: DecodeOptions, log: ApktoolLogListener?): ApkInfo {
        if (!apk.isFile || !apk.canRead()) {
            throw ApktoolException("Can't read ${apk.path}")
        }
        checkCaseCollisions(apk, outDir, options)
        val config = newConfig(context)
        applyDecodeOptions(config, options)
        config.jobs = decodeJobCount()
        return run(log, "decompile ${apk.name}", Op.DECODE, null) {
            log?.onLog(Level.INFO, "Decode mode: ${describe(options)}")
            val splits = if (options.mergeSplits) prepareSplits(context, apk, options, log) else null
            try {
                // Split-only resources referenced from the base resolve to their names, not @null.
                splits?.libraryFiles?.forEach { (name, files) -> config.libraryFiles[name] = files }
                val decoder = ApkDecoder(apk, config)
                decoder.decode(outDir)
                seedDexCache(apk, outDir, log)
                if (splits != null && finishSplits(splits, outDir, log)) {
                    ApkInfo.load(outDir)
                } else {
                    decoder.apkInfo
                }
            } catch (t: Throwable) {
                FileUtils.deleteQuietly(outDir)
                throw t
            } finally {
                splits?.close()
            }
        }
    }

    /**
     * Builds the apktool project [projectDir] into [outApk] (unsigned). Projects decoded by the old
     * apktool 2.4.1 fork (apktool.json only) get an apktool.yml first.
     * @return the project's apk info (sdk/version info from apktool.yml).
     */
    @JvmStatic
    @Throws(ApktoolException::class)
    fun build(context: Context, projectDir: File, outApk: File, options: BuildOptions, log: ApktoolLogListener?): ApkInfo {
        if (!projectDir.isDirectory) {
            throw ApktoolException("Project folder not found: ${projectDir.path}")
        }
        if (!File(projectDir, "apktool.yml").isFile) {
            if (!ProjectMeta.migrateLegacyProject(projectDir, version, log)) {
                throw ApktoolException("Not an apktool project (apktool.yml is missing): ${projectDir.path}")
            }
        }
        val aapt = aaptBinary(context)
        if (!aapt.isFile) {
            throw ApktoolException(
                "aapt2 is not available for this device (${android.os.Build.SUPPORTED_ABIS.joinToString()}): $aapt"
            )
        }
        if (!aapt.canExecute()) {
            aapt.setExecutable(true)
        }
        val config = newConfig(context)
        config.isDebuggable = options.debuggable
        config.isNetSecConf = options.netSecConf
        config.isCopyOriginal = options.copyOriginal
        config.isNoCrunch = options.noCrunch
        config.isForced = options.force
        return run(log, "build ${projectDir.name}", Op.BUILD, projectDir) {
            outApk.parentFile?.mkdirs()
            if (options.removeSplitRequirement) {
                SplitApks.removeSplitRequirement(projectDir, log)
                if (options.copyOriginal) {
                    log?.onLog(Level.WARNING, "Copy original manifest is on: the original manifest (with its split requirement, if any) goes into the APK")
                }
            }
            if (!options.force) {
                invalidateStaleBuildOutputs(projectDir, options, log)
            }
            config.jobs = buildJobCount(projectDir, options.force, log)
            try {
                ApkBuilder(projectDir, config).build(outApk)
            } catch (t: Throwable) {
                if (config.jobs <= 1 || generateSequence(t) { it.cause }.take(8).none { it is OutOfMemoryError }) throw t
                // Parallel smali jobs didn't fit in the heap: once more, one folder at a time.
                log?.onLog(Level.WARNING, "Out of memory with ${config.jobs} threads, retrying with 1")
                System.gc()
                config.jobs = 1
                ApkBuilder(projectDir, config).build(outApk)
            }
            if (!outApk.isFile) {
                throw ApktoolException("apktool did not produce ${outApk.path}")
            }
            ApkInfo.load(projectDir)
        }
    }

    /**
     * Finds and decodes the config splits of an installed split app (see [SplitApks.prepare]).
     * Never fails the decode: on an error the project gets what the base APK has.
     */
    private fun prepareSplits(context: Context, apk: File, options: DecodeOptions, log: ApktoolLogListener?): SplitApks.Prepared? {
        val decodesResources = options.mode == DecodeMode.ALL || options.mode == DecodeMode.RESOURCES_ONLY
        return try {
            SplitApks.prepare(context, apk, decodesResources, { newConfig(context) }, log)
        } catch (e: Exception) {
            log?.onLog(Level.WARNING, "Reading the splits failed (${describe(e)}): only base.apk is decompiled")
            null
        }
    }

    /** Merges the prepared splits into the project. @return true when apktool.yml may have changed. */
    private fun finishSplits(splits: SplitApks.Prepared, outDir: File, log: ApktoolLogListener?): Boolean = try {
        SplitApks.finish(splits, outDir, log)
        true
    } catch (e: Exception) {
        log?.onLog(
            Level.WARNING,
            "Merging the splits failed (${describe(e)}): native libraries or resources from the splits may be missing."
        )
        true
    }

    /**
     * apktool only re-assembles a smali folder when it is newer than its dex in `build/apk/`.
     * Right after decoding, that dex is exactly the APK's original one, so it is put there (dated
     * after the smali files): building an unedited project then never re-assembles code, which is
     * faster, byte-identical, and avoids running out of memory on huge dex files (a 44 MB dex needs
     * ~700 MB of heap to assemble, more than Android gives an app). Editing, adding or deleting
     * any file in a smali folder makes it newer again, and BuildOptions.force rebuilds everything.
     */
    private fun seedDexCache(apk: File, projectDir: File, log: ApktoolLogListener?) {
        val smaliDirs = projectDir.listFiles { f -> f.isDirectory && (f.name == "smali" || f.name.startsWith("smali_")) }
        if (smaliDirs.isNullOrEmpty()) return
        val cacheDir = File(projectDir, "build/apk")
        val seeded = ArrayList<File>()
        try {
            java.util.zip.ZipFile(apk).use { zip ->
                for (dir in smaliDirs) {
                    // Same mapping as apktool's ApkBuilder: smali -> classes.dex, smali_X@Y -> X/Y.dex
                    val dexName = if (dir.name == "smali") "classes.dex" else dir.name.substringAfter('_').replace('@', '/') + ".dex"
                    val entry = zip.getEntry(dexName) ?: continue
                    val dex = File(cacheDir, dexName)
                    dex.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> dex.outputStream().use { input.copyTo(it) } }
                    seeded.add(dex)
                }
            }
            // Dated after every decoded file; anything the user changes later is newer.
            val time = maxOf(System.currentTimeMillis(), brut.util.BrutIO.recursiveModifiedTime(smaliDirs)) + 1000
            for (dex in seeded) {
                if (!dex.setLastModified(time)) throw java.io.IOException("can't set the date of $dex")
            }
            if (seeded.isNotEmpty()) {
                log?.onLog(Level.FINE, "Kept the original ${seeded.joinToString { it.name }}: unedited smali folders won't be re-assembled")
            }
        } catch (e: Exception) {
            // Only an optimization: without the cache everything is simply re-assembled.
            seeded.forEach { it.delete() }
            log?.onLog(Level.FINE, "Original dex cache not created: ${describe(e)}")
        }
    }

    /**
     * Without force, apktool reuses `build/apk` from the previous build when the manifest and res/
     * didn't change, so toggling --debuggable, --net-sec-conf, --copy-original or --no-crunch
     * between two builds did nothing, and files deleted from res/ stayed in the APK. Drop the
     * cached resource outputs (not the dex cache) whenever they would be stale.
     */
    private fun invalidateStaleBuildOutputs(projectDir: File, options: BuildOptions, log: ApktoolLogListener?) {
        val buildDir = File(projectDir, "build")
        val apkDir = File(buildDir, "apk")
        if (!apkDir.isDirectory) return
        val stamp = File(buildDir, BUILD_OPTIONS_STAMP)
        val fingerprint = "apktool=$version debuggable=${options.debuggable} netSecConf=${options.netSecConf} " +
            "copyOriginal=${options.copyOriginal} noCrunch=${options.noCrunch} " +
            "removeSplitRequirement=${options.removeSplitRequirement}"
        val previous = try { if (stamp.isFile) stamp.readText().trim() else null } catch (e: Exception) { null }
        val manifest = File(apkDir, "AndroidManifest.xml")
        val resChanged = manifest.exists() && listOf(File(projectDir, "AndroidManifest.xml"), File(projectDir, "res"))
            .any { it.exists() && brut.util.BrutIO.recursiveModifiedTime(it) > brut.util.BrutIO.recursiveModifiedTime(manifest) }
        if (previous != fingerprint) {
            if (previous != null) log?.onLog(Level.INFO, "Build options changed: rebuilding resources")
            apkDir.listFiles()?.filter { !it.name.endsWith(".dex") && !(it.isDirectory && it.name != "res" && it.name != "META-INF") }
                ?.forEach { FileUtils.deleteQuietly(it) }
        } else if (resChanged) {
            FileUtils.deleteQuietly(File(apkDir, "res"))
        }
        try {
            buildDir.mkdirs()
            stamp.writeText(fingerprint)
        } catch (ignored: Exception) {
        }
    }

    /**
     * Threads for building, sized by the dex folders that have to be re-assembled (the cached dex in
     * build/apk, else ~1/9 of the smali folder size); see the BUILD_* heap model below. Warns about a
     * folder that likely can't be assembled in this heap at all.
     */
    private fun buildJobCount(projectDir: File, force: Boolean, log: ApktoolLogListener?): Int {
        val dirs = projectDir.listFiles { f -> f.isDirectory && (f.name == "smali" || f.name.startsWith("smali_")) }
        if (dirs.isNullOrEmpty()) return 1
        val stale = ArrayList<Pair<File, Long>>()
        for (dir in dirs) {
            val dexName = if (dir.name == "smali") "classes.dex" else dir.name.substringAfter('_').replace('@', '/') + ".dex"
            val dex = File(projectDir, "build/apk/$dexName")
            val isStale = force || !dex.exists() ||
                brut.util.BrutIO.recursiveModifiedTime(dir) > brut.util.BrutIO.recursiveModifiedTime(dex)
            if (!isStale) continue
            stale += dir to if (dex.isFile) dex.length() else FileUtils.sizeOfDirectory(dir) / 9
        }
        if (stale.isEmpty()) return 1
        val heapMb = Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0)
        val (largestDir, largest) = stale.maxBy { it.second }
        if (BUILD_BASE_HEAP_MB + BUILD_HEAP_PER_DEX_MB_MIN * (largest / (1024.0 * 1024.0)) > heapMb) {
            log?.onLog(
                Level.WARNING,
                "${largestDir.name} (~${largest shr 20} MB of dex) may need more memory to assemble than this " +
                    "device gives the app (${heapMb.toInt()} MB). If the build runs out of memory, decompile again " +
                    "with \"No debug info\" (smaller smali) or avoid editing that folder."
            )
        }
        return buildJobCount(stale.map { it.second }, heapMb)
    }

    /** Installs a framework apk (e.g. a vendor framework-res.apk) into the app's framework folder. */
    @JvmStatic
    @Throws(ApktoolException::class)
    fun installFramework(context: Context, apk: File, log: ApktoolLogListener?) {
        val config = newConfig(context)
        run(log, "install framework ${apk.name}", Op.FRAMEWORK, null) {
            Framework(config).install(apk)
        }
    }

    /**
     * Shared storage (/sdcard, including Android/data) ignores upper/lower case. Obfuscated apps
     * often contain files like `res/-S.xml` and `res/-s.xml`, which apktool writes to the project
     * as-is (raw resources, assets, unknown files): on such storage one silently overwrites the
     * other and the rebuilt app is broken. Fail up front with a way out instead.
     */
    private fun checkCaseCollisions(apk: File, outDir: File, options: DecodeOptions) {
        val decodesResources = options.mode == DecodeMode.ALL || options.mode == DecodeMode.RESOURCES_ONLY
        val names = try {
            java.util.zip.ZipFile(apk).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList()
            }
        } catch (e: Exception) {
            return // apktool reports unreadable apks itself
        }
        val written = names.filter { name ->
            // Decoded resources get new, unique names from the resource table.
            !(decodesResources && (name.startsWith("res/") || name.startsWith("r/") || name.startsWith("R/"))) &&
                !(options.noAssets && name.startsWith("assets/"))
        }
        val collisions = written.groupBy { it.lowercase(java.util.Locale.ROOT) }.values.filter { it.size > 1 }
        if (collisions.isEmpty() || !isCaseInsensitive(outDir.absoluteFile.parentFile ?: outDir)) {
            return
        }
        val example = collisions.first().take(2).joinToString(" and ")
        val inRes = collisions.all { group -> group.all { it.startsWith("res/") || it.startsWith("r/") || it.startsWith("R/") } }
        val advice = if (!decodesResources && inRes) {
            "Decode the resources too (mode \"All\" or \"Resources only\"): decoded resources get unique names."
        } else {
            "This APK can't be decompiled into shared storage; it needs a case-sensitive folder."
        }
        throw ApktoolException(
            "Can't decompile ${apk.name} into ${outDir.parent}: the APK has ${collisions.size} file name(s) " +
                "that differ only in upper/lower case (e.g. $example), and this storage ignores case, so they " +
                "would overwrite each other and the rebuilt app would be broken.\n$advice"
        )
    }

    /** True when [dir] (created if needed) treats "a" and "A" as the same file name. */
    private fun isCaseInsensitive(dir: File): Boolean {
        dir.mkdirs()
        val probe = File(dir, ".apktool-CaseProbe-" + android.os.Process.myPid())
        return try {
            probe.createNewFile()
            File(dir, probe.name.lowercase(java.util.Locale.ROOT)).exists()
        } catch (e: Exception) {
            false
        } finally {
            probe.delete()
        }
    }

    /** Readable one-line description of an exception and its causes. */
    @JvmStatic
    fun describe(t: Throwable): String {
        val parts = ArrayList<String>()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 8) {
            val text = when (cur) {
                is OutOfMemoryError -> "Out of memory"
                else -> cur.message?.let { shorten(it) }
            }
            if (text != null && text.isNotBlank() && parts.none { it.contains(text) }) {
                parts.add(text)
            } else if (text == null && parts.isEmpty()) {
                parts.add(cur.javaClass.simpleName)
            }
            cur = if (cur.cause === cur) null else cur.cause
            depth++
        }
        return parts.joinToString(": ")
    }

    private fun describe(options: DecodeOptions): String = buildString {
        append(options.mode.name.lowercase())
        if (options.onlyMainClasses) append(", only main classes")
        if (options.noDebugInfo) append(", no debug info")
        if (options.noAssets) append(", no assets")
        if (options.forceManifest) append(", force manifest")
        if (options.keepBrokenResources) append(", keep broken resources")
        if (options.analysisMode) append(", analysis mode")
        if (options.mergeSplits) append(", merge splits")
    }

    /** "Execution failed (exit code = 1): [/data/app/.../libaapt2.so, link, ...]" -> "aapt2 failed (exit code 1)". */
    private fun shorten(message: String): String {
        val exec = Regex("""Execution failed \(exit code = (-?\d+)\): \[([^,\]]*)""").find(message)
        if (exec != null) {
            val tool = File(exec.groupValues[2]).name.removePrefix("lib").removeSuffix(".so")
            return "$tool failed (exit code ${exec.groupValues[1]})"
        }
        val start = Regex("""could not exec ?: \[([^,\]]*)""").find(message)
        if (start != null) {
            val tool = File(start.groupValues[1]).name.removePrefix("lib").removeSuffix(".so")
            return "could not run $tool"
        }
        return message
    }

    private fun applyDecodeOptions(config: Config, options: DecodeOptions) {
        val sources = if (options.onlyMainClasses) Config.DecodeSources.ONLY_MAIN_CLASSES else Config.DecodeSources.FULL
        val noResources = if (options.forceManifest) Config.DecodeResources.ONLY_MANIFEST else Config.DecodeResources.NONE
        when (options.mode) {
            DecodeMode.ALL -> {
                config.setDecodeSources(sources)
                config.setDecodeResources(Config.DecodeResources.FULL)
            }
            DecodeMode.RESOURCES_ONLY -> {
                config.setDecodeSources(Config.DecodeSources.NONE)
                config.setDecodeResources(Config.DecodeResources.FULL)
            }
            DecodeMode.SOURCES_ONLY -> {
                config.setDecodeSources(sources)
                config.setDecodeResources(noResources)
            }
            DecodeMode.NONE -> {
                config.setDecodeSources(Config.DecodeSources.NONE)
                config.setDecodeResources(noResources)
            }
        }
        config.isBaksmaliDebugMode = !options.noDebugInfo
        config.setDecodeAssets(if (options.noAssets) Config.DecodeAssets.NONE else Config.DecodeAssets.FULL)
        config.isKeepBrokenResources = options.keepBrokenResources
        config.isAnalysisMode = options.analysisMode
        // The caller picks the output folder; an existing one is replaced.
        config.isForced = true
    }

    // Heap model for smali assembly, fitted on the host (HotSpot, apktool 3.0.3, -Xmx bisection on
    // SystemUI/Settings/Keep/a 14-dex app): one job needs ~64 MB + 12-36x its dex size. 20 MB per
    // dex MB is the typical factor (~16) with a 1.25 margin for ART's GC; 16 is used for the
    // "can't fit at all" warning. apktool's job count also parallelizes work inside each dex, so
    // heap jumps at 4 jobs regardless of folder sizes (Keep: 384 MB at 2 jobs, 704 MB at 4;
    // Settings 160 -> 384 MB) while 2 jobs are as fast or faster: builds use at most 2.
    // The OOM retry with one job in build() covers outliers.
    private const val BUILD_MAX_JOBS = 2
    private const val BUILD_BASE_HEAP_MB = 64.0
    private const val BUILD_HEAP_PER_DEX_MB = 20.0
    private const val BUILD_HEAP_PER_DEX_MB_MIN = 16.0

    private fun maxJobs(): Int = maxOf(1, minOf(Runtime.getRuntime().availableProcessors(), 4))

    /**
     * Threads for decoding. Measured decode heap barely depends on the job count or dex size
     * (a 43 MB dex decodes in 160 MB with 8 jobs); big resource tables dominate instead.
     */
    @JvmStatic
    fun decodeJobCount(): Int = maxJobs()

    /**
     * Threads for assembling dex folders of [staleDexSizes] bytes: the largest n <= min(cores, 2) such
     * that 64 + 20 x (sum of the n biggest dex sizes in MB) fits in [heapMb]; at least 1.
     */
    @JvmStatic
    @JvmOverloads
    fun buildJobCount(staleDexSizes: List<Long>, heapMb: Double = Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0)): Int {
        var jobs = 1
        var sumMb = 0.0
        for ((i, size) in staleDexSizes.sortedDescending().take(minOf(maxJobs(), BUILD_MAX_JOBS)).withIndex()) {
            sumMb += size / (1024.0 * 1024.0)
            if (BUILD_BASE_HEAP_MB + BUILD_HEAP_PER_DEX_MB * sumMb <= heapMb) jobs = i + 1 else break
        }
        return jobs
    }

    /**
     * The framework folder used to hold the apktool 2.4.1 fork's android.jar as `1.apk`. apktool 3
     * would pick that stale file up instead of its own bundled framework, so it's dropped once
     * (apktool re-extracts its framework on demand). Other installed frameworks are kept.
     */
    private fun prepareFrameworkDirectory(context: Context): File {
        val dir = frameworkDirectory(context)
        dir.mkdirs()
        val marker = File(dir, FRAMEWORK_MARKER)
        val current = try {
            if (marker.isFile) marker.readText().trim() else null
        } catch (e: Exception) {
            null
        }
        if (current != version) {
            File(dir, "1.apk").delete()
            try {
                marker.writeText(version)
            } catch (ignored: Exception) {
            }
        }
        return dir
    }

    private enum class Op { DECODE, BUILD, FRAMEWORK }

    private fun <T> run(log: ApktoolLogListener?, what: String, op: Op, projectDir: File?, block: () -> T): T = synchronized(lock) {
        val session = ApktoolLogBridge.open(log)
        try {
            block()
        } catch (e: ApktoolException) {
            throw e
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ApktoolException("Cancelled: $what", e)
        } catch (t: Throwable) {
            throw failure(session, what, op, projectDir, t)
        } finally {
            session.close()
        }
    }

    private fun failure(
        session: ApktoolLogBridge.Session, what: String, op: Op, projectDir: File?, t: Throwable,
    ): ApktoolException {
        val message = StringBuilder("Failed to $what: ").append(describe(t))
        val chain = generateSequence(t) { it.cause }.take(8).toList()
        var toolErrors = emptyList<String>()
        if (chain.any { it.message?.contains("Execution failed") == true }) {
            // aapt2's output is read on other threads and may still be arriving.
            try {
                Thread.sleep(150)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            // "W: /storage/.../Project/res/values/arrays.xml:95: error: ..." -> "res/values/arrays.xml:95: error: ..."
            val prefix = projectDir?.absolutePath?.let { it.trimEnd('/') + "/" }
            toolErrors = session.toolErrors().map { line -> if (prefix != null) line.replace(prefix, "") else line }
            if (toolErrors.isNotEmpty()) {
                message.append('\n').append(toolErrors.joinToString("\n"))
            }
        }
        hint(op, chain, toolErrors)?.let { message.append('\n').append(it) }
        return ApktoolException(message.toString(), t)
    }

    /** What the user can do about a failure, when there's something better than "see the log". */
    private fun hint(op: Op, chain: List<Throwable>, toolErrors: List<String>): String? {
        if (chain.any { it is OutOfMemoryError }) {
            return if (op == Op.DECODE) {
                "Hint: the app ran out of memory. Decompile with mode \"Resources only\" or \"Sources only\"."
            } else {
                "Hint: the app ran out of memory assembling smali. Only edited smali folders are re-assembled: " +
                    "keep edits out of the biggest smali folder if you can, or close other apps and try again."
            }
        }
        chain.firstOrNull { it is brut.androlib.exceptions.FrameworkNotFoundException }?.let {
            val id = Regex("""package ID (\d+)""").find(it.message ?: "")?.groupValues?.get(1) ?: "?"
            return "Hint: this app needs the framework with package ID $id (a vendor/OEM framework apk, e.g. from " +
                "/system/framework, /system_ext/framework or /product/overlay). Import it as a framework, then " +
                "decompile the app again."
        }
        if (op == Op.BUILD && toolErrors.any { FRAMEWORK_ERROR.containsMatchIn(it) }) {
            return "Hint: the app uses private or newer resources of the Android framework that apktool's " +
                "built-in framework doesn't have (typical for system apps). Import this device's framework " +
                "(/system/framework/framework-res.apk) as a framework, decompile the app again, then build."
        }
        if (op == Op.BUILD && toolErrors.isNotEmpty()) {
            return "Hint: fix the resource file(s) named above. If you only need to edit smali, decompile with " +
                "mode \"Sources only\": resources are then kept exactly as they are."
        }
        if (op == Op.BUILD && chain.any { it.message?.startsWith("Could not smali") == true }) {
            return "Hint: fix the smali file named above."
        }
        if (op == Op.DECODE && chain.any { e -> e.stackTrace.any { it.className.startsWith("brut.androlib.res.") } }) {
            return "Hint: a resource couldn't be decoded. Turn on \"Keep broken resources\", or decompile with " +
                "mode \"Sources only\" to keep the resources as they are."
        }
        return null
    }

    private val FRAMEWORK_ERROR = Regex(
        """resource android:\S+ is private|resource android:\S+ not found|attribute android:\S+ not found|""" +
            """style attribute 'android:\S+' not found"""
    )
}
