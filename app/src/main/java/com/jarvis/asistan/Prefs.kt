package com.jarvis.asistan

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Tüm ayarlar ve kalıcı veriler (hafıza, deneme sonuçları, hatırlatıcılar). */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    private fun str(key: String, def: String) = sp.getString(key, def) ?: def
    private fun put(key: String, value: Any) = sp.edit().apply {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Float -> putFloat(key, value)
            is Long -> putLong(key, value)
        }
    }.apply()

    // --- Claude ---
    var apiKey: String get() = str("apiKey", ""); set(v) = put("apiKey", v.trim())
    var model: String get() = str("model", "claude-opus-5-5"); set(v) = put("model", v)
    var effort: String get() = str("effort", "low"); set(v) = put("effort", v)

    // --- Kişisel ---
    var userName: String get() = str("userName", ""); set(v) = put("userName", v.trim())
    var hitap: String get() = str("hitap", "efendim"); set(v) = put("hitap", v.trim().ifEmpty { "efendim" })
    var city: String get() = str("city", ""); set(v) = put("city", v.trim())
    var yksDate: String get() = str("yksDate", "2027-06-19"); set(v) = put("yksDate", v.trim())
    var alan: String get() = str("alan", "SAY"); set(v) = put("alan", v)

    // --- Deneme ---
    /** 1 = Pazartesi ... 7 = Pazar (java.time.DayOfWeek). */
    var denemeDay: Int get() = sp.getInt("denemeDay", 6); set(v) = put("denemeDay", v)
    var denemeEnd: String get() = str("denemeEnd", "13:00"); set(v) = put("denemeEnd", v)
    var denemeAskEnabled: Boolean get() = sp.getBoolean("denemeAsk", true); set(v) = put("denemeAsk", v)
    var lastDenemeAsk: String get() = str("lastDenemeAsk", ""); set(v) = put("lastDenemeAsk", v)

    // --- Sabah ---
    var morningEnabled: Boolean get() = sp.getBoolean("morning", true); set(v) = put("morning", v)
    var morningOnUnlock: Boolean get() = sp.getBoolean("morningUnlock", true); set(v) = put("morningUnlock", v)
    var lastGreet: String get() = str("lastGreet", ""); set(v) = put("lastGreet", v)

    // --- Akşam motivasyonu ---
    var eveningEnabled: Boolean get() = sp.getBoolean("evening", true); set(v) = put("evening", v)
    var eveningTime: String get() = str("eveningTime", "21:00"); set(v) = put("eveningTime", v)

    // --- Ses ---
    var wakeEnabled: Boolean get() = sp.getBoolean("wake", true); set(v) = put("wake", v)
    var wakeThreshold: Float get() = sp.getFloat("wakeThreshold", 0.5f); set(v) = put("wakeThreshold", v)
    var ttsVoice: String get() = str("ttsVoice", ""); set(v) = put("ttsVoice", v)
    var ttsRate: Float get() = sp.getFloat("ttsRate", 1.0f); set(v) = put("ttsRate", v)
    var ttsPitch: Float get() = sp.getFloat("ttsPitch", 0.9f); set(v) = put("ttsPitch", v)

    // --- Hafıza ---
    var memories: List<String>
        get() = jsonList("memories")
        set(v) = put("memories", JSONArray(v).toString())

    fun addMemory(fact: String) { memories = (memories + fact).takeLast(60) }

    // --- Deneme sonuçları ---
    var denemeler: JSONArray
        get() = runCatching { JSONArray(str("denemeler", "[]")) }.getOrDefault(JSONArray())
        set(v) = put("denemeler", v.toString())

    fun addDeneme(obj: JSONObject) {
        val arr = denemeler
        arr.put(obj)
        denemeler = arr
    }

    // --- Hatırlatıcılar (id -> {zaman, mesaj}) ---
    var reminders: JSONObject
        get() = runCatching { JSONObject(str("reminders", "{}")) }.getOrDefault(JSONObject())
        set(v) = put("reminders", v.toString())

    var nextReminderId: Int
        get() = sp.getInt("nextReminderId", 1000)
        set(v) = put("nextReminderId", v)

    private fun jsonList(key: String): List<String> = runCatching {
        val arr = JSONArray(str(key, "[]"))
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())

    fun yksDaysLeft(): Long? = runCatching {
        java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(yksDate))
    }.getOrNull()
}
