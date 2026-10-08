package com.riyadm.apkrepacker.database.entity

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

@Keep
class Project {

    @SerializedName("project_name")
    @Expose
    private var mProjectName: String? = null

    @SerializedName("project_notes")
    @Expose
    private var mProjectNotes: String? = null

    fun setProjectNotes(notes: String?) {
        mProjectNotes = notes
    }

    fun getProjectNotes(): String? {
        return mProjectNotes
    }

    fun getProjectName(): String? {
        return mProjectName
    }

    fun setProjectName(projectName: String?) {
        mProjectName = projectName
    }


}
