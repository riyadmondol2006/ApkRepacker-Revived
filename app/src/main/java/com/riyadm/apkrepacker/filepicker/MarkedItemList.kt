package com.riyadm.apkrepacker.filepicker

/** Process-wide set of the picker's selected entries, keyed by absolute path. */
object MarkedItemList {

    private val marked = LinkedHashMap<String, FileListItem>()

    @JvmStatic
    fun addMultiItem(item: FileListItem) {
        marked[item.path] = item
    }

    @JvmStatic
    fun removeSelectedItem(key: String?) {
        marked.remove(key)
    }

    @JvmStatic
    fun hasItem(key: String?): Boolean = marked.containsKey(key)

    @JvmStatic
    fun clearSelectionList() {
        marked.clear()
    }

    @JvmStatic
    fun addSingleFile(item: FileListItem) {
        marked.clear()
        marked[item.path] = item
    }

    @JvmStatic
    val selectedPaths: Array<String>
        get() = marked.keys.toTypedArray()

    @JvmStatic
    val fileCount: Int
        get() = marked.size
}
