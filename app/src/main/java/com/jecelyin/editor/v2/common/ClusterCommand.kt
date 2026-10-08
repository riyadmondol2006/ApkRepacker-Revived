package com.jecelyin.editor.v2.common

import com.riyadm.apkrepacker.ide.editor.EditorDelegate

class ClusterCommand(private val buffer: ArrayList<EditorDelegate>?) {
    private var command: Command? = null

    fun setCommand(command: Command?) {
        this.command = command
    }

    fun doNextCommand() {
        if (buffer == null || buffer.size == 0) {
            return
        }
        val editorFragment = buffer.removeAt(0)
        if (!editorFragment.doCommand(command)) {
            doNextCommand()
        }
    }
}
