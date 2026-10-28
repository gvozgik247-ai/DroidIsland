package com.naymy.island

import android.graphics.Bitmap
import android.media.session.MediaController
import android.os.SystemClock

object State {
    var title = ""
    var artist = ""
    var playing = false
    var art: Bitmap? = null
    var hasMusic = false
    var posMs = 0L
    var durMs = 0L
    var posStamp = 0L
    var speed = 1f

    var navTitle = ""
    var navText = ""
    var hasNav = false

    var timerEnd = 0L
    var timerTotal = 0L
    var torch = false

    var controller: MediaController? = null
    var onChange: (() -> Unit)? = null

    val hasTimer: Boolean get() = timerEnd != 0L

    fun curPos(): Long {
        if (!playing) return posMs
        val p = posMs + ((SystemClock.elapsedRealtime() - posStamp) * speed).toLong()
        return if (durMs > 0) p.coerceAtMost(durMs) else p
    }

    fun timerLeft(): Long =
        if (timerEnd == 0L) 0L else (timerEnd - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    fun activities(): List<String> {
        val l = ArrayList<String>()
        if (hasMusic) l.add("music")
        if (hasNav) l.add("nav")
        if (hasTimer) l.add("timer")
        if (torch) l.add("torch")
        return l
    }

    fun notifyChange() { onChange?.invoke() }
}
