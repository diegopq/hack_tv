package io.github.diegopq.hacktv.keys

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import io.github.diegopq.hacktv.R

/**
 * The volume as a number, right under the TV's own volume bar. With a soundbar over HDMI, Android TV shows
 * a capsule with + and a speaker but no level; this adds the level in a capsule of the same width and
 * color, centered under it. Shown by the remote-button service as an accessibility overlay, so it needs no
 * extra permission. Plain views, no Compose: the service's process stays small.
 */
class VolumeOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private fun dp(value: Float) = (value * density).toInt()

    private val number = TextView(service).apply {
        setTextColor(Color.WHITE)
        textSize = 15f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
    }
    private val mutedIcon = ImageView(service).apply {
        setImageResource(R.drawable.ic_volume_off)
        visibility = View.GONE
    }
    private val root = FrameLayout(service).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(BAR_WIDTH_DP / 2).toFloat()
            setColor(Color.rgb(45, 46, 50))   // the TV bar's own grey
        }
        addView(number, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(mutedIcon, FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER))
        visibility = View.GONE
    }
    private var attached = false
    private val hide = Runnable {
        root.animate().alpha(0f).setDuration(220).withEndAction { root.visibility = View.GONE }.start()
    }

    /** Shows [percent] (0 to 100), or a muted speaker, and hides itself with the TV's bar. */
    fun show(percent: Int, muted: Boolean) {
        attach()
        number.text = percent.coerceIn(0, 100).toString()
        number.visibility = if (muted) View.GONE else View.VISIBLE
        mutedIcon.visibility = if (muted) View.VISIBLE else View.GONE
        root.animate().cancel()
        root.alpha = 1f
        root.visibility = View.VISIBLE
        root.removeCallbacks(hide)
        root.postDelayed(hide, HIDE_AFTER_MS)
    }

    fun release() {
        if (attached) runCatching { windowManager.removeView(root) }
        attached = false
    }

    private fun attach() {
        if (attached) return
        val params = WindowManager.LayoutParams(
            dp(BAR_WIDTH_DP),
            dp(HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // The TV's bar is centered on the right edge, BAR_RIGHT_DP from it and BAR_HEIGHT_DP tall: this
            // sits GAP_DP below it, on the same vertical line.
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(BAR_RIGHT_DP)
            y = dp(BAR_HEIGHT_DP / 2 + GAP_DP + HEIGHT_DP / 2)
        }
        attached = runCatching { windowManager.addView(root, params) }.isSuccess
    }

    private companion object {
        /** Measured on the Hisense: the TV's volume bar is 48 x 173 dp, 24 dp from the right edge. */
        const val BAR_WIDTH_DP = 48f
        const val BAR_HEIGHT_DP = 173f
        const val BAR_RIGHT_DP = 24f
        const val GAP_DP = 10f
        const val HEIGHT_DP = 34f
        /** About as long as the TV keeps its bar on screen. */
        const val HIDE_AFTER_MS = 2_500L
    }
}
