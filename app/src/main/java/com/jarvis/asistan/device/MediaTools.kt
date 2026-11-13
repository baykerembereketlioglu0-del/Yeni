package com.jarvis.asistan.device

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import com.jarvis.asistan.JarvisApp
import com.jarvis.asistan.ui.ConfirmActivity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Galeri (MediaStore) ve dosya sistemi işlemleri. */
class MediaTools(private val ctx: Context) {

    private val cr get() = ctx.contentResolver
    private val filesUri: Uri = MediaStore.Files.getContentUri("external")
    private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale("tr"))

    data class Media(val id: Long, val name: String, val isVideo: Boolean, val album: String, val path: String,
                     val date: Long, val size: Long, val duration: Long, val mime: String) {
        val uri: Uri get() = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    }

    fun hasAccess(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    private fun noAccess() = ToolOutput("Galeri/dosya erişim izni yok. Kullanıcı Jarvis > Kurulum > \"Tüm dosyalara erişim\" adımını tamamlamalı.", isError = true)

    private fun query(type: String, album: String?, ids: List<Long>? = null): List<Media> {
        val sel = ArrayList<String>()
        val args = ArrayList<String>()
        when (type) {
            "foto" -> sel.add("${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}")
            "video" -> sel.add("${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}")
            else -> sel.add("${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE},${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})")
        }
        if (!album.isNullOrBlank()) { sel.add("${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} LIKE ?"); args.add("%$album%") }
        if (ids != null) sel.add("${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",")})")
        val proj = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.MIME_TYPE,
        )
        val out = ArrayList<Media>()
        cr.query(filesUri, proj, sel.joinToString(" AND "), args.toTypedArray(),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val taken = c.getLong(5)
                out.add(Media(
                    id = c.getLong(0), name = c.getString(1) ?: "?", isVideo = c.getInt(2) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                    album = c.getString(3) ?: "", path = c.getString(4) ?: "",
                    date = if (taken > 0) taken else c.getLong(6) * 1000, size = c.getLong(7), duration = c.getLong(8),
                    mime = c.getString(9) ?: "",
                ))
            }
        }
        return out
    }

    private fun describe(i: Int, m: Media): String {
        val date = Instant.ofEpochMilli(m.date).atZone(ZoneId.systemDefault()).format(fmt)
        val dur = if (m.isVideo && m.duration > 0) ", ${m.duration / 1000} sn" else ""
        return "${i + 1}. [id ${m.id}] ${if (m.isVideo) "VİDEO" else "foto"} ${m.name} — $date — albüm: ${m.album} — %.1f MB$dur".format(m.size / 1e6)
    }

    fun list(type: String, album: String?, offset: Int, limit: Int): ToolOutput {
        if (!hasAccess()) return noAccess()
        val all = query(type, album)
        if (all.isEmpty()) return ToolOutput("Galeride uygun öğe bulunamadı.")
        val page = all.drop(offset).take(limit.coerceIn(1, 100))
        val albums = all.groupingBy { it.album }.eachCount().entries.sortedByDescending { it.value }.take(15)
            .joinToString(", ") { "${it.key} (${it.value})" }
        return ToolOutput("Toplam ${all.size} öğe (en yeni önce). Albümler: $albums\n" +
            page.mapIndexed { i, m -> describe(offset + i, m) }.joinToString("\n"))
    }

    /** Verilen öğelerin küçük resimlerinden numaralı bir pano oluşturur. */
    fun look(ids: List<Long>): ToolOutput {
        if (!hasAccess()) return noAccess()
        val items = query("hepsi", null, ids.take(16)).sortedBy { ids.indexOf(it.id) }
        if (items.isEmpty()) return ToolOutput("Bu kimliklerle öğe bulunamadı.", isError = true)
        if (items.size == 1) {
            val bmp = runCatching { cr.loadThumbnail(items[0].uri, Size(1280, 1280), null) }.getOrNull()
                ?: return ToolOutput("Önizleme alınamadı.", isError = true)
            return ToolOutput("[id ${items[0].id}] ${items[0].name}", DeviceController.toJpegBase64(bmp, 1280))
        }
        val cols = 4
        val cell = 300
        val rows = (items.size + cols - 1) / cols
        val sheet = Bitmap.createBitmap(cols * cell, rows * cell, Bitmap.Config.ARGB_8888)
        val cv = Canvas(sheet)
        cv.drawColor(Color.BLACK)
        val bg = Paint().apply { color = Color.argb(200, 0, 0, 0) }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; textSize = 30f; isFakeBoldText = true }
        items.forEachIndexed { i, m ->
            val x = (i % cols) * cell
            val y = (i / cols) * cell
            runCatching { cr.loadThumbnail(m.uri, Size(cell, cell), null) }.getOrNull()?.let { th ->
                val s = minOf(cell.toFloat() / th.width, cell.toFloat() / th.height)
                val w = (th.width * s).toInt()
                val h = (th.height * s).toInt()
                cv.drawBitmap(Bitmap.createScaledBitmap(th, w, h, true), (x + (cell - w) / 2).toFloat(), (y + (cell - h) / 2).toFloat(), null)
            }
            val label = "id ${m.id}${if (m.isVideo) " ▶" else ""}"
            cv.drawRect(x.toFloat(), y.toFloat(), x + tp.measureText(label) + 12, y + 38f, bg)
            cv.drawText(label, x + 6f, y + 30f, tp)
        }
        return ToolOutput("${items.size} öğenin küçük resimleri (sol üstte kimlikleri):", DeviceController.toJpegBase64(sheet, 1280))
    }

    /** Öğeleri çöp kutusuna taşır (30 gün geri alınabilir). */
    fun trash(ids: List<Long>): ToolOutput {
        if (!hasAccess()) return noAccess()
        val items = query("hepsi", null, ids)
        if (items.isEmpty()) return ToolOutput("Silinecek öğe bulunamadı.", isError = true)
        var ok = 0
        val failed = ArrayList<Media>()
        for (m in items) {
            val done = runCatching {
                if (Build.VERSION.SDK_INT >= 30) {
                    cr.update(m.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null) > 0
                } else cr.delete(m.uri, null, null) > 0
            }.getOrDefault(false)
            if (done) ok++ else failed.add(m)
        }
        JarvisApp.prefs.lastTrashed = items.filter { it !in failed }.joinToString(",") { "${if (it.isVideo) "v" else "i"}${it.id}" }
        if (failed.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
            // Sistem onayı gerekiyorsa onay penceresi aç.
            val pi = MediaStore.createTrashRequest(cr, failed.map { it.uri }, true)
            ConfirmActivity.launch(ctx, pi.intentSender)
            return ToolOutput("$ok öğe çöp kutusuna taşındı. ${failed.size} öğe için ekranda sistem onayı istendi (kullanıcı 'İzin ver' demeli).")
        }
        return ToolOutput("$ok öğe çöp kutusuna taşındı (30 gün içinde geri alınabilir).")
    }

    fun restoreLast(): ToolOutput {
        if (Build.VERSION.SDK_INT < 30) return ToolOutput("Bu Android sürümünde geri alma yok.", isError = true)
        val last = JarvisApp.prefs.lastTrashed.split(",").filter { it.length > 1 }
        if (last.isEmpty()) return ToolOutput("Geri alınacak silme işlemi yok.")
        var ok = 0
        for (tag in last) {
            val id = tag.drop(1).toLongOrNull() ?: continue
            val base = if (tag[0] == 'v') MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val extras = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
            val done = runCatching {
                cr.update(ContentUris.withAppendedId(base, id), ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }, extras) > 0
            }.getOrDefault(false)
            if (done) ok++
        }
        JarvisApp.prefs.lastTrashed = ""
        return ToolOutput("$ok öğe geri getirildi.")
    }

    /** Öğeleri Pictures/<klasör> altına taşır (galeride albüm olarak görünür). */
    fun moveToFolder(ids: List<Long>, folder: String): ToolOutput {
        if (!hasAccess()) return noAccess()
        val safe = folder.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { return ToolOutput("Klasör adı boş.", isError = true) }
        val items = query("hepsi", null, ids)
        var ok = 0
        for (m in items) {
            val done = runCatching {
                cr.update(m.uri, ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$safe/") }, null, null) > 0
            }.getOrDefault(false)
            if (done) ok++
        }
        return ToolOutput("$ok / ${items.size} öğe \"$safe\" albümüne taşındı.")
    }

    /** Bir galeri öğesini başka bir uygulamaya gönderir (örn. YouTube'a yükleme ekranı). */
    fun share(id: Long, app: String, text: String?, launch: (Intent) -> Boolean): ToolOutput {
        val m = query("hepsi", null, listOf(id)).firstOrNull() ?: return ToolOutput("Öğe bulunamadı.", isError = true)
        val pkg = when (app.lowercase(Locale("tr"))) {
            "youtube" -> "com.google.android.youtube"
            "instagram" -> "com.instagram.android"
            "whatsapp" -> "com.whatsapp"
            "tiktok" -> "com.zhiliaoapp.musically"
            "telegram" -> "org.telegram.messenger"
            "drive", "google drive" -> "com.google.android.apps.docs"
            else -> app.takeIf { it.contains('.') }
        }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = m.mime.ifBlank { if (m.isVideo) "video/*" else "image/*" }
            putExtra(Intent.EXTRA_STREAM, m.uri)
            text?.let { putExtra(Intent.EXTRA_TEXT, it); putExtra(Intent.EXTRA_TITLE, it.lineSequence().first()) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (pkg != null) setPackage(pkg)
        }
        val ok = launch(if (pkg == null) Intent.createChooser(i, "Paylaş").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) else i)
        return if (ok) ToolOutput("${m.name} ${pkg ?: "paylaşım menüsü"} ile açıldı. Devam etmek için screenshot al.")
        else ToolOutput("Uygulama açılamadı (yüklü olmayabilir).", isError = true)
    }

    // ---------------- Dosyalar ----------------

    private val root = Environment.getExternalStorageDirectory()

    private fun resolve(path: String): File? {
        val f = File(root, path.trim().removePrefix("/storage/emulated/0").trimStart('/'))
        return if (f.canonicalPath.startsWith(root.canonicalPath)) f else null
    }

    fun listFiles(path: String): ToolOutput {
        if (!hasAccess()) return noAccess()
        val dir = resolve(path) ?: return ToolOutput("Geçersiz yol.", isError = true)
        val list = dir.listFiles() ?: return ToolOutput("Klasör bulunamadı: $path", isError = true)
        val sorted = list.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified() })).take(150)
        return ToolOutput("${dir.absolutePath.removePrefix(root.absolutePath).ifEmpty { "/" }} içeriği (${list.size}):\n" +
            sorted.joinToString("\n") { if (it.isDirectory) "[klasör] ${it.name}/" else "${it.name} (%.1f KB)".format(it.length() / 1024.0) })
    }

    fun readFile(path: String): ToolOutput {
        if (!hasAccess()) return noAccess()
        val f = resolve(path) ?: return ToolOutput("Geçersiz yol.", isError = true)
        if (!f.isFile) return ToolOutput("Dosya bulunamadı: $path", isError = true)
        if (f.length() > 2_000_000) return ToolOutput("Dosya okunamayacak kadar büyük.", isError = true)
        return ToolOutput(f.readText().take(30_000))
    }

    fun writeFile(path: String, content: String, append: Boolean): ToolOutput {
        if (!hasAccess()) return noAccess()
        val f = resolve(path) ?: return ToolOutput("Geçersiz yol.", isError = true)
        f.parentFile?.mkdirs()
        if (append) f.appendText(content) else f.writeText(content)
        return ToolOutput("Kaydedildi: ${f.absolutePath.removePrefix(root.absolutePath)}")
    }

    fun findFiles(name: String): ToolOutput {
        if (!hasAccess()) return noAccess()
        val q = name.lowercase(Locale("tr"))
        val hits = root.walkTopDown().maxDepth(6)
            .onEnter { !it.name.startsWith(".") && it.name != "Android" }
            .filter { it.name.lowercase(Locale("tr")).contains(q) }.take(40).toList()
        return if (hits.isEmpty()) ToolOutput("\"$name\" adında dosya bulunamadı.")
        else ToolOutput(hits.joinToString("\n") { it.absolutePath.removePrefix(root.absolutePath) + if (it.isDirectory) "/" else "" })
    }
}
