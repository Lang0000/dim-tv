package ua.home.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.media.tv.TvInputManager
import kotlinx.coroutines.delay

/**
 * Wi-Fi and Bluetooth switches. WifiManager.setWifiEnabled only works for apps targeting API 28 or lower,
 * which is why this app targets 28.
 */
class SystemToggles(context: Context) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    @Suppress("DEPRECATION")
    private val bluetooth: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    val wifiOn get() = wifi?.isWifiEnabled == true

    /** From ConnectivityManager: without location access Android 11 also hides WifiInfo.networkId (-1). */
    @Suppress("DEPRECATION")
    val wifiConnected: Boolean
        get() = wifiOn && runCatching {
            connectivity?.allNetworks.orEmpty().any {
                connectivity?.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        }.getOrDefault(false)

    @Suppress("DEPRECATION")
    val wifiStatus: String
        get() {
            if (!wifiOn) return "Вимкнено"
            if (!wifiConnected) return "Не під'єднано"
            return ssid(wifi) ?: "Під'єднано"
        }

    @Suppress("DEPRECATION")
    fun setWifi(on: Boolean) = wifi?.setWifiEnabled(on) == true

    val bluetoothOn get() = bluetooth?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun setBluetooth(on: Boolean) = bluetooth?.let { if (on) it.enable() else it.disable() } == true
}

/** Network name, or "<unknown ssid>" -> null (Android hides it from apps without location access). */
@Suppress("DEPRECATION")
private fun ssid(wifi: WifiManager?): String? =
    wifi?.connectionInfo?.ssid?.trim('"')?.trim()?.takeIf { it.isNotEmpty() && !it.contains("unknown") && it != "0x" }

/** What the network pill says: Ethernet, the Wi-Fi name, or offline (CleanYourTV's updateWifiLabel). */
data class NetworkLabel(@DrawableRes val icon: Int, val text: String) {
    companion object {
        fun read(context: Context): NetworkLabel {
            val offline = NetworkLabel(R.drawable.ic_wifi, "Офлайн")
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return offline
            var wifi = false
            var ethernet = false
            try {
                for (net in cm.allNetworks) {
                    val caps = cm.getNetworkCapabilities(net) ?: continue
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true
                }
            } catch (_: Exception) {
            }
            return when {
                ethernet -> NetworkLabel(R.drawable.ic_ethernet, "Ethernet")
                wifi -> NetworkLabel(R.drawable.ic_wifi,
                    ssid(context.applicationContext.getSystemService(WifiManager::class.java)) ?: "Wi-Fi")
                else -> offline
            }
        }
    }
}

/** The open header menu's rows; [close] hides it, [launch] opens an intent after closing. */
@Composable
fun HeaderMenu(which: Dropdown, state: HomeState, launch: (Intent) -> Unit, close: () -> Unit) {
    BackHandler { close() }
    val first = remember(which) { FocusRequester() }
    fun open(intent: Intent) {
        close()
        launch(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    when (which) {
        Dropdown.INPUTS -> InputsMenu(state, first, ::open)
        Dropdown.NETWORK -> NetworkMenu(first, ::open)
        Dropdown.SETTINGS -> SettingsMenu(first, ::open, close, launch)
    }
}

@Composable
private fun InputsMenu(state: HomeState, first: FocusRequester, open: (Intent) -> Unit) {
    var rows by remember { mutableStateOf<List<InputRow>?>(null) }
    LaunchedEffect(Unit) { rows = state.inputMenu() }
    val list = rows ?: return
    LaunchedEffect(list) {
        delay(30)
        runCatching { first.requestFocus() }
    }
    GlassPanel {
        list.forEachIndexed { i, row ->
            if (i > 0 && row.group != list[i - 1].group) GlassSeparator()
            GlassRow(
                icon = row.icon,
                title = row.label,
                onClick = { open(row.intent) },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                status = when (row.state) {
                    TvInputManager.INPUT_STATE_CONNECTED -> "Під'єднано"
                    TvInputManager.INPUT_STATE_DISCONNECTED -> "Не під'єднано"
                    else -> null
                },
                dot = row.state == TvInputManager.INPUT_STATE_CONNECTED,
            )
        }
    }
}

@Composable
private fun NetworkMenu(first: FocusRequester, open: (Intent) -> Unit) {
    val context = LocalContext.current
    val toggles = remember { SystemToggles(context) }
    var wifiOn by remember { mutableStateOf(toggles.wifiOn) }
    var wifiConnected by remember { mutableStateOf(toggles.wifiConnected) }
    var wifiStatus by remember { mutableStateOf(toggles.wifiStatus) }
    var btOn by remember { mutableStateOf(toggles.bluetoothOn) }
    // The remote is a Bluetooth LE device: turning Bluetooth off needs a second press.
    var btArmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            wifiOn = toggles.wifiOn
            wifiConnected = toggles.wifiConnected
            wifiStatus = toggles.wifiStatus
            btOn = toggles.bluetoothOn
            delay(1000)
        }
    }
    LaunchedEffect(btArmed) {
        if (btArmed) {
            delay(5000)
            btArmed = false
        }
    }
    LaunchedEffect(Unit) {
        delay(30)
        runCatching { first.requestFocus() }
    }
    GlassPanel(minWidth = 260.dp) {
        GlassRow(
            icon = R.drawable.ic_wifi, title = "Wi-Fi", modifier = Modifier.focusRequester(first),
            status = wifiStatus, dot = wifiConnected,
            onClick = {
                toggles.setWifi(!wifiOn)
                wifiOn = !wifiOn
            },
        )
        GlassRow(
            icon = R.drawable.ic_bluetooth, title = "Bluetooth",
            status = when {
                btArmed -> "OK ще раз вимкне пульт"
                btOn -> "Увімкнено"
                else -> "Вимкнено"
            },
            dot = btOn && !btArmed,
            statusColor = if (btArmed) Color(0xFFFF6B6B) else null,
            onClick = {
                when {
                    !btOn -> { toggles.setBluetooth(true); btOn = true }
                    !btArmed -> btArmed = true
                    else -> { toggles.setBluetooth(false); btOn = false; btArmed = false }
                }
            },
        )
        GlassSeparator()
        GlassRow(R.drawable.ic_wifi, "Мережа та Інтернет", { open(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        GlassRow(R.drawable.ic_add_device, "Під'єднати пристрій", {
            open(Intent().setComponent(ComponentName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity")))
        })
    }
}

@Composable
private fun SettingsMenu(first: FocusRequester, open: (Intent) -> Unit, close: () -> Unit, launch: (Intent) -> Unit) {
    LaunchedEffect(Unit) {
        delay(30)
        runCatching { first.requestFocus() }
    }
    GlassPanel {
        GlassRow(R.drawable.ic_settings, "Налаштування ТВ", { open(Intent(Settings.ACTION_SETTINGS)) },
            Modifier.focusRequester(first))
        GlassRow(R.drawable.ic_picture, "Зображення", { open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) })
        GlassRow(R.drawable.ic_volume, "Звук", { open(Intent(Settings.ACTION_SOUND_SETTINGS)) })
        GlassSeparator()
        GlassRow(R.drawable.ic_power, "Вимкнення та перезапуск", {
            close()
            if (!HomeKeyService.showPowerDialog()) launch(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
    }
}

