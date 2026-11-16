package app.karala.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import app.karala.data.Subject
import app.karala.ink.PageBackground
import app.karala.ink.ToolType

/**
 * Yüzen araç çubuğu. Küçültüldüğünde tek bir baloncuğa dönüşür.
 * Tüm eylemler [OverlayService]'e iletilir; bu sınıf yalnızca görünümü kurar ve günceller.
 */
@SuppressLint("ClickableViewAccessibility")
internal class Toolbar(private val svc: OverlayService) {
    private val ctx: Context = svc
    private val wm: WindowManager = svc.getSystemService(WindowManager::class.java)
    val params = Ui.overlayParams()
    val root = Ui.col(ctx)

    private enum class Panel { NONE, TOOL, BOARD, SAVE, MORE }
    private var panel = Panel.NONE

    private val bubble = ImageView(ctx)
    private val box = Ui.col(ctx)
    private val extra = FrameLayout(ctx)

    private val modeBtn = Ui.iconBtn(ctx, Icon.POINTER, desc = "Dokunma modu: alttaki uygulamayı kullan")
    private val inputBtn = Ui.iconBtn(ctx, Icon.HAND, desc = "Çizim girişi: el / kalem")
    private val toolBtns = LinkedHashMap<ToolType, ImageView>()
    private val shapeBtn = Ui.iconBtn(ctx, Icon.SHAPES, desc = "Şekiller")
    private val undoBtn = Ui.iconBtn(ctx, Icon.UNDO, desc = "Geri al")
    private val redoBtn = Ui.iconBtn(ctx, Icon.REDO, desc = "Yinele")
    private val pageLabel = Ui.label(ctx, "1/1", 12f, Ui.DIM, bold = true)
    private val nextBtn = Ui.iconBtn(ctx, Icon.RIGHT, 36, "Sonraki sayfa")
    private val prevBtn = Ui.iconBtn(ctx, Icon.LEFT, 36, "Önceki sayfa")
    private val boardBtn = Ui.iconBtn(ctx, Icon.BOARD, desc = "Tahta / zemin")
    private val saveBtn = Ui.iconBtn(ctx, Icon.SAVE, desc = "Kaydet")
    private val moreBtn = Ui.iconBtn(ctx, Icon.WIDGETS, desc = "Araçlar")
    private val eyeBtn = Ui.iconBtn(ctx, Icon.EYE, desc = "Çizimleri gizle/göster")

    var collapsed = false; private set

    init {
        // Baloncuk (küçültülmüş hâl)
        bubble.apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 52), Ui.dp(ctx, 52))
            val p = Ui.dp(ctx, 13); setPadding(p, p, p, p)
            setImageDrawable(IconDrawable(Icon.PEN, Ui.ON_ACCENT, 2.2f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.ACCENT); setStroke(Ui.dp(ctx, 2), 0x66000000) }
            contentDescription = "Ekran kalemini aç"
            visibility = View.GONE
            setOnTouchListener(WindowDragger(wm, root, params, onClick = { svc.setCollapsed(false) }, onDropped = { svc.savePosition() }))
        }
        root.addView(bubble)

        box.background = Ui.bg(Ui.SURFACE, Ui.dp(ctx, 18).toFloat(), Ui.OUTLINE, Ui.dp(ctx, 1))
        val pad = Ui.dp(ctx, 4); box.setPadding(pad, pad, pad, pad)

        var row = Ui.row(ctx)
        val handle = Ui.iconBtn(ctx, Icon.COLLAPSE, desc = "Küçült (sürükleyerek taşı)")
        handle.setOnTouchListener(WindowDragger(wm, root, params, onClick = { svc.setCollapsed(true) }, onDropped = { svc.savePosition() }))
        row.addView(handle)
        modeBtn.setOnClickListener { svc.setPassThrough(!svc.passThrough) }
        row.addView(modeBtn)
        row.addView(Ui.sep(ctx))

        fun tool(t: ToolType, icon: Icon, desc: String) {
            val b = Ui.iconBtn(ctx, icon, desc = desc)
            b.setOnClickListener { onToolTapped(t) }
            toolBtns[t] = b; row.addView(b)
        }
        tool(ToolType.PEN, Icon.PEN, "Kalem")
        tool(ToolType.HIGHLIGHTER, Icon.HIGHLIGHTER, "Fosforlu kalem")
        tool(ToolType.ERASER, Icon.ERASER, "Silgi")
        shapeBtn.setOnClickListener { onToolTapped(svc.prefs.lastShape) }
        row.addView(shapeBtn)
        tool(ToolType.LASER, Icon.LASER, "Lazer işaretçi")
        row.addView(Ui.sep(ctx))

        undoBtn.setOnClickListener { svc.ink.undo() }
        redoBtn.setOnClickListener { svc.ink.redo() }
        val clear = Ui.iconBtn(ctx, Icon.TRASH, desc = "Sayfayı temizle (uzun bas: sayfayı sil)")
        clear.setOnClickListener { svc.ink.clearAll() }
        clear.setOnLongClickListener { svc.deletePage(); true }
        row.addView(undoBtn); row.addView(redoBtn); row.addView(clear)
        row.addView(Ui.sep(ctx))

        // Dar ekranlarda (telefon) düğmeler iki satıra bölünür; tabletlerde tek satır.
        val (screenW, _) = WindowDragger.screenSize(wm)
        val narrow = screenW < Ui.dp(ctx, 840)
        val row1 = row
        if (narrow) { row1.removeViewAt(row1.childCount - 1); row = Ui.row(ctx) } // sondaki ayırıcıyı at, ikinci satıra geç
        inputBtn.setOnClickListener { svc.setInputMode(!svc.prefs.stylusOnly, announce = true) }
        inputBtn.setOnLongClickListener { svc.showModeChooser(); true }
        row.addView(inputBtn); row.addView(Ui.sep(ctx))
        prevBtn.setOnClickListener { svc.goToPage(svc.pageIndex - 1) }
        nextBtn.setOnClickListener { svc.goToPage(svc.pageIndex + 1) }
        pageLabel.gravity = Gravity.CENTER
        pageLabel.minWidth = Ui.dp(ctx, 30)
        row.addView(prevBtn); row.addView(pageLabel); row.addView(nextBtn)
        row.addView(Ui.sep(ctx))

        boardBtn.setOnClickListener { toggle(Panel.BOARD) }
        saveBtn.setOnClickListener { svc.refreshSubjects { toggle(Panel.SAVE) } }
        moreBtn.setOnClickListener { toggle(Panel.MORE) }
        eyeBtn.setOnClickListener { svc.setInkHidden(!svc.inkHidden) }
        val close = Ui.iconBtn(ctx, Icon.CLOSE, desc = "Ekran kalemini kapat")
        close.setOnClickListener { svc.shutdown() }
        row.addView(boardBtn); row.addView(saveBtn); row.addView(moreBtn); row.addView(eyeBtn); row.addView(close)

        box.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row1) }, LinearLayout.LayoutParams(-2, -2))
        if (narrow) box.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) }, LinearLayout.LayoutParams(-2, -2))
        extra.visibility = View.GONE
        box.addView(extra, LinearLayout.LayoutParams(-2, -2))
        root.addView(box, LinearLayout.LayoutParams(-2, -2))

        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) root.post { clamp() }
        }
        refresh()
    }

    fun clamp() {
        val x = params.x; val y = params.y
        WindowDragger.clamp(wm, root, params)
        if (x != params.x || y != params.y) runCatching { wm.updateViewLayout(root, params) }
    }

    fun setCollapsed(c: Boolean) {
        collapsed = c
        bubble.visibility = if (c) View.VISIBLE else View.GONE
        box.visibility = if (c) View.GONE else View.VISIBLE
        if (c) closePanel()
    }

    private fun onToolTapped(t: ToolType) {
        if (svc.passThrough) svc.setPassThrough(false)
        if (svc.tool == t && panel == Panel.TOOL) { closePanel(); return }
        val wasSelected = svc.tool == t
        svc.selectTool(t)
        // Seçili araca ikinci dokunuş ayarlarını açar; açık panel varsa yeni araca göre yenilenir.
        if (wasSelected || panel == Panel.TOOL) open(Panel.TOOL) else closePanel()
    }

    private fun toggle(p: Panel) { if (panel == p) closePanel() else open(p) }

    fun closePanel() { panel = Panel.NONE; extra.removeAllViews(); extra.visibility = View.GONE; refresh() }

    private fun open(p: Panel) {
        panel = p
        extra.removeAllViews()
        val content = when (p) {
            Panel.TOOL -> toolPanel()
            Panel.BOARD -> boardPanel()
            Panel.SAVE -> savePanel()
            Panel.MORE -> morePanel()
            Panel.NONE -> null
        }
        if (content != null) {
            val pad = Ui.dp(ctx, 8); content.setPadding(pad, pad, pad, pad)
            extra.addView(content); extra.visibility = View.VISIBLE
        }
        refresh()
    }

    /** Düğmelerin seçili/etkin görünümünü servis durumuna göre günceller. */
    fun refresh() {
        Ui.setActive(modeBtn, svc.passThrough, Ui.GOOD)
        Ui.setIcon(inputBtn, if (svc.prefs.stylusOnly) Icon.STYLUS else Icon.HAND)
        Ui.setActive(inputBtn, svc.prefs.stylusOnly, Ui.GOOD)
        inputBtn.contentDescription = if (svc.prefs.stylusOnly) "Kalem modu açık: yalnızca kalem ucu çizer" else "El modu açık: parmakla çizilir"
        for ((t, b) in toolBtns) Ui.setActive(b, !svc.passThrough && svc.tool == t)
        val shape = svc.prefs.lastShape
        Ui.setIcon(shapeBtn, shapeIcon(shape))
        Ui.setActive(shapeBtn, !svc.passThrough && svc.tool.isShape)
        Ui.setEnabledLook(undoBtn, svc.ink.editor.canUndo)
        Ui.setEnabledLook(redoBtn, svc.ink.editor.canRedo)
        pageLabel.text = "${svc.pageIndex + 1}/${svc.pageCount}"
        Ui.setEnabledLook(prevBtn, svc.pageIndex > 0)
        Ui.setIcon(nextBtn, if (svc.pageIndex == svc.pageCount - 1) Icon.PLUS else Icon.RIGHT)
        // Tahta açıkken düğme yeşil yanar: ekranın neden kapandığı bir bakışta anlaşılır.
        val boardOn = svc.ink.page.background != PageBackground.NONE
        Ui.setActive(boardBtn, boardOn || panel == Panel.BOARD, if (boardOn) Ui.GOOD else Ui.ACCENT)
        boardBtn.contentDescription = if (boardOn) "Tahta açık: alttaki ekran gizli" else "Tahta / zemin"
        Ui.setActive(saveBtn, panel == Panel.SAVE)
        Ui.setActive(moreBtn, panel == Panel.MORE)
        Ui.setIcon(eyeBtn, if (svc.inkHidden) Icon.EYE_OFF else Icon.EYE)
        Ui.setActive(eyeBtn, svc.inkHidden, Ui.BAD)
    }

    private fun shapeIcon(t: ToolType) = when (t) {
        ToolType.ARROW -> Icon.ARROW; ToolType.RECT -> Icon.RECT; ToolType.ELLIPSE -> Icon.ELLIPSE; else -> Icon.LINE
    }

    // ---- Paneller ----

    private fun hscroll(child: View) = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(child) }

    private fun slider(min: Int, max: Int, value: Int, onChange: (Int) -> Unit): SeekBar = SeekBar(ctx).apply {
        this.max = max - min; progress = (value - min).coerceIn(0, max - min)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 220), -2)
        progressTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT)
        thumbTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p + min) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun toolPanel(): View {
        val t = svc.tool
        val col = Ui.col(ctx)
        if (t.isShape) {
            val r = Ui.row(ctx)
            for (s in listOf(ToolType.LINE, ToolType.ARROW, ToolType.RECT, ToolType.ELLIPSE)) {
                val b = Ui.iconBtn(ctx, shapeIcon(s), 40, s.name)
                Ui.setActive(b, s == t)
                b.setOnClickListener { svc.selectTool(s); open(Panel.TOOL) }
                r.addView(b)
            }
            col.addView(r)
        }
        if (t == ToolType.ERASER) {
            val r = Ui.row(ctx)
            val whole = Ui.pill(ctx, "Çizgiyi sil") { svc.prefs.eraserPartial = false; svc.applyTool(); open(Panel.TOOL) }
            val part = Ui.pill(ctx, "Parça sil") { svc.prefs.eraserPartial = true; svc.applyTool(); open(Panel.TOOL) }
            Ui.setPillActive(whole, !svc.prefs.eraserPartial); Ui.setPillActive(part, svc.prefs.eraserPartial)
            r.addView(whole); r.addView(part)
            col.addView(r)
        } else {
            val r = Ui.row(ctx)
            val current = svc.prefs.colorFor(t)
            for (c in Ui.PALETTE) {
                val sw = View(ctx).apply {
                    val size = Ui.dp(ctx, 30)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = Ui.dp(ctx, 6) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(c)
                        if (c == current) setStroke(Ui.dp(ctx, 3), Ui.ACCENT) else setStroke(Ui.dp(ctx, 1), Ui.OUTLINE)
                    }
                    setOnClickListener { svc.prefs.setColorFor(t, c); svc.applyTool(); open(Panel.TOOL) }
                }
                r.addView(sw)
            }
            col.addView(hscroll(r), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(ctx, 4) })
        }
        if (t != ToolType.LASER) {
            val (min, max) = when (t) { ToolType.HIGHLIGHTER -> 8 to 44; ToolType.ERASER -> 6 to 64; else -> 1 to 14 }
            val r = Ui.row(ctx)
            val value = Ui.label(ctx, "${svc.prefs.widthFor(t).toInt()}", 12f, Ui.DIM).apply { minWidth = Ui.dp(ctx, 24) }
            r.addView(Ui.label(ctx, "Kalınlık", 12f, Ui.DIM))
            r.addView(slider(min, max, svc.prefs.widthFor(t).toInt()) { v ->
                svc.prefs.setWidthFor(t, v.toFloat()); svc.applyTool(); value.text = "$v"
            })
            r.addView(value)
            col.addView(r, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(ctx, 8) })
        }
        return col
    }

    private fun boardPanel(): View {
        val col = Ui.col(ctx)
        val r = Ui.row(ctx)
        val names = listOf(
            PageBackground.NONE to "Şeffaf", PageBackground.WHITE to "Beyaz tahta", PageBackground.BLACK to "Kara tahta",
            PageBackground.GRID to "Kareli", PageBackground.LINED to "Çizgili", PageBackground.DOTS to "Noktalı",
        )
        for ((bg, name) in names) {
            val p = Ui.pill(ctx, name) { svc.setBoard(bg); open(Panel.BOARD) }
            Ui.setPillActive(p, svc.ink.page.background == bg)
            r.addView(p)
        }
        col.addView(hscroll(r))
        val o = Ui.row(ctx)
        val value = Ui.label(ctx, "%${svc.prefs.boardOpacity}", 12f, Ui.DIM)
        o.addView(Ui.label(ctx, "Tahta opaklığı", 12f, Ui.DIM))
        o.addView(slider(30, 100, svc.prefs.boardOpacity) { v -> svc.prefs.boardOpacity = v; svc.applyBoardOpacity(); value.text = "%$v" })
        o.addView(value)
        col.addView(o, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(ctx, 8) })
        return col
    }

    private fun savePanel(): View {
        val col = Ui.col(ctx)
        col.addView(Ui.label(ctx, "Hangi derse kaydedilsin?", 12f, Ui.DIM))
        val r = Ui.row(ctx)
        val selected = svc.prefs.lastSubjectId
        val all: List<Subject?> = listOf<Subject?>(null) + svc.subjects
        for (s in all) {
            val p = Ui.pill(ctx, s?.name ?: "Genel") { svc.prefs.lastSubjectId = s?.id ?: -1; open(Panel.SAVE) }
            Ui.setPillActive(p, (s?.id ?: -1L) == selected || (s == null && svc.subjects.none { it.id == selected }))
            r.addView(p)
        }
        col.addView(hscroll(r).apply { layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 420), -2) },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(ctx, 6); bottomMargin = Ui.dp(ctx, 8) })
        val actions = Ui.row(ctx)
        val shot = Ui.pill(ctx, "📷 Ekran görüntüsüyle") { svc.prefs.captureWithScreenshot = !svc.prefs.captureWithScreenshot; open(Panel.SAVE) }
        Ui.setPillActive(shot, svc.prefs.captureWithScreenshot, Ui.GOOD)
        val note = Ui.pill(ctx, "Deftere kaydet") { svc.save(asMistake = false) }
        Ui.setPillActive(note, true)
        val mistake = Ui.pill(ctx, "Hatalı soru olarak kaydet") { svc.save(asMistake = true) }
        Ui.setPillActive(mistake, true, Ui.BAD)
        actions.addView(shot); actions.addView(note); actions.addView(mistake)
        col.addView(hscroll(actions))
        return col
    }

    private fun morePanel(): View {
        val col = Ui.col(ctx)
        val r = Ui.row(ctx)
        r.addView(Ui.pill(ctx, "⏱ Kronometre") { svc.toggleWidget(OverlayService.W_STOPWATCH) })
        r.addView(Ui.pill(ctx, "🍅 Pomodoro") { svc.toggleWidget(OverlayService.W_POMODORO) })
        r.addView(Ui.pill(ctx, "🧮 Hesap makinesi") { svc.toggleWidget(OverlayService.W_CALC) })
        r.addView(Ui.pill(ctx, "✔ Soru sayacı") { svc.toggleWidget(OverlayService.W_COUNTER) })
        col.addView(hscroll(r))
        val r2 = Ui.row(ctx)
        val stylus = Ui.pill(ctx, if (svc.prefs.stylusOnly) "Giriş: Kalem" else "Giriş: El") { svc.showModeChooser() }
        val assist = Ui.pill(ctx, "Basılı tut → şekil") { svc.prefs.shapeAssist = !svc.prefs.shapeAssist; svc.applyTool(); open(Panel.MORE) }
        Ui.setPillActive(assist, svc.prefs.shapeAssist, Ui.GOOD)
        r2.addView(stylus); r2.addView(assist)
        r2.addView(Ui.pill(ctx, "Uygulamayı aç") { svc.openApp() })
        col.addView(hscroll(r2), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(ctx, 8) })
        return col
    }
}
