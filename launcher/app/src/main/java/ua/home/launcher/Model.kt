package ua.home.launcher

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap

/** Something that can sit in the dock or the all-apps grid. */
data class Tile(
    /** Stable id: "app:<package>" or "input:<hardware input id>". */
    val key: String,
    val label: String,
    val kind: Kind,
    val launchIntent: Intent,
    val packageName: String? = null,
    /** For inputs: "HDMI 1", "HDMI 2"... */
    val portName: String? = null,
    val banner: ImageBitmap? = null,
    val icon: ImageBitmap? = null,
) {
    enum class Kind { APP, INPUT }
}

/** A card from an app's Watch Next row or preview channel. */
data class Program(
    val title: String,
    val subtitle: String?,
    val imageUri: String?,
    val intentUri: String?,
    /** 0..1 when the app reported a playback position. */
    val progress: Float?,
)
