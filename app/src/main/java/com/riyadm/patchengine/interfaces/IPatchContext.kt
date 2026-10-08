package com.riyadm.patchengine.interfaces

interface IPatchContext {

    fun getActivities(): List<String>?

    fun getApplicationManifest(): String?

    fun getDecodeRootPath(): String?

    fun getLauncherActivities(): List<String>?

    fun getPatchNames(): List<String>?

    fun getSmaliFolders(): List<String>?

    // String getString(int resourceId);

    fun getVariableValue(str: String?): String?

    fun error(resourceId: Int, vararg objArr: Any?)

    fun info(resourceId: Int, bold: Boolean, vararg objArr: Any?)

    fun info(str: String?, bold: Boolean, vararg objArr: Any?)

    fun patchFinished()

    fun setVariableValue(key: String?, value: String?)
}
