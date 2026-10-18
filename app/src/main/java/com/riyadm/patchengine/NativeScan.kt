package com.riyadm.patchengine

import com.riyadm.apkrepacker.utils.WorkerThreads
import com.riyadm.apkrepacker.utils.common.DLog

/**
 * Native pre-filter (Rust, native/patchscan) for patch rules that search many files: it reads the
 * files on several threads and tells which ones can contain a match, and the rule then runs its
 * own Java code on those files only. The Java code still finds every match and writes every
 * replacement, so the patched project and the log are the same as without the filter.
 *
 * The filter may keep a file that turns out not to match, never drop one that does: the Java
 * regex is widened (anchors and lookarounds match anywhere, ...), and unreadable or non-UTF-8 files
 * are always kept. A pattern it can't widen safely (case-insensitive, comments mode, nested
 * classes) gives null, like a missing library, and the caller searches every file as before.
 */
object NativeScan {

    private const val TAG = "NativeScan"

    /** Files per call for rules that stop at the first match (MATCH_GOTO, MATCH_ASSIGN). */
    const val CHUNK = 512

    /** Turned off only to compare against plain Java runs. */
    @Volatile
    @JvmStatic
    var enabled = true

    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("patchscan")
            true
        } catch (t: Throwable) {
            DLog.w(TAG, "libpatchscan not available, patch rules search every file: $t")
            false
        }
    }

    /**
     * For each of [files] (relative to [root]): whether it can contain a match of one of [regexes]
     * (Java syntax, flags none or DOTALL) or contain all lines of one of [lineSets]. Null when the
     * filter can't be used; then every file has to be searched.
     */
    @JvmStatic
    fun candidates(
        root: String,
        files: List<String>,
        regexes: List<String> = emptyList(),
        lineSets: List<List<String>> = emptyList(),
    ): BooleanArray? {
        if (!enabled || files.isEmpty() || (regexes.isEmpty() && lineSets.isEmpty()) || !loaded) return null
        val result = try {
            scan(
                root,
                files.toTypedArray(),
                regexes.toTypedArray(),
                lineSets.map { it.toTypedArray() }.toTypedArray(),
                WorkerThreads.count(),
            )
        } catch (t: Throwable) {
            DLog.w(TAG, "native scan failed: $t")
            null
        }
        if (result == null || result.size != files.size) {
            DLog.d(TAG, "not used for $regexes $lineSets")
            return null
        }
        return result
    }

    @JvmStatic
    private external fun scan(
        root: String,
        files: Array<String>,
        regexes: Array<String>,
        lineSets: Array<Array<String>>,
        threads: Int,
    ): BooleanArray?
}
