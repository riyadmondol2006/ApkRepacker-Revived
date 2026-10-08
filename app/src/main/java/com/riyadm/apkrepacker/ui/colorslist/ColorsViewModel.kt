package com.riyadm.apkrepacker.ui.colorslist

import android.app.Application
import android.content.res.Resources
import android.graphics.Color
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

/**
 * Colors of one values file. [allColors] is the source of truth; the published list is that list filtered by the
 * search query (same [ColorMeta] instances, so a list position can always be mapped back to the full list).
 */
class ColorsViewModel(application: Application) : AndroidViewModel(application) {

    private val allColors = ArrayList<ColorMeta>()
    private var colorsFile: File? = null
    private var query = ""
    private val writeLock = Mutex()

    private val visibleColors = MutableLiveData<List<ColorMeta>>(emptyList())
    private val loadingState = MutableLiveData(true)
    private val messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    val colors: LiveData<List<ColorMeta>>
        get() = visibleColors

    val loading: LiveData<Boolean>
        get() = loadingState

    /** One-shot user feedback as string resource ids. */
    val events: SharedFlow<Int> = messages.asSharedFlow()

    fun setColorsFile(file: File) {
        if (colorsFile == file) return
        colorsFile = file
        Log.i(TAG, "colors file ${file.absolutePath}")
        viewModelScope.launch {
            loadingState.value = true
            runCatching { withContext(Dispatchers.IO) { ColorsLoader.load(file) } }
                .onSuccess {
                    allColors.clear()
                    allColors.addAll(it)
                    publish()
                }
                .onFailure {
                    it.printStackTrace()
                    messages.tryEmit(R.string.h_loading_failed)
                }
            loadingState.value = false
        }
    }

    fun filter(newQuery: String) {
        query = newQuery
        publish()
    }

    /** Removes the color shown at [position] of the published list. */
    fun deleteColor(position: Int) {
        val item = visibleColors.value?.getOrNull(position) ?: return
        val name = item.label ?: return
        allColors.remove(item)
        publish()
        write(R.string.h_color_deleted) { document, resources ->
            ValuesXmlEditor.find(resources, TAG_COLOR, name)?.let(resources::removeChild)
        }
    }

    fun addNewColor(name: String?, color: String?) {
        if (name.isNullOrBlank() || color == null) return
        allColors.add(ColorMeta.Builder(name).setValue(color).setIcon(color).build())
        publish()
        write(R.string.h_color_added) { document, resources ->
            ValuesXmlEditor.find(resources, TAG_COLOR, name)?.let { it.textContent = color }
                ?: resources.appendChild(
                    document.createElement(TAG_COLOR).apply {
                        setAttribute("name", name)
                        textContent = color
                    },
                )
        }
    }

    /** Renames and/or re-values the color shown at [position] of the published list. */
    fun setNewColor(position: Int, name: String?, color: String?) {
        val item = visibleColors.value?.getOrNull(position) ?: return
        val oldName = item.label ?: return
        if (name.isNullOrBlank() || color == null) return
        val index = allColors.indexOf(item)
        if (index >= 0) allColors[index] = ColorMeta.Builder(name).setValue(color).setIcon(color).build()
        publish()
        write(R.string.h_color_saved) { document, resources ->
            val element = ValuesXmlEditor.find(resources, TAG_COLOR, oldName)
                ?: document.createElement(TAG_COLOR).also(resources::appendChild)
            element.setAttribute("name", name)
            element.textContent = color
        }
    }

    /**
     * Resolves `#AARRGGBB`, `#RRGGBB`, `#RGB`, `@color/other` (looked up in this file) and `@android:color/x`
     * to an ARGB int, or null when it can't be resolved.
     */
    fun resolveColor(value: String?, depth: Int = 0): Int? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty() || depth > MAX_REFERENCE_DEPTH) return null
        return when {
            text.startsWith("#") -> parseHex(text)
            text.startsWith("@color/") -> {
                val target = text.removePrefix("@color/")
                resolveColor(allColors.firstOrNull { it.label == target }?.value, depth + 1)
            }
            text.startsWith("@android:color/") -> {
                val id = Resources.getSystem().getIdentifier(text.removePrefix("@android:color/"), "color", "android")
                if (id != 0) Resources.getSystem().getColor(id, null) else null
            }
            else -> null
        }
    }

    private fun parseHex(text: String): Int? {
        val digits = text.removePrefix("#")
        val expanded = if (digits.length == 3 || digits.length == 4) digits.map { "$it$it" }.joinToString("") else digits
        return runCatching { Color.parseColor("#$expanded") }.getOrNull()
    }

    private fun publish() {
        val needle = query.trim().lowercase(Locale.getDefault())
        visibleColors.value =
            if (needle.isEmpty()) {
                ArrayList(allColors)
            } else {
                allColors.filter {
                    it.label.orEmpty().lowercase(Locale.getDefault()).contains(needle) ||
                        it.value.orEmpty().lowercase(Locale.getDefault()).contains(needle)
                }
            }
    }

    /** Applies one change to the file on disk; the in-memory list was already updated by the caller. */
    private fun write(successMessage: Int, change: (org.w3c.dom.Document, org.w3c.dom.Element) -> Unit) {
        val file = colorsFile ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                writeLock.withLock { runCatching { ValuesXmlEditor.edit(file, change) } }
            }
            result
                .onSuccess { messages.tryEmit(successMessage) }
                .onFailure {
                    it.printStackTrace()
                    messages.tryEmit(R.string.h_save_failed)
                }
        }
    }

    private companion object {
        const val TAG = "ColorsViewModel"
        const val TAG_COLOR = "color"
        const val MAX_REFERENCE_DEPTH = 8
    }
}
