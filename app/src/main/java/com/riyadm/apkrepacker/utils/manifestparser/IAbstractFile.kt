package com.riyadm.apkrepacker.utils.manifestparser

import java.io.InputStream
import java.io.OutputStream

/**
 * A file.
 */
interface IAbstractFile : IAbstractResource {
    enum class PreferredWriteMode {
        INPUTSTREAM, OUTPUTSTREAM
    }

    /**
     * Returns an [InputStream] object on the file content.
     *
     * @throws StreamException
     */
    @Throws(StreamException::class)
    fun getContents(): InputStream?

    /**
     * Sets the content of the file.
     *
     * @param source
     *            the content
     * @throws StreamException
     */
    @Throws(StreamException::class)
    fun setContents(source: InputStream?)

    /**
     * Returns an [OutputStream] to write into the file.
     *
     * @throws StreamException
     */
    @Throws(StreamException::class)
    fun getOutputStream(): OutputStream?

    /**
     * Returns the preferred mode to write into the file.
     */
    fun getPreferredWriteMode(): PreferredWriteMode?
}
