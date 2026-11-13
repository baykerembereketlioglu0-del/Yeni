package com.jarvis.asistan.brain

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import com.anthropic.models.messages.UserLocation
import com.anthropic.models.messages.WebSearchTool20250305
import com.anthropic.models.messages.WebSearchTool20260209
import com.jarvis.asistan.JarvisApp
import com.jarvis.asistan.JarvisState
import com.jarvis.asistan.device.DeviceController
import com.jarvis.asistan.voice.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.time.Duration
import kotlin.coroutines.coroutineContext

/**
 * Jarvis'in beyni: konuşma geçmişini tutar, Claude'a akışlı istek atar,
 * cümleleri geldikçe seslendirir ve araç çağrılarını tablette çalıştırır.
 */
class ClaudeBrain(private val device: DeviceController, private val speaker: Speaker) {

    private var client: AnthropicClient? = null
    private var clientKey: String = ""
    private val history = ArrayList<MessageParam>()
    private var systemPrompt: String = ""
    private var tools: List<Tool>? = null
    private var lastActivity = 0L
    /** Sunucu tarafı yedek model parametresi reddedilirse kapatılır. */
    private var useFallbacks = true

    /** Claude `sohbeti_bitir` çağırdıysa true olur. */
    @Volatile var endRequested = false
        private set

    fun reset() {
        history.clear()
        systemPrompt = ""
    }

    private fun client(): AnthropicClient? {
        val key = JarvisApp.prefs.apiKey
        if (key.isBlank()) return null
        if (client == null || key != clientKey) {
            client?.close()
            client = AnthropicOkHttpClient.builder()
                .apiKey(key)
                .timeout(Duration.ofSeconds(90))
                .maxRetries(2)
                .build()
            clientKey = key
        }
        return client
    }

    /**
     * Kullanıcının sözüne (veya bir otomatik olaya) cevap verir. Cevap seslendirilir.
     * @return Jarvis'in söylediği tam metin.
     */
    suspend fun respond(userText: String): String {
        endRequested = false
        val p = JarvisApp.prefs
        val c = client() ?: return speakError(
            "Beynime bağlanmam için Claude API anahtarına ihtiyacım var ${p.hitap}. Lütfen uygulamanın ayarlarından anahtarı girin."
        )

        val now = System.currentTimeMillis()
        // Uzun aradan sonra temiz bir sohbet başlat (maliyeti ve gecikmeyi düşük tutar).
        if (now - lastActivity > 15 * 60_000 || history.size > 60) reset()
        lastActivity = now
        if (systemPrompt.isEmpty()) systemPrompt = Persona.systemPrompt(p)
        val toolList = tools ?: Tools.toApiTools().also { tools = it }

        history.add(MessageParam.ofUser(Persona.contextLine(p, device.batteryPercent()) + "\n" + userText))

        JarvisState.newReply()
        val spoken = StringBuilder()
        val sentence = SentenceSplitter { s ->
            speaker.enqueue(s)
            spoken.append(s).append(' ')
        }

        try {
            repeat(MAX_STEPS) {
                val params = buildParams(p.model, p.effort, toolList)
                JarvisState.setMode(com.jarvis.asistan.Mode.THINKING)
                val onText: (String) -> Unit = { delta ->
                    sentence.feed(delta)
                    JarvisState.appendToLast(delta)
                }
                val message = try {
                    stream(c, params, onText)
                } catch (e: BadRequestException) {
                    if (!useFallbacks || e.message?.contains("fallback", ignoreCase = true) != true) throw e
                    Log.w(TAG, "fallbacks parametresi reddedildi, onsuz tekrar deneniyor", e)
                    useFallbacks = false
                    stream(c, buildParams(p.model, p.effort, toolList), onText)
                }
                sentence.flush()
                history.add(message.toParam())

                val stop = message.stopReason().orElse(StopReason.END_TURN)
                when (stop) {
                    StopReason.TOOL_USE -> {
                        val uses = message.content().mapNotNull { it.toolUse().orElse(null) }
                        val results = uses.map { runTool(it, sentence) }
                        history.add(MessageParam.ofUserOfContentBlockParams(results))
                        // Yalnızca vedalaşma yapıldıysa tekrar sormaya gerek yok: sohbeti kapat.
                        if (endRequested && uses.all { it.name() == "sohbeti_bitir" || it.name() == "konus" }) {
                            reset()
                            return spoken.toString()
                        }
                    }
                    StopReason.PAUSE_TURN -> { /* sunucu aracı (web araması) sürüyor; devam et */ }
                    StopReason.REFUSAL -> {
                        speaker.enqueue("Bu konuda yardımcı olamıyorum ${p.hitap}.")
                        reset()
                        return spoken.toString()
                    }
                    else -> return spoken.toString()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Kullanıcı sözü kesti. Yarım kalan araç çağrısı geçmişi bozabileceği için sohbeti sıfırla.
            reset()
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Claude hatası", e)
            // Hatalı istekte geçmişi temizle ki bir sonraki istek düzgün başlasın.
            reset()
            return speakError(errorMessage(e))
        }
        return spoken.toString()
    }

    private fun buildParams(model: String, effort: String, toolList: List<Tool>): MessageCreateParams {
        val p = JarvisApp.prefs
        val b = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(systemPrompt)
            .messages(history)
            .cacheControl(CacheControlEphemeral.builder().build())
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(effort)).build())
        toolList.forEach { b.addTool(it) }
        val location = UserLocation.builder()
            .country("TR")
            .timezone("Europe/Istanbul")
            .apply { if (p.city.isNotBlank()) city(p.city) }
            .build()
        if (model.startsWith("claude-haiku")) {
            // Haiku 5.5 dinamik filtreli web aramasını desteklemiyor; temel sürümü kullan.
            b.addTool(WebSearchTool20250305.builder().maxUses(3L).userLocation(location).build())
        } else {
            b.addTool(WebSearchTool20260209.builder().maxUses(3L).userLocation(location).build())
        }
        // Güvenlik sınıflandırıcısı reddederse sunucu tarafında uygun modele otomatik geçiş.
        if (useFallbacks && (model == "claude-opus-5-5" || model == "claude-sonnet-5-5")) {
            b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        return b.build()
    }

    /** Akışlı istek: metin parçaları geldikçe [onText] çağrılır; tam mesaj döner. */
    private suspend fun stream(c: AnthropicClient, params: MessageCreateParams, onText: (String) -> Unit): Message {
        val acc = MessageAccumulator.create()
        runInterruptible(Dispatchers.IO) {
            c.messages().createStreaming(params).use { sr ->
                val it = sr.stream().iterator()
                while (it.hasNext()) {
                    if (Thread.interrupted()) throw InterruptedException()
                    val ev = it.next()
                    acc.accumulate(ev)
                    ev.contentBlockDelta().flatMap { d -> d.delta().text() }.ifPresent { t ->
                        if (t.text().isNotEmpty()) onText(t.text())
                    }
                }
            }
        }
        return acc.message()
    }

    private suspend fun runTool(use: ToolUseBlock, sentence: SentenceSplitter): ContentBlockParam {
        coroutineContext.ensureActive()
        val input: JSONObject? = try {
            JSONObject(jsonMapper().writeValueAsString(use._input()))
        } catch (e: Exception) {
            null
        }
        val result = if (input == null) {
            com.jarvis.asistan.device.ToolOutput("INVALID_JSON: araç girdisi okunamadı, tekrar dene.", isError = true)
        } else {
            Log.i(TAG, "Araç: ${use.name()} $input")
            when (use.name()) {
                "konus" -> {
                    val text = input.optString("metin")
                    sentence.feedSentence(text)
                    JarvisState.appendToLast(if (text.endsWith(" ")) text else "$text ")
                }
                "sohbeti_bitir" -> endRequested = true
            }
            if (Tools.waitsForSpeech(use.name())) speaker.awaitIdle(15_000)
            try {
                Tools.execute(device, use.name(), input)
            } catch (e: Exception) {
                Log.e(TAG, "Araç hatası", e)
                com.jarvis.asistan.device.ToolOutput("Hata: ${e.message}", isError = true)
            }
        }
        val b = ToolResultBlockParam.builder().toolUseId(use.id())
        if (result.imageBase64 != null) {
            b.contentOfBlocks(
                listOf(
                    ToolResultBlockParam.Content.Block.ofText(result.text),
                    ToolResultBlockParam.Content.Block.ofImage(
                        Base64ImageSource.builder()
                            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                            .data(result.imageBase64)
                            .build()
                    ),
                )
            )
        } else {
            b.content(result.text)
        }
        if (result.isError) b.isError(true)
        return ContentBlockParam.ofToolResult(b.build())
    }

    private fun speakError(msg: String): String {
        speaker.enqueue(msg)
        JarvisState.add(false, msg)
        return msg
    }

    private fun errorMessage(e: Throwable): String {
        val h = JarvisApp.prefs.hitap
        return when (e) {
            is UnauthorizedException -> "API anahtarı geçersiz görünüyor $h. Ayarlardan kontrol eder misiniz?"
            is PermissionDeniedException -> "API anahtarının bu modele erişim izni yok $h."
            is RateLimitException -> "Şu an çok fazla istek gönderdik $h, biraz sonra tekrar deneyelim."
            is InternalServerException -> "Sunucular şu an yoğun $h, birazdan tekrar dener misiniz?"
            is BadRequestException -> if (e.message?.contains("credit", true) == true)
                "Claude hesabınızın bakiyesi bitmiş görünüyor $h." else "İsteği işlerken bir sorun çıktı $h. Tekrar dener misiniz?"
            is AnthropicServiceException -> "Claude servisinden hata geldi $h: kod ${e.statusCode()}."
            is AnthropicIoException -> "İnternet bağlantısı yok gibi görünüyor $h."
            else -> "Beklenmedik bir sorun oldu $h."
        }
    }

    /** Akan metni cümlelere bölüp seslendirir. */
    class SentenceSplitter(private val emit: (String) -> Unit) {
        private val buf = StringBuilder()

        fun feed(delta: String) {
            buf.append(delta)
            while (true) {
                val idx = boundary() ?: break
                val s = buf.substring(0, idx + 1).trim()
                buf.delete(0, idx + 1)
                if (s.isNotEmpty()) emit(s)
            }
        }

        fun feedSentence(s: String) {
            flush()
            if (s.isNotBlank()) emit(s.trim())
        }

        fun flush() {
            val s = buf.toString().trim()
            buf.clear()
            if (s.isNotEmpty()) emit(s)
        }

        private fun boundary(): Int? {
            for (i in 0 until buf.length - 1) {
                val ch = buf[i]
                if ((ch == '.' || ch == '!' || ch == '?' || ch == '…' || ch == ':' || ch == ';') && buf[i + 1].isWhitespace()) {
                    if (i >= 12) return i
                }
                if (ch == '\n' && i > 0) return i
            }
            return null
        }
    }

    companion object {
        private const val TAG = "JarvisBrain"
        private const val MAX_STEPS = 12
    }
}
