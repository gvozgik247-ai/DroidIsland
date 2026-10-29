package com.naymy.island

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class IslandService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private lateinit var root: IslandRoot
    private lateinit var lp: WindowManager.LayoutParams
    private val view: IslandView get() = root.iv

    private var expanded = false
    private var p = 0f
    private var ext = 0f
    private var extTarget = 0f
    private var screenOn = true
    private var statusVisible = true
    private var mediaMode = 0       // 0 нет, 1 фото, 2 видео
    private var lastBgVer = -1L
    private var lastW = -1
    private var lastH = -1
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE

    // настройки в пикселях
    private var cW = 0f; private var cH = 0f; private var cR = 0f
    private var cTop = 0f; private var cHx = 0f
    private var cEw = 0f; private var cEh = 0f; private var cEr = 0f
    private var stiff = 350f; private var damp = 0.62f; private var dimv = 0.35f
    private var extPx = 0f
    private var collapseMs = 6000L
    private var hideFs = true
    private var haptic = true
    private var longTorch = true

    private val handler = Handler(Looper.getMainLooper())
    private val collapseLater = Runnable { setExpanded(false) }
    private var cm: CameraManager? = null
    private var camId: String? = null

    // пружинная физика: инерция и лёгкий «перелёт»
    private val pHolder = FloatValueHolder(0f)
    private val pSpring = SpringAnimation(pHolder).apply {
        setSpring(SpringForce(0f))
        setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA)
        addUpdateListener { _, v, _ -> p = v.coerceAtLeast(-0.04f); apply() }
    }
    private val eHolder = FloatValueHolder(0f)
    private val eSpring = SpringAnimation(eHolder).apply {
        setSpring(SpringForce(0f))
        setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA)
        addUpdateListener { _, v, _ -> ext = v.coerceAtLeast(0f); apply() }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        loadCfg(); loadColors()
        if (::root.isInitialized) updateVisibility()
        if (Prefs.ver(this) != lastBgVer) loadBg()
        apply()
    }

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; root.setPlaying(false) }
                Intent.ACTION_SCREEN_ON -> { screenOn = true; apply() }
                else -> { refreshInfo(); apply() }
            }
        }
    }

    private val timerRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val s = i?.getIntExtra("sec", 0) ?: 0
            if (s > 0) {
                State.timerEnd = SystemClock.elapsedRealtime() + s * 1000L
                State.timerTotal = s * 1000L
            } else State.timerEnd = 0L
            State.notifyChange()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (screenOn) {
                if (State.hasTimer && State.timerLeft() == 0L) {
                    State.timerEnd = 0L
                    State.notifyChange()
                    setExpanded(true)
                }
                if (root.isAttachedToWindow && (expanded || State.hasTimer)) view.invalidate()
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onServiceConnected() {
        val crash = File(filesDir, "crash.txt")
        if (crash.exists()) { disableSelf(); return }
        try { setup() } catch (e: Throwable) {
            try { crash.writeText("service setup:\n" + android.util.Log.getStackTraceString(e)) } catch (_: Exception) {}
            try { disableSelf() } catch (_: Exception) {}
        }
    }

    private fun setup() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = IslandRoot(this)
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }

        extPx = 46 * resources.displayMetrics.density
        loadCfg(); loadColors()
        view.onToggle = { setExpanded(!expanded) }
        view.onLong = { if (longTorch) toggleTorch() }
        view.onAction = { a, f -> act(a, f) }
        State.onChange = { syncExt(); view.invalidate() }

        initTorch()
        refreshInfo(); loadBg(); apply()
        wm.addView(root, lp)
        updateVisibility()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
        ContextCompat.registerReceiver(this, tick,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, timerRx, IntentFilter("com.naymy.island.TIMER"),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        handler.post(ticker)
        syncExt()
    }

    // ---------- настройки ----------
    private fun loadCfg() {
        val d = resources.displayMetrics.density
        cW = Prefs.get(this, "w") * d; cH = Prefs.get(this, "h") * d; cR = Prefs.get(this, "r") * d
        cTop = Prefs.get(this, "top") * d; cHx = Prefs.get(this, "hx") * d
        cEw = Prefs.get(this, "ew") * d; cEh = Prefs.get(this, "eh") * d; cEr = Prefs.get(this, "er") * d
        stiff = Prefs.get(this, "stiff").toFloat()
        damp = Prefs.get(this, "damp") / 100f
        dimv = Prefs.get(this, "dim") / 100f
        collapseMs = Prefs.get(this, "collapse") * 1000L
        hideFs = Prefs.bool(this, "hideFs", true)
        haptic = Prefs.bool(this, "haptic", true)
        longTorch = Prefs.bool(this, "longTorch", true)
    }

    private fun loadColors() {
        view.bgColor = Prefs.color(this, "cBg")
        view.cardColor = Prefs.color(this, "cCard")
        view.textColor = Prefs.color(this, "cText")
        view.accent = Prefs.color(this, "cAcc")
    }

    private fun loadBg() {
        lastBgVer = Prefs.ver(this)
        when (Prefs.str(this, "bgmode", "none")) {
            "photo" -> {
                mediaMode = 1
                root.setVideo(null)
                val f = File(filesDir, "bg_photo")
                Thread {
                    val bmp = try {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f.path, o)
                        var s = 1
                        while (o.outWidth / s > 1400) s *= 2
                        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
                    } catch (_: Exception) { null }
                    handler.post { view.bgBitmap = bmp; apply() }
                }.start()
            }
            "video" -> {
                mediaMode = 2
                view.bgBitmap = null
                root.setVideo(File(filesDir, "bg_video").path)
            }
            else -> {
                mediaMode = 0
                view.bgBitmap = null
                root.setVideo(null)
            }
        }
    }

    // ---------- фонарик, действия ----------
    private fun initTorch() {
        try {
            cm = getSystemService(CameraManager::class.java)
            camId = cm?.cameraIdList?.firstOrNull {
                cm?.getCameraCharacteristics(it)?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cm?.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == camId) { State.torch = enabled; State.notifyChange() }
                }
            }, handler)
        } catch (_: Exception) {}
    }

    private fun toggleTorch() {
        try { camId?.let { cm?.setTorchMode(it, !State.torch) } } catch (_: Exception) {}
    }

    private fun updateVisibility() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        root.visibility = if (hideFs && land) View.INVISIBLE else View.VISIBLE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::root.isInitialized) updateVisibility()
    }

    private fun act(a: String, f: Float) {
        if (haptic) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        handler.removeCallbacks(collapseLater)
        handler.postDelayed(collapseLater, collapseMs)
        val tc = State.controller?.transportControls
        when (a) {
            "play" -> if (State.playing) tc?.pause() else tc?.play()
            "next" -> tc?.skipToNext()
            "prev" -> tc?.skipToPrevious()
            "seek" -> if (State.durMs > 0) {
                val t = (f * State.durMs).toLong()
                tc?.seekTo(t)
                State.posMs = t
                State.posStamp = SystemClock.elapsedRealtime()
                State.notifyChange()
            }
            "torch" -> toggleTorch()
            "tstop" -> { State.timerEnd = 0L; State.notifyChange() }
        }
    }

    // ---------- анимации ----------
    private fun springP(target: Float) {
        val s = pSpring.spring
        s.setStiffness(if (target > 0.5f) stiff else stiff * 1.3f)
        s.setDampingRatio(damp)
        if (!pSpring.isRunning) pHolder.value = p
        pSpring.animateToFinalPosition(target)
    }

    private fun syncExt() {
        if (!::root.isInitialized) return
        val t = if (State.activities().isEmpty()) 0f else 1f
        if (t == extTarget) { apply(); return }
        extTarget = t
        val s = eSpring.spring
        s.setStiffness(stiff)
        s.setDampingRatio(damp)
        if (!eSpring.isRunning) eHolder.value = ext
        eSpring.animateToFinalPosition(t)
    }

    private fun setExpanded(e: Boolean) {
        expanded = e
        handler.removeCallbacks(collapseLater)
        if (e) { refreshInfo(); handler.postDelayed(collapseLater, collapseMs) }
        springP(if (e) 1f else 0f)
    }

    // ---------- отрисовка окна ----------
    private fun apply() {
        if (!::root.isInitialized) return
        val acts = State.activities()
        view.headH = cH
        val cw = cW + ext * 2f * extPx
        val eh = if (acts.isEmpty()) cEh else view.contentHeight(acts)
        val w = (cw + (cEw - cw) * p).toInt().coerceAtLeast(1)
        val h = (cH + (eh - cH) * p).toInt().coerceAtLeast(1)
        val x = cHx.toInt(); val y = cTop.toInt()
        root.radiusPx = cR + (cEr - cR) * p

        val mA = if (mediaMode == 0) 0f else (p * 1.6f).coerceIn(0f, 1f)
        view.mediaA = mA
        view.dim = dimv
        view.progress = p
        if (mediaMode == 2) {
            root.setVideoAlpha(mA)
            root.setPlaying(screenOn && p > 0.02f)
        } else root.setVideoAlpha(0f)
        view.invalidate()

        if (w != lastW || h != lastH || x != lastX || y != lastY) {
            lp.width = w; lp.height = h; lp.x = x; lp.y = y
            lastW = w; lastH = h; lastX = x; lastY = y
            if (root.isAttachedToWindow) wm.updateViewLayout(root, lp)
        }
    }

    private fun refreshInfo() {
        view.time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        view.date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val sc = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        view.battery = "${lvl * 100 / sc}%"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pSpring.cancel(); eSpring.cancel()
        State.onChange = null
        runCatching { unregisterReceiver(tick) }
        runCatching { unregisterReceiver(timerRx) }
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        if (::root.isInitialized) {
            root.setVideo(null)
            runCatching { wm.removeView(root) }
        }
        super.onDestroy()
    }
}
