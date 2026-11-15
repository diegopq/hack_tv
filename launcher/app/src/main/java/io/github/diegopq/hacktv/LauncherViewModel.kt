package io.github.diegopq.hacktv

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.tv.TvContract
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
import io.github.diegopq.hacktv.data.AppAnnouncements
import io.github.diegopq.hacktv.data.AppFeatured
import io.github.diegopq.hacktv.data.FeaturedItem
import io.github.diegopq.hacktv.data.NewSeason
import io.github.diegopq.hacktv.data.NewSeasons
import io.github.diegopq.hacktv.data.RecentDismissals
import io.github.diegopq.hacktv.data.RecentItem
import io.github.diegopq.hacktv.data.RecentlyPlayed
import io.github.diegopq.hacktv.data.RecommendationSet
import io.github.diegopq.hacktv.data.Tmdb
import io.github.diegopq.hacktv.data.TvInputItem
import io.github.diegopq.hacktv.keys.RemoteKeys
import io.github.diegopq.hacktv.media.PlayHistory
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    /** Continue watching from the apps, plus the launcher's own history without anything already in it. */
    private val _watchNext = MutableStateFlow<List<RecentItem>>(emptyList())
    private val recentCandidates: StateFlow<List<RecentItem>> =
        combine(_watchNext, PlayHistory.items, RecentDismissals.removed) { watchNext, history, removed ->
            // An app's "new season" announcement belongs to New in your apps, not to what was played.
            // History saved before YouTube and music apps were left out stays hidden too.
            val shows = history.filter { RecentlyPlayed.isMoviesOrSeriesApp(it.packageName) }
            RecentlyPlayed.merge(watchNext.filterNot { it.isNewFromApp }, shows).filter { RecentDismissals.isShown(it, removed) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** New seasons of the series the user watches, without the ones they removed. */
    /** New in your apps: what the apps announce as new, then the new seasons TMDB finds. */
    val newSeasons: StateFlow<List<NewSeason>> =
        combine(_watchNext, NewSeasons.items, NewSeasons.dismissed, AppAnnouncements::merge)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recent: StateFlow<List<RecentItem>> = recentCandidates
        .map { it.take(RecentlyPlayed.MAX_ITEMS) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The user's order and hidden apps for My apps. */
    private val _layout = MutableStateFlow(HomeLayoutStore.load(application))
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    /** Every title the installed apps feature on the TV's home screen, with their links. */
    private val _featured = MutableStateFlow<List<FeaturedItem>>(emptyList())
    val featured: StateFlow<List<FeaturedItem>> = _featured.asStateFlow()

    /** Featured in your apps: a few from each app, in My apps order, without what the other rows show. */
    val featuredRow: StateFlow<List<FeaturedItem>> =
        combine(_featured, recent, newSeasons, _apps, _layout) { all, recent, new, apps, layout ->
            val shown = (recent.map { it.title } + new.map { it.title }).map(AppFeatured::key).toSet()
            AppFeatured.row(all, AppsRepository.homeOrder(apps, layout).map { it.packageName }, shown)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /** Apps update their Continue watching rows as the user watches; the row follows while Home is open. */
    private val watchNextObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshRecent()
    }

    init {
        refreshApps()
        refreshInputs()
        runCatching { tvInputs?.registerCallback(inputCallback, Handler(Looper.getMainLooper())) }
        runCatching {
            application.contentResolver.registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, watchNextObserver)
        }
        refreshRecent()
        viewModelScope.launch { recentCandidates.collect { prefetchArtwork(it.take(RecentlyPlayed.MAX_ITEMS + PREFETCH_EXTRA).mapNotNull { r -> r.imageUri }) } }
        viewModelScope.launch { newSeasons.collect { prefetchArtwork(it.mapNotNull { n -> n.imageUrl }) } }
        viewModelScope.launch { featuredRow.collect { prefetchArtwork(it.mapNotNull { f -> f.imageUri }) } }
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
            refreshRecent()   // the featured titles only count installed apps
        }
    }

    /** Remembers what's connected to an input ("Xbox"), or forgets it with null. */
    fun renameInput(inputId: String, name: String?) {
        Inputs.setName(getApplication(), inputId, name)
        refreshInputs()
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

    fun refreshRecent() {
        val context = getApplication<Application>()
        PlayHistory.load(context)
        RecentDismissals.load(context)
        viewModelScope.launch(Dispatchers.IO) {
            val watchNext = RecentlyPlayed.loadWatchNext(context)
            _watchNext.value = watchNext
            _featured.value = AppFeatured.load(context, _apps.value.map { it.packageName }.toSet())
            // The series in Continue watching are the ones whose new seasons "New in your apps" looks for.
            NewSeasons.load(context)
            val newShows = NewSeasons.track(context, watchNext)
            NewSeasons.refresh(context, force = newShows)
        }
    }

    /** Hides a "New in your apps" card for good. */
    fun removeNewSeason(id: String) = NewSeasons.dismiss(getApplication(), id)

    /**
     * Downloads the artwork of the Recently played cards before they are on screen: the ones further right,
     * and the next few that move into the row when a card is removed, which otherwise showed up blank
     * until their image arrived. Coil keeps them in its caches, so cards already loaded cost nothing.
     */
    private fun prefetchArtwork(uris: List<String>) {
        val context = getApplication<Application>()
        val loader = SingletonImageLoader.get(context)
        // Only downloaded to the disk cache: decoding them into memory ahead of time filled the memory cache
        // with cards that may never be shown. Showing one then only decodes a small local file.
        uris.forEach { uri ->
            loader.enqueue(
                ImageRequest.Builder(context).data(uri).size(400, 225)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build(),
            )
        }
    }

    /** Hides a Recently played card until it is played again. */
    fun removeRecent(item: RecentItem) = RecentDismissals.remove(getApplication(), item)

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

    override fun onCleared() {
        runCatching { tvInputs?.unregisterCallback(inputCallback) }
        getApplication<Application>().contentResolver.unregisterContentObserver(watchNextObserver)
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }

    private companion object {
        /** Cards beyond the row whose artwork is fetched ahead, to replace removed ones. */
        const val PREFETCH_EXTRA = 5
    }
}
