package com.riyadm.apkrepacker.viewmodel.projects

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

@Keep
class VersionInfo {
    @SerializedName("versionName")
    @Expose
    var versionName: String? = null

    @SerializedName("versionCode")
    @Expose
    var versionCode: String? = null
}
