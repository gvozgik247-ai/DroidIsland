package com.naymy.island

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

// Размеры в dp. Дефолты подобраны под Pixel 6 (1080x2400, вырез по центру сверху).
object Prefs {
    data class Item(val key: String, val label: String, val min: Int, val max: Int, val def: Int, val unit: String = "dp")
    data class ColorItem(val key: String, val label: String, val def: Int)

    val items = listOf(
        Item("w", "Ширина (свёрнуто)", 60, 220, 112),
        Item("h", "Высота (свёрнуто)", 20, 60, 32),
        Item("r", "Скругление", 8, 40, 16),
        Item("top", "Отступ сверху", -10, 40, 6),
        Item("hx", "Сдвиг по горизонтали", -60, 60, 0),
        Item("ew", "Ширина (раскрыто)", 200, 400, 340),
        Item("eh", "Высота (раскрыто, только часы)", 80, 300, 128),
        Item("er", "Скругление (раскрыто)", 16, 60, 36),
        Item("dim", "Затемнение фото/видео", 0, 80, 35, "%"),
        Item("stiff", "Скорость анимации", 100, 1000, 350, ""),
        Item("damp", "Упругость (меньше = больше инерции)", 30, 100, 62, "%"),
        Item("collapse", "Автосворачивание", 2, 15, 6, "с"),
    )

    val colors = listOf(
        ColorItem("cBg", "Цвет острова", 0xFF000000.toInt()),
        ColorItem("cCard", "Цвет карточек", 0xFF1C1C1E.toInt()),
        ColorItem("cText", "Цвет текста и иконок", Color.WHITE),
        ColorItem("cAcc", "Акцентный цвет", 0xFF4DA3FF.toInt()),
    )

    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("island", Context.MODE_PRIVATE)
    fun get(c: Context, key: String): Int = sp(c).getInt(key, items.first { it.key == key }.def)
    fun color(c: Context, key: String): Int = sp(c).getInt(key, colors.first { it.key == key }.def)
    fun bool(c: Context, key: String, def: Boolean): Boolean = sp(c).getBoolean(key, def)
    fun str(c: Context, key: String, def: String): String = sp(c).getString(key, def) ?: def
    fun ver(c: Context): Long = sp(c).getLong("bgver", 0L)
    fun reset(c: Context) = sp(c).edit().clear().apply()
}
