package io.github.diegopq.hacktv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import io.github.diegopq.hacktv.keys.RemoteKeys
import io.github.diegopq.hacktv.ui.LauncherRoot
import io.github.diegopq.hacktv.ui.Tab
import io.github.diegopq.hacktv.ui.theme.HackTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    /** Pages to show: Home when Home is pressed again, Apps or Settings from the remote's buttons. */
    private val navigation = MutableSharedFlow<Tab>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialPage = pageFrom(intent)
        setContent {
            HackTheme {
                LauncherRoot(viewModel = viewModel, navigation = navigation, initialTab = initialPage ?: Tab.HOME)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val page = pageFrom(intent) ?: if (intent.action == Intent.ACTION_MAIN) Tab.HOME else null
        page?.let { navigation.tryEmit(it) }
    }

    override fun onResume() {
        super.onResume()
        // An input may have been renamed, or a device plugged into an HDMI port, while another app was open.
        viewModel.refreshInputs()
        // New recommendations every few hours; the cache makes this free the rest of the time.
        viewModel.refreshRecommendations()
        RemoteKeys.repairIfNeeded(this)
        viewModel.refreshKeyService()
    }

    private fun pageFrom(intent: Intent?): Tab? =
        intent?.getStringExtra(EXTRA_PAGE)?.let { page -> Tab.entries.firstOrNull { it.key == page } }

    companion object {
        /** "home", "apps" or "settings": the page to open, sent by the remote-button service. */
        const val EXTRA_PAGE = "io.github.diegopq.hacktv.PAGE"
    }
}
