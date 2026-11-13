package com.jarvis.asistan.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.asistan.service.JarvisService

/** Adım adım izin kurulumu. */
class SetupActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        list = Ui.vertical(this, 18).apply { setBackgroundColor(Ui.BG) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(list) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        list.addView(Ui.text(this, "KURULUM", 22f, Ui.CYAN, true).apply { letterSpacing = 0.3f })
        list.addView(Ui.text(this, "Jarvis'in tabletin her yerine erişebilmesi için aşağıdaki izinleri sırayla verin. Her adımdan sonra bu ekrana geri dönün.", 14f, Ui.DIM))

        for (step in Setup.steps) {
            val done = step.isDone(this)
            val card = Ui.vertical(this, 14).apply { background = Ui.panel(this@SetupActivity) }
            card.addView(Ui.text(this, (if (done) "✓  " else "○  ") + step.title + if (!step.essential) "  (isteğe bağlı)" else "",
                17f, if (done) Ui.GOOD else Ui.TEXT, true))
            card.addView(Ui.text(this, step.why, 14f, Ui.DIM).apply { setPadding(0, 6, 0, 6) })
            if (!done) card.addView(Ui.button(this, "Ayarla") { step.fix(this) }, Ui.lp(-2, -2))
            list.addView(card, Ui.lp().apply { topMargin = Ui.dp(this@SetupActivity, 10) })
        }

        list.addView(Ui.title(this, "HONOR İPUCU"))
        list.addView(Ui.text(this,
            "MagicOS arka plandaki uygulamaları agresif şekilde kapatır. Ayarlar > Pil > Uygulama başlatma bölümünde Jarvis'i \"Manuel yönet\" yapıp " +
                "Otomatik başlatma, İkincil başlatma ve Arka planda çalıştırma seçeneklerini açın. Son uygulamalar ekranında Jarvis'i aşağı çekerek kilitleyebilirsiniz.",
            14f, Ui.DIM))
        list.addView(Ui.button(this, "Uygulama bilgisini aç") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, Ui.lp(-2, -2).apply { topMargin = Ui.dp(this@SetupActivity, 8) })
        list.addView(Ui.button(this, "Bitti") {
            JarvisService.start(this)
            finish()
        }, Ui.lp().apply { topMargin = Ui.dp(this@SetupActivity, 20) })
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }
}
