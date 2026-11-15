package io.github.diegopq.hacktv.ui

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.diegopq.hacktv.R
import io.github.diegopq.hacktv.data.AppsRepository
import io.github.diegopq.hacktv.data.Inputs
import io.github.diegopq.hacktv.data.LauncherApp
import io.github.diegopq.hacktv.data.AppFeatured
import io.github.diegopq.hacktv.data.FeaturedItem
import io.github.diegopq.hacktv.data.NewSeason
import io.github.diegopq.hacktv.data.RecentItem
import io.github.diegopq.hacktv.data.RecentlyPlayed
import io.github.diegopq.hacktv.data.Recommendation
import io.github.diegopq.hacktv.data.RecommendationSet
import io.github.diegopq.hacktv.data.SystemShortcut
import io.github.diegopq.hacktv.data.TvInputItem
import io.github.diegopq.hacktv.ui.theme.HackColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TOP_BAR_HEIGHT = 75.dp

private val RowPadding = PaddingValues(horizontal = 60.dp, vertical = 6.dp)
private val TileSize = Modifier.size(156.dp, 88.dp)

/**
 * Scrolls only as far as needed to show the focused item, keeping it clear of [startInset] (the top bar).
 * Compose's default on TV keeps the focused item at 30% of the screen, which pushed the banner off Home
 * as soon as the first app was focused.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun minimalScroll(startInset: Float, endInset: Float) = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = offset - startInset
        val end = offset + size - (containerSize - endInset)
        return when {
            start >= 0f && end <= 0f -> 0f
            start < 0f -> start
            else -> minOf(end, start)
        }
    }
}

/**
 * Moving Up or Down into a row always lands on its first item, not on the one focused last time, and a row
 * goes back to its start once the focus leaves it, so that first item is on screen when you come back.
 * [keepScroll] skips the reset while the row only lost focus to a dialog of its own.
 */
private fun Modifier.entersAtFirst(
    first: FocusRequester,
    state: LazyListState,
    scope: CoroutineScope,
    keepScroll: () -> Boolean = { false },
): Modifier = focusProperties {
    onEnter = {
        if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
            runCatching { first.requestFocus() }
        }
    }
}.onFocusChanged { if (!it.hasFocus && !keepScroll()) scope.launch { state.scrollToItem(0) } }

/** Gives a list [spec] while its items get the minimal horizontal scroll back for their own rows. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WithScrollSpec(spec: BringIntoViewSpec, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** Home: the recommendations banner, then the rows of apps, inputs and settings. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    homeApps: List<LauncherApp>,
    allApps: List<LauncherApp>,
    inputs: List<TvInputItem>,
    recent: List<RecentItem>,
    newSeasons: List<NewSeason>,
    featured: List<FeaturedItem>,
    featuredRow: List<FeaturedItem>,
    recommendations: RecommendationSet,
    listState: LazyListState,
    firstAppRequester: FocusRequester,
    onBackdrop: (BackdropContent) -> Unit,
    onOpen: (Intent) -> Unit,
    onMessage: (Int) -> Unit,
    onSaveOrder: (List<String>) -> Unit,
    onHide: (String) -> Unit,
    onRemoveRecent: (RecentItem) -> Unit,
    onRemoveNewSeason: (String) -> Unit,
) {
    val context = LocalContext.current
    // A random pick of titles from every installed service, new each time Home is shown;
    // it doesn't change with the focused app.
    val recs = remember(recommendations) { recommendations.shuffledPick() }
    var slide by remember(recommendations) { mutableIntStateOf(0) }
    val rec = recs.getOrNull(slide.mod(recs.size.coerceAtLeast(1)))
    val providerApp = remember(rec, allApps) {
        val pkg = rec?.providerPackage
        allApps.firstOrNull { it.packageName == pkg }
    }

    val density = LocalDensity.current
    val columnSpec = remember(density) { with(density) { minimalScroll(6.dp.toPx(), 22.dp.toPx()) } }
    val rowSpec = remember(density) { with(density) { minimalScroll(60.dp.toPx(), 60.dp.toPx()) } }

    // The banner and the first row of apps fit on screen together: focusing either brings the banner back.
    val scope = rememberCoroutineScope()
    // It waits a moment so it runs after the scroll that brings the focused item into view: when a row sends
    // the focus to its first item, both start together and the shorter one used to win, leaving the banner
    // scrolled away and invisible.
    var bannerJob by remember { mutableStateOf<Job?>(null) }
    fun showBanner() {
        bannerJob?.cancel()
        bannerJob = scope.launch {
            delay(80)
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(0)
            }
        }
    }
    val fadeDistance = with(density) { 120.dp.toPx() }

    // Hold OK on an app for its menu; Move picks it up and the arrows carry it along the row.
    var menuApp by remember { mutableStateOf<LauncherApp?>(null) }
    // Where focus goes once the menu closes: back to the app, or to its neighbor when it was hidden.
    val tileRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var pendingFocus by remember { mutableStateOf<String?>(null) }
    var movingPackage by remember { mutableStateOf<String?>(null) }
    var movingOrder by remember { mutableStateOf(homeApps) }
    val rowApps = if (movingPackage != null) movingOrder else homeApps
    val appsRowState = rememberLazyListState()

    fun startMove(app: LauncherApp) {
        movingOrder = homeApps
        movingPackage = app.packageName
    }
    fun moveBy(step: Int) {
        val from = movingOrder.indexOfFirst { it.packageName == movingPackage }
        val to = from + step
        if (from < 0 || to !in movingOrder.indices) return
        movingOrder = movingOrder.toMutableList().apply { add(to, removeAt(from)) }
    }
    // The app being moved keeps the focus, so nothing scrolls the row for it: after each step, once the row
    // has laid out the new order, it scrolls just enough to show the whole app inside the row's margins.
    LaunchedEffect(movingOrder, movingPackage) {
        val pkg = movingPackage ?: return@LaunchedEffect
        withFrameNanos { }
        val info = appsRowState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == pkg }
        if (item == null) {
            val index = movingOrder.indexOfFirst { it.packageName == pkg }
            if (index >= 0) appsRowState.animateScrollToItem(index)
            return@LaunchedEffect
        }
        val end = info.viewportEndOffset - info.afterContentPadding
        val delta = when {
            item.offset < 0 -> item.offset.toFloat()
            item.offset + item.size > end -> (item.offset + item.size - end).toFloat()
            else -> 0f
        }
        if (delta != 0f) appsRowState.animateScrollBy(delta)
    }
    fun finishMove() {
        onSaveOrder(movingOrder.map { it.packageName })
        movingPackage = null
    }
    BackHandler(enabled = movingPackage != null) { finishMove() }

    // A focused Recently played card fills the screen behind Home with its artwork, as the banner does.
    var focusedRecent by remember { mutableStateOf<RecentItem?>(null) }
    // "New in your apps" works the same way: artwork behind Home, hold OK to remove a card.
    var focusedNew by remember { mutableStateOf<NewSeason?>(null) }
    var focusedFeatured by remember { mutableStateOf<FeaturedItem?>(null) }
    val featuredRowState = rememberLazyListState()
    val firstFeatured = remember { FocusRequester() }
    var menuNew by remember { mutableStateOf<NewSeason?>(null) }
    var pendingNewFocus by remember { mutableStateOf<String?>(null) }
    val newRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val newRowState = rememberLazyListState()
    val firstNew = remember { FocusRequester() }
    // Hold OK on a card to remove it; focus then goes to its neighbor, as when hiding an app.
    var menuRecent by remember { mutableStateOf<RecentItem?>(null) }
    var pendingRecentFocus by remember { mutableStateOf<String?>(null) }
    val recentRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val recentRowState = rememberLazyListState()
    val inputsRowState = rememberLazyListState()
    val systemRowState = rememberLazyListState()
    val firstRecent = remember { FocusRequester() }
    val firstInput = remember { FocusRequester() }
    val firstSystem = remember { FocusRequester() }
    // When the row changes order, a lazy row keeps showing the same first card, so a card that just moved to
    // the front (the one you came back from, usually) stayed off screen to the left. After each update the
    // row shows the focused card again, or goes back to the start when the focus is elsewhere. When the
    // focused card still fits on screen from the start, the row goes back to the start too, so a card that
    // came back in front of it (played again after being removed) isn't left hidden to the left.
    LaunchedEffect(recent) {
        if (pendingRecentFocus != null) return@LaunchedEffect   // the menu's own focus handling below
        delay(50)   // let the row lay out the new order first
        val focusedIndex = focusedRecent?.let { focused -> recent.indexOfFirst { it.id == focused.id } } ?: -1
        val target = focusedIndex.coerceAtLeast(0)
        val info = recentRowState.layoutInfo
        val cardSize = info.visibleItemsInfo.firstOrNull()?.size ?: return@LaunchedEffect
        val end = info.viewportEndOffset - info.afterContentPadding
        val perScreen = ((end + info.mainAxisItemSpacing) / (cardSize + info.mainAxisItemSpacing)).coerceAtLeast(1)
        val atStart = recentRowState.firstVisibleItemIndex == 0 && recentRowState.firstVisibleItemScrollOffset == 0
        val shown = info.visibleItemsInfo.firstOrNull { it.index == target }
        val fullyVisible = shown != null && shown.offset >= 0 && shown.offset + shown.size <= end
        when {
            target < perScreen -> if (!atStart) recentRowState.animateScrollToItem(0)
            !fullyVisible -> recentRowState.animateScrollToItem(target)
        }
    }

    // "Watch on …": the app's own link to the title when it features it, otherwise the app, or its store page.
    fun watch(r: Recommendation) {
        val pkg = r.providerPackage
        val direct = RecentlyPlayed.linkIntent(context, AppFeatured.linkFor(featured, pkg, r.title))
        val app = allApps.firstOrNull { it.packageName == pkg }
        onOpen(
            direct ?: app?.let { AppsRepository.launchIntent(context, it) }
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    // "More info" opens the title's own page over Home; Back closes it and returns to the banner.
    var detailFor by remember { mutableStateOf<Recommendation?>(null) }

    // Without recommendations the banner shows the focused app of My apps instead.
    var spotlightPackage by remember { mutableStateOf<String?>(null) }
    val spotlight = homeApps.firstOrNull { it.packageName == spotlightPackage } ?: homeApps.firstOrNull()
    val showRecommendations = rec != null

    LaunchedEffect(rec?.imageUrl, spotlight?.packageName, showRecommendations, focusedRecent?.imageUri, focusedNew?.imageUrl, focusedFeatured?.imageUri) {
        val recentArt = focusedRecent?.imageUri ?: focusedNew?.imageUrl ?: focusedFeatured?.imageUri
        // A card's artwork only replaces the background once the focus rests on it: moving along a row
        // started a full-screen crossfade on every press, which the TV's GPU couldn't keep up with.
        if (recentArt != null) delay(350)
        when {
            recentArt != null -> onBackdrop(BackdropContent.Image(recentArt))
            rec != null -> onBackdrop(BackdropContent.Image(rec.imageUrl))
            spotlight != null -> onBackdrop(BackdropContent.Glow(Color(spotlight.accent ?: 0xFF24384C.toInt())))
        }
    }
    // A new recommendation every 15 seconds, enough to read a synopsis. While the banner's buttons have the
    // focus it stays put (← → still change it); the count starts over once the focus leaves.
    var heroFocused by remember { mutableStateOf(false) }
    LaunchedEffect(slide, recs, heroFocused) {
        if (heroFocused) return@LaunchedEffect
        delay(15_000)
        if (recs.isNotEmpty()) slide = (slide + 1).mod(recs.size)
    }

    WithScrollSpec(columnSpec) {
    // The list starts below the top bar: a focus group that overlapped the bar was never "below" it, so
    // pressing Down on Search or a tab could not enter Home at all.
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 48.dp),
        modifier = Modifier.padding(top = TOP_BAR_HEIGHT),
    ) {
        if (rec == null && spotlight != null) item(key = "hero") {
            AppSpotlight(
                app = spotlight,
                onOpen = { onOpen(AppsRepository.launchIntent(context, spotlight)) },
                onInfo = { onOpen(appInfoIntent(spotlight.packageName)) },
                modifier = Modifier
                    .onFocusChanged { if (it.hasFocus) showBanner() }
                    .graphicsLayer {
                        alpha = if (listState.firstVisibleItemIndex > 0) 0f
                        else 1f - (listState.firstVisibleItemScrollOffset / fadeDistance).coerceIn(0f, 1f)
                    },
            )
        }
        if (rec != null) item(key = "hero") {
            Hero(
                recommendation = rec,
                index = slide.mod(recs.size),
                count = recs.size,
                providerApp = providerApp,
                onWatch = { watch(rec) },
                onInfo = { detailFor = rec },
                onPrevious = { slide = (slide - 1).mod(recs.size) },
                onNext = { slide = (slide + 1).mod(recs.size) },
                modifier = Modifier
                    .onFocusChanged {
                        heroFocused = it.hasFocus
                        if (it.hasFocus) showBanner()
                    }
                    // Fades out as the rows scroll up under the top bar.
                    .graphicsLayer {
                        alpha = if (listState.firstVisibleItemIndex > 0) 0f
                        else 1f - (listState.firstVisibleItemScrollOffset / fadeDistance).coerceIn(0f, 1f)
                    },
            )
        }
        item(key = "apps") {
            RowTitle(
                stringResource(R.string.row_my_apps),
                hint = stringResource(if (movingPackage != null) R.string.moving_hint else R.string.my_apps_hint),
            )
            WithScrollSpec(rowSpec) {
            LazyRow(
                state = appsRowState,
                contentPadding = RowPadding,
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                modifier = Modifier.entersAtFirst(firstAppRequester, appsRowState, scope, keepScroll = { menuApp != null }),
            ) {
                itemsIndexed(rowApps, key = { _, app -> app.packageName }) { i, app ->
                    val moving = movingPackage == app.packageName
                    AppTile(
                        app = app,
                        onClick = {
                            if (moving) finishMove()
                            else if (movingPackage == null) onOpen(AppsRepository.launchIntent(context, app))
                        },
                        onLongClick = { if (movingPackage == null) menuApp = app },
                        moving = moving,
                        modifier = TileSize
                            .then(if (i == 0) Modifier.focusRequester(firstAppRequester) else Modifier)
                            .focusRequester(tileRequesters.getOrPut(app.packageName) { FocusRequester() })
                            .onPreviewKeyEvent { event ->
                                if (!moving) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft, Key.DirectionRight -> {
                                        if (event.type == KeyEventType.KeyDown) moveBy(if (event.key == Key.DirectionLeft) -1 else 1)
                                        true
                                    }
                                    // Up and Down would drop the app somewhere else on the screen: they do nothing.
                                    Key.DirectionUp, Key.DirectionDown -> true
                                    else -> false
                                }
                            },
                        onFocused = {
                            showBanner()
                            spotlightPackage = app.packageName
                        },
                    )
                }
            }
            }
            Spacer(Modifier.height(22.dp))
        }
        if (recent.isNotEmpty()) {
            item(key = "recent") {
                RowTitle(stringResource(R.string.row_recent), hint = stringResource(R.string.recent_hint))
                WithScrollSpec(rowSpec) {
                LazyRow(
                    state = recentRowState,
                    contentPadding = RowPadding,
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    modifier = Modifier
                        .entersAtFirst(firstRecent, recentRowState, scope, keepScroll = { menuRecent != null })
                        .onFocusChanged { if (!it.hasFocus) focusedRecent = null },
                ) {
                    itemsIndexed(recent, key = { _, item -> item.id }) { i, item ->
                        val app = remember(item.packageName, allApps) { allApps.firstOrNull { it.packageName == item.packageName } }
                        MediaTile(
                            item = item,
                            app = app,
                            subtitle = recentSubtitle(item, app),
                            onClick = {
                                val intent = RecentlyPlayed.openIntent(context, item, app)
                                if (intent != null) onOpen(intent)
                                else Toast.makeText(context, context.getString(R.string.cannot_open, item.title), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(200.dp, 112.dp)
                                .then(if (i == 0) Modifier.focusRequester(firstRecent) else Modifier)
                                .focusRequester(recentRequesters.getOrPut(item.id) { FocusRequester() }),
                            onLongClick = { menuRecent = item },
                            onFocused = { focusedRecent = item },
                        )
                    }
                }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
        if (newSeasons.isNotEmpty()) {
            item(key = "new") {
                RowTitle(stringResource(R.string.row_new), hint = stringResource(R.string.new_hint))
                WithScrollSpec(rowSpec) {
                LazyRow(
                    state = newRowState,
                    contentPadding = RowPadding,
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    modifier = Modifier
                        .entersAtFirst(firstNew, newRowState, scope, keepScroll = { menuNew != null })
                        .onFocusChanged { if (!it.hasFocus) focusedNew = null },
                ) {
                    itemsIndexed(newSeasons, key = { _, n -> n.id }) { i, season ->
                        val app = remember(season.packageName, allApps) { allApps.firstOrNull { it.packageName == season.packageName } }
                        MediaTile(
                            item = season.asCard(),
                            app = app,
                            subtitle = newSeasonSubtitle(season),
                            onClick = {
                                val intent = RecentlyPlayed.linkIntent(context, season.intentUri)
                                    ?: app?.let { AppsRepository.launchIntent(context, it) }
                                if (intent != null) onOpen(intent)
                                else Toast.makeText(context, context.getString(R.string.cannot_open, season.title), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(200.dp, 112.dp)
                                .then(if (i == 0) Modifier.focusRequester(firstNew) else Modifier)
                                .focusRequester(newRequesters.getOrPut(season.id) { FocusRequester() }),
                            onLongClick = { menuNew = season },
                            onFocused = { focusedNew = season },
                        )
                    }
                }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
        if (featuredRow.isNotEmpty()) {
            item(key = "featured") {
                RowTitle(stringResource(R.string.row_featured), hint = stringResource(R.string.featured_hint))
                WithScrollSpec(rowSpec) {
                LazyRow(
                    state = featuredRowState,
                    contentPadding = RowPadding,
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    modifier = Modifier
                        .entersAtFirst(firstFeatured, featuredRowState, scope)
                        .onFocusChanged { if (!it.hasFocus) focusedFeatured = null },
                ) {
                    itemsIndexed(featuredRow, key = { _, f -> f.id }) { i, title ->
                        val app = remember(title.packageName, allApps) { allApps.firstOrNull { it.packageName == title.packageName } }
                        MediaTile(
                            item = RecentItem(id = title.id, packageName = title.packageName, title = title.title,
                                imageUri = title.imageUri, lastPlayed = 0, fromApp = true),
                            app = app,
                            subtitle = app?.label.orEmpty(),
                            onClick = {
                                val intent = RecentlyPlayed.linkIntent(context, title.intentUri)
                                    ?: app?.let { AppsRepository.launchIntent(context, it) }
                                if (intent != null) onOpen(intent)
                                else Toast.makeText(context, context.getString(R.string.cannot_open, title.title), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(200.dp, 112.dp)
                                .then(if (i == 0) Modifier.focusRequester(firstFeatured) else Modifier),
                            onFocused = { focusedFeatured = title },
                        )
                    }
                }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
        if (inputs.isNotEmpty()) {
            item(key = "inputs") {
                RowTitle(stringResource(R.string.row_inputs))
                WithScrollSpec(rowSpec) {
                LazyRow(
                    state = inputsRowState,
                    contentPadding = RowPadding,
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    modifier = Modifier.entersAtFirst(firstInput, inputsRowState, scope),
                ) {
                    itemsIndexed(inputs, key = { _, input -> input.id }) { i, input ->
                        GlassTile(
                            icon = R.drawable.ic_hdmi,
                            title = input.label,
                            subtitle = stringResource(R.string.input_sub),
                            onClick = { onOpen(Inputs.switchIntent(input)) },
                            modifier = TileSize.then(if (i == 0) Modifier.focusRequester(firstInput) else Modifier),
                        )
                    }
                }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
        item(key = "system") {
            RowTitle(stringResource(R.string.row_system))
            WithScrollSpec(rowSpec) {
            LazyRow(
                state = systemRowState,
                contentPadding = RowPadding,
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                modifier = Modifier.entersAtFirst(firstSystem, systemRowState, scope),
            ) {
                itemsIndexed(SystemShortcut.HOME_ROW) { i, shortcut ->
                    GlassTile(
                        icon = shortcut.icon,
                        title = stringResource(shortcut.title),
                        subtitle = stringResource(shortcut.subtitle),
                        onClick = { onOpen(shortcut.intent(context)) },
                        modifier = TileSize.then(if (i == 0) Modifier.focusRequester(firstSystem) else Modifier),
                    )
                }
            }
            }
        }
    }
    }

    menuApp?.let { app ->
        AppMenu(
            app = app,
            hidden = false,
            canMove = true,
            onMove = { startMove(app) },
            onToggleHidden = {
                val index = homeApps.indexOfFirst { it.packageName == app.packageName }
                pendingFocus = (homeApps.getOrNull(index + 1) ?: homeApps.getOrNull(index - 1))?.packageName
                onHide(app.packageName)
                Toast.makeText(context, context.getString(R.string.hidden_toast, app.label), Toast.LENGTH_LONG).show()
            },
            onInfo = { onOpen(appInfoIntent(app.packageName)) },
            onDismiss = {
                menuApp = null
                pendingFocus = app.packageName
            },
        )
    }
    menuRecent?.let { item ->
        RecentMenu(
            item = item,
            app = allApps.firstOrNull { it.packageName == item.packageName },
            onRemove = {
                val index = recent.indexOfFirst { it.id == item.id }
                pendingRecentFocus = (recent.getOrNull(index + 1) ?: recent.getOrNull(index - 1))?.id
                onRemoveRecent(item)
                // The last card took the row with it: focus goes back to My apps.
                if (pendingRecentFocus == null) scope.launch { delay(50); runCatching { firstAppRequester.requestFocus() } }
            },
            onDismiss = {
                menuRecent = null
                pendingRecentFocus = item.id
            },
        )
    }
    detailFor?.let { r ->
        DetailPage(
            recommendation = r,
            providerApp = allApps.firstOrNull { it.packageName == r.providerPackage },
            onWatch = { watch(r) },
            onDismiss = { detailFor = null },
        )
    }
    menuNew?.let { season ->
        RecentMenu(
            item = season.asCard(),
            app = allApps.firstOrNull { it.packageName == season.packageName },
            onRemove = {
                val index = newSeasons.indexOfFirst { it.id == season.id }
                pendingNewFocus = (newSeasons.getOrNull(index + 1) ?: newSeasons.getOrNull(index - 1))?.id
                onRemoveNewSeason(season.id)
                if (pendingNewFocus == null) scope.launch { delay(50); runCatching { firstAppRequester.requestFocus() } }
            },
            onDismiss = {
                menuNew = null
                pendingNewFocus = season.id
            },
        )
    }
    LaunchedEffect(pendingNewFocus, newSeasons) {
        val target = pendingNewFocus ?: return@LaunchedEffect
        val index = newSeasons.indexOfFirst { it.id == target }
        if (index < 0) return@LaunchedEffect
        delay(50)
        if (newRowState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            newRowState.scrollToItem(index)
            withFrameNanos { }
        }
        runCatching { newRequesters[target]?.requestFocus() }
        pendingNewFocus = null
    }
    // Back to the card, or to its neighbor once it's gone, scrolling the row to show it whole first.
    LaunchedEffect(pendingRecentFocus, recent) {
        val target = pendingRecentFocus ?: return@LaunchedEffect
        val index = recent.indexOfFirst { it.id == target }
        if (index < 0) return@LaunchedEffect
        delay(50)   // let the dialog close and the row lay out without the removed card
        val info = recentRowState.layoutInfo
        val shown = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (shown == null || shown.offset < 0 || shown.offset + shown.size > info.viewportEndOffset - info.afterContentPadding) {
            recentRowState.scrollToItem(index)
            withFrameNanos { }
        }
        runCatching { recentRequesters[target]?.requestFocus() }
        pendingRecentFocus = null
    }
    LaunchedEffect(pendingFocus, rowApps) {
        val target = pendingFocus ?: return@LaunchedEffect
        if (rowApps.none { it.packageName == target }) return@LaunchedEffect
        delay(50)   // let the dialog close and the row settle
        runCatching { tileRequesters[target]?.requestFocus() }
        pendingFocus = null
    }
}

/** "S2 E5 · 24 min left", the episode title, or the app's name when the app says nothing else. */
@Composable
private fun recentSubtitle(item: RecentItem, app: LauncherApp?): String {
    val episode = when {
        item.season != null && item.episode != null -> stringResource(R.string.episode_label, item.season, item.episode)
        item.episode != null -> stringResource(R.string.episode_only, item.episode)
        else -> item.subtitle
    }
    val left = item.minutesLeft?.let { stringResource(R.string.minutes_left, it) }
    return listOfNotNull(episode, left).joinToString(" · ").ifEmpty { app?.label.orEmpty() }
}

/** A new season shown with the same card as Recently played. */
private fun NewSeason.asCard() = RecentItem(id = id, packageName = packageName, title = title, imageUri = imageUrl, lastPlayed = 0, fromApp = true)

/** "New season · S3", or "S2 arrives December 1" before it premieres. */
@Composable
private fun newSeasonSubtitle(season: NewSeason): String {
    season.episode?.let { return stringResource(R.string.new_episode, season.season, it) }
    if (!season.upcoming) return stringResource(R.string.new_season, season.season)
    val pattern = stringResource(R.string.date_day_month)
    val date = runCatching {
        java.time.LocalDate.parse(season.airDate).format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()))
    }.getOrDefault(season.airDate)
    return stringResource(R.string.new_season_soon, season.season, date)
}

/** Every installed app in a grid, alphabetically. The remote's App list button opens this page. */
@Composable
fun AppsScreen(
    apps: List<LauncherApp>,
    hidden: Set<String>,
    firstRequester: FocusRequester,
    onOpen: (Intent) -> Unit,
    onSetHidden: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var menuApp by remember { mutableStateOf<LauncherApp?>(null) }
    val tileRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var pendingFocus by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(top = 93.dp)) {
        PageTitle(
            title = stringResource(R.string.all_apps),
            subtitle = pluralStringResource(R.plurals.installed_apps, apps.size, apps.size),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(start = 60.dp, end = 60.dp, top = 22.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { i, app ->
                val isHidden = app.packageName in hidden
                // Apps hidden from Home stay here, dimmed, so they can be shown again.
                Column(Modifier.alpha(if (isHidden) 0.5f else 1f)) {
                    AppTile(
                        app = app,
                        onClick = { onOpen(AppsRepository.launchIntent(context, app)) },
                        onLongClick = { menuApp = app },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .then(if (i == 0) Modifier.focusRequester(firstRequester) else Modifier)
                            .focusRequester(tileRequesters.getOrPut(app.packageName) { FocusRequester() }),
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (isHidden) stringResource(R.string.hidden_label, app.label) else app.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HackColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    menuApp?.let { app ->
        val isHidden = app.packageName in hidden
        AppMenu(
            app = app,
            hidden = isHidden,
            canMove = false,
            onMove = {},
            onToggleHidden = { onSetHidden(app.packageName, !isHidden) },
            onInfo = { onOpen(appInfoIntent(app.packageName)) },
            onDismiss = {
                menuApp = null
                pendingFocus = app.packageName
            },
        )
    }
    // Hiding here keeps the app on the page, so focus goes back to it once the menu closes.
    LaunchedEffect(pendingFocus) {
        val target = pendingFocus ?: return@LaunchedEffect
        delay(50)
        runCatching { tileRequesters[target]?.requestFocus() }
        pendingFocus = null
    }
}

/** Shortcuts to the TV's own settings pages. The remote's Settings button opens this page. */
@Composable
fun SettingsScreen(
    firstRequester: FocusRequester,
    onOpen: (Intent) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(top = 93.dp)) {
        PageTitle(
            title = stringResource(R.string.tab_settings),
            subtitle = stringResource(R.string.settings_subtitle),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 60.dp, end = 60.dp, top = 22.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            itemsIndexed(SystemShortcut.entries) { i, shortcut ->
                GlassTile(
                    icon = shortcut.icon,
                    title = stringResource(shortcut.title),
                    subtitle = stringResource(shortcut.subtitle),
                    onClick = { onOpen(shortcut.intent(context)) },
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                        .then(if (i == 0) Modifier.focusRequester(firstRequester) else Modifier),
                )
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 60.dp)) {
        Text(title, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
        Text(subtitle, fontSize = 11.sp, color = HackColors.Muted)
    }
}
