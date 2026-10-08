package com.riyadm.apkrepacker.ui.projectlist

/** One decompiled project on disk. [isChecked] is the "actions revealed" state of its row. */
class ProjectItem(
    val appIcon: String?,
    val appName: String?,
    val appPackage: String?,
    val appProjectPath: String,
    val apkPatch: String?,
    val appVersionName: String?,
    val appVersionCode: String?,
) {
    var isChecked = false

    constructor(
        icon: String?,
        appName: String?,
        appPackage: String?,
        appProjectPath: String,
        appVersionName: String?,
        appVersionCode: String?,
    ) : this(icon, appName, appPackage, appProjectPath, null, appVersionName, appVersionCode)
}
