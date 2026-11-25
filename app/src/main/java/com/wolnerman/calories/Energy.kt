package com.wolnerman.calories

import java.util.Locale
import kotlin.math.roundToInt

/** Formule utilisée pour le BMR / TDEE. */
enum class EnergyFormula { MIFFLIN, KATCH }

/**
 * Plus tard : maintien / perte / prise. Pour l'instant seul [MAINTAIN] est utilisé ;
 * l'objectif quotidien reste [Storage.dailyGoal], indépendant du TDEE.
 */
enum class EnergyTarget { MAINTAIN, LOSS, GAIN }

/** Résultat du calcul d'énergie au repos (BMR) et de maintien (TDEE). */
data class EnergyResult(
    val bmr: Double,
    val tdee: Int,
    val formula: EnergyFormula,
    val activityIndex: Int,
    val activityFactor: Double,
    val leanMassKg: Double? = null,
    val fatPct: Double? = null,
) {
    val formulaName: String get() = when (formula) {
        EnergyFormula.MIFFLIN -> "Mifflin-St Jeor"
        EnergyFormula.KATCH -> "Katch-McArdle"
    }
    val activityLabel: String get() = ACTIVITY_LEVELS[activityIndex.coerceIn(0, ACTIVITY_LEVELS.lastIndex)].first
    /** Ex. « Mifflin-St Jeor × Très actif (×1,725) ». */
    val formulaLine: String get() = "$formulaName × $activityLabel (${EnergyLogic.fmtFactor(activityFactor)})"
}

object EnergyLogic {
    const val FAT_MIN = 3.0
    const val FAT_MAX = 70.0

    /** % de masse grasse utilisable pour Katch-McArdle, sinon null (on retombe sur Mifflin). */
    fun validFatPct(raw: Double?): Double? = raw?.takeIf { !it.isNaN() && it in FAT_MIN..FAT_MAX }

    fun mifflinBmr(weightKg: Double, heightCm: Double, age: Int, male: Boolean): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + if (male) 5.0 else -161.0

    /** Masse maigre (kg) = poids × (1 − %MG / 100). */
    fun leanMass(weightKg: Double, fatPct: Double): Double = weightKg * (1.0 - fatPct / 100.0)

    /** BMR Katch-McArdle = 370 + 21,6 × masse maigre. */
    fun katchBmr(weightKg: Double, fatPct: Double): Double = 370.0 + 21.6 * leanMass(weightKg, fatPct)

    fun activityFactor(index: Int): Double =
        ACTIVITY_LEVELS[index.coerceIn(0, ACTIVITY_LEVELS.lastIndex)].second

    /** ×1,2 / ×1,375 / ×1,55 / ×1,725 / ×1,9 (virgule française). */
    fun fmtFactor(f: Double): String =
        "×" + String.format(Locale.FRENCH, "%.3f", f).trimEnd('0').trimEnd(',')

    fun activityLine(index: Int): String {
        val i = index.coerceIn(0, ACTIVITY_LEVELS.lastIndex)
        val (label, f) = ACTIVITY_LEVELS[i]
        return "$label (${fmtFactor(f)})"
    }

    /**
     * TDEE de maintien. Katch-McArdle dès qu'un % MG valide (3–70) et un poids sont connus ;
     * sinon Mifflin-St Jeor (âge, poids, taille, sexe requis).
     */
    fun compute(p: Profile): EnergyResult? {
        val w = p.weightKg ?: return null
        val i = p.activity.coerceIn(0, ACTIVITY_LEVELS.lastIndex)
        val factor = activityFactor(i)
        val fat = validFatPct(p.fatPct)
        if (fat != null) {
            val bmr = katchBmr(w, fat)
            return EnergyResult(bmr, (bmr * factor).roundToInt(), EnergyFormula.KATCH, i, factor, leanMass(w, fat), fat)
        }
        val a = p.age ?: return null
        val h = p.heightCm ?: return null
        val m = p.male ?: return null
        val bmr = mifflinBmr(w, h, a, m)
        return EnergyResult(bmr, (bmr * factor).roundToInt(), EnergyFormula.MIFFLIN, i, factor)
    }

    fun remaining(consumed: Int, tdee: Int): Int = tdee - consumed

    /**
     * Texte du reste par rapport au maintien. [second] = dépassement (à afficher en rouge).
     */
    fun remainingLine(consumed: Int, tdee: Int): Pair<String, Boolean> {
        val r = remaining(consumed, tdee)
        return if (r >= 0) "Reste à consommer : $r kcal" to false
        else "Dépassement : ${-r} kcal au-delà du maintien" to true
    }
}
