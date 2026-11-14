package app.karala.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.karala.ink.InkJson
import app.karala.ink.InkPage
import app.karala.ink.InkRenderer
import app.karala.study.Srs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Sayfa dosyaları: mürekkep (JSON), zemin görseli (JPEG) ve küçük resim. */
class PageFiles(ctx: Context) {
    val dir = File(ctx.filesDir, "pages").apply { mkdirs() }
    fun ink(id: Long) = File(dir, "$id.json")
    fun bg(id: Long) = File(dir, "$id.bg.jpg")
    fun thumb(id: Long) = File(dir, "$id.thumb.jpg")
    fun delete(id: Long) { ink(id).delete(); bg(id).delete(); thumb(id).delete() }
}

class Repo(private val ctx: Context, val db: KaralaDb, val prefs: Prefs) {
    val dao = db.dao()
    val files = PageFiles(ctx)

    // ---- Sayfalar ----

    suspend fun loadInk(pageId: Long): InkPage = withContext(Dispatchers.IO) {
        val f = files.ink(pageId)
        if (f.exists()) runCatching { InkJson.decode(f.readText()) }.getOrElse { InkPage() } else InkPage()
    }

    suspend fun loadBg(pageId: Long): Bitmap? = withContext(Dispatchers.IO) {
        val f = files.bg(pageId)
        if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    /** Mürekkebi diske yazar, küçük resmi yeniler ve defterin "güncellendi" zamanını ilerletir. */
    suspend fun saveInk(pageId: Long, ink: InkPage, bg: Bitmap? = null) {
        // Kopya çağıranın iş parçacığında alınır; çizim sürerken liste değişse bile yazılan veri tutarlı kalır.
        val snapshot = ink.copy()
        withContext(Dispatchers.IO) { writeInk(pageId, snapshot, bg) }
    }

    private suspend fun writeInk(pageId: Long, snapshot: InkPage, bg: Bitmap?) {
        writeAtomic(files.ink(pageId), InkJson.encode(snapshot))
        val background = bg ?: files.bg(pageId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        val thumb = InkRenderer().renderPage(snapshot, background, THUMB_WIDTH)
        files.thumb(pageId).outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        thumb.recycle()
        val now = System.currentTimeMillis()
        dao.pageNow(pageId)?.let { p ->
            dao.update(p.copy(updatedAt = now))
            p.noteId?.let { dao.touchNote(it, now) }
        }
    }

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
    }

    suspend fun addPage(noteId: Long?, ink: InkPage = InkPage(), bg: Bitmap? = null): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val pos = if (noteId != null) dao.nextPosition(noteId) else 0
        val id = dao.insert(Page(noteId = noteId, position = pos, hasBackgroundImage = bg != null, createdAt = now, updatedAt = now))
        if (bg != null) {
            if (ink.width <= 0 || ink.height <= 0) { ink.width = bg.width; ink.height = bg.height }
            files.bg(id).outputStream().use { bg.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        saveInk(id, ink, bg)
        id
    }

    suspend fun deletePage(id: Long) = withContext(Dispatchers.IO) { dao.deletePage(id); files.delete(id) }

    suspend fun createNote(title: String, subjectId: Long?, autoDay: String? = null): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.insert(Note(subjectId = subjectId, title = title, createdAt = now, updatedAt = now, autoDay = autoDay))
    }

    suspend fun deleteNote(id: Long) = withContext(Dispatchers.IO) {
        dao.pagesNow(id).forEach { deletePage(it.id) }
        dao.deleteNote(id)
    }

    // ---- Ekran kaleminden kaydetme ----

    /** Ekran üstü çizimi, seçilen dersin o günkü "Ekran notları" defterine sayfa olarak ekler. */
    suspend fun saveCaptureAsNote(subjectId: Long?, ink: InkPage, screenshot: Bitmap?): Long = withContext(Dispatchers.IO) {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val note = dao.autoNote(day, subjectId)
        val noteId = note?.id ?: run {
            val subject = dao.subjectsNow().firstOrNull { it.id == subjectId }?.name ?: "Ekran notları"
            val label = SimpleDateFormat("d MMMM", Locale("tr")).format(Date())
            createNote("$subject · $label", subjectId, day)
        }
        addPage(noteId, ink.copy(), screenshot)
        noteId
    }

    suspend fun saveCaptureAsMistake(subjectId: Long?, topic: String, reason: Int, ink: InkPage, screenshot: Bitmap?): Long =
        withContext(Dispatchers.IO) {
            val pageId = addPage(null, ink.copy(), screenshot)
            val now = System.currentTimeMillis()
            // İlk tekrar ertesi gün.
            dao.insert(Mistake(subjectId = subjectId, topic = topic, pageId = pageId, reason = reason,
                createdAt = now, dueAt = now + Srs.DAY_MS))
        }

    suspend fun deleteMistake(m: Mistake) = withContext(Dispatchers.IO) { dao.deleteMistake(m.id); deletePage(m.pageId) }

    suspend fun reviewMistake(m: Mistake, grade: Srs.Grade) = withContext(Dispatchers.IO) {
        val r = Srs.review(Srs.State(m.intervalDays, m.ease, m.reps, m.lapses), grade, System.currentTimeMillis())
        // Art arda 4 başarılı tekrar ve 3 haftayı aşan aralık: artık öğrenildi say.
        val learned = r.state.reps >= 4 && r.state.intervalDays >= 21
        dao.update(m.copy(dueAt = r.dueAt, intervalDays = r.state.intervalDays, ease = r.state.ease,
            reps = r.state.reps, lapses = r.state.lapses, learned = learned))
    }

    suspend fun reviewCard(c: Card, grade: Srs.Grade) = withContext(Dispatchers.IO) {
        val r = Srs.review(Srs.State(c.intervalDays, c.ease, c.reps, c.lapses), grade, System.currentTimeMillis())
        dao.update(c.copy(dueAt = r.dueAt, intervalDays = r.state.intervalDays, ease = r.state.ease,
            reps = r.state.reps, lapses = r.state.lapses))
    }

    // ---- Kayıtlar ----

    suspend fun logQuestions(subjectId: Long?, correct: Int = 0, wrong: Int = 0, blank: Int = 0) = withContext(Dispatchers.IO) {
        dao.insert(QuestionLog(subjectId = subjectId, at = System.currentTimeMillis(), correct = correct, wrong = wrong, blank = blank))
    }

    suspend fun logSession(subjectId: Long?, startAt: Long, durationSec: Int, kind: Int) = withContext(Dispatchers.IO) {
        if (durationSec >= 60) dao.insert(StudySession(subjectId = subjectId, startAt = startAt, durationSec = durationSec, kind = kind))
    }

    companion object {
        const val THUMB_WIDTH = 480

        fun startOfDay(offsetDays: Int = 0): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, offsetDays)
        }.timeInMillis
    }
}
