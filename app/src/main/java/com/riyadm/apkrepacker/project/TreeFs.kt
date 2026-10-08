package com.riyadm.apkrepacker.project

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.riyadm.apkrepacker.utils.PermissionsUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

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
        override fun list(): List<Node> = file.listFiles().orEmpty().sortedBy { it.name }.map(::FileNode)
        override fun open(): InputStream = file.inputStream()
    }

    class DocNode(private val context: Context, val doc: DocumentFile) : Node() {
        override val name: String get() = doc.name.orEmpty()
        override val isDirectory: Boolean get() = doc.isDirectory
        override fun list(): List<Node> = doc.listFiles().sortedBy { it.name.orEmpty() }.map { DocNode(context, it) }
        override fun open(): InputStream =
            context.contentResolver.openInputStream(doc.uri) ?: throw IOException("Cannot open ${doc.uri}")
    }
}

/** A writable folder, the counterpart of [Node]. */
internal sealed class Sink {
    abstract fun dir(name: String): Sink

    @Throws(IOException::class)
    abstract fun file(name: String): OutputStream

    class FileSink(val dir: File) : Sink() {
        override fun dir(name: String): Sink {
            val child = File(dir, name)
            if (!child.isDirectory && !child.mkdirs()) throw IOException("Cannot create $child")
            return FileSink(child)
        }

        override fun file(name: String): OutputStream = File(dir, name).outputStream()
    }

    class DocSink(private val context: Context, private val dir: DocumentFile) : Sink() {
        override fun dir(name: String): Sink {
            val child = dir.findFile(name)?.takeIf { it.isDirectory }
                ?: dir.createDirectory(name)
                ?: throw IOException("Cannot create folder $name")
            return DocSink(context, child)
        }

        override fun file(name: String): OutputStream {
            dir.findFile(name)?.delete()
            // octet-stream keeps the provider from appending an extension to the name.
            val doc = dir.createFile("application/octet-stream", name) ?: throw IOException("Cannot create $name")
            return context.contentResolver.openOutputStream(doc.uri) ?: throw IOException("Cannot write $name")
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
