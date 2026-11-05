package io.github.diegopq.hacktv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Colors from the design mockup (diseno/maqueta.html). Sizes in the UI are the mockup's
 * 1920×1080 pixels divided by two, since a 1080p TV runs at 960×540 dp.
 */
object HackColors {
    val Background = Color(0xFF07080B)
    val Text = Color(0xFFF4F5F7)
    val Muted = Color(0xADF4F5F7)
    val Faint = Color(0x6BF4F5F7)
    val Glass = Color(0x17FFFFFF)
    val GlassStrong = Color(0x29FFFFFF)
    val Line = Color(0x24FFFFFF)
}

@Composable
fun HackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = HackColors.Background,
            surface = HackColors.Background,
            onBackground = HackColors.Text,
            onSurface = HackColors.Text,
        ),
        content = content,
    )
}
