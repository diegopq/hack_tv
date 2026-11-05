package io.github.diegopq.hacktv

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.diegopq.hacktv.data.AppsRepository
import io.github.diegopq.hacktv.data.Inputs
import io.github.diegopq.hacktv.data.LauncherApp
import io.github.diegopq.hacktv.data.RecommendationSet
import io.github.diegopq.hacktv.data.SampleContent
import io.github.diegopq.hacktv.data.Tmdb
import io.github.diegopq.hacktv.data.TvInputItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val _apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val apps: StateFlow<List<LauncherApp>> = _apps.asStateFlow()

    private val _inputs = MutableStateFlow<List<TvInputItem>>(emptyList())
    val inputs: StateFlow<List<TvInputItem>> = _inputs.asStateFlow()

    /** The banner's recommendations: TMDB when a key is set and the network answers, the samples otherwise. */
    private val _recommendations = MutableStateFlow(SampleContent.set())
    val recommendations: StateFlow<RecommendationSet> = _recommendations.asStateFlow()
    private var recommendationsJob: Job? = null

    /** Installing, updating or removing an app refreshes the tiles while Home is open. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshApps()
    }

    init {
        refreshApps()
        refreshInputs()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(application, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun refreshApps() {
        viewModelScope.launch {
            _apps.value = AppsRepository.load(getApplication())
            refreshRecommendations()
        }
    }

    fun refreshInputs() {
        viewModelScope.launch(Dispatchers.IO) { _inputs.value = Inputs.load(getApplication()) }
    }

    /**
     * Shows the cached TMDB set right away (even an old one), then downloads a new one when the
     * cache is older than a few hours. Cheap to call on every resume.
     */
    fun refreshRecommendations() {
        if (!Tmdb.isConfigured || recommendationsJob?.isActive == true) return
        val context = getApplication<Application>()
        val installed = _apps.value.map { it.packageName }.toSet()
        recommendationsJob = viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { Tmdb.cached(context, installed) }
            if (fresh != null) {
                _recommendations.value = fresh
                return@launch
            }
            withContext(Dispatchers.IO) { Tmdb.cached(context, installed, allowStale = true) }
                ?.let { _recommendations.value = it }
            Tmdb.refresh(context, installed)?.let { _recommendations.value = it }
        }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }
}
