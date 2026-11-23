package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

/** v2.7 : objectif protéines, barre, répartition et doses de whey. */
class ProteinTest {
    @Test fun goalFromWeight() {
        assertEquals(96, ProteinLogic.proteinGoal(null))
        assertEquals(96, ProteinLogic.proteinGoal(68.8))           // 96,32 → 96
        assertEquals(97, ProteinLogic.proteinGoal(69.3))           // 97,02 → 97
        assertEquals(50, ProteinLogic.proteinGoal(30.0))           // 42 → minimum 50
        assertEquals(103, ProteinLogic.proteinGoal(68.8, 1.5))
        assertEquals(96, ProteinLogic.proteinGoal(0.0))
    }

    @Test fun currentWeightPrefersProfile() {
        assertEquals(69.3, ProteinLogic.currentWeight(69.3, 68.8)!!, 0.0)
        assertEquals(68.8, ProteinLogic.currentWeight(null, 68.8)!!, 0.0)
        assertNull(ProteinLogic.currentWeight(null, null))
    }

    @Test fun progressAndColourThreshold() {
        assertEquals(0.5f, ProteinLogic.progress(48.0, 96), 1e-6f)
        assertEquals(1f, ProteinLogic.progress(150.0, 96), 0f)       // borné à 1
        assertEquals(0f, ProteinLogic.progress(10.0, 0), 0f)
        assertTrue(ProteinLogic.onTrack(81.6, 96))                   // 85 % pile → vert
        assertFalse(ProteinLogic.onTrack(81.5, 96))
    }

    @Test fun distributionSlots() {
        assertEquals(listOf("Petit-déjeuner" to "20-25 g", "Déjeuner" to "25-35 g", "Collation" to "10-15 g", "Dîner" to "25-35 g"), ProteinLogic.SLOTS)
    }

    @Test fun wheyDoses() {
        val w24 = ProteinLogic.wheyEntry(24, 1L, "2026-11-23", "16:20")
        assertEquals("Whey (1 dose)", w24.name)
        assertEquals(30.0, w24.grams, 1e-9)
        assertEquals(24.0, w24.proteins, 1e-9)
        assertEquals(120, w24.kcal)
        assertEquals(2.0, w24.carbs, 0.01); assertEquals(1.5, w24.fat, 0.01)
        val w25 = ProteinLogic.wheyEntry(25, 2L, "2026-11-23", "16:21")
        assertEquals(25.0, w25.proteins, 1e-9)
        assertEquals(125, w25.kcal)
        // l'entrée suit la logique de repas habituelle (heure → type de repas) et se supprime comme les autres
        assertEquals(HealthMapping.mealType("16:20"), HealthMapping.toRecord(w24).mealType)
        assertEquals(24.0, listOf(w24).macros().proteins, 1e-9)
    }
}
