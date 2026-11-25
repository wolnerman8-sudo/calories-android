package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

/** v2.14 : TDEE Mifflin-St Jeor / Katch-McArdle, niveaux d'activité, reste à consommer. */
class V214Test {
    /** Femme, 48 ans, 69 kg, 168 cm, très actif (×1,725) — profil type de la demande. */
    private val woman = Profile(age = 48, weightKg = 69.0, heightCm = 168.0, male = false, activity = 3)

    @Test fun activityLabelsMigratedInPlace() {
        // Les coefficients et les index ne changent pas : « Actif ×1,725 » devient « Très actif »
        assertEquals(5, ACTIVITY_LEVELS.size)
        assertEquals("Sédentaire" to 1.2, ACTIVITY_LEVELS[0])
        assertEquals("Léger" to 1.375, ACTIVITY_LEVELS[1])
        assertEquals("Modéré" to 1.55, ACTIVITY_LEVELS[2])
        assertEquals("Très actif" to 1.725, ACTIVITY_LEVELS[3])
        assertEquals("Extrême" to 1.9, ACTIVITY_LEVELS[4])
        assertEquals("Très actif (×1,725)", EnergyLogic.activityLine(3))
        assertEquals("Extrême (×1,9)", EnergyLogic.activityLine(4))
        assertEquals("Sédentaire (×1,2)", EnergyLogic.activityLine(0))
    }

    @Test fun mifflinWoman48Active() {
        // BMR = 10×69 + 6,25×168 − 5×48 − 161 = 1339
        assertEquals(1339.0, EnergyLogic.mifflinBmr(69.0, 168.0, 48, false), 1e-9)
        val r = EnergyLogic.compute(woman)!!
        assertEquals(EnergyFormula.MIFFLIN, r.formula)
        assertEquals(1339.0, r.bmr, 0.05)
        assertEquals(1.725, r.activityFactor, 0.0)
        assertEquals(2310, r.tdee)                         // 1339 × 1,725 = 2309,775 → 2310
        assertEquals(1339.0, woman.bmr()!!, 0.05)
        assertEquals(2310, woman.suggestedGoal())
        assertEquals("Mifflin-St Jeor × Très actif (×1,725)", r.formulaLine)
        assertNull(r.leanMassKg)
    }

    @Test fun katchWoman48With30PercentFat() {
        val p = woman.copy(fatPct = 30.0)
        assertEquals(48.3, EnergyLogic.leanMass(69.0, 30.0), 1e-9)
        assertEquals(1413.28, EnergyLogic.katchBmr(69.0, 30.0), 0.005)  // 370 + 21,6 × 48,3
        val r = EnergyLogic.compute(p)!!
        assertEquals(EnergyFormula.KATCH, r.formula)
        assertEquals(1413.28, r.bmr, 0.01)
        assertEquals(48.3, r.leanMassKg!!, 1e-9)
        assertEquals(2438, r.tdee)                         // 1413,28 × 1,725 = 2437,908 → 2438
        assertEquals("Katch-McArdle × Très actif (×1,725)", r.formulaLine)
        // sans % MG on retombe sur Mifflin
        assertEquals(EnergyFormula.MIFFLIN, EnergyLogic.compute(woman)!!.formula)
    }

    @Test fun invalidFatFallsBackToMifflin() {
        for (bad in listOf(2.9, 0.0, 70.1, 80.0, 100.0, -1.0)) {
            val r = EnergyLogic.compute(woman.copy(fatPct = bad))!!
            assertEquals("fat=$bad", EnergyFormula.MIFFLIN, r.formula)
            assertEquals(2310, r.tdee)
        }
        assertNull(EnergyLogic.validFatPct(null))
        assertEquals(3.0, EnergyLogic.validFatPct(3.0)!!, 0.0)
        assertEquals(70.0, EnergyLogic.validFatPct(70.0)!!, 0.0)
        // % MG seul + poids suffisent pour Katch (âge/taille/sexe non requis)
        val onlyFat = Profile(weightKg = 69.0, fatPct = 30.0, activity = 3)
        assertEquals(EnergyFormula.KATCH, EnergyLogic.compute(onlyFat)!!.formula)
        // % MG invalide sans âge/taille/sexe → pas de calcul
        assertNull(EnergyLogic.compute(Profile(weightKg = 69.0, fatPct = 80.0, activity = 3)))
    }

    @Test fun remainingAndOvershoot() {
        assertEquals(810, EnergyLogic.remaining(1500, 2310))
        assertEquals("Reste à consommer : 810 kcal", EnergyLogic.remainingLine(1500, 2310).first)
        assertFalse(EnergyLogic.remainingLine(1500, 2310).second)
        assertEquals(0, EnergyLogic.remaining(2310, 2310))
        val over = EnergyLogic.remainingLine(2430, 2310)
        assertEquals("Dépassement : 120 kcal au-delà du maintien", over.first)
        assertTrue(over.second)
        assertEquals(-120, EnergyLogic.remaining(2430, 2310))
    }

    @Test fun fatFieldParsesFrenchComma() {
        assertEquals(30.0, parseNum("30")!!, 0.0)
        assertEquals(30.5, parseNum("30,5")!!, 0.0)
        assertEquals(30.5, parseNum("30.5")!!, 0.0)
        assertEquals("30", ProfileFmt.number(30.0))
        assertEquals("30,5", ProfileFmt.number(30.5))
    }
}
