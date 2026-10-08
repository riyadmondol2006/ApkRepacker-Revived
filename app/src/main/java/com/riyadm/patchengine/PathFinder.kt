package com.riyadm.patchengine

import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.patchengine.patchfilter.PathFilter
import com.riyadm.patchengine.patchfilter.PathFilterComponent
import com.riyadm.patchengine.patchfilter.PathFilterExactEntry
import com.riyadm.patchengine.patchfilter.PathFilterWildcard

class PathFinder(ctx: IPatchContext, pathStr: String, line: Int) {

    private val TAG = "PathFinder"

    private var filters: MutableList<PathFilter>? = ArrayList()

    init {
        DLog.d(TAG, "Starting PatchFinder")

        val expanded = PatchRule.assignValues(ctx, pathStr)
        val path = expanded ?: pathStr
        if (path.startsWith("[") && path.endsWith("]")) {
            for (word in splitWords(path)) {
                val filter = createFilter(ctx, word, line)
                if (filter != null) {
                    this.filters!!.add(filter)
                } else {
                    this.filters = null
                    break
                }
            }
        } else if (path.contains("*")) {
            this.filters!!.add(PathFilterWildcard(ctx, path))
        } else {
            this.filters!!.add(PathFilterExactEntry(ctx, path))
        }
    }

    private fun createFilter(ctx: IPatchContext, word: String, lineIdx: Int): PathFilter? {
        when (word) {
            "APPLICATION" -> return PathFilterComponent(ctx, PathFilterComponent.ComponentType.APPLICATION)
            "ACTIVITIES" -> return PathFilterComponent(ctx, PathFilterComponent.ComponentType.ACTIVITY)
            "LAUNCHER_ACTIVITIES" -> return PathFilterComponent(ctx, PathFilterComponent.ComponentType.LAUNCHER_ACTIVITY)
        }
        ctx.error(R.string.patch_error_invalid_target, lineIdx)
        return null
    }

    private fun splitWords(pathStr: String): List<String> {
        val result: MutableList<String> = ArrayList()
        var startPos = 1
        var endPos = pathStr.indexOf(']')
        while (startPos > 0 && endPos > startPos) {
            result.add(pathStr.substring(startPos, endPos))
            startPos = pathStr.indexOf('[', endPos) + 1
            if (startPos > 0) {
                endPos = pathStr.indexOf(']', startPos)
            }
        }
        return result
    }

    fun isSmaliNeeded(): Boolean {
        val filters = this.filters ?: return false
        for (i in filters.indices) {
            if (filters[i].isSmaliNeeded()) {
                return true
            }
        }
        return false
    }

    fun getNextPath(): String? {
        DLog.d(TAG, "Starting getNextPatch")
        val list = this.filters ?: return null
        if (list.isEmpty()) return null
        var nextEntry = list[0].getNextEntry()
        if (list.size > 1) {
            while (nextEntry != null) {
                var matches = true
                var i = 1
                while (true) {
                    if (i >= list.size) {
                        break
                    } else if (!list[i].isTarget(nextEntry)) {
                        matches = false
                        break
                    } else {
                        i++
                    }
                }
                if (matches) {
                    break
                }
                nextEntry = list[0].getNextEntry()
            }
        }
        return nextEntry
    }

    fun isValid(): Boolean {
        return this.filters != null
    }

    fun isWildMatch(): Boolean {
        val list = this.filters ?: return false
        for (filter in list) {
            if (!filter.isWildMatch()) {
                return false
            }
        }
        return true
    }
}
