package com.wolnerman.calories

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Logique pure du « Rappel du soir » (testable sans Android). */
object ReminderLogic {
    const val DEFAULT_HOUR = 20
    const val DEFAULT_MINUTE = 0

    /** Texte de la notification, ou null s'il ne faut rien envoyer (objectif atteint ou pas d'objectif). */
    fun message(totalKcal: Int, goal: Int): String? {
        if (goal <= 0) return null
        if (totalKcal >= goal) return null
        val missing = goal - totalKcal
        return "Il te manque $missing kcal pour atteindre ton objectif de $goal kcal aujourd'hui."
    }

    /** Prochain déclenchement : aujourd'hui à l'heure choisie si elle n'est pas passée, sinon demain. */
    fun nextTrigger(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        return if (now.isBefore(today)) today else today.plusDays(1)
    }

    fun todayTotal(entries: List<Entry>, today: LocalDate): Int = entries.filter { it.date == today.toString() }.sumOf { it.kcal }

    fun timeLabel(hour: Int, minute: Int) = String.format("%02d:%02d", hour, minute)
}
