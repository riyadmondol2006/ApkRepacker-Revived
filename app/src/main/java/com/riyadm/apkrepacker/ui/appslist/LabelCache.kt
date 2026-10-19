package com.riyadm.apkrepacker.ui.appslist

/**
 * App labels by package name from the previous scan. Loading a label opens the app's resources, so
 * a rescan after a package broadcast only loads the labels of packages that changed: an entry is
 * reused while the package's stamp (its last update time) is the same and the locale hasn't changed.
 * Not thread safe: [BackupRepository] uses it from its single scan thread only.
 */
internal class LabelCache {

    private class Entry(val stamp: Long, val label: String)

    private val entries = HashMap<String, Entry>()
    private var locales: String? = null

    val size: Int
        get() = entries.size

    /** Drops every label when the app locales differ from the last scan's (labels are localized). */
    fun useLocales(locales: String) {
        if (locales != this.locales) {
            entries.clear()
            this.locales = locales
        }
    }

    fun get(packageName: String, stamp: Long): String? =
        entries[packageName]?.takeIf { it.stamp == stamp }?.label

    fun put(packageName: String, stamp: Long, label: String) {
        entries[packageName] = Entry(stamp, label)
    }

    /** Forgets the packages that are no longer installed. */
    fun retainAll(packageNames: Set<String>) {
        entries.keys.retainAll(packageNames)
    }
}
