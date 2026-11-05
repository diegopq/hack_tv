package io.github.diegopq.hacktv

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.diegopq.hacktv.data.AppsRepository
import io.github.diegopq.hacktv.data.HomeLayout
import io.github.diegopq.hacktv.data.HomeLayoutStore
import io.github.diegopq.hacktv.data.Inputs
import io.github.diegopq.hacktv.data.LauncherApp
import io.github.diegopq.hacktv.data.RecommendationSet
import io.github.diegopq.hacktv.data.Tmdb
import io.github.diegopq.hacktv.data.TvInputItem
import io.github.diegopq.hacktv.keys.RemoteKeys
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

    /** The banner's recommendations from TMDB; [RecommendationSet.NONE] without a key or before the first download. */
    private val _recommendations = MutableStateFlow(RecommendationSet.NONE)
    val recommendations: StateFlow<RecommendationSet> = _recommendations.asStateFlow()
    private var recommendationsJob: Job? = null
    private var recommendationsFor: Set<String> = emptySet()

    /** The user's order and hidden apps for My apps. */
    private val _layout = MutableStateFlow(HomeLayoutStore.load(application))
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    /** Whether the remote-button service is on, for the card on the Settings page. */
    private val _keyServiceOn = MutableStateFlow(false)
    val keyServiceOn: StateFlow<Boolean> = _keyServiceOn.asStateFlow()

    /** Installing, updating or removing an app refreshes the tiles while Home is open. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshApps()
    }

    /**
     * After a reboot the launcher starts before the TV has registered its HDMI inputs, so the first list
     * is empty. The TV announces each input as it comes up; the list follows those announcements.
     */
    private val inputCallback = object : TvInputManager.TvInputCallback() {
        override fun onInputAdded(inputId: String) = refreshInputs()
        override fun onInputRemoved(inputId: String) = refreshInputs()
        override fun onInputUpdated(inputId: String) = refreshInputs()
        override fun onTvInputInfoUpdated(inputInfo: TvInputInfo) = refreshInputs()
    }
    private val tvInputs: TvInputManager? = application.getSystemService(TvInputManager::class.java)

    init {
        refreshApps()
        refreshInputs()
        runCatching { tvInputs?.registerCallback(inputCallback, Handler(Looper.getMainLooper())) }
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
        // Wait for the app list: without it the launcher can't tell which services are installed.
        if (!Tmdb.isConfigured || _apps.value.isEmpty()) return
        val context = getApplication<Application>()
        val apps = _apps.value
        val installed = apps.map { it.packageName }.toSet()
        if (recommendationsJob?.isActive == true) {
            if (installed == recommendationsFor) return
            recommendationsJob?.cancel()   // the installed apps changed: start over with the new set
        }
        recommendationsFor = installed
        recommendationsJob = viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { Tmdb.cached(context, apps) }
            if (fresh != null) {
                _recommendations.value = fresh
                return@launch
            }
            withContext(Dispatchers.IO) { Tmdb.cached(context, apps, allowStale = true) }
                ?.let { _recommendations.value = it }
            Tmdb.refresh(context, apps)?.let { _recommendations.value = it }
        }
    }

    /** Saves the order of My apps after the user moved an app. */
    fun saveOrder(packages: List<String>) = updateLayout { it.copy(order = packages) }

    fun setHidden(packageName: String, hidden: Boolean) = updateLayout {
        it.copy(hidden = if (hidden) it.hidden + packageName else it.hidden - packageName)
    }

    private fun updateLayout(change: (HomeLayout) -> HomeLayout) {
        val updated = change(_layout.value)
        _layout.value = updated
        HomeLayoutStore.save(getApplication(), updated)
    }

    /**
     * Turns the remote buttons on or off from the launcher's Settings card. Returns the new state, or null
     * when the launcher can't change the setting itself (no WRITE_SECURE_SETTINGS): then the TV's
     * Accessibility settings have to be opened instead.
     */
    fun toggleKeyService(): Boolean? {
        val context = getApplication<Application>()
        if (!RemoteKeys.canWriteSettings(context)) return null
        val turnOn = !RemoteKeys.isEnabled(context)
        RemoteKeys.setWanted(context, turnOn)
        if (turnOn) RemoteKeys.enable(context) else RemoteKeys.disable(context)
        refreshKeyService()
        return turnOn
    }

    fun refreshKeyService() {
        _keyServiceOn.value = RemoteKeys.isEnabled(getApplication())
    }

    override fun onCleared() {
        runCatching { tvInputs?.unregisterCallback(inputCallback) }
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }
}
