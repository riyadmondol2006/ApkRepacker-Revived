package com.riyadm.apkrepacker.utils.manifestparser

import java.io.File

/**
 * A folder.
 */
interface IAbstractFolder : IAbstractResource {
    /**
     * Instances of classes that implement this interface are used to filter
     * filenames.
     */
    fun interface FilenameFilter {
        /**
         * Tests if a specified file should be included in a file list.
         *
         * @param dir
         *            the directory in which the file was found.
         * @param name
         *            the name of the file.
         * @return <code>true</code> if and only if the name should be included
         *         in the file list; <code>false</code> otherwise.
         */
        fun accept(dir: IAbstractFolder?, name: String?): Boolean
    }

    /**
     * Returns true if the receiver contains a file with a given name
     *
     * @param name
     *            the name of the file. This is the name without the path
     *            leading to the parent folder.
     */
    fun hasFile(name: String?): Boolean

    /**
     * Returns an [IAbstractFile] representing a child of the current
     * folder with the given name. The file may not actually exist.
     *
     * @param name
     *            the name of the file.
     */
    fun getFile(name: String?): IAbstractFile?

    /**
     * Returns an [IAbstractFolder] representing a child of the current
     * folder with the given name. The folder may not actually exist.
     *
     * @param name
     *            the name of the folder.
     */
    fun getFolder(name: String?): IAbstractFolder?

    /**
     * Returns a list of all existing file and directory members in this folder.
     * The returned array can be empty but is never null.
     */
    fun listMembers(): Array<IAbstractResource>

    /**
     * Returns a list of all existing file and directory members in this folder
     * that satisfy the specified filter.
     *
     * @param filter
     *            A filename filter instance. Must not be null.
     * @return An array of file names (generated using [File.getName]).
     *         The array can be empty but is never null.
     */
    fun list(filter: FilenameFilter): Array<String>
}
