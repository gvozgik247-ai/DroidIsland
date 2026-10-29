package com.naymy.island

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private val cBg = 0xFF0E0E10.toInt()
    private val cCard = 0xFF1A1A1F.toInt()
    private val cBtn = 0xFF2A2A31.toInt()
    private val cSub = 0xFF9A9AA5.toInt()
    private val cAcc = 0xFF4DA3FF.toInt()
    private val reqPhoto = 1
    private val reqVideo = 2
    private var d = 1f
    private var page = 0
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private val navs = ArrayList<TextView>()

    private var preview: IslandView? = null
    private var previewBox: FrameLayout? = null
    private var prevBmp: Bitmap? = null
    private var prevVer = -1L
    private var prefL: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        d = resources.displayMetrics.density
        window.statusBarColor = cBg
        window.navigationBarColor = cBg

        val crash = File(filesDir, "crash.txt")
        if (crash.exists()) { showCrash(crash); return }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(cBg) }
        root.addView(TextView(this).apply {
            text = "DroidIsland"; textSize = 24f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; setPadding(px(20), px(18), px(20), px(6))
        })
        scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(16), px(8), px(16), px(16)) }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply { setBackgroundColor(cCard) }
        for ((i, name) in listOf("Главная", "Вид", "Размер", "Ещё").withIndex()) {
            val t = TextView(this).apply {
                text = name; textSize = 14f; gravity = Gravity.CENTER
                setPadding(0, px(16), 0, px(16)); typeface = Typeface.DEFAULT_BOLD
                setOnClickListener { showPage(i) }
            }
            navs.add(t)
            nav.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        showPage(0)
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized && page == 0) showPage(0)
    }

    override fun onPause() {
        super.onPause()
        prefL?.let { Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(it) }
        prefL = null
    }

    // ---------- экран ошибки ----------
    private fun showCrash(f: File) {
        val trace = f.readText()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(cBg); setPadding(px(16), px(32), px(16), px(16)) }
        box.addView(TextView(this).apply {
            text = "Приложение упало в прошлый раз.\nНажмите «Скопировать» и пришлите текст Claude. Потом нажмите «Очистить» и заново включите службу в специальных возможностях."
            setTextColor(Color.WHITE); textSize = 15f
        })
        val row = LinearLayout(this)
        row.addView(button("Скопировать", true) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("crash", trace))
            Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, px(12), px(6), px(12)) })
        row.addView(button("Очистить", false) { f.delete(); recreate() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(px(6), px(12), 0, px(12)) })
        box.addView(row)
        val sv = ScrollView(this)
        sv.addView(TextView(this).apply { text = trace; setTextColor(cSub); textSize = 11f; setTextIsSelectable(true) })
        box.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(box)
    }

    // ---------- элементы интерфейса ----------
    private fun rounded(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = r * d }

    private fun button(t: String, filled: Boolean, f: () -> Unit) = TextView(this).apply {
        text = t; textSize = 14f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (filled) Color.BLACK else Color.WHITE)
        background = rounded(if (filled) cAcc else cBtn, 14)
        setPadding(px(10), px(12), px(10), px(12))
        setOnClickListener { f() }
    }

    private fun card(title: String?): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(cCard, 20); setPadding(px(16), px(14), px(16), px(16))
        }
        if (title != null) c.addView(label(title, 16f, Color.WHITE, true))
        content.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, px(6), 0, px(6)) })
        return c
    }

    private fun label(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, px(4), 0, px(4))
    }

    private fun hrow(vararg v: View): LinearLayout {
        val r = LinearLayout(this)
        for ((i, x) in v.withIndex()) {
            r.addView(x, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(if (i == 0) 0 else px(4), px(6), if (i == v.size - 1) 0 else px(4), px(2)) })
        }
        return r
    }

    private fun slider(p: LinearLayout, key: String) {
        val it0 = Prefs.items.first { it.key == key }
        val l = label("", 14f, Color.WHITE)
        val b = SeekBar(this)
        b.max = it0.max - it0.min
        b.progress = Prefs.get(this, key) - it0.min
        b.progressTintList = ColorStateList.valueOf(cAcc)
        b.thumbTintList = ColorStateList.valueOf(cAcc)
        fun upd(v: Int) { l.text = "${it0.label}: $v ${it0.unit}".trim() }
        upd(Prefs.get(this, key))
        b.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, pr: Int, user: Boolean) {
                upd(pr + it0.min)
                if (user) Prefs.sp(this@MainActivity).edit().putInt(key, pr + it0.min).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        p.addView(l); p.addView(b)
    }

    private fun switchRow(p: LinearLayout, text: String, key: String, def: Boolean) {
        p.addView(Switch(this).apply {
            this.text = text; setTextColor(Color.WHITE); textSize = 14f
            setPadding(0, px(8), 0, px(8))
            isChecked = Prefs.bool(this@MainActivity, key, def)
            setOnCheckedChangeListener { _, c -> Prefs.sp(this@MainActivity).edit().putBoolean(key, c).apply() }
        })
    }

    private fun colorPicker(p: LinearLayout, ci: Prefs.ColorItem) {
        p.addView(label(ci.label, 14f, Color.WHITE))
        val presets = listOf(
            0xFF000000, 0xFF1C1C1E, 0xFF3A3A3C, 0xFFFFFFFF, 0xFFFF453A, 0xFFFF9F0A,
            0xFFFFD60A, 0xFF30D158, 0xFF63E6E2, 0xFF4DA3FF, 0xFFBF5AF2, 0xFFFF6482
        ).map { it.toInt() }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this)
        for (c in presets) {
            val dot = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(px(1), 0x66888888) }
                setOnClickListener { Prefs.sp(this@MainActivity).edit().putInt(ci.key, c).apply() }
            }
            row.addView(dot, LinearLayout.LayoutParams(px(38), px(38)).apply { setMargins(0, px(4), px(10), px(8)) })
        }
        hs.addView(row)
        p.addView(hs)
        val et = EditText(this).apply {
            hint = "свой HEX, например FF8800"; textSize = 13f; setSingleLine()
            setTextColor(Color.WHITE); setHintTextColor(cSub)
        }
        val ok = button("OK", false) {
            try {
                val t = et.text.toString().trim().removePrefix("#")
                val c = Color.parseColor("#" + t)
                Prefs.sp(this).edit().putInt(ci.key, if (t.length <= 6) c or 0xFF000000.toInt() else c).apply()
                Toast.makeText(this, "Цвет применён", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Неверный HEX", Toast.LENGTH_SHORT).show()
            }
        }
        val hr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hr.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        hr.addView(ok, LinearLayout.LayoutParams(px(70), -2).apply { setMargins(px(8), 0, 0, 0) })
        p.addView(hr)
    }

    // ---------- страницы ----------
    private fun showPage(i: Int) {
        page = i
        prefL?.let { Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(it) }
        prefL = null
        preview = null; previewBox = null
        content.removeAllViews()
        for ((k, t) in navs.withIndex()) t.setTextColor(if (k == i) cAcc else cSub)
        when (i) {
            0 -> pageHome()
            1 -> pageLook()
            2 -> pageSize()
            else -> pageMore()
        }
        scroll.scrollTo(0, 0)
    }

    private fun accessibilityOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains("IslandService")
    }

    private fun notifOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return s.contains(packageName)
    }

    private fun statusRow(p: LinearLayout, name: String, on: Boolean, act: () -> Unit) {
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(6), 0, px(6)) }
        r.addView(label((if (on) "●  " else "○  ") + name, 15f, if (on) 0xFF30D158.toInt() else Color.WHITE),
            LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(button(if (on) "Настройки" else "Включить", !on) { act() }, LinearLayout.LayoutParams(-2, -2))
        p.addView(r)
    }

    private fun pageHome() {
        val st = card("Статус")
        statusRow(st, "Остров (служба)", accessibilityOn()) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        statusRow(st, "Доступ к уведомлениям", notifOn()) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        st.addView(label("Если переключатель серый: Настройки → Приложения → DroidIsland → ⋮ → «Разрешить ограниченные настройки».", 12f, cSub))

        val tm = card("Таймер")
        val b1 = button("1 мин", false) { sendTimer(60) }
        val b2 = button("5 мин", false) { sendTimer(300) }
        val b3 = button("10 мин", false) { sendTimer(600) }
        val b4 = button("Стоп", true) { sendTimer(0) }
        tm.addView(hrow(b1, b2, b3, b4))

        val hp = card("Управление островом")
        hp.addView(label("• Тап по плашке — раскрыть или свернуть\n• Долгое нажатие — фонарик\n• Тап по полосе трека — перемотка\n• Кнопки плеера работают с любым музыкальным приложением", 13f, cSub))
    }

    private fun sendTimer(sec: Int) {
        sendBroadcast(Intent("com.naymy.island.TIMER").setPackage(packageName).putExtra("sec", sec))
    }

    private fun pageLook() {
        // превью
        val pc = card("Превью")
        val iv = IslandView(this)
        val box = FrameLayout(this).apply {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, Prefs.get(this@MainActivity, "er") * d)
                }
            }
            addView(iv, FrameLayout.LayoutParams(-1, -1))
        }
        val wpx = minOf(px(340), resources.displayMetrics.widthPixels - px(64))
        pc.addView(box, LinearLayout.LayoutParams(wpx, px(Prefs.get(this, "eh"))).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, px(8), 0, px(4)) })
        preview = iv; previewBox = box
        refreshPreview()
        pc.addView(label("Так выглядит раскрытый остров. Видео в превью не показывается.", 12f, cSub))
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshPreview() }
        prefL = l
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(l)

        // фон
        val bg = card("Фон")
        val mode = Prefs.str(this, "bgmode", "none")
        bg.addView(label("Сейчас: " + when (mode) { "photo" -> "фото"; "video" -> "видео"; else -> "обычный цвет" }, 13f, cSub))
        bg.addView(hrow(
            button("Фото", false) { pick("image/*", reqPhoto) },
            button("Видео", false) { pick("video/*", reqVideo) },
            button("Убрать", false) {
                Prefs.sp(this).edit().putString("bgmode", "none").putLong("bgver", System.currentTimeMillis()).apply()
                showPage(1)
            }))
        bg.addView(label("Видео лучше короткое (10–15 сек). Оно играет без звука, пока остров раскрыт.", 12f, cSub))
        slider(bg, "dim")

        // цвета
        val cc = card("Цвета")
        for (ci in Prefs.colors) colorPicker(cc, ci)
    }

    private fun refreshPreview() {
        val v = preview ?: return
        v.headH = Prefs.get(this, "h") * d
        v.bgColor = Prefs.color(this, "cBg"); v.cardColor = Prefs.color(this, "cCard")
        v.textColor = Prefs.color(this, "cText"); v.accent = Prefs.color(this, "cAcc")
        v.dim = Prefs.get(this, "dim") / 100f
        val mode = Prefs.str(this, "bgmode", "none")
        if (mode == "photo") {
            if (prevBmp == null || prevVer != Prefs.ver(this)) {
                prevVer = Prefs.ver(this)
                prevBmp = try {
                    val f = File(filesDir, "bg_photo")
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(f.path, o)
                    var sm = 1
                    while (o.outWidth / sm > 1000) sm *= 2
                    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sm })
                } catch (_: Exception) { null }
            }
            v.bgBitmap = prevBmp; v.mediaA = 1f
        } else { v.bgBitmap = null; v.mediaA = if (mode == "video") 0.6f else 0f }
        v.progress = 1f
        v.time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        v.date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        v.battery = "100%"
        v.invalidate()
        previewBox?.let {
            val lp = it.layoutParams
            val nh = px(Prefs.get(this, "eh"))
            if (lp != null && lp.height != nh) { lp.height = nh; it.layoutParams = lp }
            it.invalidateOutline()
        }
    }

    private fun pageSize() {
        val c = card("Размер и положение")
        c.addView(label("Двигайте, пока плашка не ляжет ровно на вырез камеры. Изменения видны сразу.", 12f, cSub))
        for (k in listOf("w", "h", "r", "top", "hx")) slider(c, k)
        val e = card("Раскрытый остров")
        for (k in listOf("ew", "eh", "er")) slider(e, k)
        val r = card(null)
        r.addView(button("Сбросить размеры под Pixel 6", false) {
            val ed = Prefs.sp(this).edit()
            for (k in listOf("w", "h", "r", "top", "hx", "ew", "eh", "er")) ed.remove(k)
            ed.apply(); showPage(2)
        })
    }

    private fun pageMore() {
        val a = card("Анимация")
        a.addView(label("Пружинная физика: остров слегка «перелетает» размер и оседает. Меньше упругость — больше инерции.", 12f, cSub))
        slider(a, "stiff"); slider(a, "damp"); slider(a, "collapse")
        val b = card("Поведение")
        switchRow(b, "Прятать в полноэкранных приложениях и в альбомной ориентации", "hideFs", true)
        switchRow(b, "Вибро-отклик на кнопках", "haptic", true)
        switchRow(b, "Фонарик по долгому нажатию", "longTorch", true)
        val r = card(null)
        r.addView(button("Сбросить все настройки", false) { Prefs.reset(this); prevBmp = null; showPage(3) })
        val ab = card("О приложении")
        ab.addView(label("DroidIsland v5\nDynamic Island для Android. Сделано для Pixel 6.", 13f, cSub))
    }

    // ---------- импорт фона ----------
    private fun pick(type: String, req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type = type }
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
                    Prefs.sp(this).edit().putString("bgmode", mode).putLong("bgver", System.currentTimeMillis()).apply()
                    Toast.makeText(this, "Готово", Toast.LENGTH_SHORT).show()
                    if (page == 1) showPage(1)
                } else Toast.makeText(this, "Не удалось скопировать файл", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
