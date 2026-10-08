package com.riyadm.apkrepacker.project

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import android.util.Log
import com.riyadm.apkrepacker.utils.PermissionsUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Folder names beyond this are not followed (a cycle or a hostile tree would otherwise recurse forever). */
internal const val MAX_TREE_DEPTH = 64

/** False for names a provider could use to escape the folder they are listed in. */
internal fun isSafeEntryName(name: String): Boolean =
    name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name

/** True when [file] is a symbolic link (followed neither for listing nor for copying). */
internal fun isSymlink(file: File): Boolean = try {
    java.nio.file.Files.isSymbolicLink(file.toPath())
} catch (e: Exception) {
    false
}

/**
 * A readable file tree: a plain [File] folder, or a folder the user picked through the system
 * picker (a Storage Access Framework tree). Project export/import copies between these, so one
 * code path serves both.
 */
internal sealed class Node {
    abstract val name: String
    abstract val isDirectory: Boolean
    abstract fun list(): List<Node>

    @Throws(IOException::class)
    abstract fun open(): InputStream

    class FileNode(val file: File) : Node() {
        override val name: String get() = file.name
        override val isDirectory: Boolean get() = file.isDirectory
        override fun list(): List<Node> =
            file.listFiles().orEmpty().filter { !isSymlink(it) }.sortedBy { it.name }.map(::FileNode)
        override fun open(): InputStream = file.inputStream()
    }

    class DocNode(private val context: Context, val doc: DocumentFile) : Node() {
        override val name: String get() = doc.name.orEmpty()
        override val isDirectory: Boolean get() = doc.isDirectory
        override fun list(): List<Node> = doc.listFiles()
            .filter {
                val ok = isSafeEntryName(it.name.orEmpty())
                if (!ok) Log.w("TreeFs", "Skipping entry with unsafe name: ${it.name}")
                ok
            }
            .sortedBy { it.name.orEmpty() }.map { DocNode(context, it) }
        override fun open(): InputStream =
            context.contentResolver.openInputStream(doc.uri) ?: throw IOException("Cannot open ${doc.uri}")
    }
}

private fun requireSafeName(name: String) {
    if (!isSafeEntryName(name)) throw IOException("Unsafe file name: ${name.replace('\u0000', '?')}")
}

/** A writable folder, the counterpart of [Node]. */
internal sealed class Sink {
    abstract fun dir(name: String): Sink

    /** Creates a new sub-folder named [name], or `name (2)`, `name (3)`... when that is taken. */
    @Throws(IOException::class)
    abstract fun newDir(name: String): Sink

    @Throws(IOException::class)
    abstract fun file(name: String): OutputStream

    /** The folder's own name (for telling the user where the export went). */
    abstract val folderName: String

    class FileSink(val dir: File) : Sink() {
        override val folderName: String get() = dir.name

        override fun dir(name: String): Sink {
            requireSafeName(name)
            val child = File(dir, name)
            if (!child.isDirectory && !child.mkdirs()) throw IOException("Cannot create $child")
            return FileSink(child)
        }

        override fun newDir(name: String): Sink {
            requireSafeName(name)
            var candidate = File(dir, name)
            var n = 2
            while (candidate.exists()) candidate = File(dir, "$name ($n)").also { n++ }
            if (!candidate.mkdirs()) throw IOException("Cannot create $candidate")
            return FileSink(candidate)
        }

        override fun file(name: String): OutputStream {
            requireSafeName(name)
            return File(dir, name).outputStream()
        }
    }

    class DocSink(private val context: Context, private val dir: DocumentFile) : Sink() {
        override val folderName: String get() = dir.name.orEmpty()

        override fun dir(name: String): Sink {
            requireSafeName(name)
            val child = dir.findFile(name)?.takeIf { it.isDirectory }
                ?: dir.createDirectory(name)
                ?: throw IOException("Cannot create folder $name")
            return DocSink(context, child)
        }

        override fun newDir(name: String): Sink {
            requireSafeName(name)
            var candidate = name
            var n = 2
            while (dir.findFile(candidate) != null) candidate = "$name ($n)".also { n++ }
            val child = dir.createDirectory(candidate) ?: throw IOException("Cannot create folder $candidate")
            return DocSink(context, child)
        }

        override fun file(name: String): OutputStream {
            requireSafeName(name)
            dir.findFile(name)?.delete()
            // octet-stream keeps the provider from appending an extension to the name.
            val doc = dir.createFile("application/octet-stream", name) ?: throw IOException("Cannot create $name")
            return context.contentResolver.openOutputStream(doc.uri, "wt") ?: throw IOException("Cannot write $name")
        }
    }
}

internal object TreeFs {

    /**
     * A folder picked with ACTION_OPEN_DOCUMENT_TREE as a plain [File] path, if the app may use
     * it directly. That needs All files access and a folder on shared storage; it is much faster
     * than going through the document provider for every one of a project's thousands of files.
     */
    fun directFolder(context: Context, tree: Uri): File? {
        if (tree.authority != "com.android.externalstorage.documents") return null
        if (!PermissionsUtils.hasStorageAccess(context)) return null
        val docId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val volume = docId.substringBefore(':')
        val relative = docId.substringAfter(':', "")
        @Suppress("DEPRECATION")
        val base = if (volume == "primary") Environment.getExternalStorageDirectory() else File("/storage/$volume")
        val folder = if (relative.isEmpty()) base else File(base, relative)
        return folder.takeIf { it.isDirectory && it.canRead() }
    }

    fun source(context: Context, tree: Uri): Node {
        directFolder(context, tree)?.let { return Node.FileNode(it) }
        val doc = DocumentFile.fromTreeUri(context, tree) ?: throw IOException("Cannot open the folder")
        return Node.DocNode(context, doc)
    }

    fun sink(context: Context, tree: Uri): Sink {
        directFolder(context, tree)?.takeIf { it.canWrite() }?.let { return Sink.FileSink(it) }
        val doc = DocumentFile.fromTreeUri(context, tree) ?: throw IOException("Cannot open the folder")
        return Sink.DocSink(context, doc)
    }
}
