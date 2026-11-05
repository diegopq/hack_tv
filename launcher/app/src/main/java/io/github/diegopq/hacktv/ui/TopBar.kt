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
import io.github.diegopq.hacktv.ui.theme.HackColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TAB_ARROWS = setOf(Key.DirectionLeft, Key.DirectionRight)

/** Search on the left, the Home / Apps / Settings tabs in the middle, Wi-Fi and the clock on the right. */
@Composable
fun TopBar(
    tab: Tab,
    onTabSelected: (Tab) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(75.dp).padding(horizontal = 60.dp)) {
        Pill(Modifier.align(Alignment.CenterStart)) {
            Chip(text = stringResource(R.string.search), icon = R.drawable.ic_search, onClick = onSearch)
        }
        Pill(Modifier.align(Alignment.Center)) {
            Tab.entries.forEach { t ->
                Chip(
                    text = stringResource(t.label),
                    active = t == tab,
                    onClick = { onTabSelected(t) },
                    // As on Google TV, moving onto a tab already shows its page.
                    onFocused = { onTabSelected(t) },
                )
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_wifi),
                contentDescription = null,
                tint = HackColors.Muted,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(15.dp))
            Clock()
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
    @DrawableRes icon: Int? = null,
    active: Boolean = false,
    onFocused: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
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

/** Time in the TV's 12/24-hour setting and the date in the TV's language. */
@Composable
private fun Clock() {
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
    }
}
