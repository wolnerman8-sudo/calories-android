package com.wolnerman.calories

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/** Bilan d'une journée ; kcal == null = jour sans aucune saisie (trou dans les graphiques, pas zéro). */
data class DayStat(
    val date: LocalDate,
    val kcal: Int?,
    val carbs: Double?,
    val proteins: Double?,
    val fat: Double?,
    val count: Int,
) {
    val logged: Boolean get() = kcal != null
}

/** Moyenne d'une semaine (jours renseignés seulement). */
data class WeekStat(val start: LocalDate, val end: LocalDate, val avgKcal: Int?, val daysLogged: Int)

/** Chiffres clés d'une période. */
data class PeriodSummary(
    val days: Int,
    val daysLogged: Int,
    val avgKcal: Int?,        // moyenne sur les jours renseignés
    val daysInGoal: Int,      // jours renseignés avec total ≤ objectif
    val daysOver: Int,        // jours renseignés au-dessus de l'objectif
    val maxKcal: Int?,
    val avgCarbs: Double?, val avgProteins: Double?, val avgFat: Double?,
)

object Stats {
    val PERIODS = listOf(1, 2, 4, 8, 12) // semaines

    /**
     * Fenêtre affichée. 1 semaine : du lundi au dimanche (semaine calendaire, décalée de [offset] semaines).
     * Sinon : les [weeks]×7 derniers jours jusqu'à aujourd'hui, décalés de [offset] périodes entières.
     */
    fun window(weeks: Int, offset: Int, today: LocalDate): Pair<LocalDate, LocalDate> =
        if (weeks == 1) {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(offset.toLong())
            monday to monday.plusDays(6)
        } else {
            val end = today.minusDays(offset.toLong() * weeks * 7)
            end.minusDays(weeks * 7L - 1) to end
        }

    /** Une entrée par jour de [start] à [end] inclus. Les macros sont null si aucune entrée du jour n'en a. */
    fun daily(entries: List<Entry>, start: LocalDate, end: LocalDate, extraKcalByDay: Map<String, Int> = emptyMap()): List<DayStat> {
        val byDay = entries.groupBy { it.date }
        val out = ArrayList<DayStat>()
        var d = start
        while (!d.isAfter(end)) {
            val key = d.toString()
            val list = byDay[key].orEmpty()
            val extra = extraKcalByDay[key] ?: 0
            out += if (list.isEmpty() && extra <= 0) DayStat(d, null, null, null, null, 0) else {
                val withMacros = list.filter { it.hasMacros }
                DayStat(d, list.sumOf { it.kcal } + extra,
                    if (withMacros.isEmpty()) null else list.sumOf { it.carbs },
                    if (withMacros.isEmpty()) null else list.sumOf { it.proteins },
                    if (withMacros.isEmpty()) null else list.sumOf { it.fat },
                    list.size)
            }
            d = d.plusDays(1)
        }
        return out
    }

    /** Moyennes par tranche de 7 jours depuis le début de la série. */
    fun weekly(days: List<DayStat>): List<WeekStat> = days.chunked(7).map { w ->
        val logged = w.mapNotNull { it.kcal }
        WeekStat(w.first().date, w.last().date, if (logged.isEmpty()) null else logged.average().roundToInt(), logged.size)
    }

    fun summary(days: List<DayStat>, goal: Int): PeriodSummary {
        val logged = days.filter { it.logged }
        val k = logged.map { it.kcal!! }
        fun avg(f: (DayStat) -> Double?) = logged.mapNotNull(f).takeIf { it.isNotEmpty() }?.average()
        return PeriodSummary(
            days = days.size, daysLogged = logged.size,
            avgKcal = if (k.isEmpty()) null else k.average().roundToInt(),
            daysInGoal = if (goal > 0) k.count { it <= goal } else 0,
            daysOver = if (goal > 0) k.count { it > goal } else 0,
            maxKcal = k.maxOrNull(),
            avgCarbs = avg { it.carbs }, avgProteins = avg { it.proteins }, avgFat = avg { it.fat },
        )
    }

    private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d/MM", Locale.FRENCH)
    /** « lun. », « mar. »… */
    fun dayName(d: LocalDate): String = d.format(SHORT_DAY)
    fun dayMonth(d: LocalDate): String = d.format(DAY_MONTH)
    fun longDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
}
