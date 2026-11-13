package com.jarvis.asistan.device

import com.jarvis.asistan.JarvisApp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Haftalık ders/çalışma programı ve uygulanma takibi. */
object Program {
    val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

    data class Item(val gun: Int, val bas: LocalTime, val bit: LocalTime, val ad: String, val detay: String) {
        fun label() = "%s %s-%s %s%s".format(dayNames[gun - 1], bas, bit, ad, if (detay.isNotBlank()) " ($detay)" else "")
        fun toJson() = JSONObject().put("gun", gun).put("bas", bas.toString()).put("bit", bit.toString()).put("ad", ad).put("detay", detay)
    }

    fun items(): List<Item> {
        val arr = JarvisApp.prefs.program
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                Item(o.getInt("gun"), LocalTime.parse(o.getString("bas")), LocalTime.parse(o.getString("bit")),
                    o.getString("ad"), o.optString("detay"))
            }.getOrNull()
        }.sortedWith(compareBy({ it.gun }, { it.bas }))
    }

    private fun store(list: List<Item>) {
        JarvisApp.prefs.program = JSONArray(list.sortedWith(compareBy({ it.gun }, { it.bas })).map { it.toJson() })
    }

    private fun parseTime(s: String): LocalTime? = runCatching {
        val t = s.trim().replace('.', ':')
        LocalTime.parse(if (t.length == 4 && t[1] == ':') "0$t" else t)
    }.getOrNull()

    /**
     * @param mode "degistir" (tüm programı değiştir), "ekle", "gunleri_degistir" (yalnızca verilen günleri yenile)
     */
    fun save(input: JSONArray, mode: String): String {
        val parsed = ArrayList<Item>()
        val errors = ArrayList<String>()
        for (i in 0 until input.length()) {
            val o = input.optJSONObject(i) ?: continue
            val bas = parseTime(o.optString("baslangic"))
            val bit = parseTime(o.optString("bitis"))
            val ad = o.optString("ad").trim()
            val days = o.optJSONArray("gunler")?.let { a -> (0 until a.length()).map { a.optInt(it) } }
                ?: listOf(o.optInt("gun", 0))
            if (bas == null || bit == null || ad.isEmpty()) { errors.add("${i + 1}. madde eksik/hatalı"); continue }
            for (d in days.filter { it in 1..7 }) parsed.add(Item(d, bas, bit, ad, o.optString("detay")))
        }
        val current = items()
        val result = when (mode) {
            "ekle" -> current + parsed
            "gunleri_degistir" -> current.filter { c -> parsed.none { it.gun == c.gun } } + parsed
            else -> parsed
        }
        store(result)
        return "Program kaydedildi: ${parsed.size} madde (toplam ${result.size})." +
            (if (errors.isNotEmpty()) " Atlananlar: ${errors.joinToString()}." else "")
    }

    fun remove(gun: Int, bas: String?): String {
        val t = bas?.let { parseTime(it) }
        val before = items()
        val after = before.filterNot { it.gun == gun && (t == null || it.bas == t) }
        store(after)
        return "${before.size - after.size} madde silindi."
    }

    fun clear(): String { store(emptyList()); return "Program tamamen silindi." }

    fun show(day: Int?): String {
        val all = items()
        if (all.isEmpty()) return "Kayıtlı program yok."
        val list = if (day != null) all.filter { it.gun == day } else all
        if (list.isEmpty()) return "${dayNames[(day ?: 1) - 1]} için program yok."
        return list.joinToString("\n") { it.label() }
    }

    fun today(): List<Item> = items().filter { it.gun == LocalDate.now().dayOfWeek.value }

    fun todaySummary(): String {
        val t = today()
        return if (t.isEmpty()) "Bugün için kayıtlı program yok." else "Bugünkü program: " + t.joinToString("; ") { "${it.bas}-${it.bit} ${it.ad}" }
    }

    fun logStatus(ad: String, durum: String, not: String, tarih: String?): String {
        val arr = JarvisApp.prefs.programLog
        arr.put(JSONObject().put("tarih", tarih?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString())
            .put("ad", ad).put("durum", durum).put("not", not))
        // Son 400 kaydı tut.
        val trimmed = if (arr.length() > 400) JSONArray((arr.length() - 400 until arr.length()).map { arr.get(it) }) else arr
        JarvisApp.prefs.programLog = trimmed
        return "Kaydedildi: $ad → $durum."
    }

    fun report(days: Int): String {
        val arr = JarvisApp.prefs.programLog
        val since = LocalDate.now().minusDays(days.toLong() - 1)
        val list = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { runCatching { !LocalDate.parse(it.getString("tarih")).isBefore(since) }.getOrDefault(false) }
        if (list.isEmpty()) return "Son $days günde takip kaydı yok."
        fun score(d: String) = when (d) { "yapildi" -> 1.0; "kismen" -> 0.5; else -> 0.0 }
        val overall = list.sumOf { score(it.optString("durum")) } / list.size * 100
        val bySubject = list.groupBy { it.optString("ad") }.map { (ad, l) ->
            "%s: %%%.0f (%d kayıt)".format(ad, l.sumOf { score(it.optString("durum")) } / l.size * 100, l.size)
        }
        val recent = list.takeLast(12).joinToString("\n") { "${it.optString("tarih")} ${it.optString("ad")}: ${it.optString("durum")} ${it.optString("not")}".trim() }
        return "Son $days gün uygulama oranı: %%%.0f\n".format(overall) + bySubject.joinToString("\n") + "\nSon kayıtlar:\n$recent"
    }

    fun todayLog(): String {
        val arr = JarvisApp.prefs.programLog
        val today = LocalDate.now().toString()
        val list = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optString("tarih") == today }
        return if (list.isEmpty()) "Bugün için henüz takip kaydı yok." else
            "Bugün yapılanlar: " + list.joinToString("; ") { "${it.optString("ad")} ${it.optString("durum")}" }
    }

    /** Şu andan sonraki ilk program olayı: (zaman, tür "start"/"end", madde). */
    fun nextEvent(remindStart: Boolean, askEnd: Boolean): Triple<LocalDateTime, String, Item>? {
        val now = LocalDateTime.now().plusSeconds(20)
        val all = items()
        if (all.isEmpty()) return null
        var best: Triple<LocalDateTime, String, Item>? = null
        for (offset in 0..7) {
            val date = LocalDate.now().plusDays(offset.toLong())
            for (it in all.filter { i -> i.gun == date.dayOfWeek.value }) {
                if (remindStart) {
                    val t = LocalDateTime.of(date, it.bas)
                    if (t.isAfter(now) && (best == null || t.isBefore(best.first))) best = Triple(t, "start", it)
                }
                if (askEnd) {
                    val t = LocalDateTime.of(date, it.bit)
                    if (t.isAfter(now) && (best == null || t.isBefore(best.first))) best = Triple(t, "end", it)
                }
            }
            if (best != null) return best
        }
        return best
    }
}
