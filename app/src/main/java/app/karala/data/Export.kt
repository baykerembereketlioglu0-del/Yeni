package app.karala.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import app.karala.ink.InkRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Sayfaları PNG / PDF olarak dışa aktarır ve paylaşım menüsünü açar. */
object Export {
    private fun shareDir(ctx: Context) = File(ctx.cacheDir, "share").apply { mkdirs() }
    private fun safe(name: String) = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifBlank { "karala" }.take(60)

    suspend fun pagePng(ctx: Context, repo: Repo, pageId: Long, name: String): File = withContext(Dispatchers.IO) {
        val ink = repo.loadInk(pageId); val bg = repo.loadBg(pageId)
        val width = (if (ink.width > 0) ink.width else bg?.width ?: 1600).coerceIn(800, 2400)
        val bmp = InkRenderer().renderPage(ink, bg, width)
        val f = File(shareDir(ctx), "${safe(name)}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle(); bg?.recycle()
        f
    }

    suspend fun notePdf(ctx: Context, repo: Repo, noteId: Long, name: String): File = withContext(Dispatchers.IO) {
        val pages = repo.dao.pagesNow(noteId)
        val doc = PdfDocument()
        val renderer = InkRenderer()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        pages.forEachIndexed { i, p ->
            val ink = repo.loadInk(p.id); val bg = repo.loadBg(p.id)
            val bmp = renderer.renderPage(ink, bg, 1600)
            // A4 genişliği (595 pt); yükseklik sayfanın oranına göre.
            val w = 595; val h = (w * bmp.height / bmp.width.toFloat()).toInt().coerceAtLeast(1)
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
            doc.finishPage(page)
            bmp.recycle(); bg?.recycle()
        }
        val f = File(shareDir(ctx), "${safe(name)}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        f
    }

    fun share(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Galeriden seçilen görseli bellek dostu boyutta çözer (uzun kenar en fazla [maxSide]). */
    suspend fun decodeImage(ctx: Context, uri: Uri, maxSide: Int = 2400): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
    }
}
