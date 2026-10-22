@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalTextApi::class)

package ua.home.launcher

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlin.math.pow

/*
 * Header pills and their drop-down glass menus, after CleanYourTV's launcher
 * (github.com/Gathin23/cleanyourtv, MIT, see LICENSE-cleanyourtv.txt): pill_bg.xml, glass_panel.xml,
 * item_input.xml, input_row_bg.xml and MainActivity.dropIn. Same sizes, colours and timing.
 */

/** Outfit Light (OFL, FONT-LICENSE-OFL.txt). Latin and digits only, so it is used for the clock; Cyrillic falls back. */
val OutfitLight = FontFamily(
    Font(R.font.outfit, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
)

private val PillShape = RoundedCornerShape(21.dp)
private val PanelShape = RoundedCornerShape(20.dp)
private val RowShape = RoundedCornerShape(13.dp)
private val Focused = Color(0xF5FFFFFF)
private val FocusedText = Color(0xFF111111)
val StatusGreen = Color(0xFF34C759)

/** Header button: frosted capsule, white with dark text when focused or while its menu is open. */
@Composable
fun Pill(
    @DrawableRes icon: Int,
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val lit = focused || active
    Surface(
        onClick = onClick,
        modifier = modifier.height(42.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (lit) Focused else Color.Transparent,
            focusedContainerColor = Focused,
            contentColor = if (lit) FocusedText else Color.White,
            focusedContentColor = FocusedText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier
                .then(
                    if (lit) Modifier else Modifier
                        .background(Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF))), PillShape)
                        .border(1.dp, Color(0x38FFFFFF), PillShape)
                )
                .height(42.dp)
                .padding(start = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(icon), contentDescription = null,
                colorFilter = ColorFilter.tint(LocalContentColor.current), modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text, fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.01.em,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp),
            )
        }
    }
}

/**
 * The menu hangs 10dp under [anchor] (root coordinates, px), left-aligned with it or, with [alignEnd],
 * right-aligned, and unfolds from its top edge: fade, scaleY 0.92 → 1, 8dp drop, 220 ms decelerate.
 */
@Composable
fun DropDown(anchor: Rect, alignEnd: Boolean, content: @Composable () -> Unit) {
    val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
    val lift = with(LocalDensity.current) { 8.dp.toPx() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // DecelerateInterpolator(1.6f)
        progress.animateTo(1f, tween(220, easing = { 1f - (1f - it).pow(3.2f) }))
    }
    Layout(
        content = {
            Box(
                Modifier.graphicsLayer {
                    val p = progress.value
                    alpha = p
                    scaleY = 0.92f + 0.08f * p
                    translationY = -lift * (1f - p)
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
            ) { content() }
        },
    ) { measurables, constraints ->
        val panel = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = if (alignEnd) anchor.right.toInt() - panel.width else anchor.left.toInt()
            panel.place(x.coerceIn(0, (constraints.maxWidth - panel.width).coerceAtLeast(0)), anchor.bottom.toInt() + gap)
        }
    }
}

/** Dark glass card that keeps D-pad focus inside it. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, minWidth: Dp = 220.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            // As wide as the widest row, within limits (CleanYourTV: 220dp up to 360dp).
            .widthIn(min = minWidth, max = 380.dp)
            .width(IntrinsicSize.Max)
            .background(Brush.verticalGradient(listOf(Color(0xF22A2A2E), Color(0xF5161618))), PanelShape)
            .border(1.dp, Color(0x33FFFFFF), PanelShape)
            .padding(6.dp)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        content = content,
    )
}

/** Menu row: icon, title, and an optional status on the right, with a green dot when [dot] is set. */
@Composable
fun GlassRow(
    @DrawableRes icon: Int?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    dot: Boolean = false,
    statusColor: Color? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(42.dp),
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Focused,
            contentColor = Color.White,
            focusedContentColor = FocusedText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val content = LocalContentColor.current
            if (icon != null) {
                Image(painterResource(icon), contentDescription = null, colorFilter = ColorFilter.tint(content),
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (status != null) {
                Spacer(Modifier.width(16.dp))
                if (dot) {
                    Box(Modifier.size(7.dp).background(StatusGreen, CircleShape))
                    Spacer(Modifier.width(8.dp))
                }
                val dim = if (content == FocusedText) Color(0x99111111) else Color(0x73FFFFFF)
                Text(status, fontSize = 11.sp, letterSpacing = 0.04.em, maxLines = 1, color = statusColor ?: dim)
            }
        }
    }
}

@Composable
fun GlassSeparator() {
    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
}
