package io.github.diegopq.hacktv.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.diegopq.hacktv.data.LauncherApp
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
