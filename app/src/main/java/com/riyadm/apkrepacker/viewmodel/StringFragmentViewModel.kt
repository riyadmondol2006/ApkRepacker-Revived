package com.riyadm.apkrepacker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.autotranslator.dictionary.DictionaryWriter
import com.riyadm.apkrepacker.autotranslator.translator.TranslateItem
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.ui.stringlist.DirectoryScanner
import com.riyadm.apkrepacker.ui.stringlist.StringFile
import com.riyadm.apkrepacker.utils.ProjectUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Owns the strings of the language file open in the editor. [allStrings] is the single source of truth;
 * the visible list is that list with the search query applied, sharing the same [TranslateItem] instances,
 * so in-place edits made in the UI are never lost by filtering.
 */
class StringFragmentViewModel(application: Application) : AndroidViewModel(application) {

    private val allStrings = ArrayList<TranslateItem>()
    private var query = ""
    private var started = false
    private var parseJob: Job? = null

    private val visibleStrings = MutableLiveData<List<TranslateItem>>(emptyList())
    private val stringFiles = MutableLiveData<List<StringFile>>(emptyList())
    private val language = MutableLiveData("default")
    private val loadingState = MutableLiveData(false)
    private val messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** The language file currently open. */
    var currentLangFile: StringFile? = null
        private set

    /** True after any edit that has not been written to disk yet. */
    var hasUnsavedChanges = false

    /** Strings of the open file after the search filter. */
    val stingsData: LiveData<List<TranslateItem>>
        get() = visibleStrings

    /** All language files of the project. */
    val stingsFilesData: LiveData<List<StringFile>>
        get() = stringFiles

    /** Code of the open language ("default", "ru"…). */
    val currentLanguage: LiveData<String>
        get() = language

    val loading: LiveData<Boolean>
        get() = loadingState

    /** One-shot user feedback as string resource ids. */
    val events: SharedFlow<Int> = messages.asSharedFlow()

    private val projectPath: String
        get() = ProjectUtils.getProjectPath()

    /** Loads the language list and the default language; later calls are no-ops (config changes). */
    fun startLoad() {
        if (started) return
        started = true
        findStringFiles()
        parseStings(StringFile("$projectPath/res/values/strings.xml", "default"))
    }

    fun findStringFiles() {
        // A compiled project (resources.arsc, no res/) has no editable strings.
        if (File(projectPath, "resources.arsc").exists() || !File(projectPath, "res").exists()) return
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { DirectoryScanner().findStringFiles(projectPath) }
            stringFiles.value = found
        }
    }

    fun parseStings(file: StringFile) {
        currentLangFile = file
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            loadingState.value = true
            val parsed = withContext(Dispatchers.IO) { runCatching { readStrings(file) } }
            parsed.onSuccess {
                allStrings.clear()
                allStrings.addAll(it)
                hasUnsavedChanges = false
                language.value = file.lang()
                publish()
            }.onFailure {
                it.printStackTrace()
                messages.tryEmit(R.string.toast_error_pasring)
            }
            loadingState.value = false
        }
    }

    private fun readStrings(file: File): List<TranslateItem> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        document.documentElement.normalize()
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { node ->
                val element = node as Element
                TranslateItem(element.getAttribute("name"), element.textContent, null)
            }
    }

    /**
     * Writes the current strings of the open language to disk, then reopens it, or [openAfterSave]
     * when the user is switching language. [onSaved] runs on the main thread once the file is written.
     */
    fun saveAll(openAfterSave: StringFile? = null, onSaved: (() -> Unit)? = null) =
        saveStrings(ArrayList(allStrings), openAfterSave, onSaved)

    fun saveStrings(translatedList: List<TranslateItem>, openAfterSave: StringFile? = null, onSaved: (() -> Unit)? = null) {
        val langFile = currentLangFile ?: return
        val lang = langFile.lang().orEmpty()
        val items = ArrayList(translatedList)
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = if (lang.startsWith("default")) File("$projectPath/res/values") else File("$projectPath/res/values-$lang")
                    dir.mkdirs()
                    val target = File(dir.canonicalPath, "strings.xml")
                    writeStrings(target, items)
                    target
                }
            }
            written.onSuccess { target ->
                hasUnsavedChanges = false
                messages.tryEmit(R.string.h_saved)
                parseStings(openAfterSave ?: StringFile(target.absolutePath, lang))
                findStringFiles()
                onSaved?.invoke()
            }.onFailure {
                it.printStackTrace()
                messages.tryEmit(R.string.toast_error_translate_language)
            }
        }
    }

    private fun writeStrings(target: File, items: List<TranslateItem>) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = document.createElement("resources")
        document.appendChild(root)
        items.forEach { item ->
            root.appendChild(
                document.createElement("string").apply {
                    setAttribute("name", item.name)
                    appendChild(document.createTextNode(item.translatedValue ?: item.originValue))
                },
            )
        }
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "utf-8")
            setOutputProperty(OutputKeys.METHOD, "xml")
        }
        target.outputStream().use { transformer.transform(DOMSource(document), StreamResult(it)) }
    }

    /** Creates `values<lang>/strings.xml` as a copy of the default language and opens it. */
    fun addNewLang(lang: String) {
        viewModelScope.launch {
            val created: Pair<Int?, File?> = withContext(Dispatchers.IO) {
                val newLang = File("$projectPath/res/values$lang", "strings.xml")
                when {
                    newLang.exists() -> R.string.toast_error_new_language_is_exits to null
                    else -> try {
                        FileUtils.copyFile(File("$projectPath/res/values", "strings.xml"), newLang)
                        null to newLang
                    } catch (e: IOException) {
                        e.printStackTrace()
                        R.string.toast_error_create_new_language to null
                    }
                }
            }
            val (error, file) = created
            if (error != null) {
                messages.tryEmit(error)
            } else if (file != null) {
                parseStings(StringFile(file.absolutePath, lang.removePrefix("-")))
                findStringFiles()
            }
        }
    }

    fun updateString(position: Int, newValue: String?) {
        val item = visibleStrings.value?.getOrNull(position) ?: return
        item.translatedValue = newValue
        hasUnsavedChanges = true
    }

    fun deleteString(position: Int) {
        visibleStrings.value?.getOrNull(position)?.let { deleteString(it) }
    }

    fun deleteString(item: TranslateItem) {
        if (allStrings.remove(item)) {
            hasUnsavedChanges = true
            publish()
            saveAll()
        }
    }

    fun addNewString(item: TranslateItem) {
        allStrings.add(item)
        hasUnsavedChanges = true
        publish()
        saveAll()
    }

    /** Saves the translated pairs as `<decodingPath>/dictionary/<name>.mtd`. */
    fun saveTranslationToDictionary(dictionaryName: String?, translatedList: List<TranslateItem>) {
        val items = ArrayList(translatedList)
        viewModelScope.launch(Dispatchers.IO) {
            val dictionaryDir = File(PreferenceHelper.getInstance(getApplication()).decodingPath + "/dictionary")
            dictionaryDir.mkdirs()
            DictionaryWriter(File(dictionaryDir, "$dictionaryName.mtd")).writeDictionary(items)
        }
    }

    /** Filters the list by key, original text or translation. */
    fun filter(newQuery: String) {
        query = newQuery
        publish()
    }

    private fun publish() {
        val needle = query.trim().lowercase(Locale.getDefault())
        visibleStrings.value =
            if (needle.isEmpty()) {
                ArrayList(allStrings)
            } else {
                allStrings.filter { item ->
                    item.name.orEmpty().lowercase(Locale.getDefault()).contains(needle) ||
                        item.originValue.orEmpty().lowercase(Locale.getDefault()).contains(needle) ||
                        item.translatedValue.orEmpty().lowercase(Locale.getDefault()).contains(needle)
                }
            }
    }
}
