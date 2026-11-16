package app.karala.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Ekran kalemi başlarken gösterilen "El mi, Kalem mi?" seçimi.
 *
 *  - El: parmak da kalem ucu da çizer.
 *  - Kalem: yalnızca kalem ucu çizer; ekrana değen el/avuç içi iz bırakmaz.
 *
 * Tek dokunuşla seçilir. Dışarı dokunmak mevcut ayarı korur.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class ModeChooser(
    private val ctx: Context,
    private val currentStylus: Boolean,
    private val onChosen: (stylus: Boolean, dontAskAgain: Boolean) -> Unit,
    private val onDismissed: () -> Unit,
) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var dontAsk = false
    var shown = false; private set

    val root: FrameLayout = FrameLayout(ctx).apply {
        setBackgroundColor(0xA6000000.toInt())
        setOnClickListener { dismiss(); onDismissed() }
    }

    val params: WindowManager.LayoutParams = Ui.overlayParams(-1, -1).apply {
        flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    init {
        // Kart en fazla 460dp genişler; dar ekranlarda kenar boşluklarıyla ekrana sığar.
        val maxWidth = Ui.dp(ctx, 460)
        val card = object : LinearLayout(ctx) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                val w = View.MeasureSpec.getSize(widthSpec)
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(minOf(w, maxWidth), View.MeasureSpec.EXACTLY), heightSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.bg(Ui.SURFACE, Ui.dp(ctx, 28).toFloat(), Ui.OUTLINE, Ui.dp(ctx, 1))
            val p = Ui.dp(ctx, 22); setPadding(p, p, p, Ui.dp(ctx, 16))
            isClickable = true // kartın içine dokunmak pencereyi kapatmasın
        }
        card.addView(Ui.label(ctx, "Nasıl çizeceksin?", 21f, Ui.TEXT, bold = true))
        card.addView(Ui.label(ctx, "Sonradan araç çubuğundaki ✋ / ✎ düğmesiyle değiştirebilirsin.", 13f, Ui.DIM).apply {
            setLineSpacing(0f, 1.2f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 6); bottomMargin = Ui.dp(ctx, 16) })

        val row = Ui.row(ctx).apply { gravity = Gravity.TOP }
        row.addView(option(Icon.HAND, "El", "Parmağınla çizersin.\nKalem de çalışır.", !currentStylus) { choose(false) },
            LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = Ui.dp(ctx, 6) })
        row.addView(option(Icon.STYLUS, "Kalem", "Yalnızca kalem çizer.\nElin iz bırakmaz.", currentStylus) { choose(true) },
            LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = Ui.dp(ctx, 6) })
        card.addView(row, LinearLayout.LayoutParams(-1, -2))

        val remember = Ui.row(ctx).apply { setPadding(0, Ui.dp(ctx, 14), 0, Ui.dp(ctx, 4)) }
        val box = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)).apply { marginEnd = Ui.dp(ctx, 10) }
            val p = Ui.dp(ctx, 3); setPadding(p, p, p, p)
        }
        fun paint() {
            box.background = if (dontAsk) Ui.bg(Ui.ACCENT, Ui.dp(ctx, 7).toFloat())
                else Ui.bg(0, Ui.dp(ctx, 7).toFloat(), Ui.DIM, Ui.dp(ctx, 2))
            box.setImageDrawable(if (dontAsk) IconDrawable(Icon.CHECK, Ui.ON_ACCENT, 2.6f) else null)
        }
        paint()
        remember.addView(box)
        remember.addView(Ui.label(ctx, "Seçimimi hatırla, bir daha sorma", 14f, Ui.TEXT))
        remember.contentDescription = "Seçimimi hatırla"
        remember.setOnClickListener { dontAsk = !dontAsk; paint() }
        card.addView(remember, LinearLayout.LayoutParams(-1, -2))

        val margin = Ui.dp(ctx, 20)
        root.setPadding(margin, margin, margin, margin)
        root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
    }

    private fun option(icon: Icon, title: String, desc: String, selected: Boolean, onClick: () -> Unit): View {
        val tint = if (selected) Ui.ACCENT else Ui.TEXT
        val col = Ui.col(ctx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.bg(if (selected) 0x26FFD54A else Ui.SURFACE_HI, Ui.dp(ctx, 20).toFloat(),
                if (selected) Ui.ACCENT else Ui.OUTLINE, Ui.dp(ctx, if (selected) 2 else 1))
            val p = Ui.dp(ctx, 14); setPadding(p, Ui.dp(ctx, 18), p, Ui.dp(ctx, 16))
            contentDescription = "$title modu"
            setOnClickListener { onClick() }
        }
        val bubble = ImageView(ctx).apply {
            val p = Ui.dp(ctx, 15); setPadding(p, p, p, p)
            setImageDrawable(IconDrawable(icon, tint, 1.7f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (selected) 0x33FFD54A else 0x14FFFFFF) }
        }
        col.addView(bubble, LinearLayout.LayoutParams(Ui.dp(ctx, 68), Ui.dp(ctx, 68)))
        col.addView(Ui.label(ctx, title, 17f, tint, bold = true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 12) })
        col.addView(Ui.label(ctx, desc, 12.5f, Ui.DIM).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 6) })
        // Etiket iki kutuda da yer kaplar (seçili olmayanda görünmez): kutular eşit yükseklikte kalır.
        col.addView(Ui.label(ctx, "SON SEÇİMİN", 10f, Ui.ACCENT, bold = true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.08f; visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 10) })
        return col
    }

    private fun choose(stylus: Boolean) { dismiss(); onChosen(stylus, dontAsk) }

    fun show() {
        if (shown) return
        runCatching { wm.addView(root, params); shown = true }
    }

    fun dismiss() {
        if (!shown) return
        shown = false
        runCatching { wm.removeView(root) }
    }
}
