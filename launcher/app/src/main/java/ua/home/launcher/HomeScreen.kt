@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package ua.home.launcher

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import java.util.Calendar
import java.util.Locale

private val Edge = 48.dp
private val DefaultBackdrop = Brush.linearGradient(
    listOf(Color(0xFF2E1A4D), Color(0xFF1A2847), Color(0xFF0D4A57)),
    start = Offset.Zero,
    end = Offset.Infinite,
)

@Composable
fun HomeScreen(state: HomeState, launch: (Intent) -> Unit) {
    LaunchedEffect(state.resumeCount) { state.reload() }
    // A launcher has nowhere to go back to.
    BackHandler(enabled = true) {}

    val active = state.activeKey?.let { state.tilesByKey[it] }
    val pkg = active?.packageName
    val programs = pkg?.let { state.programs[it] }.orEmpty()
    val loaded = pkg != null && state.programs.containsKey(pkg)
    LaunchedEffect(pkg, loaded) {
        if (active != null && !loaded) {
            delay(250) // don't query while the user is scrolling through the dock
            state.loadPrograms(active)
        }
    }

    val anchors = remember { mutableStateMapOf<Dropdown, Rect>() }
    val pillFocus = remember { Dropdown.entries.associateWith { FocusRequester() } }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Backdrop(state.focusedProgram?.imageUri ?: programs.firstOrNull()?.imageUri)
        Column(Modifier.fillMaxSize()) {
            TopBar(state, anchors, pillFocus)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (active != null) Hero(active, programs, state, launch)
            }
            Dock(state, launch)
            Spacer(Modifier.height(28.dp))
        }
        if (state.showAllApps) AllApps(state, launch)
        state.menuFor?.let { DockMenu(it, state) }
        state.dropdown?.let { which ->
            val anchor = anchors[which] ?: return@let
            DropDown(anchor, alignEnd = which == Dropdown.SETTINGS) {
                HeaderMenu(which, state, launch) {
                    state.dropdown = null
                    // Back to the pill the menu hangs from, as CleanYourTV does.
                    scope.launch {
                        delay(50)
                        runCatching { pillFocus.getValue(which).requestFocus() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Backdrop(imageUri: String?) {
    Box(Modifier.fillMaxSize().background(DefaultBackdrop))
    Crossfade(targetState = imageUri, animationSpec = tween(600), label = "backdrop") { uri ->
        if (uri != null) {
            // Blurring a small decode is cheap; it is stretched to full screen anyway.
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uri).size(192, 108).precision(Precision.EXACT)
                    .transformations(BlurTransformation()).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.35f),
                0.55f to Color.Black.copy(alpha = 0.2f),
                1f to Color.Black.copy(alpha = 0.85f),
            )
        )
    )
}

/**
 * Header after CleanYourTV: greeting in spaced capitals, a thin Outfit clock, a hairline, the date,
 * and three frosted pills (Inputs, network, Settings) whose menus drop down under them.
 */
@Composable
private fun TopBar(state: HomeState, anchors: MutableMap<Dropdown, Rect>, pillFocus: Map<Dropdown, FocusRequester>) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var network by remember { mutableStateOf(NetworkLabel.read(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            network = NetworkLabel.read(context)
            delay(5_000)
        }
    }
    val locale = Locale.getDefault()
    Row(
        Modifier.fillMaxWidth().padding(start = Edge, end = Edge, top = 26.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                greeting(now).uppercase(locale),
                color = Color(0x8CFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                letterSpacing = 0.22.em, maxLines = 1, modifier = Modifier.padding(start = 4.dp),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    SimpleDateFormat("HH:mm", locale).format(now),
                    color = Color.White, fontSize = 52.sp, fontFamily = OutfitLight, letterSpacing = (-0.04).em,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
                Box(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .width(1.dp).height(26.dp).background(Color(0x33FFFFFF))
                )
                Text(
                    SimpleDateFormat("EEEE\nd MMMM", locale).format(now).uppercase(locale),
                    color = Color(0xA6FFFFFF), fontSize = 14.sp, letterSpacing = 0.14.em, lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
        }
        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple(Dropdown.INPUTS, R.drawable.ic_input, "Джерела"),
                Triple(Dropdown.NETWORK, network.icon, network.text),
                Triple(Dropdown.SETTINGS, R.drawable.ic_settings, "Налаштування"),
            ).forEach { (which, icon, text) ->
                Pill(
                    icon = icon,
                    text = text,
                    active = state.dropdown == which,
                    onClick = { state.dropdown = which },
                    modifier = Modifier
                        .focusRequester(pillFocus.getValue(which))
                        .onGloballyPositioned { anchors[which] = it.boundsInRoot() },
                )
            }
        }
    }
}

private fun greeting(now: Date): String {
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Доброї ночі"
        hour < 12 -> "Доброго ранку"
        hour < 17 -> "Добрий день"
        hour < 22 -> "Добрий вечір"
        else -> "Доброї ночі"
    }
}

@Composable
private fun Hero(active: Tile, programs: List<Program>, state: HomeState, launch: (Intent) -> Unit) {
    if (programs.isEmpty()) {
        Column(Modifier.padding(start = Edge, top = 48.dp)) {
            Text(active.label, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (active.kind == Tile.Kind.INPUT) "Натисніть OK, щоб перейти на ${active.portName}"
                else "Тут з'являться «Дивитися далі» та добірки, якщо додаток їх надає",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 16.sp,
            )
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(top = 4.dp)) {
        Text(
            active.label,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = Edge),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Edge, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(programs, key = { it.title + it.imageUri }) { p ->
                ProgramCard(
                    program = p,
                    onFocused = { state.focusedProgram = p },
                    onClick = { launch(programIntent(p) ?: active.launchIntent) },
                )
            }
        }
        state.focusedProgram?.takeIf { it in programs }?.let { p ->
            Text(
                p.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Edge, end = Edge),
            )
            p.subtitle?.let {
                Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1,
                    modifier = Modifier.padding(start = Edge, end = Edge))
            }
        }
    }
}

private fun programIntent(p: Program): Intent? = p.intentUri?.let {
    runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.getOrNull()
}

@Composable
private fun ProgramCard(program: Program, onFocused: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    TileSurface(
        onClick = onClick,
        shape = shape,
        focusedScale = 1.08f,
        modifier = Modifier
            .width(300.dp)
            .aspectRatio(16f / 9f)
            .onFocusChanged { if (it.isFocused) onFocused() },
    ) {
        if (program.imageUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(program.imageUri).size(600, 338).build(),
                contentDescription = program.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(DefaultBackdrop).padding(16.dp)) {
                Text(program.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        program.progress?.let { progress ->
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
            }
        }
    }
}

@Composable
private fun Dock(state: HomeState, launch: (Intent) -> Unit) {
    val dock = state.dock
    val listState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(dock.isNotEmpty(), state.resetSignal) {
        if (dock.isEmpty()) return@LaunchedEffect
        val key = state.activeKey?.takeIf { k -> dock.any { it.key == k } } ?: dock.first().key
        listState.scrollToItem(dock.indexOfFirst { it.key == key })
        delay(80)
        runCatching { requesters[key]?.requestFocus() }
    }

    val panel = RoundedCornerShape(26.dp)
    val density = LocalDensity.current
    // Keep the focused tile well inside the panel so it is never the half-cut one at the edge.
    val scrollSpec = remember(density) { KeepInsideSpec(with(density) { 110.dp.toPx() }) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 880.dp)
                .background(Color.White.copy(alpha = 0.10f), panel)
                .border(1.dp, Color.White.copy(alpha = 0.18f), panel)
                .padding(horizontal = 4.dp)
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec) {
            LazyRow(
                state = listState,
                // Vertical padding leaves room for the focused tile's scale inside the faded layer.
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.edgeFade(listState),
            ) {
                itemsIndexed(dock, key = { _, t -> t.key }) { _, tile ->
                    TileSurface(
                        onClick = { launch(tile.launchIntent) },
                        onLongClick = { state.menuFor = tile },
                        modifier = Modifier
                            .width(148.dp)
                            .aspectRatio(16f / 9f)
                            .focusRequester(requesters.getOrPut(tile.key) { FocusRequester() })
                            .onFocusChanged {
                                if (it.isFocused && state.activeKey != tile.key) {
                                    state.activeKey = tile.key
                                    state.focusedProgram = null
                                }
                            }
                            .onKeyEvent {
                                if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) {
                                    state.menuFor = tile
                                    true
                                } else false
                            },
                    ) { TileArt(tile) }
                }
                item(key = "all") {
                    TileSurface(
                        onClick = { state.showAllApps = true },
                        modifier = Modifier.width(148.dp).aspectRatio(16f / 9f),
                    ) { AllAppsArt() }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private class KeepInsideSpec(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val m = margin.coerceAtMost(((containerSize - size) / 2f).coerceAtLeast(0f))
        val end = offset + size
        return when {
            offset < m -> offset - m
            end > containerSize - m -> end - (containerSize - m)
            else -> 0f
        }
    }
}

/** Fades the row out at an edge when there is more content beyond it. */
private fun Modifier.edgeFade(state: LazyListState) = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = (56.dp.toPx() / size.width).coerceAtMost(0.2f)
        val left = if (state.canScrollBackward) 0f else 1f
        val right = if (state.canScrollForward) 0f else 1f
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = left),
                f to Color.Black,
                1f - f to Color.Black,
                1f to Color.Black.copy(alpha = right),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Focusable rounded card with an Apple TV-like lift: scale, white edge and soft glow. */
@Composable
private fun TileSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.12f,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1C1F26),
            focusedContainerColor = Color(0xFF1C1F26),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.White.copy(alpha = 0.35f), elevation = 14.dp),
        ),
        content = content,
    )
}

@Composable
private fun TileArt(tile: Tile) {
    when {
        tile.kind == Tile.Kind.INPUT -> InputArt(tile)
        tile.banner != null -> Image(
            tile.banner,
            contentDescription = tile.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        else -> Column(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF3A3F4B), Color(0xFF1C1F26)))
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            tile.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
            Text(tile.label, color = Color.White, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun InputArt(tile: Tile) {
    val label = tile.label
    val playStation = label.contains("PlayStation", ignoreCase = true)
    val xbox = label.contains("Xbox", ignoreCase = true)
    val colors = when {
        playStation -> listOf(Color(0xFF0B47C2), Color(0xFF021240))
        xbox -> listOf(Color(0xFF178C17), Color(0xFF073D07))
        else -> listOf(Color(0xFF454B58), Color(0xFF1C1F26))
    }
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (playStation) {
            Image(
                painterResource(R.drawable.logo_ps5),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.62f),
            )
        } else {
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (label != tile.portName) {
            Text(
                tile.portName.orEmpty(),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
            )
        }
    }
}

@Composable
private fun AllAppsArt() {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF2A2E38)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) {
                        Box(Modifier.size(9.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Усі програми", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun AllApps(state: HomeState, launch: (Intent) -> Unit) {
    BackHandler { state.showAllApps = false; state.resetSignal++ }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { first.requestFocus() }
    }
    val tiles = state.inputs + state.apps
    Box(Modifier.fillMaxSize().background(Color(0xF20A0C12))) {
        Column(Modifier.padding(start = Edge, end = Edge, top = 32.dp)) {
            Text("Програми і входи", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "OK: відкрити · утримуйте OK: додати на панель або прибрати з неї",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(vertical = 20.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(tiles, key = { _, t -> t.key }) { index, tile ->
                    Column {
                        TileSurface(
                            onClick = { state.showAllApps = false; launch(tile.launchIntent) },
                            onLongClick = { state.toggleDock(tile.key) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                        ) { TileArt(tile) }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            (if (tile.key in state.dockKeys) "✓ " else "") + tile.label,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockMenu(tile: Tile, state: HomeState) {
    fun close() {
        state.menuFor = null
        state.resetSignal++
    }
    BackHandler { close() }
    val first = remember { FocusRequester() }
    LaunchedEffect(tile.key) {
        delay(50)
        runCatching { first.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        GlassPanel(minWidth = 280.dp) {
            Text(
                tile.label.uppercase(Locale.getDefault()),
                color = Color(0x8CFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.22.em,
                maxLines = 1, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
            )
            GlassRow(null, "Перемістити ліворуч", { state.move(tile.key, -1) }, Modifier.focusRequester(first))
            GlassRow(null, "Перемістити праворуч", { state.move(tile.key, +1) })
            GlassRow(null, "Прибрати з панелі", {
                state.toggleDock(tile.key)
                state.activeKey = null
                close()
            })
            GlassSeparator()
            GlassRow(null, "Готово", { close() })
        }
    }
}

