package ua.home.launcher

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Screensaver: time and date on black, moved to a new spot every minute.
 * Replaces Google's ambient mode (dreamx), which held ~160 MB while running. Plain Views, no Compose:
 * a dream has no lifecycle owner, and nothing here needs one.
 * Enable: adb shell settings put secure screensaver_components ua.home.launcher/.ClockDream
 */
class ClockDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var block: LinearLayout
    private lateinit var time: TextView
    private lateinit var date: TextView

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

    private val tick = object : Runnable {
        override fun run() {
            update()
            move()
            // Next run right after the minute changes.
            handler.postDelayed(this, MINUTE - System.currentTimeMillis() % MINUTE + 50)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        time = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 120f)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            includeFontPadding = false
        }
        date = TextView(this).apply {
            setTextColor(Color.argb(150, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(time)
            addView(date)
            alpha = 0f
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(block, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        update()
        // Wait for the first layout so the block's size is known.
        root.post { handler.post(tick) }
    }

    override fun onDreamingStopped() {
        handler.removeCallbacksAndMessages(null)
        super.onDreamingStopped()
    }

    private fun update() {
        val now = Date()
        time.text = timeFormat.format(now)
        date.text = dateFormat.format(now).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    /** Fade out, jump to a random spot that keeps the block on screen, fade back in. */
    private fun move() {
        val maxX = (root.width - block.width).coerceAtLeast(0)
        val maxY = (root.height - block.height).coerceAtLeast(0)
        block.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            block.translationX = Random.nextInt(maxX + 1).toFloat()
            block.translationY = Random.nextInt(maxY + 1).toFloat()
            block.animate().alpha(1f).setDuration(FADE_MS).start()
        }.start()
    }

    private companion object {
        const val MINUTE = 60_000L
        const val FADE_MS = 600L
    }
}
