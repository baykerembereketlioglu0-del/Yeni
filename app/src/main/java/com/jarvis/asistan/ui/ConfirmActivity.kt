package com.jarvis.asistan.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle

/** Sistem onay pencerelerini (ör. "Bu öğeler çöp kutusuna taşınsın mı?") göstermek için görünmez ekran. */
class ConfirmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val sender = intent.getParcelableExtra<IntentSender>(EXTRA_SENDER)
        if (sender == null) { finish(); return }
        try {
            @Suppress("DEPRECATION")
            startIntentSenderForResult(sender, 1, null, 0, 0, 0)
        } catch (e: Exception) {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        finish()
    }

    companion object {
        private const val EXTRA_SENDER = "sender"
        fun launch(ctx: Context, sender: IntentSender) {
            runCatching {
                ctx.startActivity(Intent(ctx, ConfirmActivity::class.java)
                    .putExtra(EXTRA_SENDER, sender)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
