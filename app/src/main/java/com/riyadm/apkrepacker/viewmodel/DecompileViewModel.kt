package com.riyadm.apkrepacker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.service.DecompileService
import com.riyadm.apkrepacker.service.DecompileSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * What the decompile screen shows: the log and the result of a run. The run itself lives in
 * [DecompileService] (a foreground service), so it keeps going when the screen is closed, rotated
 * or recreated; this just attaches to its [DecompileSession].
 */
class DecompileViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface State {
        data object Running : State
        data class Done(val project: File) : State
        data object Failed : State
    }

    private val noLines = ArrayList<String>()
    private var session: DecompileSession? = null

    /** Log lines in arrival order; only touched on the main thread. */
    val lines: List<String>
        get() = session?.lines ?: noLines

    private val _lineCount = MutableStateFlow(0)
    val lineCount: StateFlow<Int> = _lineCount

    private val _state = MutableStateFlow<State>(State.Running)
    val state: StateFlow<State> = _state

    private var started = false

    /** True once this ViewModel attached to a run (survives rotation, not process death). */
    val isStarted: Boolean
        get() = started

    /** The whole log as text, one line per entry. */
    val logText: String
        get() = lines.joinToString(separator = "\n", postfix = "\n")

    /**
     * Starts the decompile of run [runId], or attaches to it if it already runs (or has finished)
     * in this process. Later calls (view recreated) do nothing.
     */
    fun start(apk: File, options: DecodeOptions, runId: String) {
        if (started) return
        started = true
        val attached = DecompileService.start(getApplication(), runId, apk, options)
        session = attached
        viewModelScope.launch {
            launch { attached.lineCount.collect { _lineCount.value = it } }
            attached.finished.collect { outcome ->
                _state.value = when {
                    outcome == null -> State.Running
                    outcome.project != null -> State.Done(outcome.project)
                    else -> State.Failed
                }
            }
        }
    }
}
