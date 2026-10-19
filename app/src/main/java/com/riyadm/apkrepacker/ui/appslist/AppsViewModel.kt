package com.riyadm.apkrepacker.ui.appslist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.riyadm.apkrepacker.utils.PackageMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BackupRepository.getInstance(application)

    private var currentQuery = FilterQuery()
    private var filterJob: Job? = null

    private val filtered = MutableLiveData<List<PackageMeta>>(emptyList())
    private val loadingLiveData = MutableLiveData(true)

    // The repository outlives this view model; observeForever is paired with removeObserver in onCleared.
    private val repositoryObserver = Observer<BackupRepository.Scan?> { applyFilter() }

    init {
        repository.scan.observeForever(repositoryObserver)
    }

    /** The packages that match the current filter. */
    val packages: LiveData<List<PackageMeta>>
        get() = filtered

    /** True until the first package scan has listed what the filter shows (system apps come last). */
    val loading: LiveData<Boolean>
        get() = loadingLiveData

    fun filter(query: String, splitsOnly: Boolean, includeSystemApps: Boolean) {
        currentQuery = FilterQuery(query, splitsOnly, includeSystemApps)
        applyFilter()
    }

    private fun applyFilter() {
        val filterQuery = currentQuery
        val scan = repository.scan.value
        val raw = scan?.packages.orEmpty()
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            filtered.value = withContext(Dispatchers.Default) { raw.filter { filterQuery.matches(it) } }
            // Set after the list, so the empty state never flashes between the two.
            val loading = scan == null || (filterQuery.includeSystemApps && !scan.systemApps)
            if (loadingLiveData.value != loading) loadingLiveData.value = loading
        }
    }

    override fun onCleared() {
        repository.scan.removeObserver(repositoryObserver)
    }

    private data class FilterQuery(
        val query: String = "",
        val splitsOnly: Boolean = false,
        val includeSystemApps: Boolean = false,
    ) {
        private val needle = query.trim().lowercase(Locale.getDefault())

        fun matches(app: PackageMeta): Boolean {
            if (splitsOnly && !app.hasSplits) return false
            if (!includeSystemApps && app.isSystemApp) return false
            if (needle.isEmpty()) return true

            val label = app.label.orEmpty().lowercase(Locale.getDefault())
            val wordMatch = label.split(WHITESPACE).any { it.startsWith(needle) }
            return wordMatch || label.startsWith(needle) ||
                app.packageName.lowercase(Locale.getDefault()).contains(needle)
        }

        private companion object {
            val WHITESPACE = Regex("\\s+")
        }
    }
}
