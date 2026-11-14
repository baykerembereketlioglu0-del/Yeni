package app.karala.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface KaralaDao {
    // ---- Dersler ----
    @Query("SELECT * FROM subjects ORDER BY position, id") fun subjects(): Flow<List<Subject>>
    @Query("SELECT * FROM subjects ORDER BY position, id") suspend fun subjectsNow(): List<Subject>
    @Insert suspend fun insert(s: Subject): Long
    @Update suspend fun update(s: Subject)
    @Query("DELETE FROM subjects WHERE id = :id") suspend fun deleteSubject(id: Long)
    @Query("UPDATE notes SET subjectId = NULL WHERE subjectId = :id") suspend fun detachNotes(id: Long)
    @Query("UPDATE mistakes SET subjectId = NULL WHERE subjectId = :id") suspend fun detachMistakes(id: Long)
    @Query("UPDATE decks SET subjectId = NULL WHERE subjectId = :id") suspend fun detachDecks(id: Long)
    @Query("UPDATE tasks SET subjectId = NULL WHERE subjectId = :id") suspend fun detachTasks(id: Long)
    @Query("UPDATE schedule SET subjectId = NULL WHERE subjectId = :id") suspend fun detachSchedule(id: Long)
    @Query("UPDATE sessions SET subjectId = NULL WHERE subjectId = :id") suspend fun detachSessions(id: Long)
    @Query("UPDATE question_logs SET subjectId = NULL WHERE subjectId = :id") suspend fun detachLogs(id: Long)

    @Transaction suspend fun removeSubject(id: Long) {
        detachNotes(id); detachMistakes(id); detachDecks(id); detachTasks(id)
        detachSchedule(id); detachSessions(id); detachLogs(id); deleteSubject(id)
    }

    // ---- Defterler ve sayfalar ----
    @Query("""SELECT n.id, n.subjectId, n.title, n.createdAt, n.updatedAt, n.pinned,
        (SELECT COUNT(*) FROM pages p WHERE p.noteId = n.id) AS pageCount,
        (SELECT p.id FROM pages p WHERE p.noteId = n.id ORDER BY p.position, p.id LIMIT 1) AS firstPageId
        FROM notes n ORDER BY n.pinned DESC, n.updatedAt DESC""")
    fun notes(): Flow<List<NoteWithMeta>>
    @Query("SELECT * FROM notes WHERE id = :id") fun note(id: Long): Flow<Note?>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun noteNow(id: Long): Note?
    @Query("SELECT * FROM notes WHERE autoDay = :day AND ((:subjectId IS NULL AND subjectId IS NULL) OR subjectId = :subjectId) LIMIT 1")
    suspend fun autoNote(day: String, subjectId: Long?): Note?
    @Insert suspend fun insert(n: Note): Long
    @Update suspend fun update(n: Note)
    @Query("UPDATE notes SET updatedAt = :at WHERE id = :id") suspend fun touchNote(id: Long, at: Long)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: Long)

    @Query("SELECT * FROM pages WHERE noteId = :noteId ORDER BY position, id") fun pages(noteId: Long): Flow<List<Page>>
    @Query("SELECT * FROM pages WHERE noteId = :noteId ORDER BY position, id") suspend fun pagesNow(noteId: Long): List<Page>
    @Query("SELECT * FROM pages WHERE id = :id") suspend fun pageNow(id: Long): Page?
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM pages WHERE noteId = :noteId") suspend fun nextPosition(noteId: Long): Int
    @Insert suspend fun insert(p: Page): Long
    @Update suspend fun update(p: Page)
    @Query("DELETE FROM pages WHERE id = :id") suspend fun deletePage(id: Long)

    // ---- Hatalı sorular ----
    @Query("SELECT * FROM mistakes ORDER BY learned, dueAt") fun mistakes(): Flow<List<Mistake>>
    @Query("SELECT * FROM mistakes WHERE id = :id") suspend fun mistakeNow(id: Long): Mistake?
    @Query("SELECT * FROM mistakes WHERE learned = 0 AND dueAt <= :now ORDER BY dueAt") suspend fun dueMistakes(now: Long): List<Mistake>
    @Query("SELECT COUNT(*) FROM mistakes WHERE learned = 0 AND dueAt <= :now") fun dueMistakeCount(now: Long): Flow<Int>
    @Insert suspend fun insert(m: Mistake): Long
    @Update suspend fun update(m: Mistake)
    @Query("DELETE FROM mistakes WHERE id = :id") suspend fun deleteMistake(id: Long)

    // ---- Bilgi kartları ----
    @Query("""SELECT d.id, d.subjectId, d.name,
        (SELECT COUNT(*) FROM cards c WHERE c.deckId = d.id) AS total,
        (SELECT COUNT(*) FROM cards c WHERE c.deckId = d.id AND c.dueAt <= :now) AS due
        FROM decks d ORDER BY d.createdAt DESC""")
    fun decks(now: Long): Flow<List<DeckWithCounts>>
    @Query("SELECT * FROM decks WHERE id = :id") fun deck(id: Long): Flow<Deck?>
    @Insert suspend fun insert(d: Deck): Long
    @Update suspend fun update(d: Deck)
    @Query("DELETE FROM decks WHERE id = :id") suspend fun deleteDeckRow(id: Long)
    @Query("DELETE FROM cards WHERE deckId = :id") suspend fun deleteDeckCards(id: Long)
    @Transaction suspend fun deleteDeck(id: Long) { deleteDeckCards(id); deleteDeckRow(id) }

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id DESC") fun cards(deckId: Long): Flow<List<Card>>
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND dueAt <= :now ORDER BY dueAt") suspend fun dueCards(deckId: Long, now: Long): List<Card>
    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY RANDOM()") suspend fun allCardsShuffled(deckId: Long): List<Card>
    @Query("SELECT COUNT(*) FROM cards WHERE dueAt <= :now") fun dueCardCount(now: Long): Flow<Int>
    @Insert suspend fun insert(c: Card): Long
    @Update suspend fun update(c: Card)
    @Query("DELETE FROM cards WHERE id = :id") suspend fun deleteCard(id: Long)

    // ---- Görevler ----
    @Query("SELECT * FROM tasks ORDER BY done, dueAt") fun tasks(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun taskNow(id: Long): Task?
    @Query("SELECT * FROM tasks WHERE done = 0 AND remindMinutes >= 0") suspend fun tasksWithReminder(): List<Task>
    @Insert suspend fun insert(t: Task): Long
    @Update suspend fun update(t: Task)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: Long)

    // ---- Ders programı ----
    @Query("SELECT * FROM schedule ORDER BY dayOfWeek, startMinute") fun schedule(): Flow<List<ScheduleEntry>>
    @Insert suspend fun insert(e: ScheduleEntry): Long
    @Update suspend fun update(e: ScheduleEntry)
    @Query("DELETE FROM schedule WHERE id = :id") suspend fun deleteSchedule(id: Long)

    // ---- Çalışma ve soru kayıtları ----
    @Insert suspend fun insert(s: StudySession): Long
    @Query("SELECT * FROM sessions WHERE startAt >= :from ORDER BY startAt") fun sessionsSince(from: Long): Flow<List<StudySession>>
    @Query("SELECT subjectId, SUM(durationSec) AS total FROM sessions WHERE startAt >= :from GROUP BY subjectId ORDER BY total DESC")
    fun studyBySubject(from: Long): Flow<List<SubjectTotal>>

    @Insert suspend fun insert(q: QuestionLog): Long
    @Query("SELECT * FROM question_logs WHERE at >= :from ORDER BY at") fun logsSince(from: Long): Flow<List<QuestionLog>>
    @Query("""SELECT COALESCE(SUM(correct),0) AS correct, COALESCE(SUM(wrong),0) AS wrong, COALESCE(SUM(blank),0) AS blank
        FROM question_logs WHERE at >= :from AND at < :to AND ((:subjectId IS NULL AND subjectId IS NULL) OR subjectId = :subjectId)""")
    suspend fun questionTotals(from: Long, to: Long, subjectId: Long?): QuestionTotals

    // ---- Denemeler ----
    @Query("SELECT * FROM exam_results ORDER BY at") fun exams(): Flow<List<ExamResult>>
    @Insert suspend fun insert(e: ExamResult): Long
    @Update suspend fun update(e: ExamResult)
    @Query("DELETE FROM exam_results WHERE id = :id") suspend fun deleteExam(id: Long)
}

@Database(
    entities = [Subject::class, Note::class, Page::class, Mistake::class, Deck::class, Card::class,
        Task::class, ScheduleEntry::class, StudySession::class, QuestionLog::class, ExamResult::class],
    version = 1, exportSchema = true,
)
abstract class KaralaDb : RoomDatabase() {
    abstract fun dao(): KaralaDao

    companion object {
        const val NAME = "karala.db"

        val DEFAULT_SUBJECTS = listOf(
            "Matematik" to 0xFF4F8EF7, "Geometri" to 0xFF7C6CF2, "Fizik" to 0xFFF2994A,
            "Kimya" to 0xFF2DBE8D, "Biyoloji" to 0xFF6FCF4A, "Türkçe" to 0xFFEB5757,
            "Edebiyat" to 0xFFD65DB1, "Tarih" to 0xFFB8864B, "Coğrafya" to 0xFF26B5CE, "İngilizce" to 0xFFF2C94C,
        )

        fun build(ctx: Context): KaralaDb =
            Room.databaseBuilder(ctx, KaralaDb::class.java, NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_SUBJECTS.forEachIndexed { i, (name, color) ->
                            db.execSQL("INSERT INTO subjects (name, color, position) VALUES (?, ?, ?)",
                                arrayOf<Any>(name, color.toInt(), i))
                        }
                    }
                })
                .build()
    }
}
