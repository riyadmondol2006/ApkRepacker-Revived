package com.riyadm.apkrepacker.apktool

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riyadm.apkrepacker.utils.SignUtil
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Level
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * On-device apktool round trip: decode -> build -> validate -> sign -> validate, for a list of
 * APKs and decode modes, through the same [ApktoolEngine] / [SignUtil] code the app uses.
 *
 * Run (after `adb install -r` of the app and of the test APK; don't use connectedAndroidTest, it
 * uninstalls the app):
 *
 *     adb shell am instrument -w -e class com.riyadm.apkrepacker.apktool.ApktoolRoundTripTest \
 *         [-e apks com.foo,/system/framework/framework-res.apk] [-e modes ALL] \
 *         [-e allModes com.foo|none] [-e framework auto|bundled|device] \
 *         [-e debuggable true] [-e netSecConf true] [-e keepBrokenRes true] [-e noCrunch true] \
 *         [-e sign v1,v2,v3] [-e storage external|internal] [-e keep true] [-e tag name] \
 *         [-e failOnError true] [-e mergeSplits false] [-e removeSplitRequirement false] \
 *         com.riyadm.apkrepacker.test/androidx.test.runner.AndroidJUnitRunner
 *
 * `apks` entries are absolute paths or package names (resolved with `pm path` as the shell user,
 * so package visibility doesn't matter). Output goes to
 * `Android/data/com.riyadm.apkrepacker/files/roundtrip/`: `results-<tag>.md` / `.tsv` (also logged
 * under the `RoundTrip` logcat tag), `logs/<case>.log` (the full apktool log of each case) and
 * `out/<case>-signed.apk` (the signed rebuilt APKs, for host-side checks).
 *
 * framework=auto (default): APKs under /system, /system_ext, /product or /vendor are run against
 * the device's framework-res.apk, installed with [ApktoolEngine.installFramework] (the Import
 * framework path of the app); the others against apktool's bundled framework. The app's framework
 * folder is restored afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ApktoolRoundTripTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val args = InstrumentationRegistry.getArguments()

    private fun arg(name: String): String? = args.getString(name)?.trim()?.takeIf { it.isNotEmpty() }
    private fun flag(name: String, default: Boolean = false): Boolean = arg(name)?.toBoolean() ?: default

    private class Target(val name: String, val apk: File) {
        val isSystem: Boolean
            get() = SYSTEM_PREFIXES.any { apk.path.startsWith(it) }
    }

    private class CaseResult(val target: String, val mode: String, val framework: String) {
        var ok = false
        var stage = ""
        var error = ""
        var decodeMs = 0L
        var buildMs = 0L
        var signMs = 0L
        var totalMs = 0L
        var peakHeapMb = 0L
        var apkMb = 0.0
        val notes = ArrayList<String>()
    }

    @Test
    fun roundTrip() {
        val root = File(
            if (arg("storage") == "internal") context.filesDir else (context.getExternalFilesDir(null) ?: context.filesDir),
            "roundtrip",
        )
        val outDir = File(root, "out").apply { deleteRecursively(); mkdirs() }
        val logDir = File(root, "logs").apply { deleteRecursively(); mkdirs() }
        val workDir = File(root, "work").apply { deleteRecursively(); mkdirs() }
        val tag = arg("tag") ?: SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

        val targets = (arg("apks")?.split(',') ?: DEFAULT_TARGETS).mapNotNull { resolve(it.trim()) }
        val modes = (arg("modes") ?: "ALL").split(',').map { DecodeMode.valueOf(it.trim().uppercase()) }
        val allModesFor = when (val a = arg("allModes")) {
            null -> DEFAULT_ALL_MODES_FOR
            "none" -> emptySet()
            else -> a.split(',').map { it.trim() }.toSet()
        }
        val frameworkMode = arg("framework") ?: "auto"

        val signer = SignUtil.loadTestKey(context)
        // Default: the app's own rules (SignUtil.schemesFor) over the Settings switches.
        // "-e sign v1,v2,v3,v4" sets all four switches (unlisted = off); "-e signV1 true" .. "-e signV4 false"
        // set one each. SignUtil's safety rules still apply on top (see the "signed ..." note).
        arg("sign")?.lowercase()?.let { schemes ->
            signer.v1SigningEnabled = "v1" in schemes
            signer.v2SigningEnabled = "v2" in schemes
            signer.v3SigningEnabled = "v3" in schemes
            signer.v4SigningEnabled = "v4" in schemes
        }
        arg("signV1")?.let { signer.v1SigningEnabled = it.toBoolean() }
        arg("signV2")?.let { signer.v2SigningEnabled = it.toBoolean() }
        arg("signV3")?.let { signer.v3SigningEnabled = it.toBoolean() }
        arg("signV4")?.let { signer.v4SigningEnabled = it.toBoolean() }

        log(
            "apktool ${ApktoolEngine.version}, ${Build.MODEL} API ${Build.VERSION.SDK_INT}, " +
                "${Runtime.getRuntime().availableProcessors()} cpus, max heap ${Runtime.getRuntime().maxMemory() shr 20} MB, " +
                "work dir $root, ${targets.size} apks, modes $modes, framework=$frameworkMode"
        )

        val results = ArrayList<CaseResult>()
        val frameworkDir = ApktoolEngine.frameworkDirectory(context)
        val frameworkBackup = File(context.filesDir, "framework.roundtrip-backup")
        var deviceFrameworkInstalled = false
        try {
            val (systemTargets, userTargets) = when (frameworkMode) {
                "device" -> Pair(targets, emptyList())
                "bundled" -> Pair(emptyList(), targets)
                else -> targets.partition { it.isSystem }
            }
            for (target in userTargets) {
                runTarget(target, modes, allModesFor, "bundled", workDir, outDir, logDir, signer, results)
            }
            if (systemTargets.isNotEmpty()) {
                frameworkBackup.deleteRecursively()
                if (frameworkDir.isDirectory) frameworkDir.copyRecursively(frameworkBackup, overwrite = true)
                deviceFrameworkInstalled = true
                results.add(installDeviceFramework(logDir))
                for (target in systemTargets) {
                    runTarget(target, modes, allModesFor, "device", workDir, outDir, logDir, signer, results)
                }
            }
        } finally {
            if (deviceFrameworkInstalled) {
                frameworkDir.deleteRecursively()
                if (frameworkBackup.isDirectory) {
                    frameworkBackup.copyRecursively(frameworkDir, overwrite = true)
                    frameworkBackup.deleteRecursively()
                }
            }
            if (!flag("keep")) workDir.deleteRecursively()
            writeReport(root, tag, results)
        }
        if (flag("failOnError")) {
            assertTrue("Round trip failures, see ${root.path}/results-$tag.md", results.all { it.ok })
        }
    }

    private fun runTarget(
        target: Target, modes: List<DecodeMode>, allModesFor: Set<String>, framework: String,
        workDir: File, outDir: File, logDir: File, signer: SignUtil, results: MutableList<CaseResult>,
    ) {
        val caseModes = if (target.name in allModesFor || target.apk.path in allModesFor) {
            (modes + DecodeMode.entries).distinct()
        } else {
            modes
        }
        for (mode in caseModes) {
            val result = runCase(target, mode, framework, workDir, outDir, logDir, signer)
            results.add(result)
            log(row(result))
            System.gc()
        }
    }

    private fun runCase(
        target: Target, mode: DecodeMode, framework: String,
        workDir: File, outDir: File, logDir: File, signer: SignUtil,
    ): CaseResult {
        val result = CaseResult(target.name, mode.name, framework)
        result.apkMb = target.apk.length() / 1048576.0
        val label = "${sanitize(target.name)}_${mode.name}"
        val projectDir = File(workDir, label)
        val unsigned = File(outDir, "$label.apk")
        val signed = File(outDir, "$label-signed.apk")
        val logFile = File(logDir, "$label.log")
        val logWriter = logFile.bufferedWriter()
        val t0 = System.currentTimeMillis()
        val listener = ApktoolLogListener { level, message ->
            synchronized(logWriter) {
                logWriter.write(String.format(Locale.US, "[%7.2f] ", (System.currentTimeMillis() - t0) / 1000.0))
                logWriter.write(ApktoolLogListener.format(level, message))
                logWriter.newLine()
            }
        }
        val sampler = HeapSampler().also { it.start() }
        val start = System.currentTimeMillis()
        try {
            result.stage = "decode"
            val decodeOptions = DecodeOptions(
                mode = mode,
                keepBrokenResources = flag("keepBrokenRes"),
                onlyMainClasses = flag("onlyMainClasses", true),
                mergeSplits = flag("mergeSplits", true),
            )
            var t = System.currentTimeMillis()
            ApktoolEngine.decode(context, target.apk, projectDir, decodeOptions, listener)
            result.decodeMs = System.currentTimeMillis() - t
            arg("renameTo")?.let { renameManifestPackage(projectDir, it, listener) }

            result.stage = "build"
            val buildOptions = BuildOptions(
                debuggable = flag("debuggable"),
                netSecConf = flag("netSecConf"),
                noCrunch = flag("noCrunch"),
                copyOriginal = flag("copyOriginal"),
                // force=true re-assembles every smali folder; the default (like the app) reuses
                // the original dex for unedited folders. editSmali=true touches one smali file per
                // folder, i.e. an edit everywhere, to exercise smali assembly without force.
                force = flag("force"),
                removeSplitRequirement = flag("removeSplitRequirement", true),
            )
            if (flag("editSmali")) touchSmali(projectDir)
            t = System.currentTimeMillis()
            ApktoolEngine.build(context, projectDir, unsigned, buildOptions, listener)
            result.buildMs = System.currentTimeMillis() - t

            result.stage = "validate"
            val problems = validate(target.apk, unsigned, mode, buildOptions, signed = false, notes = result.notes)
            if (problems.isNotEmpty()) throw ValidationException(problems)

            result.stage = "sign"
            t = System.currentTimeMillis()
            val minSdk = ProjectMeta.minSdkVersion(projectDir) ?: 14
            val schemes = signer.schemesFor(unsigned, minSdk)
            result.notes.add(
                "signed v1=${schemes.v1} v2=${schemes.v2} v3=${schemes.v3} v4=${schemes.v4} " +
                    "(min ${schemes.minSdk}, target ${schemes.targetSdk})" +
                    schemes.notes.joinToString("") { "; $it" }
            )
            if (!signer.sign(unsigned, signed, minSdk, listener)) {
                throw IllegalStateException("SignUtil.sign returned false (see log)")
            }
            result.signMs = System.currentTimeMillis() - t

            result.stage = "validate-signed"
            // With an <apk>.idsig next to it, Android's archive parser tries a v4/fs-verity check that
            // shared storage can't do (EACCES), so certificates are read with the .idsig moved aside.
            val idsig = SignUtil.v4SignatureFile(signed)
            val idsigAside = File(idsig.path + ".aside")
            if (idsig.isFile) {
                result.notes.add("v4 ${idsig.name} ${idsig.length()} bytes")
                idsig.renameTo(idsigAside)
            }
            val signedProblems = try {
                validate(target.apk, signed, mode, buildOptions, signed = true, notes = null)
            } finally {
                if (idsigAside.isFile) idsigAside.renameTo(idsig)
            }
            if (signedProblems.isNotEmpty()) throw ValidationException(signedProblems)

            result.ok = true
            result.stage = ""
        } catch (t: Throwable) {
            result.error = firstMeaningfulLine(t)
            listener.onLog(Level.SEVERE, "ROUNDTRIP FAILURE at ${result.stage}: ${t.message ?: t}")
            listener.onLog(Level.SEVERE, Log.getStackTraceString(t))
        } finally {
            result.totalMs = System.currentTimeMillis() - start
            result.peakHeapMb = sampler.stopAndGetPeakMb()
            synchronized(logWriter) { logWriter.close() }
            unsigned.delete()
            if (!flag("keep")) projectDir.deleteRecursively()
            if (!flag("keepApks", true)) signed.delete()
        }
        return result
    }

    /**
     * For install tests: gives the decoded app another package name so it can be installed next to
     * the original (manifest package, provider authorities, own permissions and task affinities;
     * class names stay as they are, aapt2 already wrote them fully qualified).
     */
    private fun renameManifestPackage(projectDir: File, newPackage: String, log: ApktoolLogListener) {
        val manifest = File(projectDir, "AndroidManifest.xml")
        var xml = manifest.readText()
        val oldPackage = Regex("""\bpackage="([^"]+)"""").find(xml)?.groupValues?.get(1)
            ?: error("no package attribute in the decoded manifest")
        xml = xml.replace("package=\"$oldPackage\"", "package=\"$newPackage\"")
        val attrs = Regex("""(android:authorities|android:taskAffinity|android:permission|android:name)="([^"]*)"""")
        xml = attrs.replace(xml) { m ->
            val (attr, value) = m.destructured
            val renamed = when {
                attr == "android:name" && !value.startsWith("$oldPackage.") -> value
                // Own permissions (androidx's DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION etc.), not classes.
                attr == "android:name" && !value.substringAfterLast('.').all { it.isUpperCase() || it == '_' || it.isDigit() } -> value
                else -> value.replace(oldPackage, newPackage)
            }
            "$attr=\"$renamed\""
        }
        // The split requirement (INSTALL_FAILED_MISSING_SPLIT) is left to BuildOptions.removeSplitRequirement.
        manifest.writeText(xml)
        log.onLog(Level.INFO, "Renamed package $oldPackage -> $newPackage")
    }

    private fun touchSmali(projectDir: File) {
        val later = System.currentTimeMillis() + 5000
        projectDir.listFiles { f -> f.isDirectory && f.name.startsWith("smali") }?.forEach { dir ->
            dir.walk().firstOrNull { it.isFile && it.name.endsWith(".smali") }?.setLastModified(later)
        }
    }

    private class ValidationException(problems: List<String>) : Exception(problems.joinToString("; "))

    private fun installDeviceFramework(logDir: File): CaseResult {
        val apk = File("/system/framework/framework-res.apk")
        val result = CaseResult(apk.path, "INSTALL_FRAMEWORK", "device")
        result.apkMb = apk.length() / 1048576.0
        val logFile = File(logDir, "install-framework.log")
        val start = System.currentTimeMillis()
        logFile.bufferedWriter().use { w ->
            try {
                ApktoolEngine.installFramework(context, apk) { level, message ->
                    synchronized(w) { w.write(ApktoolLogListener.format(level, message)); w.newLine() }
                }
                val installed = File(ApktoolEngine.frameworkDirectory(context), "1.apk")
                if (!installed.isFile) throw IllegalStateException("framework 1.apk not written")
                result.notes.add("1.apk ${installed.length() shr 10} KB")
                result.ok = true
            } catch (t: Throwable) {
                result.stage = "install"
                result.error = firstMeaningfulLine(t)
                w.write(Log.getStackTraceString(t))
            }
        }
        result.totalMs = System.currentTimeMillis() - start
        log(row(result))
        return result
    }

    // ---------------------------------------------------------------------------------------------
    // Validation

    private fun validate(
        original: File, rebuilt: File, mode: DecodeMode, buildOptions: BuildOptions, signed: Boolean,
        notes: MutableList<String>?,
    ): List<String> {
        val problems = ArrayList<String>()
        val pm = context.packageManager
        val origInfo = archiveInfo(pm, original, 0)
        val flags = if (signed) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val info = archiveInfo(pm, rebuilt, flags)
        if (info == null) {
            problems.add("PackageManager can't parse the rebuilt APK")
        } else if (origInfo != null) {
            val expectedPackage = arg("renameTo") ?: origInfo.packageName
            if (info.packageName != expectedPackage) {
                problems.add("package ${info.packageName} != $expectedPackage")
            }
            if (info.longVersionCode != origInfo.longVersionCode) {
                problems.add("versionCode ${info.longVersionCode} != ${origInfo.longVersionCode}")
            }
            if (info.versionName != origInfo.versionName) {
                problems.add("versionName ${info.versionName} != ${origInfo.versionName}")
            }
            val origLabel = label(pm, origInfo, original)
            val newLabel = label(pm, info, rebuilt)
            if (origLabel != newLabel) {
                problems.add("app label '$newLabel' != '$origLabel'")
            }
            val debuggable = (info.applicationInfo!!.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val wasDebuggable = (origInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (buildOptions.debuggable && !debuggable && mode.decodesManifest()) {
                problems.add("--debuggable: rebuilt app is not debuggable")
            } else if (!buildOptions.debuggable && debuggable != wasDebuggable) {
                problems.add("debuggable flag changed to $debuggable")
            }
            if (signed) {
                val signers = info.signingInfo?.apkContentsSigners
                if (signers.isNullOrEmpty()) problems.add("no signer found after signing")
            }
        }

        val origEntries = zipEntries(original)
        val newEntries = zipEntries(rebuilt)
        for (required in listOf("AndroidManifest.xml", "resources.arsc")) {
            if (required in origEntries && required !in newEntries) problems.add("$required missing")
        }
        val origDex = origEntries.keys.filter { DEX.matches(it) }.toSortedSet()
        val newDex = newEntries.keys.filter { DEX.matches(it) }.toSortedSet()
        if (origDex != newDex) problems.add("dex files $newDex != $origDex")
        // Files apktool copies as-is (assets, lib, kotlin/, root files...): all must survive.
        val missingRaw = origEntries.keys.filter {
            !it.endsWith("/") && !it.startsWith("META-INF/") && !it.startsWith("res/") &&
                it != "resources.arsc" && it != "AndroidManifest.xml" && !DEX.matches(it) &&
                (it != "stamp-cert-sha256" || buildOptions.copyOriginal) &&
                it !in newEntries && !it.startsWith("r/") && !it.startsWith("R/")
        }
        if (missingRaw.isNotEmpty()) {
            problems.add("${missingRaw.size} files lost, e.g. ${missingRaw.take(5)}")
        }
        val origRes = origEntries.keys.count { it.startsWith("res/") || it.startsWith("r/") || it.startsWith("R/") }
        val newRes = newEntries.keys.count { it.startsWith("res/") || it.startsWith("r/") || it.startsWith("R/") }
        if (!mode.decodesResources() && newRes < origRes) {
            problems.add("res files $newRes < $origRes with raw resources")
        }
        if (buildOptions.netSecConf && mode.decodesManifest() && newEntries.keys.none { it.contains("network_security_config") }) {
            problems.add("--net-sec-conf: no network_security_config in the APK")
        }
        val origLibs = origEntries.keys.count { it.startsWith("lib/") && it.endsWith(".so") }
        val newLibs = newEntries.keys.count { it.startsWith("lib/") && it.endsWith(".so") }
        notes?.add("res $origRes->$newRes, libs $origLibs->$newLibs, entries ${origEntries.size}->${newEntries.size}, ${rebuilt.length() shr 10} KB")

        if (signed) {
            val targetSdk = origInfo?.applicationInfo?.targetSdkVersion ?: 0
            val arsc = newEntries["resources.arsc"]
            if (arsc != null && targetSdk >= 30) {
                if (arsc.method != ZipEntry.STORED) problems.add("resources.arsc is compressed (targetSdk $targetSdk can't install)")
                else if (arsc.dataOffset % 4 != 0L) problems.add("resources.arsc not 4-byte aligned")
            }
            val misaligned = newEntries.filter { (name, e) ->
                name.startsWith("lib/") && name.endsWith(".so") && e.method == ZipEntry.STORED && e.dataOffset % 4096 != 0L
            }.keys
            if (misaligned.isNotEmpty()) problems.add("${misaligned.size} stored .so not page aligned, e.g. ${misaligned.first()}")
            val origStoredSo = origEntries.filter { (n, e) -> n.endsWith(".so") && e.method == ZipEntry.STORED }.keys
            val nowCompressed = origStoredSo.filter { newEntries[it]?.method == ZipEntry.DEFLATED }
            if (nowCompressed.isNotEmpty()) problems.add("${nowCompressed.size} .so stored in the original are compressed now")
        }
        return problems
    }

    private fun DecodeMode.decodesResources() = this == DecodeMode.ALL || this == DecodeMode.RESOURCES_ONLY
    private fun DecodeMode.decodesManifest() = decodesResources()

    private fun archiveInfo(pm: PackageManager, apk: File, flags: Int): PackageInfo? =
        pm.getPackageArchiveInfo(apk.path, flags)?.also {
            it.applicationInfo?.sourceDir = apk.path
            it.applicationInfo?.publicSourceDir = apk.path
        }

    private fun label(pm: PackageManager, info: PackageInfo, apk: File): String? = try {
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = apk.path
        appInfo.publicSourceDir = apk.path
        appInfo.loadLabel(pm).toString()
    } catch (e: Exception) {
        "<${e.javaClass.simpleName}>"
    }

    private class Entry(val method: Int, val dataOffset: Long)

    /** Central directory of a zip with each entry's method and data offset (no zip64). */
    private fun zipEntries(file: File): Map<String, Entry> {
        val map = LinkedHashMap<String, Entry>()
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            val tailLen = minOf(len, 65557L).toInt()
            val tail = ByteArray(tailLen)
            raf.seek(len - tailLen)
            raf.readFully(tail)
            var eocd = -1
            for (i in tailLen - 22 downTo 0) {
                if (le32(tail, i) == 0x06054b50L) { eocd = i; break }
            }
            if (eocd < 0) error("no zip end record in $file")
            val count = le16(tail, eocd + 10)
            val cdSize = le32(tail, eocd + 12)
            val cdOffset = le32(tail, eocd + 16)
            val cd = ByteArray(cdSize.toInt())
            raf.seek(cdOffset)
            raf.readFully(cd)
            var p = 0
            val local = ByteArray(30)
            repeat(count) {
                check(le32(cd, p) == 0x02014b50L) { "bad central directory in $file" }
                val method = le16(cd, p + 10)
                val nameLen = le16(cd, p + 28)
                val extraLen = le16(cd, p + 30)
                val commentLen = le16(cd, p + 32)
                val lho = le32(cd, p + 42)
                val name = String(cd, p + 46, nameLen, Charsets.UTF_8)
                raf.seek(lho)
                raf.readFully(local)
                val dataOffset = lho + 30 + le16(local, 26) + le16(local, 28)
                map[name] = Entry(method, dataOffset)
                p += 46 + nameLen + extraLen + commentLen
            }
        }
        // Sanity check with java.util.zip: the rebuilt apk must be readable by it too.
        ZipFile(file).use { zip -> check(zip.size() == map.size) { "zip entry count mismatch in $file" } }
        return map
    }

    private fun le16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
    private fun le32(b: ByteArray, o: Int): Long = (le16(b, o).toLong()) or (le16(b, o + 2).toLong() shl 16)

    // ---------------------------------------------------------------------------------------------
    // Targets, report, helpers

    private fun resolve(spec: String): Target? {
        if (spec.isEmpty()) return null
        if (spec.startsWith("/")) {
            val f = File(spec)
            if (!f.canRead()) { log("SKIP $spec: not readable"); return null }
            return Target(if (f.name == "base.apk") f.parentFile!!.name.substringBefore('-') else f.name, f)
        }
        val out = shell("pm path $spec")
        val path = out.lines().map { it.removePrefix("package:").trim() }
            .firstOrNull { it.endsWith("/base.apk") } ?: out.lines().firstOrNull()?.removePrefix("package:")?.trim()
        if (path.isNullOrEmpty() || !File(path).canRead()) {
            log("SKIP $spec: not installed or not readable ($out)")
            return null
        }
        return Target(spec, File(path))
    }

    private fun shell(cmd: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }.trim()
    }

    private fun row(r: CaseResult): String = listOf(
        r.target, r.mode, r.framework, if (r.ok) "PASS" else "FAIL", r.stage,
        "%.1f".format(Locale.US, r.apkMb), sec(r.decodeMs), sec(r.buildMs), sec(r.signMs), sec(r.totalMs),
        r.peakHeapMb.toString(), r.error.replace('|', '/').replace('\t', ' '), r.notes.joinToString("; "),
    ).joinToString(" | ", "| ", " |")

    private fun sec(ms: Long) = if (ms == 0L) "-" else "%.1f".format(Locale.US, ms / 1000.0)

    private fun writeReport(root: File, tag: String, results: List<CaseResult>) {
        val header = "| APK | mode | framework | result | failed at | MB | decode s | build s | sign s | total s | peak heap MB | error | notes |\n" +
            "|---|---|---|---|---|---|---|---|---|---|---|---|---|"
        val md = buildString {
            append("# apktool round trip ").append(tag).append('\n')
            append("apktool ${ApktoolEngine.version}, ${Build.MODEL}, API ${Build.VERSION.SDK_INT}, ")
            append("${Runtime.getRuntime().availableProcessors()} cpus, max heap ${Runtime.getRuntime().maxMemory() shr 20} MB, ")
            append("args ").append(args.keySet().filter { it != "class" }.joinToString { "$it=${args.getString(it)}" }).append("\n\n")
            append(header).append('\n')
            results.forEach { append(row(it)).append('\n') }
            append("\n").append(results.count { it.ok }).append('/').append(results.size).append(" passed\n")
        }
        File(root, "results-$tag.md").writeText(md)
        File(root, "results-latest.md").writeText(md)
        File(root, "results-$tag.tsv").writeText(
            results.joinToString("\n") { r ->
                listOf(r.target, r.mode, r.framework, if (r.ok) "PASS" else "FAIL", r.stage, r.decodeMs, r.buildMs,
                    r.signMs, r.totalMs, r.peakHeapMb, r.error, r.notes.joinToString("; ")).joinToString("\t")
            }
        )
        md.lines().forEach { log(it) }
    }

    private fun firstMeaningfulLine(t: Throwable): String {
        val msg = (if (t is ApktoolException || t is ValidationException) t.message else ApktoolEngine.describe(t))
            ?: t.javaClass.name
        val lines = msg.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val head = lines.firstOrNull() ?: t.javaClass.name
        val detail = lines.drop(1).firstOrNull { it.contains("error", true) }
        return (if (detail != null) "$head // $detail" else head).take(400)
    }

    private fun sanitize(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun log(msg: String) {
        Log.i(TAG, msg)
    }

    /** Samples the Java heap use of the process while a case runs. */
    private class HeapSampler : Thread("heap-sampler") {
        @Volatile private var running = true
        @Volatile private var peak = 0L

        override fun run() {
            val rt = Runtime.getRuntime()
            while (running) {
                peak = maxOf(peak, rt.totalMemory() - rt.freeMemory())
                try { sleep(100) } catch (e: InterruptedException) { return }
            }
        }

        fun stopAndGetPeakMb(): Long {
            running = false
            interrupt()
            join(1000)
            return peak shr 20
        }
    }

    companion object {
        private const val TAG = "RoundTrip"
        private val DEX = Regex("classes\\d*\\.dex")
        private val SYSTEM_PREFIXES = listOf("/system/", "/system_ext/", "/product/", "/vendor/", "/apex/")

        /** Diverse installed apps on the test phone, smallest first; missing ones are skipped. */
        val DEFAULT_TARGETS = listOf(
            "gr.nikolasspyr.integritycheck",
            "com.google.android.apps.kids.familylinkhelper",
            "com.google.android.contactkeys",
            "com.google.android.safetycore",
            "com.google.android.calculator",
            "moe.zhs.caffeine",
            "com.rifsxd.ksunext",
            "com.google.android.deskclock",
            "com.riyadm.socksdroid.debug",
            "com.google.android.apps.subscriptions.red",
            "com.google.android.apps.magazines",
            "com.google.android.keep",
            // system apps (run against the device framework)
            "com.google.android.apps.nexuslauncher",
            "com.android.systemui",
            "com.android.settings",
            "/system/framework/framework-res.apk",
        )

        /** Also run in RESOURCES_ONLY, SOURCES_ONLY and NONE mode. */
        val DEFAULT_ALL_MODES_FOR = setOf(
            "gr.nikolasspyr.integritycheck", "com.google.android.calculator", "com.rifsxd.ksunext",
            "com.android.systemui",
        )
    }
}
