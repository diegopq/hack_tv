package io.github.diegopq.hacktv.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.diegopq.hacktv.LauncherViewModel
import io.github.diegopq.hacktv.R
import io.github.diegopq.hacktv.data.AppsRepository
import io.github.diegopq.hacktv.ui.theme.HackColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

enum class Tab(@StringRes val label: Int, val key: String) {
    HOME(R.string.tab_home, "home"),
    APPS(R.string.tab_apps, "apps"),
    SETTINGS(R.string.tab_settings, "settings"),
}

private val SEARCH_ACTIONS = listOf("android.search.action.GLOBAL_SEARCH", Intent.ACTION_ASSIST, Intent.ACTION_WEB_SEARCH)

@Composable
fun LauncherRoot(viewModel: LauncherViewModel, navigation: Flow<Tab>, initialTab: Tab = Tab.HOME) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val keyServiceOn by viewModel.keyServiceOn.collectAsStateWithLifecycle()
    val homeApps = remember(apps) { AppsRepository.homeOrder(apps) }

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var backdropUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val firstApp = remember { FocusRequester() }
    val firstGridApp = remember { FocusRequester() }
    val firstSetting = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }

    fun message(@StringRes text: Int) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.cannot_open, intent.action ?: ""), Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, context.getString(R.string.cannot_open, intent.action ?: ""), Toast.LENGTH_SHORT).show()
        }
    }
    /** Shows a page and puts focus on its first item, as the remote's Home, Apps and Settings buttons do. */
    fun goTo(target: Tab) {
        tab = target
        scope.launch {
            delay(32)   // let the page compose before moving focus into it
            when (target) {
                Tab.HOME -> {
                    listState.scrollToItem(0)
                    runCatching { firstApp.requestFocus() }
                }
                Tab.APPS -> runCatching { firstGridApp.requestFocus() }
                Tab.SETTINGS -> runCatching { firstSetting.requestFocus() }
            }
        }
    }
    fun goHome() = goTo(Tab.HOME)

    // First focus goes to the first item of the opening page, as on the stock launcher.
    LaunchedEffect(homeApps.isNotEmpty()) {
        if (homeApps.isNotEmpty() && !focusedOnce) {
            delay(32)
            val requester = when (tab) {
                Tab.HOME -> firstApp
                Tab.APPS -> firstGridApp
                Tab.SETTINGS -> firstSetting
            }
            focusedOnce = runCatching { requester.requestFocus() }.isSuccess
        }
    }
    LaunchedEffect(Unit) { navigation.collect { goTo(it) } }
    // A launcher never closes on Back: it goes back to the first row instead.
    BackHandler { goHome() }

    CompositionLocalProvider(LocalContentColor provides HackColors.Text) {
        Box(Modifier.fillMaxSize().background(HackColors.Background)) {
            Backdrop(url = if (tab == Tab.HOME) backdropUrl else null)
            Scrims()
            when (tab) {
                Tab.HOME -> HomeScreen(
                    homeApps = homeApps,
                    allApps = apps,
                    inputs = inputs,
                    recommendations = recommendations,
                    listState = listState,
                    firstAppRequester = firstApp,
                    onBackdrop = { backdropUrl = it },
                    onOpen = ::open,
                    onMessage = ::message,
                )
                Tab.APPS -> AppsScreen(apps = apps, firstRequester = firstGridApp, onOpen = ::open)
                Tab.SETTINGS -> SettingsScreen(
                    keyServiceOn = keyServiceOn,
                    firstRequester = firstSetting,
                    onOpen = ::open,
                    onMessage = ::message,
                )
            }
            TopBar(
                tab = tab,
                onTabSelected = { tab = it },
                onSearch = {
                    val pm = context.packageManager
                    val action = SEARCH_ACTIONS.firstOrNull { Intent(it).resolveActivity(pm) != null }
                    if (action != null) open(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else message(R.string.no_search)
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (tab == Tab.HOME) {
                Text(
                    stringResource(if (recommendations.fromTmdb) R.string.tmdb_credit else R.string.sample_credit),
                    fontSize = 7.sp,
                    color = Color(0x4DFFFFFF),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 66.dp, top = 61.dp),
                )
            }
        }
    }
}

/** Full-screen artwork of the current recommendation, or a soft glow on the other pages. */
@Composable
private fun Backdrop(url: String?) {
    val context = LocalContext.current
    Crossfade(targetState = url, animationSpec = tween(800), label = "backdrop") { current ->
        if (current != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(current).size(1280, 720).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color(0xCC24384C), Color.Transparent)),
                ),
            )
        }
    }
}

/** Dark gradients from the left and the bottom, so text stays readable over any artwork. */
@Composable
private fun Scrims() {
    val bg = HackColors.Background
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to bg.copy(alpha = 0.94f),
                0.30f to bg.copy(alpha = 0.72f),
                0.62f to bg.copy(alpha = 0.15f),
                0.80f to Color.Transparent,
            ),
        ),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to bg.copy(alpha = 0.70f),
                0.20f to Color.Transparent,
                0.42f to Color.Transparent,
                0.70f to bg.copy(alpha = 0.85f),
                1f to bg,
            ),
        ),
    )
}
