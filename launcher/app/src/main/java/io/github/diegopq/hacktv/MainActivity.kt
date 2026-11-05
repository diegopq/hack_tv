package io.github.diegopq.hacktv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import io.github.diegopq.hacktv.ui.LauncherRoot
import io.github.diegopq.hacktv.ui.theme.HackTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    /** Fires when Home is pressed while the launcher is already open, to go back to the first row. */
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HackTheme {
                LauncherRoot(viewModel = viewModel, homePresses = homePresses)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homePresses.tryEmit(Unit)
    }

    override fun onResume() {
        super.onResume()
        // An input may have been renamed, or a device plugged into an HDMI port, while another app was open.
        viewModel.refreshInputs()
        // New recommendations every few hours; the cache makes this free the rest of the time.
        viewModel.refreshRecommendations()
    }
}
