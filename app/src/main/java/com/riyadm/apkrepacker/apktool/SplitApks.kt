package com.riyadm.apkrepacker.apktool

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.androlib.meta.ApkInfo
import brut.xml.XmlUtils
import org.apache.commons.io.FileUtils
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.StringWriter
import java.util.logging.Level
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Split APK support for apps installed from app bundles (base.apk + split_config.<abi|density|lang>.apk).
 *
 * - [mergeConfigSplits] (decode): finds the installed splits of the decoded base.apk and merges
 *   their native libraries and resources into the project.
 * - [removeSplitRequirement] (build): drops the manifest attributes / meta-data that make Android
 *   refuse to install the rebuilt base alone (INSTALL_FAILED_MISSING_SPLIT).
 */
object SplitApks {

    /** <manifest>/<application> attributes that tie the base to its splits. */
    private val SPLIT_ATTRIBUTES = listOf("android:isSplitRequired", "android:requiredSplitTypes", "android:splitTypes")

    /** <application> meta-data written by Play / bundletool about splits and the source stamp. */
    private val SPLIT_META_DATA = setOf(
        "com.android.vending.splits.required",
        "com.android.vending.splits",
        "com.android.stamp.source",
        "com.android.stamp.type",
        "com.android.dynamic.apk.fused.modules",
    )

    private val CONFIG_SPLIT_FILE = Regex("""split_config\..+\.apk""")

    /** What [mergeConfigSplits] did, for the decode log. */
    class MergeResult {
        val splits = ArrayList<String>()
        var libs = 0
        var storedLibs = 0
        val resourceSplits = ArrayList<String>()
        var resourceFiles = 0
        var valueEntries = 0
        val skipped = ArrayList<String>()
    }

    // ---------------------------------------------------------------------------------------------
    // Decode: merge config splits

    /**
     * The installed split APKs belonging to [apk]: the app whose sourceDir is [apk], or, for a copy
     * of a base.apk, the installed app with the same package name and version code. Falls back to
     * `split_config.*.apk` files next to a file named base.apk (an extracted .apks/.xapk).
     */
    @JvmStatic
    fun findSplits(context: Context, apk: File, log: ApktoolLogListener?): List<File> {
        val pm = context.packageManager
        val path = canonical(apk)
        try {
            val archive = pm.getPackageArchiveInfo(apk.path, 0)
            if (archive != null) {
                val installed = try {
                    pm.getPackageInfo(archive.packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
                val app = installed?.applicationInfo
                val dirs = app?.splitSourceDirs
                if (app != null && !dirs.isNullOrEmpty()) {
                    val samePath = listOfNotNull(app.sourceDir, app.publicSourceDir).any { canonical(File(it)) == path }
                    val sameVersion = versionCode(installed) == versionCode(archive)
                    if (samePath || sameVersion) {
                        if (!samePath) {
                            log?.onLog(Level.INFO, "Using the splits of the installed ${archive.packageName} (same version code)")
                        }
                        return dirs.map { File(it) }.filter { it.isFile && it.canRead() }
                    }
                    log?.onLog(
                        Level.INFO,
                        "${archive.packageName} is installed with splits, but in another version: splits not merged"
                    )
                }
            }
        } catch (e: Exception) {
            log?.onLog(Level.FINE, "Split lookup failed: ${ApktoolEngine.describe(e)}")
        }
        if (apk.name == "base.apk") {
            apk.absoluteFile.parentFile?.listFiles { f -> f.isFile && CONFIG_SPLIT_FILE.matches(f.name) }
                ?.sortedBy { it.name }
                ?.let { if (it.isNotEmpty()) return it }
        }
        return emptyList()
    }

    /**
     * Config splits of [baseApk], prepared before the base is decoded. The resources of the
     * density/language splits are decoded first (into a work folder), with the base loaded as a
     * library for their package: a config split's table only has the entries of its configuration
     * and refers to the base's resources by ID. The splits that decoded are then given to the base
     * decode the same way ([libraryFiles]), so base XMLs that refer to split-only resources (e.g. a
     * selector using drawables that only exist in drawable-xxhdpi) get real names instead of @null.
     * Call [finish] after decoding the base, and [close] in any case.
     */
    class Prepared internal constructor(
        val splits: List<File>,
        internal val work: File,
        internal val decodedRes: List<Pair<String, File>>,
        /** For Config.libraryFiles of the base decode: resource package name -> decoded split APKs. */
        val libraryFiles: Map<String, Array<String>>,
        internal val result: MergeResult,
    ) : java.io.Closeable {
        override fun close() {
            FileUtils.deleteQuietly(work)
        }
    }

    /**
     * Finds and prepares the config splits of [baseApk] (see [Prepared]); null when it has none.
     * Feature splits (split_<module>.apk, with code) are not merged.
     *
     * @param decodeResources false when the project keeps the raw resources.arsc (resources of
     *   the splits can't be merged then, only native libraries).
     */
    @JvmStatic
    fun prepare(
        context: Context, baseApk: File, decodeResources: Boolean, newConfig: () -> Config, log: ApktoolLogListener?,
    ): Prepared? {
        val splits = findSplits(context, baseApk, log)
        if (splits.isEmpty()) return null
        val result = MergeResult()
        val configSplits = splits.filter { CONFIG_SPLIT_FILE.matches(it.name) || !it.name.startsWith("split_") }
        splits.filter { it !in configSplits }.forEach {
            result.skipped.add(it.name)
            log?.onLog(Level.WARNING, "Feature split ${it.name} not merged (dynamic feature modules aren't supported)")
        }
        log?.onLog(Level.INFO, "Config splits to merge: ${configSplits.joinToString { it.name }.ifEmpty { "none" }}")
        val work = File(context.cacheDir, "split-merge").apply { FileUtils.deleteQuietly(this); mkdirs() }
        val decodedRes = ArrayList<Pair<String, File>>()
        val libraryFiles = LinkedHashMap<String, MutableList<String>>()
        val manifestPackage = try {
            context.packageManager.getPackageArchiveInfo(baseApk.path, 0)?.packageName
        } catch (e: Exception) {
            null
        }
        for (split in configSplits) {
            result.splits.add(split.name)
            val packageName = try {
                ZipFile(split).use { zip -> zip.getEntry("resources.arsc")?.let { arscPackageName(zip, it) ?: manifestPackage ?: "" } }
            } catch (e: Exception) {
                log?.onLog(Level.WARNING, "Can't read ${split.name}: ${ApktoolEngine.describe(e)}")
                result.skipped.add(split.name)
                continue
            } ?: continue // no resources (ABI split)
            if (!decodeResources) {
                log?.onLog(Level.WARNING, "${split.name}: resources not merged (this decode mode keeps the raw resources.arsc)")
                continue
            }
            val out = File(work, split.nameWithoutExtension)
            try {
                val config = newConfig()
                config.setDecodeSources(Config.DecodeSources.NONE)
                config.setDecodeResources(Config.DecodeResources.FULL)
                config.setDecodeAssets(Config.DecodeAssets.NONE)
                config.isForced = true
                config.jobs = 1
                for (name in setOfNotNull(packageName.ifEmpty { null }, manifestPackage)) {
                    config.libraryFiles[name] = arrayOf(baseApk.absolutePath)
                }
                log?.onLog(Level.INFO, "Decoding the resources of ${split.name}...")
                ApkDecoder(split, config).decode(out)
                val res = File(out, "res")
                if (res.isDirectory) {
                    decodedRes.add(split.name to res)
                    for (name in setOfNotNull(packageName.ifEmpty { null }, manifestPackage)) {
                        libraryFiles.getOrPut(name) { ArrayList() }.add(split.absolutePath)
                    }
                }
            } catch (e: Exception) {
                log?.onLog(Level.WARNING, "${split.name}: resources not merged: ${ApktoolEngine.describe(e)}")
                result.skipped.add(split.name)
                FileUtils.deleteQuietly(out)
            }
        }
        return Prepared(configSplits, work, decodedRes, libraryFiles.mapValues { it.value.toTypedArray() }, result)
    }

    /**
     * Merges the prepared splits into the freshly decoded [projectDir]:
     * - lib/<abi>/... of every split (all ABIs present) into the project's lib/; stored libraries
     *   are recorded in apktool.yml doNotCompress ("so") so the rebuilt APK keeps them stored (the
     *   signer page-aligns them), which android:extractNativeLibs="false" requires;
     * - the decoded resources into res/: new files are copied, value XMLs that already exist get
     *   the missing entries appended, public.xml gets the split's IDs.
     */
    @JvmStatic
    fun finish(prepared: Prepared, projectDir: File, log: ApktoolLogListener?): MergeResult {
        val result = prepared.result
        for (split in prepared.splits) {
            try {
                ZipFile(split).use { zip -> copyLibs(zip, projectDir, result) }
            } catch (e: Exception) {
                log?.onLog(Level.WARNING, "Native libraries of ${split.name} not merged: ${ApktoolEngine.describe(e)}")
                if (split.name !in result.skipped) result.skipped.add(split.name)
            }
        }
        if (result.storedLibs > 0 || prepared.libraryFiles.isNotEmpty()) {
            val info = ApkInfo.load(projectDir)
            var changed = false
            if (result.storedLibs > 0 && "so" !in info.doNotCompress) {
                info.doNotCompress.add("so")
                changed = true
            }
            // The splits were only loaded to resolve names; the base decode recorded its own
            // package as a used library, which the build would look for ("Shared library was not
            // provided"). Their resources are now in res/.
            if (info.usesLibrary.removeAll(prepared.libraryFiles.keys)) changed = true
            if (changed) info.save(projectDir)
        }
        if (prepared.decodedRes.isNotEmpty()) {
            val projectRes = File(projectDir, "res")
            // Files and values first, public.xml last: a <public> without its resource fails aapt2.
            for ((name, res) in prepared.decodedRes) {
                mergeResDir(res, projectRes, result, publicOnly = false)
                result.resourceSplits.add(name)
            }
            for ((_, res) in prepared.decodedRes) mergeResDir(res, projectRes, result, publicOnly = true)
        }
        log?.onLog(Level.INFO, describe(result))
        return result
    }

    @JvmStatic
    fun describe(r: MergeResult): String = buildString {
        append("Splits merged: ")
        append(r.libs).append(" native lib(s)")
        if (r.storedLibs > 0) append(" (").append(r.storedLibs).append(" stored uncompressed, kept stored)")
        append(", resources of ").append(r.resourceSplits.size).append(" split(s): ")
        append(r.resourceFiles).append(" file(s), ").append(r.valueEntries).append(" value(s)")
        if (r.skipped.isNotEmpty()) append("; not merged: ").append(r.skipped.joinToString())
    }

    /**
     * Name of the first package in a resources.arsc (ResTable header, optional global string
     * pool, then ResTable_package: id, then the name as 128 UTF-16 chars).
     */
    private fun arscPackageName(zip: ZipFile, entry: ZipEntry): String? = try {
        readArscPackageName(zip, entry)
    } catch (e: java.io.IOException) {
        null
    }

    private fun readArscPackageName(zip: ZipFile, entry: ZipEntry): String? {
        zip.getInputStream(entry).use { raw ->
            val input = java.io.DataInputStream(java.io.BufferedInputStream(raw))
            fun u16(): Int = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
            fun u32(): Long = (u16().toLong()) or (u16().toLong() shl 16)
            if (u16() != 0x0002) return null // RES_TABLE_TYPE
            val headerSize = u16()
            u32() // size
            input.skipBytes(headerSize - 8)
            while (true) {
                val type = u16()
                u16() // header size
                val size = u32()
                if (type == 0x0200) { // RES_TABLE_PACKAGE_TYPE
                    u32() // id
                    val name = StringBuilder()
                    for (i in 0 until 128) {
                        val c = u16()
                        if (c == 0) break
                        name.append(c.toChar())
                    }
                    return name.toString().ifEmpty { null }
                }
                var toSkip = size - 8
                while (toSkip > 0) {
                    val n = input.skip(toSkip)
                    if (n <= 0) return null
                    toSkip -= n
                }
            }
        }
    }

    private fun copyLibs(zip: ZipFile, projectDir: File, result: MergeResult) {
        val libRoot = File(projectDir, "lib")
        val rootPath = canonical(libRoot) + File.separator
        for (entry in zip.entries().asSequence()) {
            if (entry.isDirectory || !entry.name.startsWith("lib/")) continue
            val target = File(projectDir, entry.name)
            if (!canonical(target).startsWith(rootPath)) continue // "../" in an entry name
            if (target.exists()) continue
            target.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            result.libs++
            if (entry.method == ZipEntry.STORED && entry.name.endsWith(".so")) result.storedLibs++
        }
    }

    private fun mergeResDir(splitRes: File, projectRes: File, result: MergeResult, publicOnly: Boolean) {
        val dirs = splitRes.listFiles { f -> f.isDirectory } ?: return
        for (dir in dirs) {
            val targetDir = File(projectRes, dir.name)
            for (file in dir.listFiles { f -> f.isFile } ?: emptyArray()) {
                val isPublic = dir.name == "values" && file.name == "public.xml"
                if (isPublic != publicOnly) continue
                val target = File(targetDir, file.name)
                if (!target.exists()) {
                    targetDir.mkdirs()
                    file.copyTo(target)
                    if (dir.name.startsWith("values")) result.valueEntries += countEntries(file) else result.resourceFiles++
                } else if (dir.name.startsWith("values") && file.name.endsWith(".xml")) {
                    result.valueEntries += appendMissingValues(file, target)
                }
                // An existing file resource (same name and qualifiers) stays as decoded from the base.
            }
        }
    }

    private fun countEntries(valuesXml: File): Int = try {
        topLevelElements(XmlUtils.loadDocument(valuesXml).documentElement).size
    } catch (e: Exception) {
        0
    }

    private fun topLevelElements(root: Element): List<Element> {
        val list = ArrayList<Element>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) list.add(n as Element)
        }
        return list
    }

    private fun key(e: Element): String = e.tagName + "|" + e.getAttribute("type") + "|" + e.getAttribute("name")

    /**
     * Appends the entries of [src] missing from [dst] (both apktool values XMLs) as text before
     * `</resources>`, so the existing file is kept byte for byte. @return entries added.
     */
    private fun appendMissingValues(src: File, dst: File): Int {
        val existing = topLevelElements(XmlUtils.loadDocument(dst).documentElement).map { key(it) }.toHashSet()
        val missing = topLevelElements(XmlUtils.loadDocument(src).documentElement).filter { key(it) !in existing }
        if (missing.isEmpty()) return 0
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }
        val added = StringBuilder()
        for (e in missing) {
            val w = StringWriter()
            transformer.transform(DOMSource(e), StreamResult(w))
            added.append("    ").append(w.toString().trim()).append('\n')
        }
        val text = dst.readText()
        val end = text.lastIndexOf("</resources>")
        if (end < 0) return 0
        dst.writeText(text.substring(0, end) + added + text.substring(end))
        return missing.size
    }

    // ---------------------------------------------------------------------------------------------
    // Build: remove the split requirement

    /**
     * Removes android:isSplitRequired / requiredSplitTypes / splitTypes from <manifest> and
     * <application>, and the split / source stamp meta-data, from the decoded AndroidManifest.xml
     * of [projectDir]. The file is only rewritten when something was removed.
     * @return what was removed (empty when nothing).
     */
    @JvmStatic
    fun removeSplitRequirement(projectDir: File, log: ApktoolLogListener?): List<String> {
        val manifest = File(projectDir, "AndroidManifest.xml")
        if (!manifest.isFile) return emptyList()
        if (isBinaryXml(manifest)) {
            // Attribute names are in the binary XML's UTF-16 string pool.
            val strings = String(manifest.readBytes(), Charsets.UTF_16LE)
            if (strings.contains("isSplitRequired") || strings.contains("requiredSplitTypes")) {
                log?.onLog(
                    Level.WARNING,
                    "The manifest isn't decoded (binary), so the split requirement can't be removed: the APK " +
                        "will only install together with its splits. Decompile with resources to fix this."
                )
            }
            return emptyList()
        }
        val doc = XmlUtils.loadDocument(manifest)
        val root = doc.documentElement ?: return emptyList()
        val removed = ArrayList<String>()
        val targets = ArrayList<Element>()
        targets.add(root)
        val apps = root.getElementsByTagName("application")
        for (i in 0 until apps.length) targets.add(apps.item(i) as Element)
        for (element in targets) {
            for (attr in SPLIT_ATTRIBUTES) {
                if (element.hasAttribute(attr)) {
                    removed.add("${element.tagName} $attr=\"${element.getAttribute(attr)}\"")
                    element.removeAttribute(attr)
                }
            }
        }
        for (i in 0 until apps.length) {
            val app = apps.item(i) as Element
            for (meta in topLevelElements(app).filter { it.tagName == "meta-data" }) {
                val name = meta.getAttribute("android:name")
                if (name in SPLIT_META_DATA) {
                    removed.add("meta-data $name")
                    app.removeChild(meta)
                }
            }
        }
        if (removed.isEmpty()) return removed
        XmlUtils.saveDocument(doc, manifest)
        log?.onLog(Level.INFO, "Removed the split requirement from AndroidManifest.xml: ${removed.joinToString()}")
        return removed
    }

    private fun isBinaryXml(file: File): Boolean = try {
        file.inputStream().use { val b = ByteArray(2); it.read(b) == 2 && b[0].toInt() == 0x03 && b[1].toInt() == 0x00 }
    } catch (e: Exception) {
        false
    }

    private fun canonical(f: File): String = try {
        f.canonicalPath
    } catch (e: Exception) {
        f.absolutePath
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
}
