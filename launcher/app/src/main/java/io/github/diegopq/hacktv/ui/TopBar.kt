package io.github.diegopq.hacktv.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.diegopq.hacktv.R
import io.github.diegopq.hacktv.data.Connection
import io.github.diegopq.hacktv.data.WeatherKind
import io.github.diegopq.hacktv.data.WeatherNow
import io.github.diegopq.hacktv.ui.theme.HackColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TAB_ARROWS = setOf(Key.DirectionLeft, Key.DirectionRight)

private val WeatherKind.icon: Int
    get() = when (this) {
        WeatherKind.CLEAR_DAY -> R.drawable.ic_w_sun
        WeatherKind.CLEAR_NIGHT -> R.drawable.ic_w_moon
        WeatherKind.PARTLY_CLOUDY -> R.drawable.ic_w_partly
        WeatherKind.CLOUDY -> R.drawable.ic_w_cloud
        WeatherKind.FOG -> R.drawable.ic_w_fog
        WeatherKind.DRIZZLE -> R.drawable.ic_w_drizzle
        WeatherKind.RAIN -> R.drawable.ic_w_rain
        WeatherKind.SNOW -> R.drawable.ic_w_snow
        WeatherKind.STORM -> R.drawable.ic_w_storm
    }

private val WeatherKind.label: Int
    get() = when (this) {
        WeatherKind.CLEAR_DAY, WeatherKind.CLEAR_NIGHT -> R.string.weather_clear
        WeatherKind.PARTLY_CLOUDY -> R.string.weather_partly
        WeatherKind.CLOUDY -> R.string.weather_cloudy
        WeatherKind.FOG -> R.string.weather_fog
        WeatherKind.DRIZZLE -> R.string.weather_drizzle
        WeatherKind.RAIN -> R.string.weather_rain
        WeatherKind.SNOW -> R.string.weather_snow
        WeatherKind.STORM -> R.string.weather_storm
    }

/** Search on the left, the Home / Apps / Settings tabs in the middle, the connection, clock and weather on the right. */
@Composable
fun TopBar(
    tab: Tab,
    connection: Connection,
    weather: WeatherNow?,
    onTabSelected: (Tab) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabRequesters = remember { Tab.entries.associateWith { FocusRequester() } }
    // Coming up from a page always lands on the page's own tab. Android picked the tab nearest to the
    // focused item, and since moving onto a tab opens its page, going up from an app on the right side of
    // Apps switched to Settings, and from the left side to Home.
    Box(
        modifier.fillMaxWidth().height(75.dp).padding(horizontal = 60.dp)
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Up) runCatching { tabRequesters.getValue(tab).requestFocus() }
                }
            }
            .focusGroup(),
    ) {
        Pill(Modifier.align(Alignment.CenterStart)) {
            Chip(text = stringResource(R.string.search), icon = R.drawable.ic_search, onClick = onSearch)
        }
        Pill(Modifier.align(Alignment.Center)) {
            Tab.entries.forEach { t ->
                Chip(
                    text = stringResource(t.label),
                    modifier = Modifier.focusRequester(tabRequesters.getValue(t)),
                    active = t == tab,
                    onClick = { onTabSelected(t) },
                    // As on Google TV, moving onto a tab already shows its page.
                    onFocused = { onTabSelected(t) },
                )
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            // Ethernet or Wi-Fi, a warning when the network doesn't reach the internet, crossed out when offline.
            Icon(
                painter = painterResource(
                    when (connection) {
                        Connection.ETHERNET -> R.drawable.ic_ethernet
                        Connection.WIFI -> R.drawable.ic_wifi
                        Connection.NO_INTERNET -> R.drawable.ic_no_internet
                        Connection.OFFLINE -> R.drawable.ic_offline
                    },
                ),
                contentDescription = null,
                tint = if (connection == Connection.ETHERNET || connection == Connection.WIFI) HackColors.Muted else Color(0xFFFFB74D),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(15.dp))
            Clock(weather)
        }
    }
}

@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        modifier = modifier
            .height(38.dp)
            .background(HackColors.Glass, shape)
            .border(BorderStroke(0.5.dp, HackColors.Line), shape)
            .padding(horizontal = 4.5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    active: Boolean = false,
    onFocused: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(29.dp)
            .onFocusChanged { if (it.isFocused) onFocused() }
            // A held arrow repeats; on the tabs that skipped a tab, since each tab you pass opens its page.
            .onPreviewKeyEvent { it.nativeKeyEvent.repeatCount > 0 && it.key in TAB_ARROWS },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(15.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) HackColors.GlassStrong else Color.Transparent,
            contentColor = if (active) Color.White else HackColors.Muted,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0A0A0A),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Time in the TV's 12/24-hour setting, the date in the TV's language, and the weather under it. */
@Composable
private fun Clock(weather: WeatherNow?) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val locale = Locale.getDefault()
    val time = DateFormat.getTimeFormat(context).format(Date(now))
    val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
        .format(Date(now)).replaceFirstChar { it.titlecase(locale) }
    Column(horizontalAlignment = Alignment.End) {
        Text(time, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        Text(date, fontSize = 9.5.sp, color = HackColors.Muted, textAlign = TextAlign.End)
        weather?.let { w ->
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // In its own colors: yellow sun, light clouds, blue rain.
                Icon(painterResource(w.kind.icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    "${w.temperature}° · ${stringResource(w.kind.label)}",
                    fontSize = 9.5.sp,
                    color = HackColors.Muted,
                )
            }
        }
    }
}
