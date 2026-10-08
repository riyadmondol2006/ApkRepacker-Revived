package com.riyadm.patchengine.patchfilter

abstract class PathFilter {

    abstract fun getNextEntry(): String?

    abstract fun isSmaliNeeded(): Boolean

    abstract fun isTarget(str: String): Boolean

    abstract fun isWildMatch(): Boolean
}
