package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

/** v2.15 : objectifs Maintien / Perte / Prise, déficit-surplus, plancher, migration. */
class V215Test {
    private val woman = Profile(age = 48, weightKg = 69.0, heightCm = 168.0, male = false, activity = 3)
    private val energy get() = EnergyLogic.compute(woman)!!

    @Test fun herProfileTdee2310() {
        assertEquals(1339.0, energy.bmr, 0.05)
        assertEquals(2310, energy.tdee)
    }

    @Test fun maintainLossGainForHerProfile() {
        val e = energy
        val maint = EnergyLogic.plan(e, GoalPlan(EnergyTarget.MAINTAIN), male = false)
        assertEquals(2310, maint.kcal)
        assertFalse(maint.clamped)
        assertEquals("Objectif : 2310 kcal (maintien)", EnergyLogic.goalLine(maint))

        val loss400 = EnergyLogic.plan(e, GoalPlan(EnergyTarget.LOSS, 400), male = false)
        assertEquals(1910, loss400.kcal)                 // 2310 − 400
        assertFalse(loss400.clamped)
        assertEquals("Objectif : 1910 kcal (−400 par rapport au maintien)", EnergyLogic.goalLine(loss400))

        assertEquals(2010, EnergyLogic.plan(e, GoalPlan(EnergyTarget.LOSS, 300), false).kcal)
        assertEquals(1810, EnergyLogic.plan(e, GoalPlan(EnergyTarget.LOSS, 500), false).kcal)

        val gain = EnergyLogic.plan(e, GoalPlan(EnergyTarget.GAIN, 400), male = false)
        assertEquals(2710, gain.kcal)                    // 2310 + 400
        assertEquals("Objectif : 2710 kcal (+400 par rapport au maintien)", EnergyLogic.goalLine(gain))
    }

    @Test fun lossFloorIsMaxOfBmrAndSexMinimum() {
        assertEquals(1339, EnergyLogic.lossFloor(1339.0, male = false))   // BMR > 1200
        assertEquals(1500, EnergyLogic.lossFloor(1400.0, male = true))    // plancher homme 1500
        assertEquals(1600, EnergyLogic.lossFloor(1600.0, male = true))
        assertEquals(1200, EnergyLogic.lossFloor(1100.0, male = false))
        assertEquals(1200, EnergyLogic.lossFloor(null, male = false))
        assertEquals(1500, EnergyLogic.lossFloor(null, male = true))
        assertEquals(1200, EnergyLogic.lossFloor(null, male = null))

        // sédentaire : TDEE 1607, −400 = 1207 < BMR 1339 → clamp
        val sed = EnergyLogic.compute(woman.copy(activity = 0))!!
        assertEquals(1607, sed.tdee)                     // 1339 × 1,2 = 1606,8
        val loss = EnergyLogic.plan(sed, GoalPlan(EnergyTarget.LOSS, 400), male = false)
        assertEquals(1339, loss.kcal)
        assertTrue(loss.clamped)
        assertEquals(1339, loss.floor)
        assertTrue(EnergyLogic.lossFloorNote(1339, sed.bmr).contains("métabolisme de base"))
    }

    @Test fun customAndDeltaClamp() {
        val e = energy
        val custom = EnergyLogic.plan(e, GoalPlan(EnergyTarget.CUSTOM, 400, 2000), false)
        assertEquals(2000, custom.kcal)
        assertEquals("Objectif personnalisé : 2000 kcal", EnergyLogic.goalLine(custom))
        assertEquals(100, EnergyLogic.clampDelta(20))
        assertEquals(1000, EnergyLogic.clampDelta(5000))
        assertEquals(250, EnergyLogic.clampDelta(250))
        // un delta hors puces est accepté (puis clampé)
        assertEquals(2310 - 250, EnergyLogic.plan(e, GoalPlan(EnergyTarget.LOSS, 250), false).kcal)
    }

    @Test fun migrateRoughlyTdeeBecomesMaintain() {
        assertEquals(EnergyTarget.MAINTAIN, EnergyLogic.migrate(2310, 2310).target)
        assertEquals(EnergyTarget.MAINTAIN, EnergyLogic.migrate(2306, 2310).target)  // ancien objectif Mifflin 68,8 kg
        assertEquals(EnergyTarget.MAINTAIN, EnergyLogic.migrate(2260, 2310).target)  // −50
        assertEquals(EnergyTarget.CUSTOM, EnergyLogic.migrate(2259, 2310).target)
        val kept = EnergyLogic.migrate(2000, 2310)
        assertEquals(EnergyTarget.CUSTOM, kept.target)
        assertEquals(2000, kept.customKcal)
        // sans TDEE : on conserve le nombre
        assertEquals(EnergyTarget.CUSTOM, EnergyLogic.migrate(2000, null).target)
        assertEquals(2000, EnergyLogic.migrate(2000, null).customKcal)
    }

    @Test fun remainingVsTargetAndReminderUnchanged() {
        val (ok, overFlag) = EnergyLogic.remainingLine(1500, 1910, vsMaintain = false)
        assertEquals("Reste à consommer : 410 kcal", ok)
        assertFalse(overFlag)
        val over = EnergyLogic.remainingLine(2000, 1910, vsMaintain = false)
        assertEquals("Dépassement : 90 kcal au-delà de l'objectif", over.first)
        assertTrue(over.second)
        // rappel du soir : même formule, avec le nouvel objectif effectif
        assertEquals("Il te manque 410 kcal pour atteindre ton objectif de 1910 kcal aujourd'hui.",
            ReminderLogic.message(1500, 1910))
        assertNull(ReminderLogic.message(1910, 1910))
    }

    @Test fun recomputesWhenWeightChanges() {
        val plan = GoalPlan(EnergyTarget.LOSS, 400)
        val at69 = EnergyLogic.plan(energy, plan, false)
        val at71 = EnergyLogic.plan(EnergyLogic.compute(woman.copy(weightKg = 71.0))!!, plan, false)
        assertEquals(1910, at69.kcal)
        assertTrue(at71.kcal > at69.kcal)                // TDEE plus élevé → objectif perte aussi
        assertEquals(EnergyLogic.compute(woman.copy(weightKg = 71.0))!!.tdee - 400, at71.kcal)
    }
}
