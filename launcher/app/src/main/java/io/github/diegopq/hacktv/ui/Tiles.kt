package io.github.diegopq.hacktv.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.diegopq.hacktv.data.LauncherApp
import io.github.diegopq.hacktv.data.RecentItem
import io.github.diegopq.hacktv.ui.theme.HackColors

private val TileShape = RoundedCornerShape(10.dp)
private val FocusBorder = Border(BorderStroke(2.dp, Color.White), shape = TileShape)
/** A tile being moved gets a blue outline, so it's clear the arrows now move it instead of the focus. */
private val MovingBorder = Border(BorderStroke(3.dp, Color(0xFF8AB4F8)), shape = TileShape)

/** An app tile: the app's TV banner, or its icon and name when it has no banner. */
@Composable
fun AppTile(
    app: LauncherApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
    moving: Boolean = false,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (moving) 1.14f else 1.1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = if (moving) MovingBorder else FocusBorder),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF16181D),
            contentColor = HackColors.Text,
            focusedContainerColor = Color(0xFF23262E),
            focusedContentColor = HackColors.Text,
        ),
    ) {
        if (app.banner != null) {
            Image(
                bitmap = app.banner,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                app.icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = app.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A frosted tile with an icon, a title and a subtitle: inputs and settings shortcuts. */
@Composable
fun GlassTile(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.5.dp, HackColors.Line), shape = TileShape),
            focusedBorder = FocusBorder,
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = HackColors.Glass,
            contentColor = HackColors.Text,
            focusedContainerColor = HackColors.GlassStrong,
            focusedContentColor = HackColors.Text,
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 11.dp)) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp).align(Alignment.TopStart),
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 9.sp, color = HackColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A row title, such as "My apps", with an optional hint beside it. */
@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier, hint: String? = null) {
    Row(modifier.padding(start = 60.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (hint != null) {
            Spacer(Modifier.width(8.dp))
            Text(hint, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = HackColors.Faint)
        }
    }
}

/**
 * A Recently played card: the show's artwork with its title, episode and progress, and the app's banner
 * in the corner. Without artwork, the app's banner fills the card instead.
 */
@Composable
fun MediaTile(
    item: RecentItem,
    app: LauncherApp?,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = FocusBorder),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF16181D),
            contentColor = HackColors.Text,
            focusedContainerColor = Color(0xFF23262E),
            focusedContentColor = HackColors.Text,
        ),
    ) {
        // The card is kept in its own cached layer: growing on focus only scales that image, instead of
        // drawing the artwork, the gradient and the text again on every frame of the animation.
        var artShown by remember(item.imageUri) { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            // The app's banner only while the artwork loads, or when it fails: under it, it was drawn for nothing.
            if (!artShown) app?.banner?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.6f))
            }
            if (item.imageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(item.imageUri).size(400, 225).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { artShown = true },
                    onError = { artShown = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Darkens only the lower part, where the text is.
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.7f).background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.36f to Color(0x59000000), 1f to Color(0xE0000000)),
                ),
            )
            if (item.imageUri != null && app?.banner != null) {
                Image(
                    app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(36.dp, 20.dp).clip(RoundedCornerShape(4.dp)),
                )
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 11.dp, end = 11.dp, bottom = 15.dp)) {
                Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, fontSize = 8.5.sp, color = HackColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item.progress?.let { progress ->
                Box(
                    Modifier.align(Alignment.BottomStart).padding(start = 11.dp, end = 11.dp, bottom = 8.dp)
                        .fillMaxWidth().height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF)),
                ) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
                }
            }
        }
    }
}
