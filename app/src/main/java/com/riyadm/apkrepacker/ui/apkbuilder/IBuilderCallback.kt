package com.riyadm.apkrepacker.ui.apkbuilder

import java.io.File

/** What a build task reports to its host (the build service). All calls may come from a background thread. */
interface IBuilderCallback {

    fun setTaskStepInfo(taskStepInfo: TaskStepInfo?)

    fun taskFailed(str: String?)

    fun taskSucceed(file: File?)

    fun taskTime(time: Long)
}
