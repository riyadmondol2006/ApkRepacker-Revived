package com.riyadm.apkrepacker.project

import androidx.annotation.StringRes
import brut.androlib.meta.ApkInfo
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ProjectMeta
import com.riyadm.apkrepacker.utils.SafeZip
import com.riyadm.apkrepacker.utils.WorkerThreads
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A failure the user should be told about; [resId] is the (localized) message. */
class TransferException(@StringRes val resId: Int, vararg val args: Any?) : IOException("transfer failed: $resId")

/**
 * Saves a project as a ZIP or into a folder, and loads projects back from either. Streams
 * everything (projects can be hundreds of MB), never touches the UI, and reports progress through
 * [Progress]. Runs inside [com.riyadm.apkrepacker.service.ProjectTransferService].
 *
 * A project is a folder that has `apktool.json` (the app's own metadata) or `apktool.yml`
 * (apktool's); the latter alone is enough to load a project made with desktop apktool.
 */
object ProjectTransfer {

    /** [total] is 0 while unknown. */
    fun interface Progress {
        fun onProgress(done: Int, total: Int, name: String)
    }

    /** Stops a ZIP that expands to something absurd from filling the phone. */
    private const val MAX_IMPORT_BYTES = 8L * 1024 * 1024 * 1024

    /** apktool's incremental-build cache; large, and rebuilt on demand, so it isn't exported. */
    private val EXPORT_SKIP_TOP_LEVEL = setOf("build")

    /** How deep below the picked folder / ZIP root to look for projects. */
    private const val MAX_SEARCH_DEPTH = 3

    /** An apktool.json bigger than this is treated as broken and replaced by minimal metadata. */
    private const val MAX_META_BYTES = 512L * 1024

    /** Import stages older than this are leftovers of a killed or timed-out import. */
    private const val STALE_STAGE_MS = 30L * 60 * 1000

    private const val STAGE_PREFIX = "tmp_import_"

    /** Stages of imports running in this process; the sweeper must not touch them. */
    private val activeStages: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // region export

    /** Writes [project] as a ZIP to [out] (closed afterwards). Files sit at the ZIP's root. */
    @Throws(IOException::class)
    fun exportZip(project: File, out: OutputStream, progress: Progress?) {
        val total = countFiles(project, topLevel = true, depth = 0)
        if (total == 0) {
            runCatching { out.close() }
            throw TransferException(R.string.transfer_error_empty_project)
        }
        var done = 0
        ZipOutputStream(BufferedOutputStream(out, 64 * 1024)).use { zip ->
            fun add(dir: File, prefix: String, topLevel: Boolean, depth: Int) {
                if (depth > MAX_TREE_DEPTH) return
                for (child in dir.listFiles().orEmpty().sortedBy { it.name }) {
                    if (isSymlink(child)) continue
                    if (topLevel && child.name in EXPORT_SKIP_TOP_LEVEL && child.isDirectory) continue
                    val entryName = prefix + child.name
                    if (child.isDirectory) {
                        zip.putNextEntry(ZipEntry("$entryName/").apply { time = child.lastModified() })
                        zip.closeEntry()
                        add(child, "$entryName/", topLevel = false, depth = depth + 1)
                    } else {
                        zip.putNextEntry(ZipEntry(entryName).apply { time = child.lastModified() })
                        child.inputStream().use { it.copyTo(zip, 64 * 1024) }
                        zip.closeEntry()
                        progress?.onProgress(++done, total, child.name)
                    }
                }
            }
            add(project, "", topLevel = true, depth = 0)
        }
    }

    /**
     * Copies [project] into a new folder (named like the project, made unique with " (2)"... if
     * taken, so an earlier export is never merged into or overwritten) inside [destination].
     */
    @Throws(IOException::class)
    internal fun exportFolder(project: File, destination: Sink, progress: Progress?): String {
        if (countFiles(project, topLevel = true, depth = 0) == 0) {
            throw TransferException(R.string.transfer_error_empty_project)
        }
        if (destination is Sink.FileSink) {
            // Writing straight into (or under) the project would truncate its own files before
            // they are read, or keep re-listing the fresh copy.
            val projectPath = project.canonicalFile.path
            val destPath = destination.dir.canonicalFile.path
            if (destPath == projectPath || destPath.startsWith(projectPath + File.separator)) {
                throw TransferException(R.string.transfer_error_destination_in_project)
            }
        }
        // The new folder is always a fresh one, so even when the project sits inside the
        // destination it can never be the folder written to.
        val target = destination.newDir(safeName(project.name))
        copyTree(Node.FileNode(project), target, EXPORT_SKIP_TOP_LEVEL, progress)
        return target.folderName
    }

    /**
     * Copies the folder [from] into [into]: all sub-folders are created first, then the files are
     * copied by several threads. A project is thousands of small files and creating each one on
     * shared storage is latency-bound (a single thread managed ~50 files/s), so running them
     * side by side is several times faster.
     */
    @Throws(IOException::class)
    private fun copyTree(from: Node, into: Sink, skipTopLevel: Set<String>, progress: Progress?) {
        val jobs = ArrayList<Pair<Node, Sink>>()
        fun walk(dir: Node, sink: Sink, topLevel: Boolean, depth: Int) {
            if (depth > MAX_TREE_DEPTH) return
            for (child in dir.list()) {
                if (topLevel && child.isDirectory && child.name in skipTopLevel) continue
                if (child.isDirectory) walk(child, sink.dir(child.name), topLevel = false, depth = depth + 1) else jobs += child to sink
            }
        }
        walk(from, into, topLevel = true, depth = 0)
        if (jobs.isEmpty()) return
        // Creating a file holds its folder's lock for the whole round trip, and a project keeps
        // thousands of files in a few big folders. In folder order the threads would all queue on
        // the same folder; mixing the order spreads them over different ones.
        jobs.shuffle(java.util.Random(42))

        val pool = Executors.newFixedThreadPool(WorkerThreads.count())
        val done = AtomicInteger(0)
        // Set by the first failing task: the queued ones then skip their work instead of
        // trying thousands more files after, say, a disk-full error.
        val failed = AtomicBoolean(false)
        val firstError = AtomicReference<Throwable?>(null)
        try {
            val futures = jobs.map { (node, sink) ->
                pool.submit(Runnable {
                    if (failed.get()) return@Runnable
                    try {
                        sink.file(node.name).use { out -> node.open().use { it.copyTo(out, 64 * 1024) } }
                    } catch (t: Throwable) {
                        if (failed.compareAndSet(false, true)) firstError.set(t)
                        throw t
                    }
                    progress?.onProgress(done.incrementAndGet(), jobs.size, node.name)
                })
            }
            for (future in futures) {
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    failed.set(true)
                    val cause = firstError.get() ?: e.cause
                    throw (cause as? IOException) ?: IOException(cause)
                }
            }
        } finally {
            failed.set(true)
            pool.shutdownNow()
            // Wait for the workers so the caller's cleanup never races with a file still being written.
            try {
                pool.awaitTermination(30, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun countFiles(dir: File, topLevel: Boolean, depth: Int): Int {
        if (depth > MAX_TREE_DEPTH) return 0
        var count = 0
        for (child in dir.listFiles().orEmpty()) {
            if (isSymlink(child)) continue
            if (topLevel && child.name in EXPORT_SKIP_TOP_LEVEL && child.isDirectory) continue
            count += if (child.isDirectory) countFiles(child, topLevel = false, depth = depth + 1) else 1
        }
        return count
    }

    // endregion

    // region import

    /**
     * Loads the project(s) in a ZIP into [projectsDir]. The ZIP may hold one project at its root,
     * inside a single folder, or several project folders. Returns the new project folders.
     */
    @Throws(IOException::class)
    fun importZip(input: InputStream, suggestedName: String, projectsDir: File, progress: Progress?): List<File> {
        val stage = newStage(projectsDir)
        try {
            var extracted = 0L
            var files = 0
            ZipInputStream(input.buffered(64 * 1024)).use { zip ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    // Entry names are untrusted ("../x"): never write outside the stage folder.
                    val target = SafeZip.resolve(stage, entry.name) ?: continue
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            extracted += read
                            if (extracted > MAX_IMPORT_BYTES) throw TransferException(R.string.transfer_error_too_big)
                            out.write(buffer, 0, read)
                        }
                    }
                    progress?.onProgress(++files, 0, target.name)
                }
            }
            val roots = findProjects(Node.FileNode(stage))
            if (roots.isEmpty()) throw TransferException(R.string.transfer_error_no_project)
            return roots.map { (node, isStageRoot) ->
                val dir = (node as Node.FileNode).file
                val name = if (isStageRoot) safeName(suggestedName.removeSuffix(".zip")) else dir.name
                moveInto(dir, projectsDir, name)
            }
        } finally {
            discardStage(stage)
        }
    }

    /**
     * Loads the project(s) found in [source] (a project folder, or a folder of projects) into
     * [projectsDir]. Returns the new project folders.
     */
    @Throws(IOException::class)
    internal fun importFolder(source: Node, projectsDir: File, progress: Progress?): List<File> {
        val roots = findProjects(source)
        if (roots.isEmpty()) throw TransferException(R.string.transfer_error_no_project)
        val imported = ArrayList<File>()
        for ((node, _) in roots) {
            val stage = newStage(projectsDir)
            try {
                copyTree(node, Sink.FileSink(stage), emptySet(), progress)
                imported += moveInto(stage, projectsDir, safeName(node.name.ifEmpty { "project" }))
            } finally {
                discardStage(stage)
            }
        }
        return imported
    }

    private fun isProject(node: Node): Boolean {
        val names = node.list().filter { !it.isDirectory }.map { it.name }
        return ProjectMeta.FILE_NAME in names || "apktool.yml" in names
    }

    /**
     * Project folders at or below [root] (not looking inside a project it found). The Boolean is
     * true for [root] itself, whose folder name is meaningless (a stage folder or a picked tree).
     */
    private fun findProjects(root: Node): List<Pair<Node, Boolean>> {
        val found = ArrayList<Pair<Node, Boolean>>()
        fun search(node: Node, depth: Int, isRoot: Boolean) {
            if (isProject(node)) {
                found += node to isRoot
                return
            }
            if (depth >= MAX_SEARCH_DEPTH) return
            for (child in node.list()) {
                if (child.isDirectory) search(child, depth + 1, isRoot = false)
            }
        }
        search(root, 0, isRoot = true)
        return found
    }

    private fun newStage(projectsDir: File): File {
        // Beside (not inside) the projects folder: the project list scans that one, and a rename
        // between the two stays on one file system.
        sweepStaleStages(projectsDir)
        val parent = projectsDir.parentFile ?: projectsDir
        return File(parent, "$STAGE_PREFIX${System.nanoTime()}").also {
            if (!it.mkdirs()) throw IOException("Cannot create $it")
            activeStages += it.name
        }
    }

    private fun discardStage(stage: File) {
        try {
            stage.deleteRecursively()
        } finally {
            activeStages -= stage.name
        }
    }

    /**
     * Deletes `tmp_import_*` folders next to [projectsDir] that are older than 30 minutes: what a
     * killed or timed-out import left behind. Stages of imports running now are kept.
     */
    @JvmStatic
    fun sweepStaleStages(projectsDir: File) {
        try {
            val parent = projectsDir.parentFile ?: return
            val now = System.currentTimeMillis()
            for (dir in parent.listFiles().orEmpty()) {
                if (!dir.name.startsWith(STAGE_PREFIX) || dir.name in activeStages) continue
                if (isSymlink(dir) || !dir.isDirectory) continue
                if (now - dir.lastModified() > STALE_STAGE_MS) dir.deleteRecursively()
            }
        } catch (e: Exception) {
            android.util.Log.w("ProjectTransfer", "Could not sweep stale import stages", e)
        }
    }

    /** Moves [from] to a free `projectsDir/name`, completes its metadata and returns it. */
    private fun moveInto(from: File, projectsDir: File, name: String): File {
        projectsDir.mkdirs()
        val target = uniqueDir(projectsDir, name)
        if (!from.renameTo(target)) {
            from.copyRecursively(target, overwrite = false) { _, e -> throw e }
            from.deleteRecursively()
        }
        ensureMeta(target)
        return target
    }

    private fun uniqueDir(parent: File, name: String): File {
        var candidate = File(parent, name)
        var n = 2
        while (candidate.exists()) candidate = File(parent, "$name ($n)").also { n++ }
        return candidate
    }

    /**
     * The project list reads `apktool.json`. A project from desktop apktool only has
     * `apktool.yml`, so write a minimal `apktool.json` from it (package from the decoded manifest).
     */
    private fun ensureMeta(project: File) {
        val meta = ProjectMeta.file(project)
        // A hostile or damaged apktool.json (huge, or not JSON) would break the project list.
        if (meta.exists()) {
            if (meta.isFile && meta.length() <= MAX_META_BYTES && ProjectMeta.readFile(meta) != null) return
            runCatching { meta.delete() }
        }
        val info = runCatching { ApkInfo.load(project) }.getOrNull()
        val manifest = File(project, "AndroidManifest.xml").takeIf { it.isFile }?.let { file ->
            runCatching { file.readText().take(4096) }.getOrNull()
        }
        val packageName = manifest?.let { Regex("""package\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
        // apktool's own loader is strict about the file's layout, which differs between apktool
        // versions; if it rejects the file, still read the two values the project list shows.
        val yml = File(project, "apktool.yml").takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
        fun yamlValue(key: String): String? = yml?.let {
            Regex("""^\s*$key:\s*['"]?([^'"\r\n]+?)['"]?\s*$""", RegexOption.MULTILINE).find(it)?.groupValues?.get(1)?.trim()
        }
        val versionCode = info?.versionInfo?.versionCode?.takeIf { it >= 0 }?.toString() ?: yamlValue("versionCode")
        val versionName = info?.versionInfo?.versionName ?: yamlValue("versionName")
        val json = JSONObject().apply {
            put("apkFileIcon", JSONObject.NULL)
            put("apkFileName", safeName(project.name))
            put("apkFilePackageName", packageName ?: JSONObject.NULL)
            put("apkFilePatch", JSONObject.NULL)
            put("VersionInfo", JSONObject().apply {
                put("versionCode", versionCode ?: JSONObject.NULL)
                put("versionName", versionName ?: JSONObject.NULL)
            })
        }
        runCatching { meta.writeText(json.toString(2)) }
    }

    // endregion

    /** A name that is safe as a file or folder name on shared storage. */
    fun safeName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trim('.')
        return cleaned.ifEmpty { "project" }
    }
}
