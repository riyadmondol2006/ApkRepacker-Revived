package com.jecelyin.editor.v2.manager

import java.io.File

interface ITabManager {
    fun newTab(file: File?): Boolean

    fun getTabCount(): Int

    fun getCurrentTab(): Int

    fun setCurrentTab(index: Int)

    fun closeAllTab()

    fun closeTab(position: Int)
}
