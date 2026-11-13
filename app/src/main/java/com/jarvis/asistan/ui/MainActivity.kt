package com.jarvis.asistan.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jarvis.asistan.JarvisApp
import com.jarvis.asistan.JarvisState
import com.jarvis.asistan.service.JarvisService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var reactor: ArcReactorView
    private lateinit var status: TextView
    private lateinit var partial: TextView
    private lateinit var transcript: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var setupBtn: View
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val root = Ui.vertical(this, 16).apply { setBackgroundColor(Ui.BG) }

        val header = Ui.text(this, "J.A.R.V.I.S.", 26f, Ui.CYAN, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.4f
            setShadowLayer(20f, 0f, 0f, Ui.CYAN)
        }
        info = Ui.text(this, "", 13f, Ui.DIM).apply { gravity = Gravity.CENTER }
        root.addView(header, Ui.lp())
        root.addView(info, Ui.lp())

        reactor = ArcReactorView(this).apply {
            setOnClickListener { JarvisService.command(this@MainActivity, JarvisService.ACTION_TALK) }
            contentDescription = "Konuşmak için dokun"
        }
        val reactorBox = FrameLayout(this).apply { addView(reactor, FrameLayout.LayoutParams(-1, -1)) }
        root.addView(reactorBox, Ui.lp(h = 0, weight = 1.1f))

        status = Ui.text(this, "", 18f, Ui.CYAN, true).apply { gravity = Gravity.CENTER }
        partial = Ui.text(this, "", 15f, Ui.TEXT).apply { gravity = Gravity.CENTER; setPadding(0, 6, 0, 6) }
        root.addView(status, Ui.lp())
        root.addView(partial, Ui.lp())

        transcript = Ui.vertical(this, 12)
        scroll = ScrollView(this).apply {
            background = Ui.panel(this@MainActivity)
            addView(transcript)
        }
        root.addView(scroll, Ui.lp(h = 0, weight = 1f).apply { topMargin = Ui.dp(this@MainActivity, 8) })

        val buttons = Ui.horizontal(this).apply { setPadding(0, Ui.dp(this@MainActivity, 12), 0, 0) }
        setupBtn = Ui.button(this, "Kurulum") { startActivity(Intent(this, SetupActivity::class.java)) }
        buttons.addView(setupBtn, Ui.lp(0, -2, 1f).apply { marginEnd = Ui.dp(this@MainActivity, 6) })
        buttons.addView(Ui.button(this, "Konuş") { JarvisService.command(this, JarvisService.ACTION_TALK) },
            Ui.lp(0, -2, 1f).apply { marginEnd = Ui.dp(this@MainActivity, 6) })
        buttons.addView(Ui.button(this, "Ayarlar") { startActivity(Intent(this, SettingsActivity::class.java)) }, Ui.lp(0, -2, 1f))
        root.addView(buttons, Ui.lp())

        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { JarvisState.mode.collect { m -> reactor.mode = m; status.text = m.label } }
                launch { JarvisState.level.collect { reactor.level = it } }
                launch { JarvisState.partial.collect { partial.text = it } }
                launch { JarvisState.lines.collect { renderTranscript(it) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val p = JarvisApp.prefs
        val missing = Setup.missing(this)
        (setupBtn as? android.widget.Button)?.text = if (missing.isEmpty()) "Kurulum ✓" else "Kurulum (${missing.size})"
        val days = p.yksDaysLeft()
        info.text = buildString {
            append("Beyin: ").append(if (p.provider == "claude") p.model else "Gemini (${p.geminiModel})")
            if (days != null && days >= 0) append("   •   TYT'ye $days gün")
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        } else {
            JarvisService.start(this)
        }
        if (missing.any { it.essential } && !askedSetup) {
            askedSetup = true
            startActivity(Intent(this, SetupActivity::class.java))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Mikrofon izni geldiyse servis kendini mikrofon yetkisiyle tazeler.
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) JarvisService.start(this)
    }

    private fun renderTranscript(lines: List<com.jarvis.asistan.Line>) {
        transcript.removeAllViews()
        if (lines.isEmpty()) {
            transcript.addView(Ui.text(this, "\"Hey Jarvis\" deyin ya da reaktöre dokunun.", 15f, Ui.DIM))
            return
        }
        for (l in lines) {
            if (l.text.isBlank()) continue
            val tv = Ui.text(this, (if (l.fromUser) "Siz: " else "Jarvis: ") + l.text, 15f,
                if (l.fromUser) Color.WHITE else Ui.CYAN)
            tv.setPadding(0, 6, 0, 6)
            transcript.addView(tv)
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    companion object {
        private var askedSetup = false
    }
}
