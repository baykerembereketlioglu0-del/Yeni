package app.karala.overlay

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.karala.App
import app.karala.data.Repo
import app.karala.data.Subject
import app.karala.study.Expr
import app.karala.study.FocusTimer
import app.karala.study.Phase
import app.karala.study.net
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Ekranın üstünde yüzen, başlığından sürüklenen küçük araç penceresi. */
abstract class OverlayWidget(protected val ctx: Context, title: String, private val onClosed: (OverlayWidget) -> Unit) {
    protected val wm: WindowManager = ctx.getSystemService(WindowManager::class.java)
    protected val handler = Handler(Looper.getMainLooper())
    val params = Ui.overlayParams()
    val root: LinearLayout = Ui.col(ctx).apply {
        background = Ui.bg(Ui.SURFACE, Ui.dp(ctx, 16).toFloat(), Ui.OUTLINE, Ui.dp(ctx, 1))
        setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 10))
    }
    protected val body: FrameLayout = FrameLayout(ctx)
    var shown = false; private set

    init {
        val header = Ui.row(ctx)
        val name = Ui.label(ctx, title, 12f, Ui.DIM, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 34), 1f); gravity = Gravity.CENTER_VERTICAL; maxLines = 1
        }
        val close = Ui.iconBtn(ctx, Icon.CLOSE, 34, "Kapat").apply { setOnClickListener { dismiss() } }
        header.addView(name); header.addView(close)
        header.setOnTouchListener(WindowDragger(wm, root, params))
        name.setOnTouchListener(WindowDragger(wm, root, params))
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        root.addView(body, LinearLayout.LayoutParams(-2, -2))
    }

    fun show(x: Int, y: Int) {
        if (shown) return
        params.x = x; params.y = y
        runCatching { wm.addView(root, params); shown = true; onShown() }
    }

    fun dismiss() {
        if (!shown) return
        shown = false
        onHidden()
        handler.removeCallbacksAndMessages(null)
        runCatching { wm.removeView(root) }
        onClosed(this)
    }

    fun setVisible(v: Boolean) { root.visibility = if (v) View.VISIBLE else View.INVISIBLE }
    fun clamp() { WindowDragger.clamp(wm, root, params); runCatching { wm.updateViewLayout(root, params) } }

    protected open fun onShown() {}
    protected open fun onHidden() {}

    protected fun big(text: String, sp: Float = 30f) = Ui.label(ctx, text, sp, Ui.TEXT, bold = true).apply {
        fontFeatureSettings = "tnum"; gravity = Gravity.CENTER // eşit genişlikli rakamlar: süre akarken titremez
    }

    companion object {
        fun clock(ms: Long): String {
            val s = ms / 1000
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
        }
    }
}

/** Kronometre: her "Soru ✓" bir tur sayılır; soru başına ortalama süreyi gösterir. */
class StopwatchWidget(ctx: Context, onClosed: (OverlayWidget) -> Unit) : OverlayWidget(ctx, "KRONOMETRE", onClosed) {
    private var running = false
    private var base = 0L          // çalışırken başlangıç (elapsedRealtime)
    private var accumulated = 0L   // duraklatılana kadar biriken
    private var lapStart = 0L
    private var laps = 0
    private var lastLap = 0L
    private val time = big("00:00.0", 28f)
    private val info = Ui.label(ctx, "Her soruda ✓'e bas", 12f, Ui.DIM).apply { gravity = Gravity.CENTER }
    private val play = Ui.iconBtn(ctx, Icon.PLAY, 44, "Başlat/Duraklat")
    private val ticker = object : Runnable { override fun run() { render(); if (running) handler.postDelayed(this, 100) } }

    init {
        val col = Ui.col(ctx).apply { gravity = Gravity.CENTER_HORIZONTAL; minimumWidth = Ui.dp(ctx, 190) }
        col.addView(time, LinearLayout.LayoutParams(-1, -2))
        col.addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 4); bottomMargin = Ui.dp(ctx, 6) })
        val row = Ui.row(ctx).apply { gravity = Gravity.CENTER }
        play.setOnClickListener { toggle() }
        val lap = Ui.iconBtn(ctx, Icon.CHECK, 44, "Soru tamam").apply { setOnClickListener { lap() } }
        val reset = Ui.iconBtn(ctx, Icon.RESET, 44, "Sıfırla").apply { setOnClickListener { reset() } }
        Ui.setActive(lap, true, Ui.GOOD)
        row.addView(play); row.addView(lap); row.addView(reset)
        col.addView(row)
        body.addView(col)
    }

    private fun elapsed() = accumulated + if (running) SystemClock.elapsedRealtime() - base else 0

    private fun toggle() {
        if (running) { accumulated = elapsed(); running = false }
        else { base = SystemClock.elapsedRealtime(); running = true; handler.post(ticker) }
        Ui.setIcon(play, if (running) Icon.PAUSE else Icon.PLAY); render()
    }

    private fun lap() {
        val e = elapsed()
        if (e - lapStart < 500) return
        lastLap = e - lapStart; lapStart = e; laps++
        render()
    }

    private fun reset() {
        running = false; accumulated = 0; lapStart = 0; laps = 0; lastLap = 0
        Ui.setIcon(play, Icon.PLAY); render()
    }

    private fun render() {
        val e = elapsed()
        time.text = "${clock(e)}.${e % 1000 / 100}"
        info.text = if (laps == 0) "Her soruda ✓'e bas"
        else "$laps soru · son ${clock(lastLap)} · ort ${clock(lapStart / laps)}"
    }

    override fun onShown() { render(); if (running) handler.post(ticker) }
}

/** Pomodoro: uygulamadaki odak zamanlayıcıyla aynı durumu paylaşır. */
class PomodoroWidget(ctx: Context, onClosed: (OverlayWidget) -> Unit) : OverlayWidget(ctx, "POMODORO", onClosed) {
    private val phase = Ui.label(ctx, "", 12f, Ui.ACCENT, bold = true).apply { gravity = Gravity.CENTER }
    private val time = big("25:00")
    private val play = Ui.iconBtn(ctx, Icon.PLAY, 44, "Başlat/Duraklat")
    private val ticker = object : Runnable { override fun run() { FocusTimer.tick(); render(); handler.postDelayed(this, 500) } }

    init {
        val col = Ui.col(ctx).apply { gravity = Gravity.CENTER_HORIZONTAL; minimumWidth = Ui.dp(ctx, 170) }
        col.addView(phase, LinearLayout.LayoutParams(-1, -2))
        col.addView(time, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 2); bottomMargin = Ui.dp(ctx, 4) })
        val row = Ui.row(ctx).apply { gravity = Gravity.CENTER }
        play.setOnClickListener {
            val s = FocusTimer.state.value
            when { s.phase == Phase.IDLE -> FocusTimer.startFocus(); s.running -> FocusTimer.pause(); else -> FocusTimer.resume() }
            render()
        }
        val skip = Ui.iconBtn(ctx, Icon.SKIP, 44, "Aşamayı geç").apply { setOnClickListener { FocusTimer.skip(); render() } }
        val reset = Ui.iconBtn(ctx, Icon.RESET, 44, "Sıfırla").apply { setOnClickListener { FocusTimer.reset(); render() } }
        row.addView(play); row.addView(skip); row.addView(reset)
        col.addView(row)
        body.addView(col)
    }

    private fun render() {
        val s = FocusTimer.state.value
        phase.text = when (s.phase) {
            Phase.IDLE -> "HAZIR"; Phase.FOCUS -> "ODAK · ${s.round + 1}. tur"
            Phase.BREAK -> "KISA MOLA"; Phase.LONG_BREAK -> "UZUN MOLA"
        }
        phase.setTextColor(if (s.phase == Phase.BREAK || s.phase == Phase.LONG_BREAK) Ui.GOOD else Ui.ACCENT)
        time.text = clock(if (s.phase == Phase.IDLE) App.instance.prefs.focusMinutes * 60_000L else s.remaining() + 999)
        Ui.setIcon(play, if (s.running) Icon.PAUSE else Icon.PLAY)
    }

    override fun onShown() { handler.post(ticker) }
}

/** Bilimsel hesap makinesi. */
class CalcWidget(ctx: Context, onClosed: (OverlayWidget) -> Unit) : OverlayWidget(ctx, "HESAP MAKİNESİ", onClosed) {
    private var expr = ""
    private var justEvaluated = false
    private val exprView = Ui.label(ctx, "", 14f, Ui.DIM).apply { gravity = Gravity.END; maxLines = 2 }
    private val result = big("0", 24f).apply { gravity = Gravity.END; maxLines = 1 }

    init {
        val col = Ui.col(ctx)
        val keyW = Ui.dp(ctx, 46); val keyH = Ui.dp(ctx, 40); val gap = Ui.dp(ctx, 3)
        col.addView(exprView, LinearLayout.LayoutParams(keyW * 5 + gap * 10, -2))
        col.addView(result, LinearLayout.LayoutParams(keyW * 5 + gap * 10, -2).apply { topMargin = gap; bottomMargin = gap * 2 })
        val grid = GridLayout(ctx).apply { columnCount = 5 }
        val keys = listOf(
            "sin", "cos", "tan", "ln", "log",
            "C", "(", ")", "⌫", "÷",
            "7", "8", "9", "×", "√",
            "4", "5", "6", "−", "^",
            "1", "2", "3", "+", "π",
            "0", ",", "%", "!", "=",
        )
        for (k in keys) {
            val isOp = k in "÷×−+^√π%!()" || k.length > 1
            val tv = Ui.label(ctx, k, if (k.length > 1) 13f else 17f, when (k) { "=" -> Ui.ON_ACCENT; "C", "⌫" -> Ui.BAD; else -> if (isOp) Ui.ACCENT else Ui.TEXT }).apply {
                gravity = Gravity.CENTER
                background = Ui.bg(if (k == "=") Ui.ACCENT else Ui.SURFACE_HI, Ui.dp(ctx, 10).toFloat())
                layoutParams = GridLayout.LayoutParams().apply { width = keyW; height = keyH; setMargins(gap, gap, gap, gap) }
                setOnClickListener { press(k) }
            }
            grid.addView(tv)
        }
        col.addView(grid)
        body.addView(col)
    }

    private fun press(k: String) {
        when (k) {
            "C" -> { expr = ""; result.text = "0" }
            "⌫" -> if (expr.isNotEmpty()) {
                // Fonksiyon adlarını tek seferde sil: "sin(" → ""
                val fn = listOf("sin(", "cos(", "tan(", "ln(", "log(").firstOrNull { expr.endsWith(it) }
                expr = expr.dropLast(fn?.length ?: 1)
            }
            "=" -> {
                if (expr.isBlank()) return
                try {
                    val v = Expr.evaluate(expr, App.instance.prefs.calcDegrees)
                    val text = Expr.format(v)
                    result.text = text.replace('.', ',')
                    exprView.text = "$expr ="
                    expr = text.replace('.', ','); justEvaluated = true
                    return
                } catch (e: Expr.Error) { result.text = e.message }
            }
            else -> {
                val isDigit = k[0].isDigit() || k == ","
                if (justEvaluated && isDigit) expr = ""
                expr += if (k.length > 1) "$k(" else k
            }
        }
        justEvaluated = false
        exprView.text = expr
        if (k != "=" && k != "C") preview()
    }

    private fun preview() {
        val v = runCatching { Expr.evaluate(expr, App.instance.prefs.calcDegrees) }.getOrNull() ?: return
        result.text = Expr.format(v).replace('.', ',')
    }
}

/** Soru sayacı: doğru/yanlış/boş kayıtlarını seçili derse yazar. Uzun basış bir geri alır. */
class CounterWidget(ctx: Context, private val repo: Repo, onClosed: (OverlayWidget) -> Unit) : OverlayWidget(ctx, "SORU SAYACI", onClosed) {
    private var subjects: List<Subject> = emptyList()
    private var subjectIndex = -1 // -1: Genel
    private val subjectPill = Ui.pill(ctx, "Genel") { nextSubject() }
    private val counts = IntArray(3)
    private val buttons = ArrayList<TextView>()
    private val netView = Ui.label(ctx, "", 12f, Ui.DIM).apply { gravity = Gravity.CENTER }
    private var job: Job? = null

    init {
        val col = Ui.col(ctx).apply { gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(subjectPill, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = Ui.dp(ctx, 8) })
        val row = Ui.row(ctx)
        val colors = intArrayOf(Ui.GOOD, Ui.BAD, Ui.DIM)
        for (i in 0..2) {
            val b = TextView(ctx).apply {
                gravity = Gravity.CENTER; setTextColor(Ui.ON_ACCENT); typeface = Ui.font(ctx, true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                background = Ui.bg(colors[i], Ui.dp(ctx, 12).toFloat())
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 62), Ui.dp(ctx, 54)).apply { marginEnd = if (i < 2) Ui.dp(ctx, 6) else 0 }
                setOnClickListener { change(i, +1) }
                setOnLongClickListener { if (counts[i] > 0) change(i, -1); true }
            }
            buttons.add(b); row.addView(b)
        }
        // Satır genişliği pencereyi belirlesin (MATCH_PARENT olursa en dar çocuğa sıkışır).
        col.addView(row, LinearLayout.LayoutParams(-2, -2))
        col.addView(netView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 8) })
        body.addView(col)
        render()
    }

    private fun subjectId(): Long? = subjects.getOrNull(subjectIndex)?.id

    private fun nextSubject() {
        subjectIndex = if (subjectIndex + 1 >= subjects.size) -1 else subjectIndex + 1
        App.instance.prefs.lastSubjectId = subjectId() ?: -1
        reload()
    }

    private fun change(i: Int, delta: Int) {
        counts[i] += delta; render()
        val sid = subjectId()
        App.instance.scope.launch {
            repo.logQuestions(sid, if (i == 0) delta else 0, if (i == 1) delta else 0, if (i == 2) delta else 0)
        }
    }

    private fun reload() {
        subjectPill.text = subjects.getOrNull(subjectIndex)?.name ?: "Genel"
        job?.cancel()
        job = App.instance.scope.launch {
            val t = repo.dao.questionTotals(Repo.startOfDay(), Repo.startOfDay(1), subjectId())
            counts[0] = t.correct; counts[1] = t.wrong; counts[2] = t.blank
            render()
        }
    }

    private fun render() {
        val names = arrayOf("Doğru", "Yanlış", "Boş")
        for (i in 0..2) buttons[i].text = "${names[i]}\n${counts[i]}"
        val div = App.instance.prefs.examDivisor
        netView.text = "Bugün ${counts.sum()} soru · net ${"%.2f".format(net(counts[0], counts[1], div))}"
    }

    override fun onShown() {
        App.instance.scope.launch {
            subjects = repo.dao.subjectsNow()
            val last = App.instance.prefs.lastSubjectId
            subjectIndex = subjects.indexOfFirst { it.id == last }
            reload()
        }
    }

    override fun onHidden() { job?.cancel() }
}
