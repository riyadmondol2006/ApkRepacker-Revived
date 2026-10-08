package com.riyadm.apkrepacker.ui.apkbuilder

/** Progress of a build task: the step that just started, its 1-based index and the expected step count. */
class TaskStepInfo {
    @JvmField
    var stepDescription: String? = null

    @JvmField
    var stepIndex: Int = 0

    @JvmField
    var stepTotal: Int = 0
}
