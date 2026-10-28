package com.naymy.island

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.*
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class IslandService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private lateinit var view: IslandView
    private lateinit var lp: WindowManager.LayoutParams
    private var expanded = false
    private var p = 0f
    private var anim: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val collapseLater = Runnable { setExpanded(false) }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> apply() }
    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { refreshInfo(); apply() }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = IslandView(this)
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        view.setOnClickListener { setExpanded(!expanded) }
        refreshInfo(); apply()
        wm.addView(view, lp)
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(this, tick, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun dp(v: Int) = v * resources.displayMetrics.density

    private fun lerp(a: Float, b: Float) = a + (b - a) * p

    private fun apply() {
        if (!::view.isInitialized) return
        lp.width = lerp(dp(Prefs.get(this, "w")), dp(Prefs.get(this, "ew"))).toInt()
        lp.height = lerp(dp(Prefs.get(this, "h")), dp(Prefs.get(this, "eh"))).toInt()
        lp.y = dp(Prefs.get(this, "top")).toInt()
        lp.x = dp(Prefs.get(this, "hx")).toInt()
        view.radiusPx = lerp(dp(Prefs.get(this, "r")), dp(Prefs.get(this, "er")))
        view.progress = p
        view.invalidate()
        if (view.isAttachedToWindow) wm.updateViewLayout(view, lp)
    }

    private fun refreshInfo() {
        view.time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        view.date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val sc = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        view.battery = "${lvl * 100 / sc}%"
    }

    private fun setExpanded(e: Boolean) {
        expanded = e
        handler.removeCallbacks(collapseLater)
        if (e) { refreshInfo(); handler.postDelayed(collapseLater, 4000) }
        anim?.cancel()
        anim = ValueAnimator.ofFloat(p, if (e) 1f else 0f).apply {
            duration = if (e) 450 else 300
            interpolator = if (e) OvershootInterpolator(1.1f) else android.view.animation.DecelerateInterpolator()
            addUpdateListener { p = it.animatedValue as Float; apply() }
            start()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(tick) }
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        if (::view.isInitialized) runCatching { wm.removeView(view) }
        super.onDestroy()
    }
}
