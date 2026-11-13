package com.jarvis.asistan.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.jarvis.asistan.device.DeviceController
import com.jarvis.asistan.service.JarvisService

/**
 * "Paylaş → Jarvis": başka uygulamalardan gelen yazı, fotoğraf (ör. ders programı) veya metin dosyasını
 * Jarvis'e iletir.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handle(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Jarvis bu içeriği açamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun handle(i: Intent) {
        val texts = ArrayList<String>()
        i.getStringExtra(Intent.EXTRA_SUBJECT)?.let { texts.add(it) }
        i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { texts.add(it.toString()) }

        val uris = ArrayList<Uri>()
        if (i.action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        } else {
            @Suppress("DEPRECATION")
            (i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uris.add(it) }
        }

        var image: String? = null
        for (u in uris) {
            val type = contentResolver.getType(u) ?: i.type ?: ""
            when {
                type.startsWith("image/") && image == null -> image = DeviceController.toJpegBase64(decode(u), 1568)
                type.startsWith("text/") -> contentResolver.openInputStream(u)?.use {
                    texts.add(it.readBytes().decodeToString().take(20_000))
                }
            }
        }
        if (texts.isEmpty() && image == null) {
            Toast.makeText(this, "Paylaşılan içerik desteklenmiyor.", Toast.LENGTH_SHORT).show()
            return
        }
        JarvisService.share(this, texts.joinToString("\n"), image)
        Toast.makeText(this, "Jarvis'e iletildi", Toast.LENGTH_SHORT).show()
    }

    private fun decode(u: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, u)) { d, info, _ ->
                val max = maxOf(info.size.width, info.size.height)
                if (max > 2048) d.setTargetSampleSize(max / 2048 + 1)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(contentResolver, u)
        }
}
