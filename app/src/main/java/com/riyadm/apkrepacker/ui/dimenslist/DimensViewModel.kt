package com.riyadm.apkrepacker.ui.dimenslist

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.resourceeditor.ValuesXmlEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Dimensions of one values file; [allDimens] is the source of truth, the published list is the filtered view of it. */
class DimensViewModel(application: Application) : AndroidViewModel(application) {

    private val allDimens = ArrayList<DimensMeta>()
    private var dimensFile: File? = null
    private var query = ""
    private val writeLock = Mutex()

    private val visibleDimens = MutableLiveData<List<DimensMeta>>(emptyList())
    private val loadingState = MutableLiveData(true)
    private val messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    val dimens: LiveData<List<DimensMeta>>
        get() = visibleDimens

    val loading: LiveData<Boolean>
        get() = loadingState

    /** One-shot user feedback as string resource ids. */
    val events: SharedFlow<Int> = messages.asSharedFlow()

    fun setDimensFile(file: File) {
        if (dimensFile == file) return
        dimensFile = file
        Log.i(TAG, "dimens file ${file.absolutePath}")
        viewModelScope.launch {
            loadingState.value = true
            runCatching { withContext(Dispatchers.IO) { DimensLoader.load(file) } }
                .onSuccess {
                    allDimens.clear()
                    allDimens.addAll(it)
                    publish()
                }
                .onFailure {
                    it.printStackTrace()
                    messages.tryEmit(R.string.h_loading_failed)
                }
            loadingState.value = false
        }
    }

    /** Replaces the value of the dimension shown at [position] of the published list. */
    fun setNewDimens(position: Int, dimens: String?, name: String?) {
        val item = visibleDimens.value?.getOrNull(position) ?: return
        val oldName = item.label ?: return
        val newName = name ?: oldName
        if (dimens.isNullOrBlank()) return
        val index = allDimens.indexOf(item)
        if (index >= 0) allDimens[index] = DimensMeta.Builder(newName).setValue(dimens).build()
        publish()
        val file = dimensFile ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                writeLock.withLock {
                    runCatching {
                        ValuesXmlEditor.edit(file) { _, resources ->
                            ValuesXmlEditor.find(resources, TAG_DIMEN, oldName)?.apply {
                                setAttribute("name", newName)
                                textContent = dimens
                            }
                        }
                    }
                }
            }
            result
                .onSuccess { messages.tryEmit(R.string.h_dimen_saved) }
                .onFailure {
                    it.printStackTrace()
                    messages.tryEmit(R.string.h_save_failed)
                }
        }
    }

    fun filter(newQuery: String) {
        query = newQuery
        publish()
    }

    private fun publish() {
        val needle = query.trim().lowercase(Locale.getDefault())
        visibleDimens.value =
            if (needle.isEmpty()) {
                ArrayList(allDimens)
            } else {
                allDimens.filter {
                    it.label.orEmpty().lowercase(Locale.getDefault()).contains(needle) ||
                        it.value.orEmpty().lowercase(Locale.getDefault()).contains(needle)
                }
            }
    }

    private companion object {
        const val TAG = "DimensViewModel"
        const val TAG_DIMEN = "dimen"
    }
}
