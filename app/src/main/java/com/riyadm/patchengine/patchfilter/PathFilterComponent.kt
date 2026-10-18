package com.riyadm.patchengine.patchfilter

import com.riyadm.patchengine.interfaces.IPatchContext
import java.io.File

class PathFilterComponent(ctx: IPatchContext, componentType: ComponentType) : PathFilter() {

    private var applicationName: String? = null
    private val compType: ComponentType = componentType
    private var componentList: List<String>? = ArrayList()
    private var cursor = 0
    private val decodeRootPath: String? = ctx.getDecodeRootPath()

    init {
        val i = ComponetClass.component[componentType.ordinal]
        when (i) {
            1 -> this.applicationName = ctx.getApplicationManifest()
            2 -> this.componentList = ctx.getActivities() ?: emptyList()
            3 -> this.componentList = ctx.getLauncherActivities() ?: emptyList()
        }
    }

    // The component list stays the one read with the patch: a later rule still targets the app's
    // own launcher after an earlier one added another (SplashScreen patches rely on that).
    override fun rewind() {
        cursor = 0
    }

    override fun getNextEntry(): String? {
        val i = ComponetClass.component[this.compType.ordinal]
        if (i == 1) {
            val i2 = this.cursor
            if (i2 != 0) {
                return null
            }
            this.cursor = i2 + 1
            return getSmaliPath(this.applicationName)
        } else if ((i != 2 && i != 3) || this.cursor >= (this.componentList?.size ?: 0)) {
            return null
        } else {
            val list = this.componentList ?: return null
            val i3 = this.cursor
            this.cursor = i3 + 1
            return getSmaliPath(list[i3])
        }
    }

    private fun getSmaliPath(clsName: String?): String? {
        if (clsName == null) return null
        // Look in every smali*/ folder the project has (smali, smali_classes2, 3 ... any N),
        // not just smali_classes2..7: apps with many dex files have smali_classes8+ and the old
        // `index < 8` loop missed them, so [APPLICATION]/[ACTIVITIES]/[LAUNCHER_ACTIVITIES]
        // targets for those classes resolved to a non-existent "smali/..." path.
        for (folder in smaliFolders()) {
            val path = getRelativePath(folder, clsName, true)
            if (path != null) return path
        }
        // Not found in any folder: fall back to the primary "smali" relative path (may not exist).
        return getRelativePath("smali", clsName, false)
    }

    /** Every `smali`/`smali_*` folder in the decoded project, `smali` first then classesN in order. */
    private fun smaliFolders(): List<String> {
        val root = this.decodeRootPath ?: return listOf("smali")
        val dirs = File(root).listFiles { f -> f.isDirectory && (f.name == "smali" || f.name.startsWith("smali_")) }
            ?: return listOf("smali")
        return dirs.map { it.name }.sortedWith(compareBy({ smaliOrder(it) }, { it }))
    }

    private fun smaliOrder(name: String): Int {
        if (name == "smali") return 1
        return Regex("smali_classes(\\d+)").matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun getRelativePath(smaliFolderName: String, clsName: String?, notExistRetNull: Boolean): String? {
        if (clsName == null) return null
        val relativePath = smaliFolderName + "/" + clsName.replace(".", "/") + ".smali"
        val absolutionPath = this.decodeRootPath + "/" + relativePath
        if (!notExistRetNull) {
            return relativePath
        }
        if (File(absolutionPath).exists()) {
            return relativePath
        }
        return null
    }

    override fun isTarget(str: String): Boolean {
        val pos = str.indexOf('/')
        if (pos == -1 || !str.endsWith(".smali")) {
            return false
        }
        val clsName = str.substring(pos + 1, str.length - 6).replace("/", ".")
        val i = ComponetClass.component[this.compType.ordinal]
        when (i) {
            1 -> return clsName == this.applicationName
            2, 3 -> return this.componentList?.contains(clsName) ?: false
        }
        return false
    }

    override fun isSmaliNeeded(): Boolean {
        return true
    }

    override fun isWildMatch(): Boolean {
        return false
    }

    enum class ComponentType {
        APPLICATION,
        ACTIVITY,
        LAUNCHER_ACTIVITY
    }

    class ComponetClass {
        companion object {
            @JvmField
            internal val component: IntArray

            init {
                val iArr = IntArray(ComponentType.values().size)
                component = iArr
                try {
                    iArr[ComponentType.APPLICATION.ordinal] = 1
                } catch (e: NoSuchFieldError) {
                    e.printStackTrace()
                }
                try {
                    component[ComponentType.ACTIVITY.ordinal] = 2
                } catch (e2: NoSuchFieldError) {
                    e2.printStackTrace()
                }
                try {
                    component[ComponentType.LAUNCHER_ACTIVITY.ordinal] = 3
                } catch (e3: NoSuchFieldError) {
                    e3.printStackTrace()
                }
            }
        }
    }
}
