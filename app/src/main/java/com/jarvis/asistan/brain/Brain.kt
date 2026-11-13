package com.jarvis.asistan.brain

/** Jarvis'in beyni: Claude veya Gemini. */
interface Brain {
    /** Kullanıcının sözüne/olaya cevap verir; cevabı seslendirir ve döndürür. */
    suspend fun respond(userText: String, image: String? = null): String

    /** Sohbet geçmişini temizler. */
    fun reset()

    /** Model sohbeti bitirmek istediyse true. */
    val endRequested: Boolean
}
