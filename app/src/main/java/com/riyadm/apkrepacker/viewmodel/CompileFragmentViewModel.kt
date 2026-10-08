package com.riyadm.apkrepacker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.riyadm.apkrepacker.ui.apkbuilder.BuildStepRow
import com.riyadm.apkrepacker.ui.apkbuilder.BuildStepState

class CompileFragmentViewModel(application: Application) : AndroidViewModel(application) {

    /** True once the build service was started (or the build options sheet was confirmed). */
    var buildStarted: Boolean = false

    /** Index of the step that is running (1-based, 0 before the first) and the expected step count. */
    var stepIndex: Int = 0
        private set
    var stepTotal: Int = 0
        private set
    private val stepDescriptions = HashMap<Int, String>()

    /** The build service reported that step [index] of [total] started. */
    fun onStep(index: Int, total: Int, description: String?) {
        stepIndex = index
        stepTotal = maxOf(total, index)
        if (!description.isNullOrBlank()) stepDescriptions[index] = description
    }

    /** The rows of the step list; once [finished] every step counts as done. */
    fun stepRows(finished: Boolean): List<BuildStepRow> =
        (1..stepTotal).map { number ->
            val state = when {
                finished || number < stepIndex -> BuildStepState.Done
                number == stepIndex -> BuildStepState.Running
                else -> BuildStepState.Pending
            }
            BuildStepRow(number, stepDescriptions[number], state)
        }
}
