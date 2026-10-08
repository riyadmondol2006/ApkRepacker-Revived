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
            2 -> this.componentList = ctx.getActivities()
            3 -> this.componentList = ctx.getLauncherActivities()
        }
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
        } else if ((i != 2 && i != 3) || this.cursor >= this.componentList!!.size) {
            return null
        } else {
            val list = this.componentList!!
            val i3 = this.cursor
            this.cursor = i3 + 1
            return getSmaliPath(list[i3])
        }
    }

    private fun getSmaliPath(clsName: String?): String? {
        var path = getRelativePath("smali", clsName, true)
        var index = 2
        while (path == null && index < 8) {
            path = getRelativePath("smali_classes$index", clsName, true)
            index++
        }
        if (path == null) {
            return getRelativePath("smali", clsName, false)
        }
        return path
    }

    private fun getRelativePath(smaliFolderName: String, clsName: String?, notExistRetNull: Boolean): String? {
        val relativePath = smaliFolderName + "/" + clsName!!.replace(".", "/") + ".smali"
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
            2, 3 -> return this.componentList!!.contains(clsName)
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
