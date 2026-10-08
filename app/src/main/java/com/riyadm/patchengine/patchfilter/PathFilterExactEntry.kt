package com.riyadm.patchengine.patchfilter

import com.riyadm.patchengine.interfaces.IPatchContext

class PathFilterExactEntry(ctx: IPatchContext?, pathStr: String?) : PathFilter() {

    private var cursor = 0
    private val entryName: String? = pathStr

    override fun getNextEntry(): String? {
        val i = this.cursor
        if (i != 0) {
            return null
        }
        this.cursor = i + 1
        return this.entryName
    }

    override fun isTarget(str: String): Boolean {
        return this.entryName!! == str
    }

    override fun isSmaliNeeded(): Boolean {
        val str = this.entryName
        if (str != null) {
            val pos = str.indexOf('/')
            if (pos != -1) {
                val firstDir = str.substring(0, pos)
                return "smali" == firstDir || firstDir.startsWith("smali_")
            }
        }
        return false
    }

    override fun isWildMatch(): Boolean {
        return false
    }
}
