package io.github.diegopq.hacktv.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.diegopq.hacktv.R
import io.github.diegopq.hacktv.data.LauncherApp
import io.github.diegopq.hacktv.data.PROVIDERS
import io.github.diegopq.hacktv.data.Recommendation
import io.github.diegopq.hacktv.ui.theme.HackColors

/**
 * The recommendations banner. Left on the first button and right on the last one change the
 * recommendation, as in the mockup.
 */
@Composable
fun Hero(
    recommendation: Recommendation,
    index: Int,
    count: Int,
    providerApp: LauncherApp?,
    onWatch: () -> Unit,
    onInfo: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    watchRequester: FocusRequester = FocusRequester(),
) {
    val provider = PROVIDERS.getValue(recommendation.provider)
    val kind = stringResource(if (recommendation.isMovie) R.string.movie else R.string.series)
    Column(modifier.fillMaxWidth().heightIn(min = 345.dp).padding(start = 60.dp, top = 98.dp, end = 60.dp)) {
        AnimatedContent(
            targetState = recommendation,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
            label = "hero-text",
        ) { rec ->
            Column(Modifier.widthIn(max = 520.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderBadge(color = Color(provider.color), name = provider.name, app = providerApp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.available_on, provider.name),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HackColors.Muted,
                    )
                }
                Spacer(Modifier.height(9.dp))
                // Long TMDB titles get a smaller size and a second line instead of being cut off.
                val titleSize = when {
                    rec.title.length <= 20 -> 46.sp
                    rec.title.length <= 32 -> 38.sp
                    else -> 32.sp
                }
                Text(
                    rec.title,
                    fontSize = titleSize,
                    lineHeight = titleSize * 1.06f,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.2).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    listOfNotNull(rec.year?.toString(), rec.genres.takeIf { it.isNotBlank() }, kind).joinToString(" · "),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = HackColors.Muted,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    rec.description,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = HackColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 380.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroButton(
                text = stringResource(if (providerApp != null) R.string.watch_on else R.string.get_app, provider.name),
                icon = R.drawable.ic_play,
                primary = true,
                onClick = onWatch,
                modifier = Modifier
                    .focusRequester(watchRequester)
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { onPrevious(); true } else false
                    },
            )
            HeroButton(
                text = stringResource(R.string.more_info),
                icon = R.drawable.ic_info,
                primary = false,
                onClick = onInfo,
                modifier = Modifier.onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) { onNext(); true } else false
                },
            )
        }
        Spacer(Modifier.height(15.dp))
        Dots(count = count, index = index)
    }
}

@Composable
private fun HeroButton(
    text: String,
    @DrawableRes icon: Int,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(19.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(37.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        border = ClickableSurfaceDefaults.border(
            border = if (primary) Border.None else Border(BorderStroke(0.5.dp, HackColors.Line), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), inset = (-3).dp, shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White else HackColors.GlassStrong,
            contentColor = if (primary) Color(0xFF0A0A0A) else Color.White,
            focusedContainerColor = if (primary) Color.White else Color(0x40FFFFFF),
            focusedContentColor = if (primary) Color(0xFF0A0A0A) else Color.White,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 20.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The streaming service's own banner when the app is installed, a colored label otherwise. */
@Composable
private fun ProviderBadge(color: Color, name: String, app: LauncherApp?) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = Modifier.size(48.dp, 27.dp).clip(shape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (app?.banner != null) {
            Image(app.banner, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun Dots(count: Int, index: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == index) 18.dp else 5.dp, label = "dot")
            Box(
                Modifier
                    .size(width, 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i == index) Color.White else Color(0x4DFFFFFF)),
            )
        }
    }
}
