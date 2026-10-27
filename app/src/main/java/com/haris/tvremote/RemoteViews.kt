package com.haris.tvremote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** What a key draws on its face. */
enum class Icon {
    POWER, ASSISTANT, MENU, MIC, PROFILE, SETTINGS, BACK, HOME, LIVE_TV,
    CHANNEL_UP, CHANNEL_DOWN, MUTE, PLAY_PAUSE, REWIND, FAST_FORWARD,
    VOLUME_UP, VOLUME_DOWN, APPS, KEYBOARD, YOUTUBE, TEXT
}

object Palette {
    val BODY = Color.rgb(0xD4, 0xD5, 0xDA)
    val KEY = Color.rgb(0xF7, 0xF7, 0xF9)
    val KEY_DARK = Color.rgb(0x1E, 0x1E, 0x22)
    val INK = Color.rgb(0x2A, 0x2A, 0x30)
    val PRESSED = Color.rgb(0xB8, 0xBA, 0xC2)
    val RED = Color.rgb(0xE5, 0x39, 0x35)
}

/**
 * One remote key: a circle with a drawn icon. A [flat] key draws no circle
 * of its own and sits inside a shared pill, like the channel rocker.
 * [onPress] fires on touch down and [onRelease] on touch up, so holding a key
 * holds it on the TV too (volume and arrows repeat like a real remote).
 */
@SuppressLint("ViewConstructor")
class RemoteKey(
    context: Context,
    private val icon: Icon,
    private val label: String = "",
    private val dark: Boolean = false,
    private val flat: Boolean = false,
) : View(context) {

    var onPress: (() -> Unit)? = null
    var onRelease: (() -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 0, 0, 0) }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = RectF()
    private var down = false

    init {
        isClickable = true
        contentDescription = label.ifEmpty { icon.name.lowercase().replace('_', ' ') }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onPress?.invoke()
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (down) onRelease?.invoke()
                down = false
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 3f
        rect.set(pad, pad, w - pad, h - pad)
        val r = min(rect.width(), rect.height()) / 2f
        fill.color = when {
            down -> if (dark) Color.rgb(0x44, 0x44, 0x4A) else Palette.PRESSED
            dark -> Palette.KEY_DARK
            else -> Palette.KEY
        }
        if (!flat) {
            canvas.drawRoundRect(rect.left, rect.top + 2f, rect.right, rect.bottom + 2f, r, r, shadow)
            canvas.drawRoundRect(rect, r, r, fill)
        } else if (down) {
            canvas.drawRoundRect(rect, r, r, fill)
        }

        val cx = w / 2f
        val cy = h / 2f
        val s = min(w, h) * 0.22f
        val color = if (dark) Color.WHITE else Palette.INK
        ink.color = color
        ink.strokeWidth = s * 0.22f
        solid.color = color
        text.color = color
        drawIcon(canvas, cx, cy, s)
    }

    private fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        when (icon) {
            Icon.POWER -> {
                ink.color = Palette.RED
                rect.set(cx - s, cy - s, cx + s, cy + s)
                c.drawArc(rect, -60f, 300f, false, ink)
                c.drawLine(cx, cy - s * 1.25f, cx, cy - s * 0.1f, ink)
            }
            Icon.ASSISTANT -> {
                val colors = intArrayOf(
                    Color.rgb(0xEA, 0x43, 0x35), Color.rgb(0xFB, 0xBC, 0x05),
                    Color.rgb(0x34, 0xA8, 0x53), Color.rgb(0x42, 0x85, 0xF4)
                )
                for (i in 0..3) {
                    solid.color = colors[i]
                    c.drawCircle(cx + (i - 1.5f) * s * 0.75f, cy, s * 0.24f, solid)
                }
            }
            Icon.MENU -> for (i in -1..1) c.drawLine(cx - s, cy + i * s * 0.6f, cx + s, cy + i * s * 0.6f, ink)
            Icon.MIC -> {
                ink.color = Color.rgb(0x42, 0x85, 0xF4)
                rect.set(cx - s * 0.4f, cy - s * 1.1f, cx + s * 0.4f, cy + s * 0.3f)
                c.drawRoundRect(rect, s * 0.4f, s * 0.4f, ink)
                ink.color = Color.rgb(0xEA, 0x43, 0x35)
                rect.set(cx - s * 0.8f, cy - s * 0.6f, cx + s * 0.8f, cy + s * 0.7f)
                c.drawArc(rect, 0f, 180f, false, ink)
                ink.color = Color.rgb(0x34, 0xA8, 0x53)
                c.drawLine(cx, cy + s * 0.7f, cx, cy + s * 1.1f, ink)
            }
            Icon.PROFILE -> {
                c.drawCircle(cx, cy - s * 0.45f, s * 0.42f, solid)
                rect.set(cx - s * 0.85f, cy + s * 0.15f, cx + s * 0.85f, cy + s * 1.6f)
                c.drawArc(rect, 180f, 180f, true, solid)
            }
            Icon.SETTINGS -> {
                for (i in 0 until 8) {
                    c.save()
                    c.rotate(i * 45f, cx, cy)
                    c.drawRect(cx - s * 0.2f, cy - s * 1.05f, cx + s * 0.2f, cy - s * 0.5f, solid)
                    c.restore()
                }
                c.drawCircle(cx, cy, s * 0.7f, solid)
                fill.color = if (down) Palette.PRESSED else Palette.KEY
                c.drawCircle(cx, cy, s * 0.3f, fill)
            }
            Icon.BACK -> {
                c.drawLine(cx - s * 0.9f, cy, cx + s * 0.9f, cy, ink)
                path.moveTo(cx - s * 0.2f, cy - s * 0.7f)
                path.lineTo(cx - s * 0.9f, cy)
                path.lineTo(cx - s * 0.2f, cy + s * 0.7f)
                c.drawPath(path, ink)
            }
            Icon.HOME -> {
                path.moveTo(cx, cy - s)
                path.lineTo(cx + s, cy - s * 0.05f)
                path.lineTo(cx + s * 0.75f, cy - s * 0.05f)
                path.lineTo(cx + s * 0.75f, cy + s * 0.9f)
                path.lineTo(cx + s * 0.25f, cy + s * 0.9f)
                path.lineTo(cx + s * 0.25f, cy + s * 0.35f)
                path.lineTo(cx - s * 0.25f, cy + s * 0.35f)
                path.lineTo(cx - s * 0.25f, cy + s * 0.9f)
                path.lineTo(cx - s * 0.75f, cy + s * 0.9f)
                path.lineTo(cx - s * 0.75f, cy - s * 0.05f)
                path.lineTo(cx - s, cy - s * 0.05f)
                path.close()
                c.drawPath(path, solid)
            }
            Icon.LIVE_TV -> {
                ink.strokeWidth = s * 0.16f
                rect.set(cx - s, cy - s * 0.45f, cx + s, cy + s * 0.85f)
                c.drawRoundRect(rect, s * 0.15f, s * 0.15f, ink)
                c.drawLine(cx - s * 0.45f, cy - s, cx, cy - s * 0.5f, ink)
                c.drawLine(cx + s * 0.45f, cy - s, cx, cy - s * 0.5f, ink)
            }
            Icon.CHANNEL_UP, Icon.CHANNEL_DOWN -> {
                val dir = if (icon == Icon.CHANNEL_UP) -1f else 1f
                path.moveTo(cx - s * 0.8f, cy - dir * s * 0.35f)
                path.lineTo(cx, cy + dir * s * 0.45f)
                path.lineTo(cx + s * 0.8f, cy - dir * s * 0.35f)
                c.drawPath(path, ink)
            }
            Icon.MUTE, Icon.VOLUME_UP, Icon.VOLUME_DOWN -> {
                val ox = cx - s * 0.45f
                path.moveTo(ox - s * 0.7f, cy - s * 0.35f)
                path.lineTo(ox - s * 0.25f, cy - s * 0.35f)
                path.lineTo(ox + s * 0.3f, cy - s * 0.85f)
                path.lineTo(ox + s * 0.3f, cy + s * 0.85f)
                path.lineTo(ox - s * 0.25f, cy + s * 0.35f)
                path.lineTo(ox - s * 0.7f, cy + s * 0.35f)
                path.close()
                c.drawPath(path, solid)
                val gx = cx + s * 0.65f
                ink.strokeWidth = s * 0.18f
                when (icon) {
                    Icon.MUTE -> {
                        c.drawLine(gx - s * 0.35f, cy - s * 0.35f, gx + s * 0.35f, cy + s * 0.35f, ink)
                        c.drawLine(gx - s * 0.35f, cy + s * 0.35f, gx + s * 0.35f, cy - s * 0.35f, ink)
                    }
                    Icon.VOLUME_UP -> {
                        c.drawLine(gx - s * 0.4f, cy, gx + s * 0.4f, cy, ink)
                        c.drawLine(gx, cy - s * 0.4f, gx, cy + s * 0.4f, ink)
                    }
                    else -> c.drawLine(gx - s * 0.4f, cy, gx + s * 0.4f, cy, ink)
                }
            }
            Icon.PLAY_PAUSE -> {
                path.moveTo(cx - s, cy - s * 0.75f)
                path.lineTo(cx + s * 0.05f, cy)
                path.lineTo(cx - s, cy + s * 0.75f)
                path.close()
                c.drawPath(path, solid)
                c.drawRect(cx + s * 0.3f, cy - s * 0.75f, cx + s * 0.55f, cy + s * 0.75f, solid)
                c.drawRect(cx + s * 0.8f, cy - s * 0.75f, cx + s * 1.05f, cy + s * 0.75f, solid)
            }
            Icon.REWIND, Icon.FAST_FORWARD -> {
                val d = if (icon == Icon.FAST_FORWARD) 1f else -1f
                for (k in 0..1) {
                    val ox = cx + d * (k - 0.5f) * s * 0.9f
                    path.moveTo(ox - d * s * 0.45f, cy - s * 0.6f)
                    path.lineTo(ox + d * s * 0.45f, cy)
                    path.lineTo(ox - d * s * 0.45f, cy + s * 0.6f)
                    path.close()
                }
                c.drawPath(path, solid)
            }
            Icon.APPS -> {
                val step = s * 0.62f
                for (i in -1..1) for (j in -1..1) {
                    c.drawRect(
                        cx + i * step - s * 0.22f, cy + j * step - s * 0.22f,
                        cx + i * step + s * 0.22f, cy + j * step + s * 0.22f, solid
                    )
                }
            }
            Icon.KEYBOARD -> {
                ink.strokeWidth = s * 0.14f
                rect.set(cx - s * 1.1f, cy - s * 0.65f, cx + s * 1.1f, cy + s * 0.65f)
                c.drawRoundRect(rect, s * 0.15f, s * 0.15f, ink)
                for (row in 0..1) for (col in 0..3) {
                    val x = cx - s * 0.7f + col * s * 0.47f
                    val y = cy - s * 0.25f + row * s * 0.35f
                    c.drawRect(x - s * 0.1f, y - s * 0.08f, x + s * 0.1f, y + s * 0.08f, solid)
                }
                c.drawLine(cx - s * 0.45f, cy + s * 0.42f, cx + s * 0.45f, cy + s * 0.42f, ink)
            }
            Icon.YOUTUBE -> {
                val h = s * 1.5f
                val w = h * 1.42f
                val textPaint = text.apply { color = Palette.INK; textSize = h * 0.95f; textAlign = Paint.Align.LEFT }
                val total = w + h * 0.3f + textPaint.measureText("YouTube")
                val left = cx - total / 2f
                solid.color = Palette.RED
                rect.set(left, cy - h / 2f, left + w, cy + h / 2f)
                c.drawRoundRect(rect, h * 0.28f, h * 0.28f, solid)
                solid.color = Color.WHITE
                val tx = left + w / 2f
                path.moveTo(tx - h * 0.2f, cy - h * 0.25f)
                path.lineTo(tx + h * 0.28f, cy)
                path.lineTo(tx - h * 0.2f, cy + h * 0.25f)
                path.close()
                c.drawPath(path, solid)
                c.drawText("YouTube", left + w + h * 0.3f, cy + textPaint.textSize * 0.35f, textPaint)
                text.textAlign = Paint.Align.CENTER
            }
            Icon.TEXT -> {
                text.textSize = s * 1.15f
                c.drawText(label, cx, cy + text.textSize * 0.36f, text)
            }
        }
    }
}

/**
 * The round D-pad: a dark ring with up/down/left/right zones and an OK button
 * in the middle. Reports which zone was pressed and released.
 */
class DPadView(context: Context) : View(context) {

    enum class Zone { UP, DOWN, LEFT, RIGHT, OK }

    var onPress: ((Zone) -> Unit)? = null
    var onRelease: ((Zone) -> Unit)? = null

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val center = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(45, 0, 0, 0) }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x8A, 0x8A, 0x92) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val okText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x9A, 0x9A, 0xA0)
        textAlign = Paint.Align.CENTER
    }
    private val arc = RectF()
    private var pressed: Zone? = null

    init {
        isClickable = true
        contentDescription = "Direction pad"
    }

    private fun zoneAt(x: Float, y: Float): Zone {
        val dx = x - width / 2f
        val dy = y - height / 2f
        val outer = min(width, height) / 2f
        if (hypot(dx, dy) < outer * 0.48f) return Zone.OK
        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        return when {
            deg >= -45 && deg < 45 -> Zone.RIGHT
            deg >= 45 && deg < 135 -> Zone.DOWN
            deg >= -135 && deg < -45 -> Zone.UP
            else -> Zone.LEFT
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val z = zoneAt(event.x, event.y)
                pressed = z
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onPress?.invoke(z)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressed?.let { onRelease?.invoke(it) }
                pressed = null
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 6f
        val inner = r * 0.48f

        ring.color = Palette.KEY_DARK
        canvas.drawCircle(cx, cy + 3f, r, shadow)
        canvas.drawCircle(cx, cy, r, ring)

        pressed?.takeIf { it != Zone.OK }?.let {
            val start = when (it) {
                Zone.RIGHT -> -45f
                Zone.DOWN -> 45f
                Zone.LEFT -> 135f
                else -> 225f
            }
            arc.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(arc, start, 90f, true, glow)
        }

        // Small dots marking the four directions
        val m = (r + inner) / 2f
        val dot = r * 0.035f
        canvas.drawCircle(cx, cy - m, dot, mark)
        canvas.drawCircle(cx, cy + m, dot, mark)
        canvas.drawCircle(cx - m, cy, dot, mark)
        canvas.drawCircle(cx + m, cy, dot, mark)

        center.color = if (pressed == Zone.OK) Palette.PRESSED else Palette.KEY
        canvas.drawCircle(cx, cy, inner, center)
        okText.textSize = inner * 0.45f
        canvas.drawText("OK", cx, cy + okText.textSize * 0.36f, okText)
    }
}
