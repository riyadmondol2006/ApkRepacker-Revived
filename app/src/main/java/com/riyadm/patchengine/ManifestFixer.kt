package com.riyadm.patchengine

import java.io.File

/**
 * Repairs what older patches leave in a decoded AndroidManifest.xml.
 *
 * Patches written before Android 12 add activities with an intent filter but no android:exported.
 * An app targeting Android 12+ (API 31) is then refused at install ("Targeting S+ requires that an
 * explicit value for android:exported be defined when intent filters are present"). Before
 * Android 12 such a component was exported by default, so android:exported="true" is what the
 * patch meant: [addMissingExported] adds exactly that, as a text edit that leaves the rest of the
 * file untouched.
 */
object ManifestFixer {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private val COMPONENTS = setOf("activity", "activity-alias", "service", "receiver")
    private val TAG = Regex("<!--.*?-->|<(/?)([A-Za-z_][\\w:.-]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*?)(/?)>", RegexOption.DOT_MATCHES_ALL)

    /** Adds android:exported="true" where it's missing next to an intent filter; the number of components fixed. */
    @JvmStatic
    fun addMissingExported(manifest: File): Int {
        if (!manifest.isFile) return 0
        val text = manifest.readText()
        val fixed = addMissingExported(text) ?: return 0
        manifest.writeText(fixed.first)
        return fixed.second
    }

    /** The fixed text and the number of components fixed, or null when nothing needs fixing. */
    @JvmStatic
    fun addMissingExported(text: String): Pair<String, Int>? {
        val prefix = Regex("xmlns:([\\w.-]+)\\s*=\\s*[\"']" + Regex.escape(ANDROID_NS) + "[\"']").find(text)?.groupValues?.get(1)
            ?: return null
        val exported = Regex("(^|\\s)" + Regex.escape(prefix) + ":exported\\s*=")

        // Open components: where to insert the attribute, whether it already has one, whether an
        // intent filter was seen inside it.
        class Open(val insertAt: Int, val hasExported: Boolean, var hasFilter: Boolean = false)
        val stack = ArrayList<Open?>()
        val inserts = ArrayList<Int>()
        for (m in TAG.findAll(text)) {
            val name = m.groups[2]?.value ?: continue // a comment
            val closing = m.groupValues[1] == "/"
            val selfClosing = m.groupValues[4] == "/"
            val local = name.substringAfter(':')
            when {
                closing -> {
                    val open = if (stack.isEmpty()) null else stack.removeAt(stack.size - 1)
                    if (open != null && open.hasFilter && !open.hasExported) inserts += open.insertAt
                }
                selfClosing -> Unit
                else -> {
                    // Only a direct child counts: the component is then the innermost open element.
                    if (local == "intent-filter") stack.lastOrNull()?.hasFilter = true
                    stack += if (local in COMPONENTS) Open(m.range.first + 1 + name.length, exported.containsMatchIn(m.groupValues[3])) else null
                }
            }
        }
        if (inserts.isEmpty()) return null
        val out = StringBuilder(text)
        for (at in inserts.sortedDescending()) out.insert(at, " $prefix:exported=\"true\"")
        return out.toString() to inserts.size
    }
}
