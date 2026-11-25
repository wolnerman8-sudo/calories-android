package com.wolnerman.calories

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formule utilisée pour le BMR / TDEE. */
enum class EnergyFormula { MIFFLIN, KATCH }

/** Objectif calorique : maintien, déficit, surplus, ou un nombre choisi à la main. */
enum class EnergyTarget {
    MAINTAIN, LOSS, GAIN, CUSTOM;

    val title: String get() = when (this) {
        MAINTAIN -> "Maintien du poids"
        LOSS -> "Perte de poids"
        GAIN -> "Prise de poids"
        CUSTOM -> "Personnalisé"
    }
    val shortTitle: String get() = when (this) {
        MAINTAIN -> "Maintien"
        LOSS -> "Perte"
        GAIN -> "Prise"
        CUSTOM -> "Perso"
    }
}

/** Réglage persisté : mode, déficit/surplus (kcal), et nombre manuel si [CUSTOM]. */
data class GoalPlan(
    val target: EnergyTarget = EnergyTarget.MAINTAIN,
    val delta: Int = EnergyLogic.DEFAULT_DELTA,
    val customKcal: Int? = null,
)

/** Objectif effectif, recalculé à partir du TDEE dès que le profil change (sauf mode personnalisé). */
data class GoalOutcome(
    val kcal: Int,
    val plan: GoalPlan,
    val energy: EnergyResult?,
    val clamped: Boolean = false,
    val floor: Int? = null,
) {
    val tdee: Int? get() = energy?.tdee
}

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

    val DELTAS = listOf(300, 400, 500)
    const val DEFAULT_DELTA = 400
    const val DELTA_MIN = 100
    const val DELTA_MAX = 1000
    /** Écart (kcal) en dessous duquel un ancien objectif est considéré « ≈ TDEE » à la migration. */
    const val MIGRATE_TOLERANCE = 50
    const val FLOOR_WOMAN = 1200
    const val FLOOR_MAN = 1500

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

    fun clampDelta(d: Int) = d.coerceIn(DELTA_MIN, DELTA_MAX)

    /**
     * Plancher de sécurité en perte : le plus élevé entre le BMR et 1 200 kcal (femme ou sexe inconnu)
     * ou 1 500 kcal (homme), pour éviter un déficit trop agressif.
     */
    fun lossFloor(bmr: Double?, male: Boolean?): Int {
        val sexFloor = if (male == true) FLOOR_MAN else FLOOR_WOMAN
        val b = bmr?.roundToInt()
        return if (b != null) maxOf(b, sexFloor) else sexFloor
    }

    fun lossFloorNote(floor: Int, bmr: Double?): String {
        val b = bmr?.roundToInt()
        return if (b != null && floor == b)
            "Plancher de sécurité : $floor kcal (ton métabolisme de base). Le déficit a été réduit pour ne pas descendre en dessous."
        else
            "Plancher de sécurité : $floor kcal (1 200 kcal pour une femme, 1 500 pour un homme). Le déficit a été réduit pour ne pas descendre en dessous."
    }

    /**
     * Migration v2.15 : si l'ancien objectif est à peu près le TDEE (±50 kcal) → Maintien ;
     * sinon on le garde en personnalisé, pour ne rien changer en silence.
     */
    fun migrate(dailyGoal: Int, tdee: Int?): GoalPlan =
        if (tdee != null && dailyGoal > 0 && abs(dailyGoal - tdee) <= MIGRATE_TOLERANCE)
            GoalPlan(EnergyTarget.MAINTAIN, DEFAULT_DELTA)
        else GoalPlan(EnergyTarget.CUSTOM, DEFAULT_DELTA, dailyGoal.takeIf { it > 0 })

    /** Objectif effectif selon le mode. Recalculé dès que le TDEE change, sauf en personnalisé. */
    fun plan(energy: EnergyResult?, settings: GoalPlan, male: Boolean?): GoalOutcome {
        val delta = clampDelta(settings.delta)
        val s = settings.copy(delta = delta)
        val tdee = energy?.tdee
        val fallback = s.customKcal?.takeIf { it > 0 } ?: 0
        return when (s.target) {
            EnergyTarget.CUSTOM -> GoalOutcome(fallback, s, energy)
            EnergyTarget.MAINTAIN -> GoalOutcome(tdee ?: fallback, s, energy)
            EnergyTarget.GAIN -> GoalOutcome(if (tdee != null) tdee + delta else fallback, s, energy)
            EnergyTarget.LOSS -> {
                if (tdee == null) GoalOutcome(fallback, s, energy)
                else {
                    val raw = tdee - delta
                    val floor = lossFloor(energy.bmr, male)
                    val kcal = maxOf(raw, floor)
                    GoalOutcome(kcal, s, energy, clamped = kcal > raw, floor = floor)
                }
            }
        }
    }

    /** Libellé de l'objectif (ex. « 1910 kcal (−400 par rapport au maintien) »). */
    fun goalLine(o: GoalOutcome): String {
        val t = o.tdee
        return when (o.plan.target) {
            EnergyTarget.MAINTAIN -> "Objectif : ${o.kcal} kcal (maintien)"
            EnergyTarget.LOSS -> if (t != null)
                "Objectif : ${o.kcal} kcal (−${t - o.kcal} par rapport au maintien)"
            else "Objectif : ${o.kcal} kcal (perte)"
            EnergyTarget.GAIN -> if (t != null)
                "Objectif : ${o.kcal} kcal (+${o.kcal - t} par rapport au maintien)"
            else "Objectif : ${o.kcal} kcal (prise)"
            EnergyTarget.CUSTOM -> "Objectif personnalisé : ${o.kcal} kcal"
        }
    }

    fun remaining(consumed: Int, target: Int): Int = target - consumed

    /**
     * Texte du reste. [second] = dépassement (à afficher en rouge).
     * [vsMaintain] = vrai pour le libellé « au-delà du maintien » (rétrocompatibilité v2.14).
     */
    fun remainingLine(consumed: Int, target: Int, vsMaintain: Boolean = true): Pair<String, Boolean> {
        val r = remaining(consumed, target)
        val over = if (vsMaintain) "au-delà du maintien" else "au-delà de l'objectif"
        return if (r >= 0) "Reste à consommer : $r kcal" to false
        else "Dépassement : ${-r} kcal $over" to true
    }
}
