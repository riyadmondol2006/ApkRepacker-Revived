package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Holds the search and replace text of the replace sheet across configuration changes. */
class ReplaceInFileDialogViewModel(application: Application) : AndroidViewModel(application) {

    private val searchEditData = MutableStateFlow("")
    private val replaceEditData = MutableStateFlow("")

    fun setSearchEditData(data: String?) {
        searchEditData.value = data.orEmpty()
    }

    fun setReplaceEditData(data: String?) {
        replaceEditData.value = data.orEmpty()
    }

    fun getSearchEditData(): StateFlow<String> = searchEditData

    fun getReplaceEditData(): StateFlow<String> = replaceEditData
}
