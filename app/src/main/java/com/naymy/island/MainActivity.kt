package com.naymy.island

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (40 * d).toInt(), (20 * d).toInt(), (24 * d).toInt())
        }
        fun tv(t: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (10 * d).toInt(), 0, 0)
        }
        root.addView(tv("DroidIsland", 26f, true))
        root.addView(tv("Dynamic Island для Pixel 6. Включи службу специальных возможностей, " +
            "затем подгоняй размеры под свой вырез — плашка меняется вживую. Тап по плашке — раскрыть.", 14f))
        root.addView(Button(this).apply {
            text = "Включить службу"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        val bars = mutableListOf<Pair<Prefs.Item, SeekBar>>()
        for (it in Prefs.items) {
            val label = tv("", 14f)
            val bar = SeekBar(this)
            bar.max = it.max - it.min
            bar.progress = Prefs.get(this, it.key) - it.min
            fun upd(v: Int) { label.text = "${it.label}: $v dp" }
            upd(Prefs.get(this, it.key))
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, user: Boolean) {
                    upd(p + it.min)
                    if (user) Prefs.sp(this@MainActivity).edit().putInt(it.key, p + it.min).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            root.addView(label); root.addView(bar)
            bars += it to bar
        }
        root.addView(Button(this).apply {
            text = "Сбросить на Pixel 6"
            setOnClickListener {
                Prefs.reset(this@MainActivity)
                bars.forEach { (it, b) -> b.progress = it.def - it.min }
            }
        })
        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams(-1, -2))
        })
    }
}
