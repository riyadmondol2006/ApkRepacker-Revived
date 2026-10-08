package com.riyadm.apkrepacker.ui.filemanager.storage.operation

import java.io.File
import java.io.IOException
import java.util.UUID

abstract class FileOperation<A : FileOperation.Arguments> {
    protected val id: Int = UUID.randomUUID().hashCode()

    /**
     * @return Whether the operation was successful.
     */
    @Throws(IOException::class)
    abstract fun operate(args: A): Boolean

    /**
     * Try the operation using SAF facilities
     * Triggered if [operate] returns false and we have write permissions.
     *
     * @return Whether the operation was successful.
     */
    abstract fun operateSaf(args: A): Boolean

    /**
     * Good place to show initial UI, or prepare any dialogs etc.
     * Called right before running the operation. Can be called multiple times.
     *
     * @param args Original arguments for the invocation that is getting started.
     */
    abstract fun onStartOperation(args: A)

    /**
     * Good place to show final result (success/failure) UI.
     * No other callbacks will happen after this.
     *
     * @param success Whether the invocation was successful.
     * @param args    Original arguments for the invocation that just finished.
     */
    abstract fun onResult(success: Boolean, args: A)

    /**
     * Called if the user has denied storage write permissions on the parent volume of [Arguments.target].
     * No other callbacks will happen after this.
     */
    abstract fun onAccessDenied()

    /**
     * Good place to hide any progress UI. You may still get calls to onResult()/onAccessDenied() after this.
     */
    abstract fun onRequestingAccess()

    /**
     * @return Whether this type of operation can fail because of lack of storage write permissions.
     */
    abstract fun needsWriteAccess(): Boolean

    abstract class Arguments protected constructor(target: File) {
        val target: File = target
    }
}
