package com.riyadm.apkrepacker.ui.filemanager.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Deletes a folder tree with several threads: each folder is listed and emptied by one worker,
 * its sub-folders by others, and a folder is removed once all of its contents are gone (children
 * before parents). On shared storage every delete is a round trip to the storage daemon, and a
 * folder's lock is held for it, so working on different folders side by side is several times
 * faster than one thread walking the tree.
 *
 * Symbolic links are never followed: a link is deleted itself, never what it points to.
 */
object TreeDeleter {

    class Result(
        /** Null when everything was deleted; else the first file or folder that could not be. */
        val failed: File?,
        /** Deleted paths (files, then their folder) of the reported part of the tree; see [delete]. */
        val reported: List<String>,
    ) {
        val success: Boolean get() = failed == null
    }

    /**
     * Deletes [root] and everything below it. With [stopOnFailure] the first failure stops the
     * work (what is left stays); otherwise everything deletable is deleted. A [root] that does
     * not exist counts as deleted unless [missingRootFails].
     *
     * When [report] is given, the paths of deleted files and folders are collected for the media
     * scanner; it is asked once per folder (with the folder's entry names) whether that folder's
     * part of the tree is to be reported at all. [reportRoot] says so for [root] itself.
     */
    @JvmStatic
    @JvmOverloads
    fun delete(
        root: File,
        threads: Int,
        stopOnFailure: Boolean = false,
        missingRootFails: Boolean = false,
        reportRoot: Boolean = false,
        report: ((dir: File, names: Array<String>) -> Boolean)? = null,
    ): Result {
        val reported = ConcurrentLinkedQueue<String>()
        val rootAttributes = attributes(root)
        if (rootAttributes == null) {
            return Result(if (missingRootFails) root else null, emptyList())
        }
        if (!rootAttributes.isDirectory) {
            // A file, or a link (to a folder or not): just this one entry.
            if (!deleteEntry(root)) return Result(root, emptyList())
            if (reportRoot && report != null) reported += root.absolutePath
            return Result(null, reported.toList())
        }
        Run(threads, stopOnFailure, report, reported).use { run ->
            run.start(root, reportRoot && report != null)
            return Result(run.awaitFailure(), reported.toList())
        }
    }

    /** Null when nothing (not even a broken link) is at [file]; links are not followed. */
    private fun attributes(file: File): BasicFileAttributes? = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (e: Exception) {
        null
    }

    /** Deletes one file, link or empty folder; something already gone counts as deleted. */
    private fun deleteEntry(file: File): Boolean = file.delete() || attributes(file) == null

    /** [reported]: whether this folder's own path is reported (its contents also need the filter's yes). */
    private class Folder(val dir: File, val parent: Folder?, val reported: Boolean) {
        /** Unfinished sub-folders, plus one for the folder's own listing pass. */
        val pending = AtomicInteger(1)
    }

    private class Run(
        threads: Int,
        private val stopOnFailure: Boolean,
        private val report: ((File, Array<String>) -> Boolean)?,
        private val reported: ConcurrentLinkedQueue<String>,
    ) : AutoCloseable {
        private val pool = Executors.newFixedThreadPool(threads.coerceAtLeast(1))
        private val done = CountDownLatch(1)
        private val failure = AtomicReference<File?>(null)
        private val stopped = AtomicBoolean(false)

        fun start(root: File, reportRoot: Boolean) {
            val folder = Folder(root, null, reportRoot)
            pool.execute { visit(folder) }
        }

        fun awaitFailure(): File? {
            done.await()
            return failure.get()
        }

        private fun fail(file: File) {
            failure.compareAndSet(null, file)
            if (stopOnFailure) stopped.set(true)
        }

        private fun visit(folder: Folder) {
            try {
                if (!stopped.get()) empty(folder)
            } catch (t: Throwable) {
                fail(folder.dir)
            } finally {
                finish(folder)
            }
        }

        private fun empty(folder: Folder) {
            val names = folder.dir.list() ?: return // unreadable: removing the folder below fails
            val reportHere = folder.reported && report!!.invoke(folder.dir, names)
            for (name in names) {
                if (stopped.get()) return
                val child = File(folder.dir, name)
                val attributes = attributes(child) ?: continue
                if (attributes.isDirectory) {
                    val sub = Folder(child, folder, reportHere)
                    folder.pending.incrementAndGet()
                    pool.execute { visit(sub) }
                } else if (deleteEntry(child)) {
                    if (reportHere) reported += child.absolutePath
                } else {
                    fail(child)
                }
            }
        }

        /** One part of [folder] is done; the last one removes the folder and tells its parent. */
        private fun finish(folder: Folder) {
            var current: Folder? = folder
            while (current != null) {
                if (current.pending.decrementAndGet() != 0) return
                if (!stopped.get()) {
                    if (deleteEntry(current.dir)) {
                        if (current.reported) reported += current.dir.absolutePath
                    } else {
                        fail(current.dir)
                    }
                }
                if (current.parent == null) done.countDown()
                current = current.parent
            }
        }

        override fun close() {
            pool.shutdownNow()
            try {
                pool.awaitTermination(30, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
