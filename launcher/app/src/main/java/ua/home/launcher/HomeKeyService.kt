package ua.home.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Google TV ignores a third-party HOME while its own launcher is enabled, and disabling it hands Home to
 * the setup wizard's recovery screen. So this service does the job instead:
 *  - the Home key opens this launcher;
 *  - whenever a system home screen comes to front (boot, leaving an app), this launcher replaces it.
 */
class HomeKeyService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingHold: Runnable? = null
    private var holdFired = false

    override fun onServiceConnected() {
        instance = this
        // After boot or an app update the stock home may already be in front, so no window event will come.
        showHome()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * Remote buttons that Google TV's launcher used to handle. Codes come from the Xiaomi RC
     * (captured 2026-10-21): NETFLIX=BUTTON_6, prime video=BUTTON_7, YouTube=BUTTON_3, APPS=ALL_APPS, ⚙=SETTINGS.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        APP_KEYS[event.keyCode]?.let { return onAppKey(event, it) }
        val action: () -> Unit = when (event.keyCode) {
            KeyEvent.KEYCODE_HOME -> { { showHome() } }
            KeyEvent.KEYCODE_ALL_APPS -> { { showHome(MainActivity.PANEL_APPS) } }
            KeyEvent.KEYCODE_SETTINGS -> { { showHome(MainActivity.PANEL_QUICK) } }
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) action()
        return true
    }

    /** A press opens the button's app; holding it for [HOLD_MS] opens its second app instead, if it has one. */
    private fun onAppKey(event: KeyEvent, key: AppKey): Boolean {
        val tap = launchIntentFor(key.tap) ?: return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                holdFired = false
                val hold = key.hold?.let(::launchIntentFor) ?: return true
                pendingHold = Runnable {
                    holdFired = true
                    startSafely(hold)
                }.also { handler.postDelayed(it, HOLD_MS) }
            }
            KeyEvent.ACTION_UP -> {
                pendingHold?.let(handler::removeCallbacks)
                pendingHold = null
                if (!holdFired && !event.isCanceled) startSafely(tap)
            }
        }
        return true
    }

    private fun launchIntentFor(pkg: String): Intent? =
        (packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.className?.toString() in SYSTEM_HOMES
        ) {
            showHome()
        }
    }

    override fun onInterrupt() = Unit

    private fun showHome(panel: String? = null) = startSafely(
        Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (panel != null) putExtra(MainActivity.EXTRA_PANEL, panel) }
    )

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w("HomeLauncher", "start failed: $intent", e)
        }
    }

    companion object {
        /**
         * Activities the system may bring up as HOME: Google TV's home, and, when that is disabled,
         * the setup wizard's (invisible) recovery screen or the settings fallback. Keep in sync with
         * packageNames in res/xml/home_key_service.xml.
         */
        private val SYSTEM_HOMES = setOf(
            "com.google.android.apps.tv.launcherx.home.HomeActivity",
            "com.google.android.tungsten.setupwraith.RecoveryActivity",
            "com.android.tv.settings.system.FallbackHome",
        )

        private class AppKey(val tap: String, val hold: String? = null)

        private val APP_KEYS = mapOf(
            KeyEvent.KEYCODE_BUTTON_6 to AppKey("com.netflix.ninja", hold = "ua.kino"),
            KeyEvent.KEYCODE_BUTTON_7 to AppKey("com.amazon.amazonvideo.livingroom"),
            KeyEvent.KEYCODE_BUTTON_3 to AppKey("com.google.android.youtube.tv", hold = "com.google.android.youtube.tvmusic"),
        )

        private const val HOLD_MS = 500L

        private var instance: HomeKeyService? = null

        /** System power menu (off / restart). False when the service is not enabled. */
        fun showPowerDialog() = instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) == true
    }
}
