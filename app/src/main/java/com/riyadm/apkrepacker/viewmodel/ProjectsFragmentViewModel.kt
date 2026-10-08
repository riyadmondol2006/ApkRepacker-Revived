package com.riyadm.apkrepacker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.riyadm.apkrepacker.ui.projectlist.ProjectItem
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.viewmodel.projects.ProjectLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProjectsFragmentViewModel(application: Application) : AndroidViewModel(application) {

    private val loader: ProjectLoader = ProjectLoader.getInstance(application)

    val projects: LiveData<List<ProjectItem>>
        get() = loader

    fun refresh() = loader.loadProjects()

    fun deleteProject(position: Int) {
        loader.value?.getOrNull(position)?.let(::deleteProject)
    }

    /** Deletes the project's folder, then rescans. [onDone] runs on the main thread with the outcome. */
    fun deleteProject(item: ProjectItem, onDone: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                runCatching { FileUtil.deleteFile(File(item.appProjectPath)) }.isSuccess
            }
            loader.loadProjects()
            onDone?.invoke(deleted)
        }
    }
}
