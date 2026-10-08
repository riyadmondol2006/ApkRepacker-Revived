package com.riyadm.apkrepacker.utils.manifestparser

/**
 * Exception thrown when [IAbstractFile.getContents] fails.
 */
class StreamException(e: Exception?) : Exception(e) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
