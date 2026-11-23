package com.wolnerman.calories

import androidx.health.connect.client.records.MealType
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class HealthMappingTest {
    private val zone = ZoneId.of("America/Martinique")

    @Test fun typeDeRepas() {
        assertEquals(MealType.MEAL_TYPE_BREAKFAST, HealthMapping.mealType("07:15"))
        assertEquals(MealType.MEAL_TYPE_LUNCH, HealthMapping.mealType("12:30"))
        assertEquals(MealType.MEAL_TYPE_SNACK, HealthMapping.mealType("16:00"))
        assertEquals(MealType.MEAL_TYPE_DINNER, HealthMapping.mealType("19:45"))
        assertEquals(MealType.MEAL_TYPE_SNACK, HealthMapping.mealType("02:00"))
        assertEquals(MealType.MEAL_TYPE_UNKNOWN, HealthMapping.mealType("??"))
    }

    @Test fun entreeAvecMacros() {
        val e = Entry(1763900000000, "2026-11-23", "12:30", "Riz blanc cuit", 200.0, 130.0, null, 28.0, 2.7, 0.3)
        val r = HealthMapping.toRecord(e, zone)
        assertEquals(260.0, r.energy!!.inKilocalories, 1e-6)
        assertEquals(56.0, r.totalCarbohydrate!!.inGrams, 1e-6)
        assertEquals(5.4, r.protein!!.inGrams, 1e-6)
        assertEquals(0.6, r.totalFat!!.inGrams, 1e-6)
        assertEquals("Riz blanc cuit (200 g)", r.name)
        assertEquals(MealType.MEAL_TYPE_LUNCH, r.mealType)
        assertEquals("calories-1763900000000", r.metadata.clientRecordId)
        assertEquals(java.time.ZoneOffset.ofHours(-4), r.startZoneOffset)
        assertEquals("2026-11-23T16:30:00Z", r.startTime.toString())
        assertTrue(r.endTime.isAfter(r.startTime))
    }

    @Test fun entreeSansMacros() {
        val e = Entry(5, "2026-11-23", "20:00", "Accras", 75.5, 250.0)
        val r = HealthMapping.toRecord(e, zone)
        assertEquals(188.75, r.energy!!.inKilocalories, 1e-6)
        assertNull(r.totalCarbohydrate); assertNull(r.protein); assertNull(r.totalFat)
        assertEquals("Accras (75,5 g)", r.name)
        assertEquals(MealType.MEAL_TYPE_DINNER, r.mealType)
    }
}
