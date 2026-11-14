package app.karala.ink

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Sayfaların kalıcı biçimi: kompakt JSON. Koordinatlar 0.1 px hassasiyetle saklanır. */
object InkJson {
    private const val VERSION = 1

    fun encode(p: InkPage): String = toJson(p).toString()

    fun toJson(p: InkPage): JSONObject {
        val arr = JSONArray()
        for (s in p.strokes) {
            if (!s.tool.persists) continue
            val o = JSONObject()
            o.put("t", s.tool.name); o.put("c", s.color); o.put("w", r1(s.width))
            val pts = JSONArray(); for (v in s.points) pts.put(r1(v)); o.put("p", pts)
            s.pressures?.let { pr -> val a = JSONArray(); for (v in pr) a.put((v * 100).roundToInt()); o.put("pr", a) }
            arr.put(o)
        }
        return JSONObject().put("v", VERSION).put("w", p.width).put("h", p.height)
            .put("bg", p.background.name).put("s", arr)
    }

    fun decode(text: String): InkPage = fromJson(JSONObject(text))

    fun fromJson(o: JSONObject): InkPage {
        val page = InkPage(o.optInt("w"), o.optInt("h"),
            background = runCatching { PageBackground.valueOf(o.optString("bg", "NONE")) }.getOrDefault(PageBackground.NONE))
        val arr = o.optJSONArray("s") ?: return page
        for (i in 0 until arr.length()) {
            val so = arr.getJSONObject(i)
            val tool = runCatching { ToolType.valueOf(so.getString("t")) }.getOrNull() ?: continue
            val pa = so.getJSONArray("p")
            if (pa.length() < 2 || pa.length() % 2 != 0) continue
            val pts = FloatArray(pa.length()) { pa.getDouble(it).toFloat() }
            val pr = so.optJSONArray("pr")?.takeIf { it.length() == pts.size / 2 }
                ?.let { a -> FloatArray(a.length()) { a.getInt(it) / 100f } }
            page.strokes.add(Stroke(tool, so.getInt("c"), so.getDouble("w").toFloat(), pts, pr))
        }
        return page
    }

    fun encodePages(pages: List<InkPage>, current: Int): String {
        val arr = JSONArray(); pages.forEach { arr.put(toJson(it)) }
        return JSONObject().put("v", VERSION).put("cur", current).put("pages", arr).toString()
    }

    /** @return sayfalar ve geçerli sayfa indeksi */
    fun decodePages(text: String): Pair<MutableList<InkPage>, Int> {
        val o = JSONObject(text)
        val arr = o.optJSONArray("pages") ?: JSONArray()
        val list = MutableList(arr.length()) { fromJson(arr.getJSONObject(it)) }
        if (list.isEmpty()) list.add(InkPage())
        return list to o.optInt("cur").coerceIn(0, list.size - 1)
    }

    private fun r1(v: Float): Double = (v * 10f).roundToInt() / 10.0
}
