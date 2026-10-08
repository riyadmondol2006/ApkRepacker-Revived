package com.riyadm.apkrepacker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.FragmentAboutProjectBinding
import com.riyadm.apkrepacker.database.ITabDatabase
import com.riyadm.apkrepacker.database.JsonDatabase
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.projectlist.ProjectItem
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.ProjectUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Full-screen info page of a project: where it lives, its package, size and a free-form note. */
class AboutProjectFragment : Fragment() {

    private var binding: FragmentAboutProjectBinding? = null
    private lateinit var database: ITabDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentAboutProjectBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = checkNotNull(binding)
        ui.toolbar.setNavigationOnClickListener { FragmentUtils.remove(this) }

        // The item is handed over in memory only; after process death there is nothing to show.
        val project = projectItem
        if (project == null) {
            FragmentUtils.remove(this)
            return
        }
        database = JsonDatabase.getInstance(requireContext())
        bindProject(ui, project)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun bindProject(ui: FragmentAboutProjectBinding, project: ProjectItem) {
        val context = requireContext()
        val path = project.appProjectPath

        ui.aboutHeader.tvAboutAppIcon.setImageDrawable(ProjectUtils.getProjectIconDrawable(project.appIcon, context))
        ui.aboutHeader.tvAboutAppName.text = project.appName
        ui.aboutHeader.tvAboutVersion.text = getString(R.string.about_version, project.appVersionName, project.appVersionCode)
        ui.labelPathPrj.text = path
        ui.labelAppPkgPrj.text = project.appPackage
        ui.labelDateWrite.text = FileUtil.getLastModified(File(path))
        ui.noteProject.setText(database.getProjectNotes(path))

        ui.labelProjectSize.setText(R.string.loading_short)
        viewLifecycleOwner.lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) {
                File(path).takeIf { it.isDirectory }?.let { FileUtil.getFormatFolderSize(context, it) }
            }
            ui.labelProjectSize.text = size
        }

        ui.fabSaveNotes.setOnClickListener {
            database.addProjectNotes(ui.noteProject.text.toString(), path)
            Snackbar.make(ui.root, R.string.project_notes_saved, Snackbar.LENGTH_SHORT)
                .setAnchorView(ui.fabSaveNotes)
                .show()
        }
        ui.fabSaveNotes.springIn(delayMs = 150L, fromScale = 0.6f)
    }

    companion object {
        const val TAG = "full_screen_dialog"
        private var projectItem: ProjectItem? = null

        @JvmStatic
        fun newInstance(projectItem: ProjectItem?): AboutProjectFragment {
            this.projectItem = projectItem
            return AboutProjectFragment()
        }
    }
}
