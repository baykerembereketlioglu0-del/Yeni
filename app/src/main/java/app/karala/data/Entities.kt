package app.karala.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val position: Int = 0,
)

/** Defter: sayfalardan oluşur. */
@Entity(tableName = "notes", indices = [Index("subjectId")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    /** Ekran kaleminden otomatik oluşturulan günlük defterler için "yyyy-MM-dd"; elle açılanlarda null. */
    val autoDay: String? = null,
)

/**
 * Bir çizim sayfası. Mürekkep ve görseller dosyada tutulur (bkz. [PageFiles]).
 * noteId null ise sayfa bir hatalı soruya aittir.
 */
@Entity(tableName = "pages", indices = [Index("noteId")])
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long? = null,
    val position: Int = 0,
    val hasBackgroundImage: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Hatalı / çözülemeyen soru. Aralıklı tekrarla yeniden sorulur. */
@Entity(tableName = "mistakes", indices = [Index("subjectId"), Index("dueAt")])
data class Mistake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val topic: String = "",
    val pageId: Long,
    /** 0 bilgi eksiği, 1 dikkat hatası, 2 işlem hatası, 3 süre yetmedi, 4 soruyu anlamadım */
    val reason: Int = 0,
    val note: String = "",
    val learned: Boolean = false,
    val createdAt: Long,
    val dueAt: Long,
    val intervalDays: Int = 0,
    val ease: Float = 2.5f,
    val reps: Int = 0,
    val lapses: Int = 0,
)

@Entity(tableName = "decks", indices = [Index("subjectId")])
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "cards", indices = [Index("deckId"), Index("dueAt")])
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val createdAt: Long,
    val dueAt: Long,
    val intervalDays: Int = 0,
    val ease: Float = 2.5f,
    val reps: Int = 0,
    val lapses: Int = 0,
)

/** Ödev / sınav / çalışma görevi. type: 0 ödev, 1 sınav, 2 çalışma */
@Entity(tableName = "tasks", indices = [Index("dueAt")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectId: Long? = null,
    val type: Int = 0,
    val dueAt: Long,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val note: String = "",
    /** Hatırlatma: bitişten kaç dakika önce; -1 = yok */
    val remindMinutes: Int = -1,
)

/** Haftalık ders programı. dayOfWeek: 1 Pazartesi … 7 Pazar */
@Entity(tableName = "schedule")
data class ScheduleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val subjectId: Long? = null,
    val title: String = "",
    val place: String = "",
)

/** Tamamlanan çalışma oturumu. kind: 0 pomodoro, 1 serbest */
@Entity(tableName = "sessions", indices = [Index("startAt")])
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val startAt: Long,
    val durationSec: Int,
    val kind: Int = 0,
)

/** Çözülen soru kaydı (artımlı). */
@Entity(tableName = "question_logs", indices = [Index("at")])
data class QuestionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val at: Long,
    val correct: Int = 0,
    val wrong: Int = 0,
    val blank: Int = 0,
)

/** Deneme sınavı sonucu. */
@Entity(tableName = "exam_results", indices = [Index("at")])
data class ExamResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val at: Long,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    /** Kaç yanlış bir doğruyu götürür (4: YKS, 3: LGS, 0: götürmez) */
    val divisor: Int = 4,
)

// ---- Sorgu sonuç tipleri ----

data class NoteWithMeta(
    val id: Long, val subjectId: Long?, val title: String, val createdAt: Long, val updatedAt: Long,
    val pinned: Boolean, val pageCount: Int, val firstPageId: Long?,
)

data class DeckWithCounts(
    val id: Long, val subjectId: Long?, val name: String, val total: Int, val due: Int,
)

data class SubjectTotal(val subjectId: Long?, @ColumnInfo(name = "total") val total: Long)

data class QuestionTotals(val correct: Int, val wrong: Int, val blank: Int) {
    val all get() = correct + wrong + blank
}
