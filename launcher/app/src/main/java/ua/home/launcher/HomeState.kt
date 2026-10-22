package ua.home.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeState(context: Context) {
    private val appsRepo = AppsRepository(context)
    private val inputsRepo = InputsRepository(context)
    private val contentRepo = ContentRepository(context)
    private val prefs = DockPrefs(context)

    var apps by mutableStateOf<List<Tile>>(emptyList())
        private set
    var inputs by mutableStateOf<List<Tile>>(emptyList())
        private set
    val dockKeys = mutableStateListOf<String>()

    /** Dock tile that last had focus; the hero row shows its content. */
    var activeKey by mutableStateOf<String?>(null)
    var focusedProgram by mutableStateOf<Program?>(null)

    /** package -> cards; a missing key means "not loaded yet". */
    val programs = mutableStateMapOf<String, List<Program>>()

    var showAllApps by mutableStateOf(false)
    /** Header menu that is open, if any. */
    var dropdown by mutableStateOf<Dropdown?>(null)
    var menuFor by mutableStateOf<Tile?>(null)

    /** Bumped on resume to reload apps and content. */
    var resumeCount by mutableIntStateOf(0)

    /** Bumped when focus must go back to the dock (Home pressed, tile removed). */
    var resetSignal by mutableIntStateOf(0)

    val tilesByKey: Map<String, Tile> get() = (inputs + apps).associateBy { it.key }
    val dock: List<Tile> get() = tilesByKey.let { all -> dockKeys.mapNotNull { all[it] } }

    suspend fun reload() {
        val (loadedApps, loadedInputs) = withContext(Dispatchers.IO) { appsRepo.load() to inputsRepo.load() }
        apps = loadedApps
        inputs = loadedInputs
        if (dockKeys.isEmpty()) {
            val defaults = inputsRepo.withDevice(loadedInputs).map { it.key } +
                DockPrefs.DEFAULT_APPS.map { "app:$it" }.filter { key -> loadedApps.any { it.key == key } }
            dockKeys.addAll(prefs.load() ?: defaults)
        }
        programs.clear()
    }

    suspend fun inputMenu(): List<InputRow> = withContext(Dispatchers.IO) { inputsRepo.menu() }

    suspend fun loadPrograms(tile: Tile) {
        val pkg = tile.packageName ?: return
        if (programs.containsKey(pkg)) return
        programs[pkg] = withContext(Dispatchers.IO) { contentRepo.programsFor(pkg) }
    }

    fun toggleDock(key: String) {
        if (!dockKeys.remove(key)) dockKeys.add(key)
        prefs.save(dockKeys.toList())
    }

    fun move(key: String, delta: Int) {
        val from = dockKeys.indexOf(key)
        val to = (from + delta).coerceIn(0, dockKeys.lastIndex)
        if (from < 0 || from == to) return
        dockKeys.add(to, dockKeys.removeAt(from))
        prefs.save(dockKeys.toList())
    }
}

enum class Dropdown { INPUTS, NETWORK, SETTINGS }
