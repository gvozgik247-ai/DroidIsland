package com.naymy.island

import android.content.Context
import android.content.SharedPreferences

// Значения в dp. Дефолты подобраны под Pixel 6 (1080x2400, вырез по центру сверху).
object Prefs {
    data class Item(val key: String, val label: String, val min: Int, val max: Int, val def: Int)

    val items = listOf(
        Item("w", "Ширина (свёрнуто)", 60, 220, 112),
        Item("h", "Высота (свёрнуто)", 20, 60, 32),
        Item("r", "Скругление", 8, 40, 16),
        Item("top", "Отступ сверху", -10, 40, 6),
        Item("hx", "Сдвиг по горизонтали", -60, 60, 0),
        Item("ew", "Ширина (раскрыто)", 200, 400, 340),
        Item("eh", "Высота (раскрыто)", 80, 300, 128),
        Item("er", "Скругление (раскрыто)", 16, 60, 36),
    )

    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("island", Context.MODE_PRIVATE)
    fun get(c: Context, key: String): Int =
        sp(c).getInt(key, items.first { it.key == key }.def)
    fun reset(c: Context) = sp(c).edit().clear().apply()
}
