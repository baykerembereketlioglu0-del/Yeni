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
import com.anthropic.models.messages.ComputerToolset20260801
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.RawContentBlockDeltaEvent
import com.anthropic.models.messages.RawMessageStreamEvent
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
import com.jarvis.asistan.device.ToolOutput
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
class ClaudeBrain(private val device: DeviceController, private val speaker: Speaker) : Brain {

    private var client: AnthropicClient? = null
    private var clientKey: String = ""
    private val history = ArrayList<MessageParam>()
    private var systemPrompt: String = ""
    private var tools: List<Tool>? = null
    private var lastActivity = 0L
    /** Geçmişteki görüntü sayısı (istek başına görüntü sınırını aşmamak için). */
    private var imagesInHistory = 0
    /** Sunucu tarafı yedek model parametresi reddedilirse kapatılır. */
    private var useFallbacks = true

    /** Claude `sohbeti_bitir` çağırdıysa true olur. */
    @Volatile override var endRequested = false
        private set

    override fun reset() {
        history.clear()
        imagesInHistory = 0
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
     * @param image isteğe bağlı JPEG (base64), örn. paylaşılan ders programı fotoğrafı.
     * @return Jarvis'in söylediği tam metin.
     */
    override suspend fun respond(userText: String, image: String?): String {
        endRequested = false
        val p = JarvisApp.prefs
        val c = client() ?: return speakError(
            "Beynime bağlanmam için Claude API anahtarına ihtiyacım var ${p.hitap}. Lütfen uygulamanın ayarlarından anahtarı girin."
        )

        val now = System.currentTimeMillis()
        // Uzun aradan sonra temiz bir sohbet başlat (maliyeti ve gecikmeyi düşük tutar).
        if (now - lastActivity > 15 * 60_000 || history.size > 60 || imagesInHistory >= IMAGE_BUDGET) reset()
        lastActivity = now
        if (systemPrompt.isEmpty()) systemPrompt = Persona.systemPrompt(p, claude = true)
        val toolList = tools ?: Tools.toApiTools().also { tools = it }

        val userLine = Persona.contextLine(p, device.batteryPercent()) + "\n" + userText
        history.add(userMessage(userLine, image))
        if (image != null) imagesInHistory++

        JarvisState.newReply()
        val spoken = StringBuilder()
        val sentence = SentenceSplitter { s ->
            speaker.enqueue(s)
            spoken.append(s).append(' ')
        }
        val onText: (String) -> Unit = { delta ->
            sentence.feed(delta)
            JarvisState.appendToLast(delta)
        }

        try {
            repeat(MAX_STEPS) {
                JarvisState.setMode(com.jarvis.asistan.Mode.THINKING)
                val message = call(c, p, toolList, history, onText)
                sentence.flush()
                history.add(message.toParam())

                val stop = message.stopReason().orElse(StopReason.END_TURN)
                when (stop) {
                    StopReason.TOOL_USE -> {
                        val uses = message.content().mapNotNull { it.toolUse().orElse(null) }
                        val results = runTools(uses, sentence)
                        history.add(MessageParam.ofUserOfContentBlockParams(results))
                        // Yalnızca vedalaşma yapıldıysa tekrar sormaya gerek yok: sohbeti kapat.
                        if (endRequested && uses.all { it.name() == "sohbeti_bitir" || it.name() == "konus" }) {
                            reset()
                            return spoken.toString()
                        }
                        // Görüntü sınırına yaklaşıldıysa uzun ekran görevini yeni bir bağlama devret.
                        if (imagesInHistory >= IMAGE_BUDGET) handoff(c, p, toolList, results, userText)
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
            speaker.enqueue("Bu görev beklenenden uzun sürdü ${p.hitap}, burada durdum. İsterseniz kaldığım yerden devam edebilirim.")
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

    private fun userMessage(text: String, image: String?): MessageParam =
        if (image == null) MessageParam.ofUser(text)
        else MessageParam.ofUserOfContentBlockParams(
            listOf(
                ContentBlockParam.ofText(text),
                ContentBlockParam.ofImage(
                    Base64ImageSource.builder().mediaType(Base64ImageSource.MediaType.IMAGE_JPEG).data(image).build()
                ),
            )
        )

    private suspend fun call(
        c: AnthropicClient, p: com.jarvis.asistan.Prefs, toolList: List<Tool>,
        messages: List<MessageParam>, onText: (String) -> Unit,
    ): Message = try {
        stream(c, buildParams(p.model, p.effort, toolList, messages), onText)
    } catch (e: BadRequestException) {
        if (!useFallbacks || e.message?.contains("fallback", ignoreCase = true) != true) throw e
        Log.w(TAG, "fallbacks parametresi reddedildi, onsuz tekrar deneniyor", e)
        useFallbacks = false
        stream(c, buildParams(p.model, p.effort, toolList, messages), onText)
    }

    /**
     * Uzun ekran görevlerinde istek başına görüntü sınırı aşılmasın diye: Claude'dan şimdiye kadarki
     * ilerlemenin özetini ister ve görevi bu özetle temiz bir bağlamda sürdürür.
     */
    private suspend fun handoff(
        c: AnthropicClient, p: com.jarvis.asistan.Prefs, toolList: List<Tool>,
        lastResults: List<ContentBlockParam>, task: String,
    ) {
        Log.i(TAG, "Görüntü bütçesi doldu, görev devrediliyor")
        val ask = history.dropLast(1) + MessageParam.ofUserOfContentBlockParams(
            lastResults + ContentBlockParam.ofText(
                "[SİSTEM] Bağlam sınırına ulaşıldı; görev temiz bir oturumda sürecek. Şimdi HİÇBİR araç kullanmadan, " +
                    "yalnızca düz metinle şunları yaz: görevin tam tanımı, şu ana kadar tamamlanan adımlar, ekranın şu anki durumu " +
                    "ve sıradaki adımlar. Kısa ve kesin ol."
            )
        )
        val summary = runCatching {
            call(c, p, toolList, ask) {}.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n")
        }.getOrDefault("")
        history.clear()
        imagesInHistory = 0
        history.add(
            MessageParam.ofUser(
                Persona.contextLine(p, device.batteryPercent()) + "\n" + task +
                    "\n\n[GÖREV DEVRİ — bu görev önceki bir oturumda başladı. Önceki oturumun notları:]\n" +
                    summary.ifBlank { "(özet alınamadı)" } +
                    "\n\nKaldığın yerden devam et; önce screenshot al. Zaten yapılmış adımları tekrarlama."
            )
        )
    }

    private fun buildParams(model: String, effort: String, toolList: List<Tool>, messages: List<MessageParam>): MessageCreateParams {
        val p = JarvisApp.prefs
        val b = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(systemPrompt)
            .messages(messages)
            .cacheControl(CacheControlEphemeral.builder().build())
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(effort)).build())
        toolList.forEach { b.addTool(it) }
        // Claude'un ekranı bir insan gibi kullanabilmesi için bilgisayar kullanımı araç seti.
        b.addTool(ComputerToolset20260801.builder().build())
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
                // Girdisiz araç çağrılarında (ör. screenshot) hiç girdi parçası gelmezse birleştirici hata verir;
                // bu durumda blok kapanmadan önce boş bir girdi parçası ekle.
                val toolBlocksWithoutInput = HashSet<Long>()
                while (it.hasNext()) {
                    if (Thread.interrupted()) throw InterruptedException()
                    val ev = it.next()
                    ev.contentBlockStart().ifPresent { st ->
                        val cb = st.contentBlock()
                        if (cb.toolUse().isPresent || cb.serverToolUse().isPresent) toolBlocksWithoutInput.add(st.index())
                    }
                    ev.contentBlockDelta().ifPresent { d -> if (d.delta().inputJson().isPresent) toolBlocksWithoutInput.remove(d.index()) }
                    ev.contentBlockStop().ifPresent { st ->
                        if (toolBlocksWithoutInput.remove(st.index())) {
                            acc.accumulate(RawMessageStreamEvent.ofContentBlockDelta(
                                RawContentBlockDeltaEvent.builder().index(st.index()).inputJsonDelta("").build()))
                        }
                    }
                    acc.accumulate(ev)
                    ev.contentBlockDelta().flatMap { d -> d.delta().text() }.ifPresent { t ->
                        if (t.text().isNotEmpty()) onText(t.text())
                    }
                }
            }
        }
        return acc.message()
    }

    /** Bir yanıttaki tüm araç çağrılarını sırayla çalıştırır. */
    private suspend fun runTools(uses: List<ToolUseBlock>, sentence: SentenceSplitter): List<ContentBlockParam> {
        var computerFailed = false
        return uses.map { use ->
            val isComputer = use.toolsetName().orElse(null) == "computer"
            if (isComputer && computerFailed) {
                // Toplu ekran eylemlerinde ilk hatadan sonrakiler çalıştırılmaz.
                toolResult(use, ToolOutput("Not executed: an earlier computer action in this turn failed.", isError = true))
            } else {
                val out = runTool(use, isComputer, sentence)
                if (isComputer && out.isError) computerFailed = true
                if (out.imageBase64 != null) imagesInHistory++
                toolResult(use, out)
            }
        }
    }

    private suspend fun runTool(use: ToolUseBlock, isComputer: Boolean, sentence: SentenceSplitter): ToolOutput {
        coroutineContext.ensureActive()
        val input: JSONObject = try {
            JSONObject(jsonMapper().writeValueAsString(use._input()))
        } catch (e: Exception) {
            return ToolOutput("INVALID_JSON: araç girdisi okunamadı, tekrar dene.", isError = true)
        }
        Log.i(TAG, "Araç: ${if (isComputer) "computer." else ""}${use.name()} ${input.toString().take(300)}")
        if (isComputer) {
            JarvisState.setMode(com.jarvis.asistan.Mode.THINKING)
            return try {
                device.computer.execute(use.name(), input)
            } catch (e: Exception) {
                Log.e(TAG, "Ekran eylemi hatası", e)
                ToolOutput("Hata: ${e.message}", isError = true)
            }
        }
        when (use.name()) {
            "konus" -> {
                val text = input.optString("metin")
                sentence.feedSentence(text)
                JarvisState.appendToLast(if (text.endsWith(" ")) text else "$text ")
            }
            "sohbeti_bitir" -> endRequested = true
        }
        if (Tools.waitsForSpeech(use.name())) speaker.awaitIdle(15_000)
        return try {
            Tools.execute(device, use.name(), input)
        } catch (e: Exception) {
            Log.e(TAG, "Araç hatası", e)
            ToolOutput("Hata: ${e.message}", isError = true)
        }
    }

    private fun toolResult(use: ToolUseBlock, result: ToolOutput): ContentBlockParam {
        val b = ToolResultBlockParam.builder().toolUseId(use.id())
        use.toolsetName().ifPresent { b.toolsetName(it) }
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
        private const val MAX_STEPS = 60
        private const val IMAGE_BUDGET = 16
    }
}
