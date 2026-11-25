package com.wolnerman.calories

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Logique pure des rappels du matin (objectifs) et du soir (ce qu'il manque). */
object ReminderLogic {
    const val DEFAULT_HOUR = 20
    const val DEFAULT_MINUTE = 0
    const val DEFAULT_MORNING_HOUR = 8
    const val DEFAULT_MORNING_MINUTE = 0

    /** Rappel du matin : objectifs du jour. Null s'il n'y a pas d'objectif kcal. */
    fun morningMessage(targets: MacroTargets): String? {
        if (targets.kcal <= 0) return null
        return "Objectif du jour : ${targets.kcal} kcal – Glucides : ${targets.carbsG} g – Protéines : ${targets.proteinG} g – Lipides : ${targets.fatG} g"
    }

    /**
     * Rappel du soir : ce qu'il reste à atteindre. Les macros déjà atteintes sont omises.
     * Null si l'objectif kcal est indéfini, ou si kcal et macros sont tous atteints
     * (pas de notification ; le bouton Tester affiche alors un message positif).
     */
    fun eveningMessage(consumedKcal: Int, targets: MacroTargets, consumed: Macros): String? {
        if (targets.kcal <= 0) return null
        val parts = mutableListOf<String>()
        val missKcal = targets.kcal - consumedKcal
        if (missKcal > 0) parts += "$missKcal kcal"
        val missC = MacroGoalLogic.remainingG(consumed.carbs, targets.carbsG)
        val missP = MacroGoalLogic.remainingG(consumed.proteins, targets.proteinG)
        val missF = MacroGoalLogic.remainingG(consumed.fat, targets.fatG)
        if (missC > 0) parts += "Glucides : $missC g"
        if (missP > 0) parts += "Protéines : $missP g"
        if (missF > 0) parts += "Lipides : $missF g"
        if (parts.isEmpty()) return null
        return "Il te manque " + parts.joinToString(" – ")
    }

    /** Message positif (bouton Tester, ou si on voulait notifier un succès). */
    fun reachedMessage(targets: MacroTargets, consumedKcal: Int): String {
        val kcalBit = if (targets.kcal > 0) "$consumedKcal / ${targets.kcal} kcal" else "$consumedKcal kcal"
        return "Objectif atteint ($kcalBit). Bravo !"
    }

    /**
     * Ancienne signature (kcal seulement), conservée pour les tests v2.2 / v2.15.
     * Sans macros, on ne rappelle que les kcal manquantes.
     */
    fun message(totalKcal: Int, goal: Int): String? {
        if (goal <= 0) return null
        if (totalKcal >= goal) return null
        return eveningMessage(totalKcal, MacroTargets(0, 0, 0, goal), Macros(0.0, 0.0, 0.0))
    }

    /** Prochain déclenchement : aujourd'hui à l'heure choisie si elle n'est pas passée, sinon demain. */
    fun nextTrigger(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        return if (now.isBefore(today)) today else today.plusDays(1)
    }

    fun todayTotal(entries: List<Entry>, today: LocalDate): Int = entries.filter { it.date == today.toString() }.sumOf { it.kcal }
    fun todayMacros(entries: List<Entry>, today: LocalDate): Macros = entries.filter { it.date == today.toString() }.macros()

    fun timeLabel(hour: Int, minute: Int) = String.format("%02d:%02d", hour, minute)
}
