package com.riyadm.apkrepacker.utils.manifestparser

/**
 * Base representation of a file system resource.
 * <p/>
 * This somewhat limited interface is designed to let classes use file-system
 * resources, without having the manually handle either the standard Java file
 * or the Eclipse file API..
 */
interface IAbstractResource {

    /**
     * Returns the name of the resource.
     */
    fun getName(): String?

    /**
     * Returns the OS path of the folder location.
     */
    fun getOsLocation(): String?

    /**
     * Returns whether the resource actually exists.
     */
    fun exists(): Boolean

    /**
     * Returns the parent folder or null if there is no parent.
     */
    fun getParentFolder(): IAbstractFolder?
}
