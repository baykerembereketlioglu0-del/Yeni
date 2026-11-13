package com.jarvis.asistan.brain

import android.util.Log
import com.jarvis.asistan.JarvisApp
import com.jarvis.asistan.JarvisState
import com.jarvis.asistan.Mode
import com.jarvis.asistan.Prefs
import com.jarvis.asistan.device.DeviceController
import com.jarvis.asistan.device.JarvisAccessibilityService
import com.jarvis.asistan.device.ToolOutput
import com.jarvis.asistan.voice.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Google Gemini API (ücretsiz katman) ile çalışan beyin.
 * - Kotası biten modelden kotası olan bir sonrakine otomatik geçer.
 * - Modelin döndürdüğü parçaları (düşünce imzaları dahil) değiştirmeden geçmişe ekler.
 */
class GeminiBrain(private val device: DeviceController, private val speaker: Speaker) : Brain {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val history = ArrayList<JSONObject>()
    private var systemPrompt = ""
    private var lastActivity = 0L
    private var imagesInHistory = 0
    private var candidates: List<String>? = null
    private var candidatesKey = ""
    /** model -> kotanın yenileneceği an (ms) */
    private val exhausted = HashMap<String, Long>()
    private var lastModel: String? = null

    @Volatile override var endRequested = false
        private set

    override fun reset() {
        history.clear()
        imagesInHistory = 0
        systemPrompt = ""
    }

    class GeminiError(val code: Int, val status: String, override val message: String, val perDay: Boolean, val retrySec: Int?) : IOException(message)

    // ------------------------------------------------------------------ ana döngü

    override suspend fun respond(userText: String, image: String?): String {
        endRequested = false
        val p = JarvisApp.prefs
        if (p.geminiKey.isBlank()) return speakError(
            "Beynime bağlanmam için Gemini API anahtarına ihtiyacım var ${p.hitap}. Ayarlardan ücretsiz anahtarı girin."
        )
        val now = System.currentTimeMillis()
        if (now - lastActivity > 15 * 60_000 || history.size > 60 || imagesInHistory >= IMAGE_BUDGET) reset()
        lastActivity = now
        if (systemPrompt.isEmpty()) systemPrompt = Persona.systemPrompt(p, claude = false)

        val parts = JSONArray().put(JSONObject().put("text", Persona.contextLine(p, device.batteryPercent()) + "\n" + userText))
        if (image != null) {
            parts.put(inline(image))
            imagesInHistory++
        }
        history.add(JSONObject().put("role", "user").put("parts", parts))

        JarvisState.newReply()
        val spoken = StringBuilder()
        val sentence = ClaudeBrain.SentenceSplitter { s -> speaker.enqueue(s); spoken.append(s).append(' ') }
        val onText: (String) -> Unit = { t -> sentence.feed(t); JarvisState.appendToLast(t) }
        var flattenedOnce = false
        var retries = 0

        try {
            var steps = 0
            while (steps < MAX_STEPS) {
                coroutineContext.ensureActive()
                JarvisState.setMode(Mode.THINKING)
                val model = pickModel(p) ?: return speakError(
                    "Bugünkü ücretsiz Gemini kotası doldu ${p.hitap}. Kota Türkiye saatiyle sabah on bir civarında yenilenir."
                )
                if (lastModel != null && lastModel != model && hasFunctionParts()) flatten()
                lastModel = model

                val result = try {
                    stream(p, model, onText)
                } catch (e: GeminiError) {
                    if (++retries > 10) throw e
                    when {
                        e.code == 429 -> {
                            if (!e.perDay && (e.retrySec ?: 60) <= 20) {
                                Log.w(TAG, "$model dakikalık sınır, ${e.retrySec} sn bekleniyor")
                                delay(((e.retrySec ?: 5) + 1) * 1000L)
                            } else {
                                Log.w(TAG, "$model kotası doldu (günlük=${e.perDay}), sonraki modele geçiliyor")
                                exhausted[model] = if (e.perDay) nextPacificMidnight() else System.currentTimeMillis() + (e.retrySec ?: 60) * 1000L
                            }
                            continue
                        }
                        e.code == 404 -> {
                            exhausted[model] = Long.MAX_VALUE
                            continue
                        }
                        e.code == 400 && !flattenedOnce && history.size > 1 -> {
                            // Geçmiş biçimi (ör. düşünce imzası) reddedildiyse sade metne çevirip tekrar dene.
                            Log.w(TAG, "400, geçmiş sadeleştiriliyor: ${e.message}")
                            flatten()
                            flattenedOnce = true
                            continue
                        }
                        else -> throw e
                    }
                }
                steps++
                sentence.flush()
                result.contents.forEach { history.add(it) }

                if (result.blocked) {
                    speaker.enqueue("Bu konuda yardımcı olamıyorum ${p.hitap}.")
                    reset()
                    return spoken.toString()
                }
                if (result.calls.isEmpty()) return spoken.toString()

                val responseParts = JSONArray()
                val extraParts = ArrayList<JSONObject>()
                val v3 = version(model) >= 3.0
                for (call in result.calls) {
                    val out = runTool(call, sentence)
                    val fr = JSONObject().put("name", call.optString("name"))
                        .put("response", if (out.isError) JSONObject().put("hata", out.text) else JSONObject().put("sonuc", out.text))
                    call.optString("id").takeIf { it.isNotEmpty() }?.let { fr.put("id", it) }
                    if (out.imageBase64 != null) {
                        imagesInHistory++
                        if (v3) {
                            fr.put("parts", JSONArray().put(JSONObject().put("inlineData",
                                JSONObject().put("mimeType", "image/jpeg").put("data", out.imageBase64))))
                        } else {
                            extraParts.add(JSONObject().put("text", "(${call.optString("name")} aracının görüntüsü)"))
                            extraParts.add(inline(out.imageBase64))
                        }
                    }
                    responseParts.put(JSONObject().put("functionResponse", fr))
                }
                extraParts.forEach { responseParts.put(it) }
                history.add(JSONObject().put("role", "user").put("parts", responseParts))

                val names = result.calls.map { it.optString("name") }
                if (endRequested && names.all { it == "sohbeti_bitir" || it == "konus" }) {
                    reset()
                    return spoken.toString()
                }
                if (imagesInHistory >= IMAGE_BUDGET) handoff(p, userText)
            }
            speaker.enqueue("Bu görev beklenenden uzun sürdü ${p.hitap}, burada durdum.")
        } catch (e: CancellationException) {
            reset()
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Gemini hatası", e)
            reset()
            return speakError(errorMessage(e))
        }
        return spoken.toString()
    }

    // ------------------------------------------------------------------ istek

    private class StreamResult(val contents: List<JSONObject>, val calls: List<JSONObject>, val blocked: Boolean)

    private fun requestBody(p: Prefs, contents: List<JSONObject>, allowTools: Boolean = true): JSONObject {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", JSONArray(contents))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations())))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 8192))
        if (!allowTools) {
            body.put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "NONE")))
        }
        return body
    }

    private suspend fun stream(p: Prefs, model: String, onText: (String) -> Unit, contents: List<JSONObject> = history, allowTools: Boolean = true): StreamResult {
        val req = Request.Builder()
            .url("${BASE}models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", p.geminiKey)
            .post(requestBody(p, contents, allowTools).toString().toRequestBody("application/json".toMediaType()))
            .build()
        return execute(http.newCall(req)) { resp ->
            if (!resp.isSuccessful) throw parseError(resp)
            val out = ArrayList<JSONObject>()
            val calls = ArrayList<JSONObject>()
            var blocked = false
            val src = resp.body!!.source()
            while (true) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val json = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
                json.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let { blocked = true }
                val cand = json.optJSONArray("candidates")?.optJSONObject(0) ?: continue
                val reason = cand.optString("finishReason")
                if (reason == "SAFETY" || reason == "PROHIBITED_CONTENT" || reason == "BLOCKLIST") blocked = true
                val content = cand.optJSONObject("content") ?: continue
                val parts = content.optJSONArray("parts") ?: continue
                if (!content.has("role")) content.put("role", "model")
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.has("functionCall")) calls.add(part.getJSONObject("functionCall"))
                    else if (part.has("text") && !part.optBoolean("thought")) {
                        val t = part.optString("text")
                        if (t.isNotEmpty()) onText(t)
                    }
                }
                // Parçaları birleştirmeden, geldiği gibi sakla (düşünce imzaları korunur).
                out.add(content)
            }
            StreamResult(out, calls, blocked)
        }
    }

    /** OkHttp çağrısını iptal edilebilir şekilde çalıştırır. */
    private suspend fun <T> execute(call: Call, block: (Response) -> T): T = coroutineScope {
        val d = async(Dispatchers.IO) { call.execute().use(block) }
        try {
            d.await()
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        }
    }

    private fun parseError(resp: Response): GeminiError {
        val raw = runCatching { resp.body?.string() }.getOrNull().orEmpty()
        val err = runCatching { JSONObject(raw).getJSONObject("error") }.getOrNull()
        val msg = err?.optString("message") ?: raw.take(300)
        val status = err?.optString("status").orEmpty()
        val perDay = raw.contains("PerDay", ignoreCase = true)
        val retry = Regex("\"retryDelay\"\\s*:\\s*\"(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
        return GeminiError(resp.code, status, msg, perDay, retry)
    }

    // ------------------------------------------------------------------ model seçimi

    private fun version(model: String): Double =
        Regex("gemini-(\\d+(?:\\.\\d+)?)").find(model)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    private suspend fun pickModel(p: Prefs): String? {
        val list = candidates?.takeIf { candidatesKey == p.geminiKey + p.geminiModel } ?: loadCandidates(p)
        val now = System.currentTimeMillis()
        return list.firstOrNull { (exhausted[it] ?: 0L) < now }
    }

    private suspend fun loadCandidates(p: Prefs): List<String> {
        val req = Request.Builder().url("${BASE}models?pageSize=1000").header("x-goog-api-key", p.geminiKey).get().build()
        val names = try {
            execute(http.newCall(req)) { resp ->
                if (!resp.isSuccessful) throw parseError(resp)
                val arr = JSONObject(resp.body!!.string()).optJSONArray("models") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val m = arr.getJSONObject(i)
                    val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                    if (!methods.contains("generateContent")) null else m.optString("name").removePrefix("models/")
                }
            }
        } catch (e: GeminiError) {
            if (e.code == 400 || e.code == 401 || e.code == 403) throw e
            emptyList()
        } catch (e: IOException) {
            emptyList()
        }
        val excluded = listOf("embedding", "tts", "image", "imagen", "live", "audio", "computer", "robotics", "aqa",
            "vision", "learnlm", "veo", "lyria", "native", "research", "nano", "banana", "gemma", "exp", "thinking", "customtools")
        val usable = names.filter { n -> n.startsWith("gemini") && excluded.none { n.contains(it) } }
        // Hızlı/verimli modeller (flash ve "Gemini 4 Hydrogen" gibi yeni isimli modeller) önce, en yeni sürüm başta.
        fun tier(n: String) = when {
            n.contains("flash-lite") || n.contains("lite") -> 1
            n.contains("pro") || n.contains("ultra") -> 2
            else -> 0
        }
        val ranked = usable.filter { version(it) > 0 }
            .sortedWith(compareBy<String>({ tier(it) }, { -version(it) }, { if (it.contains("preview")) 1 else 0 }))
            .take(10) + usable.filter { version(it) == 0.0 && it.contains("flash") }
        val fallback = listOf("gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite")
        val base = (ranked.ifEmpty { fallback }).distinct()
        val chosen = p.geminiModel.takeIf { it != "auto" }
        val list = if (chosen != null) listOf(chosen) + (base - chosen) else base
        Log.i(TAG, "Gemini aday modelleri: $list")
        candidates = list
        candidatesKey = p.geminiKey + p.geminiModel
        return list
    }

    private fun nextPacificMidnight(): Long {
        val la = ZoneId.of("America/Los_Angeles")
        return ZonedDateTime.now(la).toLocalDate().plusDays(1).atStartOfDay(la).toInstant().toEpochMilli()
    }

    // ------------------------------------------------------------------ araçlar

    private fun declarations(): JSONArray {
        val arr = JSONArray()
        val all = Tools.specs.map { Triple(it.name, it.description, it.props to it.required) } + extraSpecs
        for ((name, desc, pr) in all) {
            val schema = JSONObject().put("type", "object").put("properties", JSONObject(pr.first))
            if (pr.second.isNotEmpty()) schema.put("required", JSONArray(pr.second))
            arr.put(JSONObject().put("name", name).put("description", desc).put("parametersJsonSchema", schema))
        }
        return arr
    }

    private val extraSpecs = listOf(
        Triple("internette_ara", "Google'da güncel bilgi arar (hava durumu, haberler, sonuçlar, ÖSYM duyuruları…) ve kısa bir özet döndürür.",
            mapOf("sorgu" to mapOf("type" to "string", "description" to "Arama sorusu")) to listOf("sorgu")),
        Triple("ekrana_dokun", "En son ekran görüntüsünde (ekrana_bak) gördüğün bir noktaya dokunur. Koordinatlar 0-1000 ölçeklidir: x soldan sağa, y yukarıdan aşağıya.",
            mapOf(
                "x" to mapOf("type" to "integer", "description" to "0-1000, soldan sağa"),
                "y" to mapOf("type" to "integer", "description" to "0-1000, yukarıdan aşağıya"),
                "uzun" to mapOf("type" to "boolean", "description" to "true ise uzun basar"),
            ) to listOf("x", "y")),
    )

    private suspend fun runTool(call: JSONObject, sentence: ClaudeBrain.SentenceSplitter): ToolOutput {
        coroutineContext.ensureActive()
        val name = call.optString("name")
        val args = call.optJSONObject("args") ?: JSONObject()
        Log.i(TAG, "Araç: $name ${args.toString().take(300)}")
        when (name) {
            "konus" -> {
                val text = args.optString("metin")
                sentence.feedSentence(text)
                JarvisState.appendToLast(if (text.endsWith(" ")) text else "$text ")
            }
            "sohbeti_bitir" -> endRequested = true
            "internette_ara" -> return search(args.optString("sorgu"))
            "ekrana_dokun" -> return tapNormalized(args)
        }
        if (Tools.waitsForSpeech(name)) speaker.awaitIdle(15_000)
        return try {
            Tools.execute(device, name, args)
        } catch (e: Exception) {
            Log.e(TAG, "Araç hatası", e)
            ToolOutput("Hata: ${e.message}", isError = true)
        }
    }

    private suspend fun tapNormalized(args: JSONObject): ToolOutput {
        val s = JarvisAccessibilityService.instance
            ?: return ToolOutput("Erişilebilirlik izni kapalı.", isError = true)
        val (w, h) = s.realSize()
        val x = args.optDouble("x", -1.0)
        val y = args.optDouble("y", -1.0)
        if (x !in 0.0..1000.0 || y !in 0.0..1000.0) return ToolOutput("x ve y 0-1000 arasında olmalı.", isError = true)
        val rx = (x / 1000 * w).toFloat()
        val ry = (y / 1000 * h).toFloat()
        val ok = if (args.optBoolean("uzun")) s.longPress(rx, ry) else s.tap(rx, ry)
        delay(700)
        return if (ok) ToolOutput("Dokunuldu. Ekrandakiler:\n" + s.screenText(60)) else ToolOutput("Dokunulamadı.", isError = true)
    }

    /** Google Arama ile ayrı bir istek (araç birleştirme her modelde desteklenmediği için). */
    private suspend fun search(query: String): ToolOutput {
        val p = JarvisApp.prefs
        repeat(4) {
            val model = pickModel(p) ?: return ToolOutput("Arama kotası doldu.", isError = true)
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts",
                    JSONArray().put(JSONObject().put("text", "Google aramasıyla güncel bilgiye bakarak kısa ve net Türkçe yanıtla (tarih/saat bağlamı: ${Persona.contextLine(p, -1)}): $query")))))
                .put("tools", JSONArray().put(JSONObject().put("googleSearch", JSONObject())))
            val req = Request.Builder().url("${BASE}models/$model:generateContent")
                .header("x-goog-api-key", p.geminiKey)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            try {
                return execute(http.newCall(req)) { resp ->
                    if (!resp.isSuccessful) throw parseError(resp)
                    val json = JSONObject(resp.body!!.string())
                    val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val text = (0 until (parts?.length() ?: 0)).mapNotNull { i ->
                        parts!!.optJSONObject(i)?.takeIf { !it.optBoolean("thought") }?.optString("text")
                    }.joinToString("").trim()
                    if (text.isEmpty()) ToolOutput("Arama sonuç vermedi.", isError = true) else ToolOutput(text)
                }
            } catch (e: GeminiError) {
                if (e.code == 429) exhausted[model] = if (e.perDay) nextPacificMidnight() else System.currentTimeMillis() + (e.retrySec ?: 60) * 1000L
                else return ToolOutput("Arama yapılamadı: ${e.message}", isError = true)
            }
        }
        return ToolOutput("Arama yapılamadı.", isError = true)
    }

    // ------------------------------------------------------------------ geçmiş yönetimi

    private fun inline(b64: String) = JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", b64))

    private fun hasFunctionParts(): Boolean = history.any { c ->
        val parts = c.optJSONArray("parts") ?: return@any false
        (0 until parts.length()).any { parts.optJSONObject(it)?.let { p -> p.has("functionCall") || p.has("functionResponse") || p.has("thoughtSignature") } == true }
    }

    /** Geçmişi sade metne çevirir (model değişince veya biçim reddedilince). */
    private fun flatten() {
        val out = ArrayList<JSONObject>()
        for (c in history) {
            val role = if (c.optString("role") == "model") "model" else "user"
            val texts = ArrayList<String>()
            val parts = c.optJSONArray("parts") ?: JSONArray()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                when {
                    part.has("functionCall") -> part.getJSONObject("functionCall").let {
                        texts.add("[araç çağrısı: ${it.optString("name")} ${it.optJSONObject("args") ?: ""}]")
                    }
                    part.has("functionResponse") -> part.getJSONObject("functionResponse").let {
                        texts.add("[araç sonucu ${it.optString("name")}: ${it.optJSONObject("response")?.toString()?.take(2000)}]")
                    }
                    part.has("text") && !part.optBoolean("thought") -> part.optString("text").takeIf { it.isNotBlank() }?.let { texts.add(it) }
                }
            }
            if (texts.isEmpty()) continue
            val text = texts.joinToString("\n")
            val last = out.lastOrNull()
            if (last != null && last.optString("role") == role) {
                val lp = last.getJSONArray("parts").getJSONObject(0)
                lp.put("text", lp.optString("text") + "\n" + text)
            } else {
                out.add(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text))))
            }
        }
        while (out.isNotEmpty() && out.first().optString("role") != "user") out.removeAt(0)
        if (out.isNotEmpty() && out.last().optString("role") == "model") {
            out.add(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "Devam et."))))
        }
        history.clear()
        history.addAll(out)
        imagesInHistory = 0
    }

    /** Görüntü sınırı dolunca: ilerleme özetini alıp temiz bir bağlamda sürdür. */
    private suspend fun handoff(p: Prefs, task: String) {
        val model = pickModel(p) ?: return
        val ask = ArrayList(history)
        val last = JSONObject(ask.removeAt(ask.size - 1).toString())
        last.getJSONArray("parts").put(JSONObject().put("text",
            "[SİSTEM] Bağlam sınırına ulaşıldı. Araç kullanmadan, düz metinle: görevin tanımı, tamamlanan adımlar, ekranın şu anki durumu ve sıradaki adımlar."))
        ask.add(last)
        val summary = runCatching {
            val sb = StringBuilder()
            stream(p, model, { sb.append(it) }, ask, allowTools = false)
            sb.toString()
        }.getOrDefault("")
        history.clear()
        imagesInHistory = 0
        history.add(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text",
            Persona.contextLine(p, device.batteryPercent()) + "\n" + task +
                "\n\n[GÖREV DEVRİ — önceki oturumun notları:]\n" + summary.ifBlank { "(özet yok)" } +
                "\n\nKaldığın yerden devam et; önce ekrana bak. Yapılmış adımları tekrarlama."))))
    }

    // ------------------------------------------------------------------ hatalar

    private fun speakError(msg: String): String {
        speaker.enqueue(msg)
        JarvisState.add(false, msg)
        return msg
    }

    private fun errorMessage(e: Throwable): String {
        val h = JarvisApp.prefs.hitap
        return when (e) {
            is GeminiError -> when {
                e.code == 400 && e.message.contains("API key", true) -> "Gemini API anahtarı geçersiz görünüyor $h. Ayarlardan kontrol eder misiniz?"
                e.code == 403 -> "Gemini anahtarının bu işlem için izni yok $h."
                e.code == 429 -> "Gemini'nin ücretsiz kullanım sınırına ulaştık $h, biraz sonra tekrar deneyelim."
                e.code >= 500 -> "Google sunucuları şu an yoğun $h, birazdan tekrar dener misiniz?"
                else -> "Gemini'den hata geldi $h: ${e.message.take(120)}"
            }
            is IOException -> "İnternet bağlantısı yok gibi görünüyor $h."
            else -> "Beklenmedik bir sorun oldu $h."
        }
    }

    companion object {
        private const val TAG = "JarvisGemini"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/"
        private const val MAX_STEPS = 40
        private const val IMAGE_BUDGET = 12
    }
}
