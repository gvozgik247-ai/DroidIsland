package com.naymy.island

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

class IslandView(ctx: Context) : View(ctx) {
    var progress = 0f
    var headH = 0f
    var time = ""
    var date = ""
    var battery = ""

    // оформление
    var bgColor = Color.BLACK
    var cardColor = 0xFF1C1C1E.toInt()
    var textColor = Color.WHITE
    var accent = 0xFF4DA3FF.toInt()
    var dim = 0.35f
    var mediaA = 0f
    var bgBitmap: Bitmap? = null

    var onToggle: (() -> Unit)? = null
    var onLong: (() -> Unit)? = null
    var onAction: ((String) -> Unit)? = null

    private val d = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val cardR = RectF()
    private val src = Rect()
    private val path = Path()
    private val hits = ArrayList<Pair<RectF, String>>()

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val a = if (progress > 0.9f) hits.firstOrNull { it.first.contains(e.x, e.y) }?.second else null
            if (a != null) onAction?.invoke(a) else onToggle?.invoke()
            return true
        }
        override fun onLongPress(e: MotionEvent) { onLong?.invoke() }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean = gd.onTouchEvent(event)

    private fun dp(v: Float) = v * d
    private fun col(c: Int, a: Float): Int =
        (c and 0x00FFFFFF) or ((((c ushr 24) and 0xFF) * a).toInt() shl 24)
    private val sec: Int get() = col(textColor, 0.7f)

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, a: Float,
                     bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        tp.textSize = dp(size); tp.color = col(color, a)
        tp.isFakeBoldText = bold; tp.textAlign = align
        c.drawText(s, x, y, tp)
    }

    private fun fit(s: String, size: Float, avail: Float): String {
        tp.textSize = dp(size)
        return TextUtils.ellipsize(s, tp, avail, TextUtils.TruncateAt.END).toString()
    }

    private fun mmss(ms: Long): String {
        val s = (ms + 999) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun cardH(a: String) = dp(if (a == "music") 128f else 58f)

    fun contentHeight(acts: List<String>): Float {
        var h = headH + dp(6f)
        for (a in acts) h += cardH(a) + dp(8f)
        return h + dp(6f)
    }

    private fun addHit(r: RectF, name: String) {
        if (progress > 0.9f) hits.add(Pair(RectF(r), name))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        hits.clear()
        // фон: цвет + фото/видео с затемнением (скругление делает контейнер)
        fill.color = col(bgColor, 1f - mediaA)
        c.drawRect(0f, 0f, w, h, fill)
        if (mediaA > 0f) {
            val b = bgBitmap
            if (b != null) {
                val s = maxOf(w / b.width, h / b.height)
                val sw = (w / s).toInt(); val sh = (h / s).toInt()
                src.set((b.width - sw) / 2, (b.height - sh) / 2, (b.width + sw) / 2, (b.height + sh) / 2)
                rect.set(0f, 0f, w, h)
                fill.color = Color.WHITE; fill.alpha = (255 * mediaA).toInt()
                c.drawBitmap(b, src, rect, fill)
            }
            fill.color = col(Color.BLACK, dim * mediaA)
            c.drawRect(0f, 0f, w, h, fill)
        }

        val acts = State.activities()
        val ca = (1f - progress * 2f).coerceIn(0f, 1f)
        if (ca > 0f) drawCollapsed(c, acts, ca)
        val ea = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
        if (ea > 0f) { if (acts.isEmpty()) drawClock(c, ea) else drawCards(c, acts, ea) }
        if (State.playing && State.hasMusic && progress < 0.5f) postInvalidateOnAnimation()
        else if (progress > 0.9f && (State.playing || State.hasTimer)) postInvalidateDelayed(250)
    }

    // ---------- свёрнутый вид ----------
    private fun drawCollapsed(c: Canvas, acts: List<String>, a: Float) {
        if (acts.isEmpty()) return
        val cy = headH / 2f
        mini(c, acts[0], dp(20f), cy, a)
        if (acts.size > 1) mini(c, acts[1], width - dp(20f), cy, a)
        else if (acts[0] == "music" && State.playing) eq(c, width - dp(20f), cy, a)
    }

    private fun mini(c: Canvas, act: String, cx: Float, cy: Float, a: Float) {
        when (act) {
            "music" -> {
                val art = State.art
                if (art != null) {
                    c.save()
                    path.reset(); path.addCircle(cx, cy, dp(10f), Path.Direction.CW)
                    c.clipPath(path)
                    fill.color = Color.WHITE; fill.alpha = (255 * a).toInt()
                    rect.set(cx - dp(10f), cy - dp(10f), cx + dp(10f), cy + dp(10f))
                    c.drawBitmap(art, null, rect, fill)
                    c.restore()
                } else text(c, "♪", cx, cy + dp(5f), 15f, textColor, a, false, Paint.Align.CENTER)
            }
            "nav" -> arrow(c, cx, cy, 1f, accent, a)
            "timer" -> text(c, mmss(State.timerLeft()), cx, cy + dp(4f), 11f, 0xFFFF9F0A.toInt(), a, true, Paint.Align.CENTER)
            "torch" -> { fill.color = col(0xFFFFD60A.toInt(), a); c.drawCircle(cx, cy, dp(6f), fill) }
        }
    }

    private fun eq(c: Canvas, cx: Float, cy: Float, a: Float) {
        val t = SystemClock.uptimeMillis() / 170.0
        fill.color = col(accent, a)
        for (i in 0..2) {
            val h = dp(4f) + dp(9f) * abs(sin(t + i * 1.9)).toFloat()
            val x = cx - dp(7f) + i * dp(7f)
            rect.set(x - dp(2f), cy - h / 2f, x + dp(2f), cy + h / 2f)
            c.drawRoundRect(rect, dp(2f), dp(2f), fill)
        }
    }

    private fun arrow(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, a: Float) {
        fill.color = col(color, a)
        path.reset()
        path.moveTo(cx + dp(7f) * s, cy - dp(7f) * s)
        path.lineTo(cx - dp(6f) * s, cy - dp(1f) * s)
        path.lineTo(cx - dp(1f) * s, cy + dp(1f) * s)
        path.lineTo(cx + dp(1f) * s, cy + dp(6f) * s)
        path.close()
        c.drawPath(path, fill)
    }

    // ---------- раскрытый вид ----------
    private fun drawClock(c: Canvas, a: Float) {
        val pad = dp(24f)
        text(c, time, pad, headH + dp(46f), 44f, textColor, a)
        text(c, date, pad, headH + dp(72f), 14f, sec, a)
        text(c, battery, width - pad, headH + dp(72f), 14f, sec, a, false, Paint.Align.RIGHT)
    }

    private fun drawCards(c: Canvas, acts: List<String>, a: Float) {
        var y = headH + dp(6f)
        val ca = a * (1f - 0.3f * mediaA)
        for (act in acts) {
            val h = cardH(act)
            cardR.set(dp(14f), y, width - dp(14f), y + h)
            fill.color = col(cardColor, ca)
            c.drawRoundRect(cardR, dp(22f), dp(22f), fill)
            when (act) {
                "music" -> drawMusic(c, cardR, a)
                "nav" -> drawNav(c, cardR, a)
                "timer" -> drawTimer(c, cardR, a)
                "torch" -> drawTorch(c, cardR, a)
            }
            y += h + dp(8f)
        }
    }

    private fun drawMusic(c: Canvas, r: RectF, a: Float) {
        val ax = r.left + dp(12f); val ay = r.top + dp(12f); val sz = dp(56f)
        rect.set(ax, ay, ax + sz, ay + sz)
        val art = State.art
        if (art != null) {
            c.save()
            path.reset(); path.addRoundRect(rect, dp(12f), dp(12f), Path.Direction.CW)
            c.clipPath(path)
            fill.color = Color.WHITE; fill.alpha = (255 * a).toInt()
            c.drawBitmap(art, null, rect, fill)
            c.restore()
        } else {
            fill.color = col(0xFF3A3A3C.toInt(), a)
            c.drawRoundRect(rect, dp(12f), dp(12f), fill)
            text(c, "♪", ax + sz / 2f, ay + sz / 2f + dp(9f), 26f, textColor, a, false, Paint.Align.CENTER)
        }
        val tx = ax + sz + dp(12f)
        val avail = r.right - dp(14f) - tx
        text(c, fit(State.title, 15f, avail), tx, r.top + dp(32f), 15f, textColor, a, true)
        text(c, fit(State.artist, 13f, avail), tx, r.top + dp(52f), 13f, sec, a)
        val bl = r.left + dp(16f); val br = r.right - dp(16f); val by = r.top + dp(80f)
        rect.set(bl, by - dp(2f), br, by + dp(2f))
        fill.color = col(textColor, 0.2f * a)
        c.drawRoundRect(rect, dp(2f), dp(2f), fill)
        if (State.durMs > 0) {
            val f = (State.curPos().toFloat() / State.durMs).coerceIn(0f, 1f)
            rect.set(bl, by - dp(2f), bl + (br - bl) * f, by + dp(2f))
            fill.color = col(accent, a)
            c.drawRoundRect(rect, dp(2f), dp(2f), fill)
        }
        val cy = r.top + dp(106f); val cx = r.centerX()
        icon(c, "prev", cx - dp(64f), cy, a)
        icon(c, if (State.playing) "pause" else "play", cx, cy, a)
        icon(c, "next", cx + dp(64f), cy, a)
        if (progress > 0.9f) {
            rect.set(cx - dp(90f), cy - dp(20f), cx - dp(38f), cy + dp(20f)); addHit(rect, "prev")
            rect.set(cx - dp(32f), cy - dp(20f), cx + dp(32f), cy + dp(20f)); addHit(rect, "play")
            rect.set(cx + dp(38f), cy - dp(20f), cx + dp(90f), cy + dp(20f)); addHit(rect, "next")
        }
    }

    private fun icon(c: Canvas, kind: String, cx: Float, cy: Float, a: Float) {
        fill.color = col(textColor, a)
        path.reset()
        when (kind) {
            "play" -> {
                path.moveTo(cx - dp(7f), cy - dp(10f)); path.lineTo(cx - dp(7f), cy + dp(10f))
                path.lineTo(cx + dp(10f), cy); path.close(); c.drawPath(path, fill)
            }
            "pause" -> {
                rect.set(cx - dp(8f), cy - dp(10f), cx - dp(2.5f), cy + dp(10f)); c.drawRoundRect(rect, dp(1.5f), dp(1.5f), fill)
                rect.set(cx + dp(2.5f), cy - dp(10f), cx + dp(8f), cy + dp(10f)); c.drawRoundRect(rect, dp(1.5f), dp(1.5f), fill)
            }
            "next" -> {
                path.moveTo(cx - dp(9f), cy - dp(8f)); path.lineTo(cx - dp(9f), cy + dp(8f))
                path.lineTo(cx + dp(3f), cy); path.close(); c.drawPath(path, fill)
                rect.set(cx + dp(5f), cy - dp(8f), cx + dp(8f), cy + dp(8f)); c.drawRect(rect, fill)
            }
            "prev" -> {
                path.moveTo(cx + dp(9f), cy - dp(8f)); path.lineTo(cx + dp(9f), cy + dp(8f))
                path.lineTo(cx - dp(3f), cy); path.close(); c.drawPath(path, fill)
                rect.set(cx - dp(8f), cy - dp(8f), cx - dp(5f), cy + dp(8f)); c.drawRect(rect, fill)
            }
        }
    }

    private fun drawNav(c: Canvas, r: RectF, a: Float) {
        arrow(c, r.left + dp(32f), r.centerY(), 1.6f, accent, a)
        val tx = r.left + dp(64f); val avail = r.right - dp(16f) - tx
        text(c, fit(State.navTitle, 15f, avail), tx, r.top + dp(25f), 15f, textColor, a, true)
        text(c, fit(State.navText, 13f, avail), tx, r.top + dp(46f), 13f, sec, a)
    }

    private fun drawTimer(c: Canvas, r: RectF, a: Float) {
        text(c, "Таймер", r.left + dp(20f), r.top + dp(20f), 12f, sec, a)
        text(c, mmss(State.timerLeft()), r.left + dp(20f), r.top + dp(48f), 26f, 0xFFFF9F0A.toInt(), a, true)
        val cx = r.right - dp(32f); val cy = r.centerY()
        fill.color = col(textColor, 0.2f * a)
        c.drawCircle(cx, cy, dp(15f), fill)
        line.color = col(textColor, a); line.strokeWidth = dp(2f)
        c.drawLine(cx - dp(5f), cy - dp(5f), cx + dp(5f), cy + dp(5f), line)
        c.drawLine(cx - dp(5f), cy + dp(5f), cx + dp(5f), cy - dp(5f), line)
        rect.set(cx - dp(24f), cy - dp(24f), cx + dp(24f), cy + dp(24f)); addHit(rect, "tstop")
    }

    private fun drawTorch(c: Canvas, r: RectF, a: Float) {
        fill.color = col(0xFFFFD60A.toInt(), a)
        c.drawCircle(r.left + dp(30f), r.centerY(), dp(9f), fill)
        text(c, "Фонарик", r.left + dp(54f), r.centerY() + dp(6f), 16f, textColor, a, true)
        val bw = dp(64f); val bh = dp(30f)
        rect.set(r.right - dp(16f) - bw, r.centerY() - bh / 2f, r.right - dp(16f), r.centerY() + bh / 2f)
        fill.color = col(textColor, a)
        c.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        text(c, "Выкл", rect.centerX(), rect.centerY() + dp(5f), 14f, bgColor or 0xFF000000.toInt(), a, true, Paint.Align.CENTER)
        addHit(r, "torch")
    }
}
