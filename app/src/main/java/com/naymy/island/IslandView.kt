package com.naymy.island

import android.content.Context
import android.graphics.*
import android.view.View

class IslandView(ctx: Context) : View(ctx) {
    var progress = 0f          // 0 = свёрнуто, 1 = раскрыто
    var radiusPx = 0f
    var time = ""
    var date = ""
    var battery = ""

    private val d = ctx.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 44 * d; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3FFFFFF.toInt(); textSize = 14 * d
    }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val r = minOf(radiusPx, height / 2f)
        c.drawRoundRect(rect, r, r, bg)

        // контент проявляется во второй половине раскрытия
        val a = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
        if (a <= 0f) return
        big.alpha = (255 * a).toInt(); small.alpha = (179 * a).toInt()
        val pad = 24 * d
        c.drawText(time, pad, height * 0.55f, big)
        c.drawText(date, pad, height * 0.55f + 28 * d, small)
        val bw = small.measureText(battery)
        c.drawText(battery, width - pad - bw, height * 0.55f + 28 * d, small)
    }
}
