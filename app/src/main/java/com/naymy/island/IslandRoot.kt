package com.naymy.island

import android.content.Context
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

// Контейнер окна: видео-фон (TextureView) снизу, IslandView сверху, всё обрезается по скруглению.
class IslandRoot(ctx: Context) : FrameLayout(ctx) {
    val iv = IslandView(ctx)
    private val tv = TextureView(ctx)
    private var mp: MediaPlayer? = null
    private var surf: Surface? = null
    private var prepared = false
    private var wantPlay = false
    private var path: String? = null
    private var vw = 0
    private var vh = 0
    private val m = Matrix()

    var radiusPx = 0f
        set(v) { if (field != v) { field = v; invalidateOutline() } }

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val r = radiusPx.coerceAtMost(minOf(view.width, view.height) / 2f)
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        tv.alpha = 0f
        tv.visibility = View.INVISIBLE
        addView(tv, LayoutParams(-1, -1))
        addView(iv, LayoutParams(-1, -1))
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                surf = Surface(st); prepare()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { fit() }
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                release(); surf?.release(); surf = null; return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        fit()
    }

    fun setVideo(p: String?) {
        if (p == path) return
        path = p
        if (p == null) release() else prepare()
    }

    fun setVideoAlpha(a: Float) {
        tv.alpha = a
        val vis = if (a > 0.01f && path != null) View.VISIBLE else View.INVISIBLE
        if (tv.visibility != vis) tv.visibility = vis
    }

    fun setPlaying(b: Boolean) {
        wantPlay = b
        val player = mp ?: return
        if (!prepared) return
        try {
            if (b && !player.isPlaying) player.start()
            else if (!b && player.isPlaying) player.pause()
        } catch (_: Exception) {}
    }

    private fun prepare() {
        release()
        val p = path ?: return
        val s = surf ?: return
        try {
            val player = MediaPlayer()
            player.setDataSource(p)
            player.setSurface(s)
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setOnVideoSizeChangedListener { _, w, h -> vw = w; vh = h; fit() }
            player.setOnPreparedListener {
                prepared = true
                it.start()
                if (!wantPlay) it.pause()
            }
            player.setOnErrorListener { _, _, _ -> true }
            player.prepareAsync()
            mp = player
        } catch (_: Exception) {}
    }

    private fun release() {
        try { mp?.release() } catch (_: Exception) {}
        mp = null
        prepared = false
    }

    private fun fit() {
        val w = tv.width.toFloat(); val h = tv.height.toFloat()
        if (vw == 0 || vh == 0 || w == 0f || h == 0f) return
        val s = maxOf(w / vw, h / vh)
        m.setScale(vw * s / w, vh * s / h, w / 2f, h / 2f)
        tv.setTransform(m)
    }
}
