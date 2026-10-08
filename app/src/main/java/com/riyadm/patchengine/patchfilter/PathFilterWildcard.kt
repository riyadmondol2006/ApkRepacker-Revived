package com.riyadm.patchengine.patchfilter

import com.riyadm.patchengine.interfaces.IPatchContext
import java.io.File
import java.util.LinkedList
import java.util.regex.Pattern

class PathFilterWildcard(context: IPatchContext, pathStr: String) : PathFilter() {

    private val ctx: IPatchContext = context
    private val decodedRootPath: String?
    private var fileCursor = 0
    private val fileList: MutableList<String> = ArrayList()
    private val folderList: MutableList<String> = LinkedList()
    private var initialized = false
    // Compiled once instead of per file: a wildcard target scans the whole project tree, so
    // Pattern.matches() (which recompiles every call) was re-parsing this regex thousands of times.
    private val regex: Pattern
    private val wildPathStr: String = pathStr

    init {
        this.regex = Pattern.compile("^" + pathStr.replace("*", ".*") + "\$")
        this.decodedRootPath = context.getDecodeRootPath()
    }

    private fun init() {
        val subFiles = File(this.decodedRootPath).listFiles()
        if (subFiles != null) {
            for (f in subFiles) {
                if (f.isDirectory) {
                    this.folderList.add(f.name)
                } else {
                    val relativePath = f.name
                    if (isTarget(relativePath)) {
                        this.fileList.add(relativePath)
                    }
                }
            }
        }
        this.initialized = true
    }

    override fun getNextEntry(): String? {
        if (!this.initialized) {
            init()
        }
        if (this.fileCursor < this.fileList.size) {
            val path = this.fileList[this.fileCursor]
            this.fileCursor++
            return path
        } else if (this.folderList.isEmpty()) {
            return null
        } else {
            this.fileCursor = 0
            this.fileList.clear()
            while (!this.folderList.isEmpty()) {
                val path2 = this.folderList.removeAt(0)
                val files = File(this.decodedRootPath + "/" + path2).listFiles()
                if (files != null) {
                    for (f in files) {
                        val relativePath = path2 + "/" + f.name
                        if (f.isDirectory) {
                            this.folderList.add(relativePath)
                        } else if (isTarget(relativePath)) {
                            this.fileList.add(relativePath)
                        }
                    }
                }
                if (!this.fileList.isEmpty()) {
                    break
                }
            }
            if (this.fileList.isEmpty()) {
                return null
            }
            this.fileCursor = 1
            return this.fileList[0]
        }
    }

    override fun isTarget(str: String): Boolean {
        return this.regex.matcher(str).matches()
    }

    override fun isSmaliNeeded(): Boolean {
        return this.wildPathStr.startsWith("smali") || this.wildPathStr.endsWith(".smali")
    }

    override fun isWildMatch(): Boolean {
        return true
    }
}
