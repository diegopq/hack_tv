package io.github.diegopq.hacktv.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.diegopq.hacktv.R
import io.github.diegopq.hacktv.data.CastMember
import io.github.diegopq.hacktv.data.LauncherApp
import io.github.diegopq.hacktv.data.Recommendation
import io.github.diegopq.hacktv.data.SeasonInfo
import io.github.diegopq.hacktv.data.TitleDetails
import io.github.diegopq.hacktv.data.TitleDetailsRepository
import io.github.diegopq.hacktv.data.providerColor
import io.github.diegopq.hacktv.ui.theme.HackColors

/**
 * The "More info" page of a banner recommendation: its artwork across the screen, logo, details, synopsis,
 * Watch and Trailer, then the cast and, for a series, its seasons. It opens over Home in its own window,
 * so Back returns to the banner exactly where it was. What the banner already knows shows right away; the
 * rest arrives from TMDB a moment later.
 */
@Composable
fun DetailPage(
    recommendation: Recommendation,
    providerApp: LauncherApp?,
    onWatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val details by produceState<TitleDetails?>(null, recommendation.id) {
        value = TitleDetailsRepository.load(recommendation.id)
    }
    val watchRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(HackColors.Background)) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(recommendation.imageUrl).size(1280, 720).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize(),
            )
            val bg = HackColors.Background
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to bg.copy(alpha = 0.97f), 0.38f to bg.copy(alpha = 0.82f), 0.72f to bg.copy(alpha = 0.15f), 0.9f to Color.Transparent),
                ),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to bg.copy(alpha = 0.95f))))

            LazyColumn(contentPadding = PaddingValues(top = 42.dp, bottom = 36.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "header") {
                    Header(recommendation, details, providerApp, onWatch, watchRequester, onTrailer = { key ->
                        val youtube = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { context.startActivity(youtube) } catch (_: ActivityNotFoundException) { }
                    })
                }
                details?.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
                    item(key = "cast") {
                        Section(stringResource(R.string.details_cast)) {
                            items(cast, key = { it.name + it.character }) { CastCard(it) }
                        }
                    }
                }
                details?.seasons?.takeIf { it.isNotEmpty() }?.let { seasons ->
                    item(key = "seasons") {
                        Section(stringResource(R.string.details_seasons)) {
                            items(seasons, key = { it.number }) { SeasonCard(it) }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { watchRequester.requestFocus() } }
    }
}

@Composable
private fun Header(
    rec: Recommendation,
    details: TitleDetails?,
    providerApp: LauncherApp?,
    onWatch: () -> Unit,
    watchRequester: FocusRequester,
    onTrailer: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.padding(start = 60.dp, end = 60.dp).widthIn(max = 500.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderBadge(color = Color(providerColor(rec.provider)), name = rec.providerName, app = providerApp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (rec.isNew) R.string.new_on else R.string.available_on, rec.providerName),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = HackColors.Muted,
            )
        }
        Spacer(Modifier.height(12.dp))
        val logo = details?.logoUrl
        if (logo != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(logo).build(),
                contentDescription = rec.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier.heightIn(max = 92.dp).widthIn(max = 340.dp),
            )
        } else {
            val size = if (rec.title.length <= 22) 42.sp else 34.sp
            Text(rec.title, fontSize = size, lineHeight = size * 1.06f, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        MetaLine(rec, details)
        val genres = details?.genres?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: rec.genres
        if (genres.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(genres, fontSize = 11.sp, color = HackColors.Muted)
        }
        details?.tagline?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = HackColors.Text)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            details?.overview ?: rec.description,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = HackColors.Muted,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        details?.makers?.takeIf { it.isNotEmpty() }?.let { names ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (rec.isMovie) R.string.details_directed_by else R.string.details_created_by, names.joinToString(", ")),
                fontSize = 11.sp,
                color = HackColors.Faint,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroButton(
                text = stringResource(if (providerApp != null) R.string.watch_on else R.string.get_app, rec.providerName),
                icon = R.drawable.ic_play,
                primary = true,
                onClick = onWatch,
                modifier = Modifier.focusRequester(watchRequester),
            )
            details?.trailerKey?.let { key ->
                HeroButton(text = stringResource(R.string.details_trailer), icon = R.drawable.ic_play, primary = false, onClick = { onTrailer(key) })
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** "2026 · Series · 1 season · B · ★ 8.3", or "2026 · Movie · 2 h 2 min · R · ★ 6.3". */
@Composable
private fun MetaLine(rec: Recommendation, details: TitleDetails?) {
    val parts = buildList {
        (details?.year ?: rec.year)?.let { add(it.toString()) }
        add(stringResource(if (rec.isMovie) R.string.movie else R.string.series))
        if (rec.isMovie) {
            details?.runtime?.let { minutes ->
                add(if (minutes >= 60) stringResource(R.string.details_hours, minutes / 60, minutes % 60) else stringResource(R.string.details_minutes, minutes))
            }
        } else {
            details?.seasonCount?.let { add(pluralStringResource(R.plurals.details_season_count, it, it)) }
            details?.episodeCount?.let { add(pluralStringResource(R.plurals.details_episode_count, it, it)) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(parts.joinToString(" · "), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = HackColors.Muted)
        details?.certification?.let { rating ->
            Spacer(Modifier.width(8.dp))
            Text(
                rating,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = HackColors.Text,
                modifier = Modifier
                    .background(Color(0x26FFFFFF), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        details?.score?.let { score ->
            Spacer(Modifier.width(8.dp))
            Text("★ " + String.format(java.util.Locale.getDefault(), "%.1f", score), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF5C518))
        }
    }
}

@Composable
private fun Section(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        RowTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 60.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        Spacer(Modifier.height(16.dp))
    }
}

private val CardShape = RoundedCornerShape(10.dp)

/** A cast member: round photo, name and character. Focusable, so the page scrolls down to it. */
@Composable
private fun CastCard(member: CastMember) {
    val context = LocalContext.current
    Surface(
        onClick = {},
        modifier = Modifier.width(92.dp),
        shape = ClickableSurfaceDefaults.shape(CardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CardShape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = HackColors.Text,
            focusedContainerColor = HackColors.GlassStrong,
            focusedContentColor = HackColors.Text,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(HackColors.Glass), contentAlignment = Alignment.Center) {
                if (member.photoUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(member.photoUrl).size(185, 185).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(member.name.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HackColors.Muted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(member.name, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            member.character?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontSize = 8.5.sp, lineHeight = 10.sp, color = HackColors.Faint, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A season: its poster, name, year and number of episodes. */
@Composable
private fun SeasonCard(season: SeasonInfo) {
    val context = LocalContext.current
    Surface(
        onClick = {},
        modifier = Modifier.width(96.dp),
        shape = ClickableSurfaceDefaults.shape(CardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CardShape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = HackColors.Text,
            focusedContainerColor = HackColors.GlassStrong,
            focusedContentColor = HackColors.Text,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(6.dp)) {
            Box(Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(7.dp)).background(HackColors.Glass), contentAlignment = Alignment.Center) {
                if (season.posterUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(season.posterUrl).size(185, 278).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(season.number.toString(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = HackColors.Muted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(season.name, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                season.year?.toString(),
                season.episodes?.let { pluralStringResource(R.plurals.details_episode_count, it, it) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, fontSize = 8.5.sp, lineHeight = 10.sp, color = HackColors.Faint, maxLines = 1)
        }
    }
}
