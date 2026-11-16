package app.karala.data

import android.content.Context
import android.content.SharedPreferences
import app.karala.ink.EraserMode
import app.karala.ink.ToolType
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Ayarlar ve araç durumu. SharedPreferences üzerine ince bir katman. */
class Prefs(ctx: Context) {
    val sp: SharedPreferences = ctx.getSharedPreferences("karala", Context.MODE_PRIVATE)

    private fun int(key: String, def: Int) = object : ReadWriteProperty<Prefs, Int> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>) = sp.getInt(key, def)
        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: Int) = sp.edit().putInt(key, value).apply()
    }
    private fun float(key: String, def: Float) = object : ReadWriteProperty<Prefs, Float> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>) = sp.getFloat(key, def)
        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: Float) = sp.edit().putFloat(key, value).apply()
    }
    private fun bool(key: String, def: Boolean) = object : ReadWriteProperty<Prefs, Boolean> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>) = sp.getBoolean(key, def)
        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: Boolean) = sp.edit().putBoolean(key, value).apply()
    }
    private fun long(key: String, def: Long) = object : ReadWriteProperty<Prefs, Long> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>) = sp.getLong(key, def)
        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: Long) = sp.edit().putLong(key, value).apply()
    }

    // ---- Çizim araçları (kalınlıklar dp cinsinden) ----
    var penColor by int("penColor", 0xFFFFD54A.toInt())
    var penWidth by float("penWidth", 3.5f)
    var highlighterColor by int("hlColor", 0xFFFFF176.toInt())
    var highlighterWidth by float("hlWidth", 18f)
    var shapeColor by int("shapeColor", 0xFF4FC3F7.toInt())
    var shapeWidth by float("shapeWidth", 3.5f)
    var laserColor by int("laserColor", 0xFFFF5252.toInt())
    var eraserSize by float("eraserSize", 16f)
    var eraserPartial by bool("eraserPartial", false)
    /** Çizim girişi: false = el (parmak ve kalem çizer), true = kalem (yalnızca kalem ucu çizer). */
    var stylusOnly by bool("stylusOnly", false)
    /** Ekran kalemi her başladığında "El mi, Kalem mi?" diye sorulsun mu? */
    var askInputMode by bool("askInputMode", true)
    var shapeAssist by bool("shapeAssist", true)
    var pressure by bool("pressure", true)
    var boardOpacity by int("boardOpacity", 100)

    var lastTool: ToolType
        get() = runCatching { ToolType.valueOf(sp.getString("lastTool", "PEN")!!) }.getOrDefault(ToolType.PEN)
        set(v) = sp.edit().putString("lastTool", v.name).apply()
    var lastShape: ToolType
        get() = runCatching { ToolType.valueOf(sp.getString("lastShape", "LINE")!!) }.getOrDefault(ToolType.LINE)
        set(v) = sp.edit().putString("lastShape", v.name).apply()
    val eraserMode get() = if (eraserPartial) EraserMode.PARTIAL else EraserMode.STROKE

    fun colorFor(t: ToolType) = when (t) {
        ToolType.HIGHLIGHTER -> highlighterColor; ToolType.LASER -> laserColor
        ToolType.PEN, ToolType.ERASER -> penColor; else -> shapeColor
    }
    fun setColorFor(t: ToolType, c: Int) {
        when (t) {
            ToolType.HIGHLIGHTER -> highlighterColor = c
            ToolType.LASER -> laserColor = c
            ToolType.PEN, ToolType.ERASER -> penColor = c
            else -> shapeColor = c
        }
    }
    fun widthFor(t: ToolType) = when (t) {
        ToolType.HIGHLIGHTER -> highlighterWidth; ToolType.ERASER -> eraserSize
        ToolType.PEN -> penWidth; ToolType.LASER -> 4f; else -> shapeWidth
    }
    fun setWidthFor(t: ToolType, w: Float) {
        when (t) {
            ToolType.HIGHLIGHTER -> highlighterWidth = w
            ToolType.ERASER -> eraserSize = w
            ToolType.PEN -> penWidth = w
            ToolType.LASER -> {}
            else -> shapeWidth = w
        }
    }

    // ---- Katman ----
    var bubbleX by int("bubbleX", -1)
    var bubbleY by int("bubbleY", -1)
    var captureWithScreenshot by bool("captureShot", true)
    var lastSubjectId by long("lastSubject", -1L)

    // ---- Odak ----
    var focusMinutes by int("focusMin", 25)
    var breakMinutes by int("breakMin", 5)
    var longBreakMinutes by int("longBreakMin", 15)
    var roundsBeforeLongBreak by int("rounds", 4)

    // ---- Hedefler ve genel ----
    var dailyQuestionGoal by int("goalQuestions", 100)
    var dailyMinuteGoal by int("goalMinutes", 120)
    var examDivisor by int("examDivisor", 4)
    /** 0 sistem, 1 açık, 2 koyu */
    var theme by int("theme", 0)
    var calcDegrees by bool("calcDeg", true)
    var onboarded by bool("onboarded", false)
}
