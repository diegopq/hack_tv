package io.github.diegopq.hacktv.ui

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
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
import io.github.diegopq.hacktv.data.PROVIDERS
import io.github.diegopq.hacktv.data.RecommendationSet
import io.github.diegopq.hacktv.data.SampleContent
import io.github.diegopq.hacktv.data.SystemShortcut
import io.github.diegopq.hacktv.data.TvInputItem
import io.github.diegopq.hacktv.ui.theme.HackColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GLOBAL_SEARCH = "android.search.action.GLOBAL_SEARCH"

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
    recommendations: RecommendationSet,
    listState: LazyListState,
    firstAppRequester: FocusRequester,
    onBackdrop: (String) -> Unit,
    onOpen: (Intent) -> Unit,
    onMessage: (Int) -> Unit,
) {
    val context = LocalContext.current
    // The banner follows the focused app's service; apps without a catalog keep the current list.
    var recKey by rememberSaveable { mutableStateOf(SampleContent.MIX) }
    val recs = remember(recKey, recommendations) { recommendations.forKey(recKey) }
    var slide by remember(recKey, recommendations) { mutableIntStateOf(0) }
    val rec = recs.getOrNull(slide.mod(recs.size.coerceAtLeast(1)))
    val providerApp = remember(rec, allApps) {
        val pkg = rec?.let { PROVIDERS[it.provider]?.packageName }
        allApps.firstOrNull { it.packageName == pkg }
    }

    val density = LocalDensity.current
    val columnSpec = remember(density) { with(density) { minimalScroll(75.dp.toPx(), 12.dp.toPx()) } }
    val rowSpec = remember(density) { with(density) { minimalScroll(60.dp.toPx(), 60.dp.toPx()) } }

    // The banner and the first row of apps fit on screen together: focusing either brings the banner back.
    val scope = rememberCoroutineScope()
    fun showBanner() {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            scope.launch { listState.animateScrollToItem(0) }
        }
    }
    val fadeDistance = with(density) { 120.dp.toPx() }

    LaunchedEffect(rec?.imageUrl) { rec?.let { onBackdrop(it.imageUrl) } }
    LaunchedEffect(recKey, slide, recs) {
        delay(9_000)
        if (recs.isNotEmpty()) slide = (slide + 1).mod(recs.size)
    }

    WithScrollSpec(columnSpec) {
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 48.dp)) {
        if (rec != null) item(key = "hero") {
            Hero(
                recommendation = rec,
                index = slide.mod(recs.size),
                count = recs.size,
                providerApp = providerApp,
                onWatch = {
                    val pkg = PROVIDERS[rec.provider]?.packageName.orEmpty()
                    onOpen(
                        providerApp?.let { AppsRepository.launchIntent(context, it) }
                            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                // Google TV's own search finds the title in every app, with a direct "Watch" link.
                onInfo = {
                    val search = Intent(GLOBAL_SEARCH).putExtra(SearchManager.QUERY, rec.title)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (search.resolveActivity(context.packageManager) != null) onOpen(search)
                    else onMessage(R.string.no_search)
                },
                onPrevious = { slide = (slide - 1).mod(recs.size) },
                onNext = { slide = (slide + 1).mod(recs.size) },
                modifier = Modifier
                    .onFocusChanged { if (it.hasFocus) showBanner() }
                    // Fades out as the rows scroll up under the top bar.
                    .graphicsLayer {
                        alpha = if (listState.firstVisibleItemIndex > 0) 0f
                        else 1f - (listState.firstVisibleItemScrollOffset / fadeDistance).coerceIn(0f, 1f)
                    },
            )
        }
        item(key = "apps") {
            RowTitle(stringResource(R.string.row_my_apps))
            WithScrollSpec(rowSpec) {
            LazyRow(
                contentPadding = RowPadding,
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                modifier = Modifier.focusRestorer(),
            ) {
                itemsIndexed(homeApps, key = { _, app -> app.packageName }) { i, app ->
                    AppTile(
                        app = app,
                        onClick = { onOpen(AppsRepository.launchIntent(context, app)) },
                        modifier = TileSize.then(if (i == 0) Modifier.focusRequester(firstAppRequester) else Modifier),
                        onFocused = {
                            showBanner()
                            SampleContent.providerKeyFor(app.packageName)?.let { recKey = it }
                        },
                    )
                }
            }
            }
            Spacer(Modifier.height(22.dp))
        }
        if (inputs.isNotEmpty()) {
            item(key = "inputs") {
                RowTitle(stringResource(R.string.row_inputs))
                WithScrollSpec(rowSpec) {
                LazyRow(
                    contentPadding = RowPadding,
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(inputs, key = { it.id }) { input ->
                        GlassTile(
                            icon = R.drawable.ic_hdmi,
                            title = input.label,
                            subtitle = stringResource(R.string.input_sub),
                            onClick = { onOpen(Inputs.switchIntent(input)) },
                            modifier = TileSize,
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
                contentPadding = RowPadding,
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                modifier = Modifier.focusRestorer(),
            ) {
                items(SystemShortcut.HOME_ROW) { shortcut ->
                    GlassTile(
                        icon = shortcut.icon,
                        title = stringResource(shortcut.title),
                        subtitle = stringResource(shortcut.subtitle),
                        onClick = { onOpen(shortcut.intent(context)) },
                        modifier = TileSize,
                    )
                }
            }
            }
        }
    }
    }
}

/** Every installed app in a grid, alphabetically. The remote's App list button opens this page. */
@Composable
fun AppsScreen(apps: List<LauncherApp>, onOpen: (Intent) -> Unit) {
    val context = LocalContext.current
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
            items(apps, key = { it.packageName }) { app ->
                Column {
                    AppTile(
                        app = app,
                        onClick = { onOpen(AppsRepository.launchIntent(context, app)) },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        app.label,
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
}

/** Shortcuts to the TV's own settings pages. The remote's Settings button opens this page. */
@Composable
fun SettingsScreen(onOpen: (Intent) -> Unit) {
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
            itemsIndexed(SystemShortcut.entries) { _, shortcut ->
                GlassTile(
                    icon = shortcut.icon,
                    title = stringResource(shortcut.title),
                    subtitle = stringResource(shortcut.subtitle),
                    onClick = { onOpen(shortcut.intent(context)) },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
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
