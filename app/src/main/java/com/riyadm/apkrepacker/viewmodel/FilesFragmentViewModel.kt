package com.riyadm.apkrepacker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder

/**
 * State of a file browser that has to outlive its views: the directory being shown (also saved
 * for process death), the last loaded listing, and where the user had scrolled in each folder.
 */
class FilesFragmentViewModel(
    application: Application,
    private val state: SavedStateHandle,
) : AndroidViewModel(application) {

    var path: String?
        get() = state[KEY_PATH]
        set(value) {
            state[KEY_PATH] = value
        }

    /** Listing of [displayedPath]; refilled by every scan. */
    val files = ArrayList<FileHolder>()

    /** Path whose [files] are currently on screen, or null before the first scan finished. */
    var displayedPath: String? = null

    /** First visible row per directory, restored when the user comes back to it. */
    val scrollPositions = HashMap<String, Int>()

    private companion object {
        const val KEY_PATH = "path"
    }
}
