package com.naymy.island

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private val reqPhoto = 1
    private val reqVideo = 2
    private var d = 1f

    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(40), px(20), px(24))
        }
        fun tv(t: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, px(10), 0, 0)
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

        root.addView(tv("DroidIsland", 26f, true))
        root.addView(tv("Dynamic Island для Pixel 6. Тап по плашке — раскрыть, долгое нажатие — фонарик.", 14f))
        root.addView(btn("1. Включить службу") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(btn("2. Доступ к уведомлениям (музыка, навигация)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        // ----- таймер -----
        root.addView(tv("Таймер", 18f, true))
        val trow = LinearLayout(this)
        for ((label, sec) in listOf("1 мин" to 60, "5 мин" to 300, "10 мин" to 600, "Стоп" to 0)) {
            trow.addView(btn(label) {
                sendBroadcast(Intent("com.naymy.island.TIMER").setPackage(packageName).putExtra("sec", sec))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(trow)

        // ----- фон -----
        root.addView(tv("Фон острова", 18f, true))
        root.addView(tv("Фото или короткое видео (лучше до 10–15 сек, без звука). Видео играет только пока остров раскрыт.", 13f))
        val brow = LinearLayout(this)
        brow.addView(btn("Фото") { pick("image/*", reqPhoto) }, LinearLayout.LayoutParams(0, -2, 1f))
        brow.addView(btn("Видео") { pick("video/*", reqVideo) }, LinearLayout.LayoutParams(0, -2, 1f))
        brow.addView(btn("Убрать") {
            Prefs.sp(this).edit().putString("bgmode", "none").putLong("bgver", System.currentTimeMillis()).apply()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(brow)

        // ----- цвета -----
        root.addView(tv("Цвета", 18f, true))
        val presets = listOf(
            0xFF000000, 0xFF1C1C1E, 0xFF3A3A3C, 0xFFFFFFFF, 0xFFFF453A, 0xFFFF9F0A,
            0xFFFFD60A, 0xFF30D158, 0xFF63E6E2, 0xFF4DA3FF, 0xFFBF5AF2, 0xFFFF6482
        ).map { it.toInt() }
        for (ci in Prefs.colors) {
            root.addView(tv(ci.label, 14f))
            val hs = HorizontalScrollView(this)
            val row = LinearLayout(this)
            for (c in presets) {
                val dot = android.view.View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(c); setStroke(px(1), 0x66888888)
                    }
                    setOnClickListener { Prefs.sp(this@MainActivity).edit().putInt(ci.key, c).apply() }
                }
                row.addView(dot, LinearLayout.LayoutParams(px(36), px(36)).apply { setMargins(0, px(6), px(8), px(6)) })
            }
            hs.addView(row)
            root.addView(hs)
            val hexRow = LinearLayout(this)
            val et = EditText(this).apply {
                hint = "свой HEX, например FF8800"; textSize = 13f; setSingleLine()
            }
            hexRow.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
            hexRow.addView(btn("OK") {
                try {
                    val t = et.text.toString().trim().removePrefix("#")
                    val c = Color.parseColor("#" + t)
                    Prefs.sp(this).edit().putInt(ci.key, if (t.length <= 6) c or 0xFF000000.toInt() else c).apply()
                } catch (_: Exception) {
                    Toast.makeText(this, "Неверный HEX", Toast.LENGTH_SHORT).show()
                }
            })
            root.addView(hexRow)
        }

        // ----- размеры и анимация -----
        root.addView(tv("Размеры и анимация", 18f, true))
        val bars = mutableListOf<Pair<Prefs.Item, SeekBar>>()
        for (it in Prefs.items) {
            val label = tv("", 14f)
            val bar = SeekBar(this)
            bar.max = it.max - it.min
            bar.progress = Prefs.get(this, it.key) - it.min
            fun upd(v: Int) { label.text = "${it.label}: $v ${it.unit}".trim() }
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
        root.addView(btn("Сбросить всё") {
            Prefs.reset(this)
            recreate()
        })
        setContentView(ScrollView(this).apply { addView(root, ViewGroup.LayoutParams(-1, -2)) })
    }

    private fun pick(type: String, req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); this.type = type
        }
        startActivityForResult(i, req)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (res != RESULT_OK) return
        if (req == reqPhoto) importMedia(uri, "bg_photo", "photo")
        if (req == reqVideo) importMedia(uri, "bg_video", "video")
    }

    private fun importMedia(uri: Uri, name: String, mode: String) {
        Toast.makeText(this, "Копирую…", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = try {
                contentResolver.openInputStream(uri)!!.use { i ->
                    File(filesDir, name).outputStream().use { o -> i.copyTo(o) }
                }
                true
            } catch (_: Exception) { false }
            runOnUiThread {
                if (ok) {
                    Prefs.sp(this).edit().putString("bgmode", mode)
                        .putLong("bgver", System.currentTimeMillis()).apply()
                    Toast.makeText(this, "Готово", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "Не удалось скопировать файл", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
