package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** v2.17 : repères G/P/L et messages des rappels matin / soir. */
class V217Test {

    private val womanKg = 69.0
    /** Perte −400 → 1910 kcal ; protéines 69 × 1,4 = 96,6 → 96 g. */
    private val targets = MacroGoalLogic.targets(1910, womanKg)

    @Test fun fatThirtyPercentAndCarbRemainder() {
        assertEquals(96, targets.proteinG)
        // 1910 × 30 % / 9 = 63,666… → 64 g (plancher 0,8 × 69 = 55,2, donc le 30 % l'emporte)
        assertEquals(64, targets.fatG)
        // reste (1910 − 96×4 − 64×9) / 4 = 237,5 → 238 g
        assertEquals(238, targets.carbsG)
        assertEquals(1910, targets.kcal)
        val macroKcal = targets.carbsG * 4 + targets.proteinG * 4 + targets.fatG * 9
        assertEquals(1912, macroKcal) // +2 kcal dus à l'arrondi au gramme
    }

    @Test fun fatFloorAtLowKcal() {
        // 1000 kcal : 30 % → 33,3 g, plancher 0,8 × 69 = 55,2 → 55 g
        val t = MacroGoalLogic.targets(1000, womanKg)
        assertEquals(96, t.proteinG)
        assertEquals(55, t.fatG)
        assertEquals(30, t.carbsG) // (1000 − 384 − 495) / 4 = 30,25 → 30
    }

    @Test fun noWeightUsesDefaultProteinAndNoFatFloor() {
        val t = MacroGoalLogic.targets(1910, null)
        assertEquals(96, t.proteinG)          // défaut v2.7
        assertEquals(64, t.fatG)              // 30 % seulement
        assertEquals(238, t.carbsG)
        assertEquals(0, MacroGoalLogic.targets(0, 69.0).carbsG)
        assertEquals(0, MacroGoalLogic.targets(0, 69.0).fatG)
        assertEquals(96, MacroGoalLogic.targets(0, 69.0).proteinG)
    }

    @Test fun carbsNeverNegative() {
        // objectif très bas : protéines 96×4 + lipides plancher dépassent déjà les kcal
        val t = MacroGoalLogic.targets(700, womanKg)
        assertEquals(96, t.proteinG)
        assertEquals(55, t.fatG)
        assertEquals(0, t.carbsG)
        assertTrue(t.proteinG * 4 + t.fatG * 9 > 700)
    }

    @Test fun morningListsAllTargets() {
        assertEquals(
            "Objectif du jour : 1910 kcal – Glucides : 238 g – Protéines : 96 g – Lipides : 64 g",
            ReminderLogic.morningMessage(targets))
        assertNull(ReminderLogic.morningMessage(MacroGoalLogic.targets(0, womanKg)))
    }

    @Test fun eveningListsOnlyWhatIsMissing() {
        val empty = Macros(0.0, 0.0, 0.0)
        assertEquals(
            "Il te manque 1910 kcal – Glucides : 238 g – Protéines : 96 g – Lipides : 64 g",
            ReminderLogic.eveningMessage(0, targets, empty))
        // Exemple du cahier : 450 kcal, 40 g G, 30 g P, 10 g L encore à faire
        val mid = Macros(198.0, 66.0, 54.0)
        assertEquals(
            "Il te manque 450 kcal – Glucides : 40 g – Protéines : 30 g – Lipides : 10 g",
            ReminderLogic.eveningMessage(1460, targets, mid))
        // kcal et lipides atteints, protéines et glucides encore en retrait
        assertEquals(
            "Il te manque Glucides : 40 g – Protéines : 30 g",
            ReminderLogic.eveningMessage(2000, targets, Macros(198.0, 66.0, 70.0)))
        // uniquement les protéines
        assertEquals(
            "Il te manque Protéines : 30 g",
            ReminderLogic.eveningMessage(1910, targets, Macros(238.0, 66.0, 64.0)))
    }

    @Test fun eveningSilentWhenEverythingReached() {
        assertNull(ReminderLogic.eveningMessage(1910, targets, Macros(238.0, 96.0, 64.0)))
        assertNull(ReminderLogic.eveningMessage(2100, targets, Macros(300.0, 120.0, 80.0)))
        assertNull(ReminderLogic.eveningMessage(100, MacroGoalLogic.targets(0, womanKg), Macros(0.0, 10.0, 0.0)))
        assertTrue(ReminderLogic.reachedMessage(targets, 1910).contains("1910 kcal"))
    }

    @Test fun kcalOnlySignatureStillWorks() {
        assertEquals("Il te manque 410 kcal", ReminderLogic.message(1500, 1910))
        assertNull(ReminderLogic.message(1910, 1910))
        assertNull(ReminderLogic.message(500, 0))
    }

    @Test fun remainingGramsRoundConsumed() {
        assertEquals(1, MacroGoalLogic.remainingG(95.4, 96))   // 95 g saisis
        assertEquals(0, MacroGoalLogic.remainingG(95.5, 96))   // arrondi à 96
        assertTrue(MacroGoalLogic.reached(95.5, 96))
        assertFalse(MacroGoalLogic.reached(95.4, 96))
        assertEquals(0, MacroGoalLogic.remainingG(10.0, 0))
    }

    @Test fun todayMacrosOnlyToday() {
        fun e(date: String, c: Double, p: Double, f: Double, k: Double = 100.0) =
            Entry(1, date, "12:00", "x", 100.0, k, null, c, p, f)
        val list = listOf(
            e("2026-11-25", 10.0, 20.0, 5.0),
            e("2026-11-24", 99.0, 99.0, 99.0),
            e("2026-11-25", 5.0, 6.0, 1.0),
        )
        val m = ReminderLogic.todayMacros(list, LocalDate.of(2026, 11, 25))
        assertEquals(15.0, m.carbs, 0.0)
        assertEquals(26.0, m.proteins, 0.0)
        assertEquals(6.0, m.fat, 0.0)
        assertEquals(LocalDateTime.of(2026, 11, 26, 8, 0),
            ReminderLogic.nextTrigger(LocalDateTime.of(2026, 11, 25, 8, 0), 8, 0))
        assertEquals(LocalDateTime.of(2026, 11, 25, 8, 0),
            ReminderLogic.nextTrigger(LocalDateTime.of(2026, 11, 25, 7, 59), 8, 0))
    }

    @Test fun followsEffectiveKcalTarget() {
        // femme 48 ans, 69 kg, 168 cm, ×1,725 → TDEE 2310, perte −400 = 1910 (v2.15)
        val p = Profile(age = 48, weightKg = 69.0, heightCm = 168.0, male = false, activity = 3)
        val energy = EnergyLogic.compute(p)!!
        val loss = EnergyLogic.plan(energy, GoalPlan(EnergyTarget.LOSS, 400), false)
        val maintain = EnergyLogic.plan(energy, GoalPlan(EnergyTarget.MAINTAIN), false)
        val gain = EnergyLogic.plan(energy, GoalPlan(EnergyTarget.GAIN, 400), false)
        assertEquals(1910, loss.kcal)
        assertEquals(2310, maintain.kcal)
        assertEquals(2710, gain.kcal)
        val tLoss = MacroGoalLogic.targets(loss.kcal, 69.0)
        val tMaint = MacroGoalLogic.targets(maintain.kcal, 69.0)
        val tGain = MacroGoalLogic.targets(gain.kcal, 69.0)
        assertEquals(96, tLoss.proteinG)
        assertEquals(96, tMaint.proteinG)
        assertEquals(96, tGain.proteinG)            // protéines liées au poids, pas au mode
        assertTrue(tMaint.carbsG > tLoss.carbsG)
        assertTrue(tGain.carbsG > tMaint.carbsG)
        assertEquals(77, tMaint.fatG)               // 2310 × 0,3 / 9 = 77
        assertEquals(90, tGain.fatG)                // 2710 × 0,3 / 9 = 90,333… → 90
    }
}
