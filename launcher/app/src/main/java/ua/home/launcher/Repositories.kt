package ua.home.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

private const val TAG = "HomeLauncher"

class AppsRepository(private val context: Context) {
    private val pm = context.packageManager

    /** package -> art decoded for its current install; reused across reloads (every Home resume). */
    private val artCache = mutableMapOf<String, Art>()

    private class Art(val updated: Long, val banner: ImageBitmap?, val icon: ImageBitmap?)

    @Synchronized
    fun load(): List<Tile> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tiles = pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val ai = ri.activityInfo
                val art = art(ri)
                Tile(
                    key = "app:${ai.packageName}",
                    label = ri.loadLabel(pm).toString(),
                    kind = Tile.Kind.APP,
                    packageName = ai.packageName,
                    launchIntent = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setComponent(ComponentName(ai.packageName, ai.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                    banner = art.banner,
                    icon = art.icon,
                )
            }
            .sortedBy { it.label.lowercase() }
        artCache.keys.retainAll(tiles.mapNotNull { it.packageName }.toSet())
        return tiles
    }

    private fun art(ri: ResolveInfo): Art {
        val ai = ri.activityInfo
        val updated = runCatching { pm.getPackageInfo(ai.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        artCache[ai.packageName]?.takeIf { it.updated == updated }?.let { return it }
        val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
        return Art(
            updated = updated,
            banner = banner?.toBitmap(BANNER_WIDTH, BANNER_HEIGHT)?.asImageBitmap(),
            icon = if (banner == null) ri.loadIcon(pm).toBitmap(ICON_SIZE, ICON_SIZE).asImageBitmap() else null,
        ).also { artCache[ai.packageName] = it }
    }

    private companion object {
        // Biggest on-screen size at 320 dpi (2 px per dp): a focused dock tile, 148dp × 1.12 ≈ 166dp; grid cells are 155dp.
        const val BANNER_WIDTH = 336
        const val BANNER_HEIGHT = 189
        // Shown at 40dp.
        const val ICON_SIZE = 80
    }
}

class InputsRepository(private val context: Context) {
    /** hardware input id -> last CEC device name seen on that port. */
    private val names = context.getSharedPreferences("inputs", Context.MODE_PRIVATE)

    fun load(): List<Tile> {
        val tim = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        val all = try {
            tim.tvInputList
        } catch (e: Exception) {
            Log.w(TAG, "tvInputList failed", e)
            return emptyList()
        }
        return all
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }
            .sortedBy { it.id }
            .mapIndexed { index, hw ->
                // A CEC device (e.g. "PlayStation 5") shows up as a child input of the HDMI port.
                // It is missing while the device is off or CEC discovery has not run yet, so the name is cached.
                val detected = all.firstOrNull { it.parentId == hw.id }?.loadLabel(context)?.toString()
                if (detected != null) remember(hw.id, detected)
                val port = "HDMI ${index + 1}"
                Tile(
                    key = "input:${hw.id}",
                    label = detected ?: names.getString(hw.id, null) ?: port,
                    kind = Tile.Kind.INPUT,
                    portName = port,
                    // Tune the port, not the CEC child input: selecting the child makes the TV send
                    // <User Control Pressed>[Power] and wakes a console in rest mode. The port only sends <Routing Change>.
                    launchIntent = Intent(
                        Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(hw.id),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
    }

    /**
     * Rows for the Inputs menu, after CleanYourTV's showInputs: HDMI ports in port order, named
     * "<device> · HDMI n" (CEC device, cached device or the name given in Settings > Inputs), then AV,
     * then Live TV. Ports are tuned directly, never the CEC child input, so a console in rest mode stays asleep.
     */
    fun menu(): List<InputRow> {
        val tim = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        val all = try {
            tim.tvInputList
        } catch (e: Exception) {
            Log.w(TAG, "tvInputList failed", e)
            return emptyList()
        }
        val ports = all.filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }.sortedBy { it.id }
        // Some TVs report every port as connected whatever is plugged in; then the state says nothing.
        val connected = ports.count { stateOf(tim, it, all) == TvInputManager.INPUT_STATE_CONNECTED }
        val stateKnown = !(ports.size > 1 && connected == ports.size)
        val rows = ports.mapIndexed { index, hw ->
            val device = all.firstOrNull { it.parentId == hw.id }
            val name = device?.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
                ?: device?.loadLabel(context)?.toString()
                ?: names.getString(hw.id, null)
                ?: hw.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
            val port = "HDMI ${index + 1}"
            InputRow(
                label = name?.let { "$it · $port" } ?: port,
                icon = R.drawable.ic_input,
                state = if (stateKnown) stateOf(tim, hw, all) else null,
                intent = passthrough(hw.id),
                group = 0,
            )
        }.toMutableList()
        all.filter { it.type == TvInputInfo.TYPE_COMPOSITE && it.isPassthroughInput && !it.isHidden(context) }
            .forEach { rows += InputRow("AV", R.drawable.ic_av, null, passthrough(it.id), group = 1) }
        liveTvIntent(all)?.let { rows += InputRow("Телеканали", R.drawable.ic_livetv, null, it, group = 2) }
        return rows
    }

    private fun stateOf(tim: TvInputManager, hw: TvInputInfo, all: List<TvInputInfo>): Int {
        val id = all.firstOrNull { it.parentId == hw.id }?.id ?: hw.id
        return runCatching { tim.getInputState(id) }.getOrDefault(-1)
    }

    private fun passthrough(id: String) =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Xiaomi's Live TV on the hardware DVB tuner; opened plainly it reopens the last input, often an HDMI port. */
    private fun liveTvIntent(all: List<TvInputInfo>): Intent? {
        val pm = context.packageManager
        val app = pm.getLeanbackLaunchIntentForPackage(LIVE_TV) ?: return null
        val tuner = all.filter { it.type == TvInputInfo.TYPE_TUNER && it.id.matches(Regex(".*/HW\\d+$")) }
            .filterNot { it.id.contains("analog", ignoreCase = true) }
            .minByOrNull { it.id }
        val tuned = tuner?.let {
            Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(it.id)).setPackage(LIVE_TV)
        }
        return (tuned?.takeIf { it.resolveActivity(pm) != null } ?: app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun remember(inputId: String, name: String) {
        if (names.getString(inputId, null) == name) return
        // The device moved to another port: forget it on the old one.
        val stale = names.all.filter { (id, value) -> id != inputId && value == name }.keys
        names.edit().apply {
            stale.forEach { remove(it) }
            putString(inputId, name)
        }.apply()
    }

    /** Hardware inputs that have a CEC device attached, i.e. something worth putting in the dock. */
    fun withDevice(inputs: List<Tile>) = inputs.filter { it.label != it.portName }

    private companion object {
        const val LIVE_TV = "com.mitv.livetv"
    }
}

/** A row of the Inputs menu; [state] is a TvInputManager.INPUT_STATE_* worth showing, [group] draws separators. */
data class InputRow(val label: String, val icon: Int, val state: Int?, val intent: Intent, val group: Int)

class ContentRepository(context: Context) {
    private val resolver = context.contentResolver
    private val watchNextUri = Uri.parse("content://android.media.tv/watch_next_program")
    private val previewUri = Uri.parse("content://android.media.tv/preview_program")

    fun programsFor(pkg: String): List<Program> {
        val watchNext = query(watchNextUri, pkg, sortColumn = "last_engagement_time_utc_millis")
        val preview = query(previewUri, pkg, sortColumn = "weight")
        return (watchNext + preview).distinctBy { it.title to it.imageUri }.take(20)
    }

    private fun query(uri: Uri, pkg: String, sortColumn: String): List<Program> = try {
        resolver.query(uri, null, null, null, null)?.use { c ->
            fun idx(name: String) = c.getColumnIndex(name)
            val iPkg = idx("package_name")
            val iTitle = idx("title")
            val iEpisode = idx("episode_title")
            val iPoster = idx("poster_art_uri")
            val iThumb = idx("thumbnail_uri")
            val iIntent = idx("intent_uri")
            val iBrowsable = idx("browsable")
            val iPos = idx("last_playback_position_millis")
            val iDuration = idx("duration_millis")
            val iSort = idx(sortColumn)
            val rows = mutableListOf<Pair<Long, Program>>()
            val perPackage = mutableMapOf<String, Int>()
            var total = 0
            while (c.moveToNext()) {
                total++
                val rowPkg = if (iPkg >= 0) c.getString(iPkg) else null
                if (rowPkg != null) perPackage[rowPkg] = (perPackage[rowPkg] ?: 0) + 1
                if (rowPkg != pkg) continue
                if (iBrowsable >= 0 && c.getInt(iBrowsable) == 0) continue
                val title = c.getStringOrNull(iTitle) ?: continue
                val pos = c.getLongOrNull(iPos) ?: 0L
                val duration = c.getLongOrNull(iDuration) ?: 0L
                rows += (c.getLongOrNull(iSort) ?: 0L) to Program(
                    title = title,
                    subtitle = c.getStringOrNull(iEpisode),
                    imageUri = c.getStringOrNull(iPoster) ?: c.getStringOrNull(iThumb),
                    intentUri = c.getStringOrNull(iIntent),
                    progress = if (pos > 0 && duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else null,
                )
            }
            Log.i(TAG, "$uri: $total rows visible, ${rows.size} for $pkg; by package: $perPackage")
            rows.sortedByDescending { it.first }.map { it.second }
        } ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "query $uri failed", e)
        emptyList()
    }

    private fun android.database.Cursor.getStringOrNull(i: Int) =
        if (i >= 0 && !isNull(i)) getString(i)?.takeIf { it.isNotBlank() } else null

    private fun android.database.Cursor.getLongOrNull(i: Int) =
        if (i >= 0 && !isNull(i)) getLong(i) else null
}

class DockPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("dock", Context.MODE_PRIVATE)

    fun load(): List<String>? = prefs.getString("order", null)?.split('|')?.filter { it.isNotBlank() }

    fun save(keys: List<String>) = prefs.edit().putString("order", keys.joinToString("|")).apply()

    companion object {
        /** Initial dock, in this order, for packages that are installed. */
        val DEFAULT_APPS = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.apple.atve.androidtv.appletv",
            "com.amazon.amazonvideo.livingroom",
            "tv.sweet.tvplayer",
            "ua.kino",
            "com.mitv.livetv",
        )
    }
}
