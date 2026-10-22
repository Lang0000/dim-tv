package ua.home.launcher

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    private lateinit var state: HomeState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = HomeState(applicationContext)
        openPanel(intent.getStringExtra(EXTRA_PANEL), wasOpen = false)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeScreen(state = state, launch = ::launch)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        state.resumeCount++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            val panel = intent.getStringExtra(EXTRA_PANEL)
            // Pressing ⚙ / APPS again while that panel is on screen closes it.
            val wasOpen = when (panel) {
                PANEL_QUICK -> state.dropdown == Dropdown.SETTINGS
                PANEL_APPS -> state.showAllApps
                else -> false
            }
            state.showAllApps = false
            state.dropdown = null
            state.menuFor = null
            state.resetSignal++
            openPanel(panel, wasOpen)
        }
    }

    private fun openPanel(panel: String?, wasOpen: Boolean) {
        if (wasOpen) return
        when (panel) {
            PANEL_QUICK -> state.dropdown = Dropdown.SETTINGS
            PANEL_APPS -> state.showAllApps = true
        }
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w("HomeLauncher", "launch failed: $intent", e)
            Toast.makeText(this, "Не вдалося відкрити", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        /** Opened by remote buttons via HomeKeyService: ⚙ -> quick panel, APPS -> all apps. */
        const val EXTRA_PANEL = "ua.home.launcher.PANEL"
        const val PANEL_QUICK = "quick"
        const val PANEL_APPS = "apps"
    }
}
