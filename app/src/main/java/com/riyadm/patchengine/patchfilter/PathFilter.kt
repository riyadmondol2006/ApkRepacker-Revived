package com.riyadm.patchengine.patchfilter

abstract class PathFilter {

    abstract fun getNextEntry(): String?

    abstract fun isSmaliNeeded(): Boolean

    abstract fun isTarget(str: String): Boolean

    abstract fun isWildMatch(): Boolean

    /** Starts the walk from the first entry again (a rule reached a second time by GOTO). */
    open fun rewind() {}
}
