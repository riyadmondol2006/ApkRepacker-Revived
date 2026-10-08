package com.riyadm.apkrepacker.ui.filemanager

import android.content.Context
import com.jecelyin.common.utils.DLog
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.fragment.base.BaseFilesFragment
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.CreateDirectoryOperation
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.CreateFileOperation
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.DeleteOperation
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.FileOperationRunnerInjector
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.RenameOperation
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.CreateDirectoryArguments
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.CreateFileArguments
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.DeleteArguments
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.RenameArguments
import com.riyadm.apkrepacker.ui.filemanager.utils.Utils
import com.riyadm.apkrepacker.utils.AppExecutor
import java.io.File
import java.io.IOException

/** User-facing file operations: ask for input with Material dialogs, then run the operation. */
class PanelActions(private val context: Context, private val fragment: BaseFilesFragment) {

    fun actionRenameFile(file: File) {
        FileDialogs.promptText(
            context = context,
            title = R.string.action_rename,
            initial = file.name,
            confirmLabel = R.string.action_rename,
            validate = { name ->
                if (name != file.name && File(file.parentFile, name).exists()) context.getString(R.string.file_exists) else null
            },
        ) { name ->
            if (name != file.name) runSafely(R.string.toast_error_on_rename) {
                FileOperationRunnerInjector.operationRunner(context).run(
                    RenameOperation(context), RenameArguments.renameArguments(file, name, fragment),
                )
            }
        }
    }

    fun actionCreateFile(parent: File?) {
        promptNew(R.string.action_create_new_file, parent) { target ->
            FileOperationRunnerInjector.operationRunner(context).run(
                CreateFileOperation(context), CreateFileArguments.createFileArguments(target),
            )
        }
    }

    fun actionCreateNewDirectory(parent: File?) {
        promptNew(R.string.action_create_new_folder, parent) { target ->
            FileOperationRunnerInjector.operationRunner(context).run(
                CreateDirectoryOperation(context), CreateDirectoryArguments.createDirectoryArguments(target, fragment),
            )
        }
    }

    fun actionDelete(vararg params: FileHolder) {
        if (params.isEmpty()) return
        AppExecutor.getInstance().diskIO.execute {
            try {
                FileOperationRunnerInjector.operationRunner(context).run(
                    DeleteOperation(context),
                    DeleteArguments.deleteArgs(params[0].file.parentFile!!, fragment, *params),
                )
            } catch (e: IOException) {
                DLog.e(e)
            }
        }
    }

    fun actionShare(file: FileHolder) {
        Utils.sendFile(file, context)
    }

    private fun promptNew(title: Int, parent: File?, create: (File) -> Unit) {
        FileDialogs.promptText(
            context = context,
            title = title,
            confirmLabel = R.string.files_action_create,
            validate = { name -> if (File(parent, name).exists()) context.getString(R.string.file_exists) else null },
        ) { name ->
            runSafely(R.string.error) { create(File(parent, name)) }
        }
    }

    private inline fun runSafely(errorMessage: Int, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            FileFeedback.show(context, errorMessage)
            DLog.e(e)
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(context: Context, fragment: BaseFilesFragment) = PanelActions(context, fragment)
    }
}
