package com.riyadm.patchengine

import com.riyadm.patchengine.interfaces.IPatchContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Reads a patch's `patch.txt` header and rules without applying anything. Used by the Patcher UI
 * to show what a patch is (author, target package, rule count) and to decide, before patching,
 * whether the patch edits smali code — see [PatchInfo.needsSmali].
 */
object PatchInspector {

    data class PatchInfo(
        val author: String? = null,
        val packageName: String? = null,
        val ruleCount: Int = 0,
        val requiredEngine: Int = 0,
        /** True if any rule targets smali (so the project must be decompiled to code). */
        val needsSmali: Boolean = false,
        val valid: Boolean = false,
    )

    /** Parsing needs an [IPatchContext]; inspection doesn't touch the project, so this is inert. */
    private object SilentContext : IPatchContext {
        override fun getActivities(): List<String>? = null
        override fun getApplicationManifest(): String? = null
        override fun getDecodeRootPath(): String? = ""
        override fun getLauncherActivities(): List<String>? = null
        override fun getPatchNames(): List<String>? = null
        override fun getSmaliFolders(): List<String>? = null
        override fun getVariableValue(str: String?): String? = null
        override fun error(resourceId: Int, vararg objArr: Any?) {}
        override fun info(resourceId: Int, bold: Boolean, vararg objArr: Any?) {}
        override fun info(str: String?, bold: Boolean, vararg objArr: Any?) {}
        override fun patchFinished() {}
        override fun setVariableValue(key: String?, value: String?) {}
    }

    @JvmStatic
    fun inspect(patchZipPath: String?): PatchInfo {
        if (patchZipPath.isNullOrEmpty()) return PatchInfo()
        return try {
            ZipFile(patchZipPath).use { zip ->
                val entry = zip.getEntry("patch.txt") ?: return PatchInfo()
                val patch = zip.getInputStream(entry).use { PatchParser.parse(it, SilentContext) }
                val rules = patch.rules
                PatchInfo(
                    author = patch.author?.trim()?.takeIf { it.isNotEmpty() && it != "*" },
                    packageName = patch.packagename?.trim()?.takeIf { it.isNotEmpty() },
                    ruleCount = rules.size,
                    requiredEngine = patch.requiredEngine,
                    needsSmali = rules.any { runCatching { it.isSmaliNeeded() }.getOrDefault(false) },
                    valid = true,
                )
            }
        } catch (e: Exception) {
            PatchInfo()
        }
    }

    /** True if any of the patches edits smali code. */
    @JvmStatic
    fun anyNeedsSmali(patchZipPaths: List<String?>): Boolean =
        patchZipPaths.any { inspect(it).needsSmali }

    /** True if the project at [projectPath] already has decompiled smali. */
    @JvmStatic
    fun hasSmali(projectPath: String?): Boolean {
        if (projectPath.isNullOrEmpty()) return false
        val dir = File(projectPath)
        val children = dir.listFiles() ?: return false
        return children.any { it.isDirectory && (it.name == "smali" || it.name.startsWith("smali_")) }
    }
}
