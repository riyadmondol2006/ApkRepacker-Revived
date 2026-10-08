package com.riyadm.apkrepacker.viewmodel.projects

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/** Gson shape of a project's metadata file. */
@Keep
class ProjectItemJson {

    @SerializedName("apkFileName")
    @Expose
    @JvmField
    var apkFileName: String? = null

    @SerializedName("apkFilePackageName")
    @Expose
    @JvmField
    var apkFilePackageName: String? = null

    @SerializedName("apkFileIcon")
    @Expose
    @JvmField
    var apkFileIcon: String? = null

    @SerializedName("apkFilePatch")
    @Expose
    @JvmField
    var apkFilePatch: String? = null

    @SerializedName("VersionInfo")
    @Expose
    @JvmField
    var versionInfo: VersionInfo? = null
}
