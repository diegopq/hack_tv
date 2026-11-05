package io.github.diegopq.hacktv.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.diegopq.hacktv.R
import io.github.diegopq.hacktv.data.LauncherApp
import io.github.diegopq.hacktv.ui.theme.HackColors

/** Opens Android's App info page for an app: storage, permissions, force stop, uninstall. */
fun appInfoIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * The menu that opens when OK is held on an app: Move (only in My apps), Hide from Home or
 * Show on Home, and App info.
 */
@Composable
fun AppMenu(
    app: LauncherApp,
    hidden: Boolean,
    canMove: Boolean,
    onMove: () -> Unit,
    onToggleHidden: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstItem = remember { FocusRequester() }
    // The menu opens while OK is still held: its release must not choose the first option.
    // Key-ups are ignored until a key has been pressed inside the menu.
    var armed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .onPreviewKeyEvent { event ->
                    when (event.type) {
                        KeyEventType.KeyDown -> { armed = true; false }
                        KeyEventType.KeyUp -> !armed
                        else -> false
                    }
                }
                .width(270.dp)
                .background(Color(0xF2181A20), shape)
                .border(BorderStroke(0.5.dp, HackColors.Line), shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                val thumb = app.banner ?: app.icon
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp, 32.dp).clip(RoundedCornerShape(5.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    app.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HackColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val items = buildList {
                if (canMove) add(R.string.menu_move to { onDismiss(); onMove() })
                add((if (hidden) R.string.menu_show else R.string.menu_hide) to { onDismiss(); onToggleHidden() })
                add(R.string.menu_info to { onDismiss(); onInfo() })
            }
            items.forEachIndexed { i, (label, action) ->
                MenuItem(
                    text = stringResource(label),
                    onClick = action,
                    modifier = if (i == 0) Modifier.focusRequester(firstItem) else Modifier,
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { firstItem.requestFocus() } }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(34.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = HackColors.Text,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0A0A0A),
        ),
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp),
        )
    }
}
