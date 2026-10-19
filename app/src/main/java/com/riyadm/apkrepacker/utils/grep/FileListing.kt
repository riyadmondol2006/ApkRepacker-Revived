package com.riyadm.apkrepacker.utils.grep

import com.riyadm.apkrepacker.utils.WorkerThreads
import java.io.File
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveTask

/**
 * FileUtils.listFiles(directory, extensions, recursive) (commons-io) with the directories read on
 * several threads: the same files in the same order. A project has tens of thousands of files and
 * each needs a stat, which on shared storage is slow enough to dominate a search.
 */
object FileListing {

    @JvmStatic
    fun listFiles(directory: File, extensions: Array<String>, recursive: Boolean): List<File> {
        val suffixes = extensions.map { if (it[0] == '.') it else ".$it" }.toTypedArray()
        val pool = ForkJoinPool(WorkerThreads.count())
        try {
            return pool.invoke(Dir(directory, suffixes, recursive))
        } finally {
            pool.shutdown()
        }
    }

    /**
     * One directory, as commons-io lists it: its matching files in listing order, then the files
     * of each subdirectory in turn.
     */
    private class Dir(
        private val dir: File,
        private val suffixes: Array<String>,
        private val recursive: Boolean,
    ) : RecursiveTask<List<File>>() {

        override fun compute(): List<File> {
            val entries = dir.listFiles() ?: return emptyList()
            val files = ArrayList<File>()
            val dirs = ArrayList<Dir>()
            for (f in entries) {
                if (recursive && f.isDirectory) {
                    dirs.add(Dir(f, suffixes, recursive))
                } else if (matches(f.name) && f.isFile) {
                    // The name first: it saves a stat for every file of another type.
                    files.add(f)
                }
            }
            if (dirs.isEmpty()) return files
            invokeAll(dirs)
            for (d in dirs) files.addAll(d.join())
            return files
        }

        private fun matches(name: String) = suffixes.any { name.endsWith(it) }
    }
}
