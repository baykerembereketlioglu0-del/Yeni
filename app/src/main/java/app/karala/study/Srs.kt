package app.karala.study

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Aralıklı tekrar (SM-2 tabanlı). Hem bilgi kartları hem hatalı sorular için kullanılır.
 * Bir tekrar sonunda bir sonraki tekrar tarihini hesaplar.
 */
object Srs {
    const val DAY_MS = 86_400_000L

    enum class Grade { AGAIN, HARD, GOOD, EASY }

    data class State(val intervalDays: Int = 0, val ease: Float = 2.5f, val reps: Int = 0, val lapses: Int = 0)

    data class Result(val state: State, val dueAt: Long)

    fun review(s: State, grade: Grade, now: Long): Result {
        var ease = s.ease; var reps = s.reps; var lapses = s.lapses
        val interval: Int
        when (grade) {
            Grade.AGAIN -> {
                ease = max(1.3f, ease - 0.2f); reps = 0; lapses++
                // Aynı oturumda tekrar gelsin: 10 dakika sonra.
                return Result(State(0, ease, reps, lapses), now + 10 * 60_000L)
            }
            Grade.HARD -> {
                ease = max(1.3f, ease - 0.15f)
                interval = if (reps == 0) 1 else max(s.intervalDays + 1, (s.intervalDays * 1.2f).roundToInt())
                reps++
            }
            Grade.GOOD -> {
                interval = when (reps) { 0 -> 1; 1 -> 3; else -> max(s.intervalDays + 1, (s.intervalDays * ease).roundToInt()) }
                reps++
            }
            Grade.EASY -> {
                ease += 0.15f
                interval = when (reps) { 0 -> 3; 1 -> 7; else -> max(s.intervalDays + 2, (s.intervalDays * ease * 1.3f).roundToInt()) }
                reps++
            }
        }
        return Result(State(interval, ease, reps, lapses), now + interval * DAY_MS)
    }

    /** Buton üzerinde gösterilecek tahmini aralık yazısı. */
    fun preview(s: State, grade: Grade): String {
        val r = review(s, grade, 0L)
        if (grade == Grade.AGAIN) return "10 dk"
        val d = r.state.intervalDays
        return when {
            d < 30 -> "$d gün"
            d < 365 -> "${(d / 30f).roundToInt()} ay"
            else -> "${"%.1f".format(d / 365f)} yıl"
        }
    }
}

/** Sınav neti: doğru − yanlış / bölen (YKS/TYT/AYT: 4, LGS: 3, ceza yoksa 0). */
fun net(correct: Int, wrong: Int, divisor: Int): Float =
    if (divisor <= 0) correct.toFloat() else correct - wrong / divisor.toFloat()
