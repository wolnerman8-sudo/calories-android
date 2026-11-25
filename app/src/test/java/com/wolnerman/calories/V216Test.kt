package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

/** v2.16 : huiles Ciqual 2025 ; « huile X » trouve l'huile, sans écraser lin / olive / coco / noix / tournesol. */
class V216Test {

    @Test fun typoAndAliasesFindTheOil() {
        assertEquals("Huile de lin", findFood("huile de lin")?.name)
        assertEquals("Huile de lin", findFood("huile lin")?.name)
        assertEquals("Huile de lin", findFood("huille de lin")?.name)
        assertEquals("Huile d'olive", findFood("huile d olive")?.name)
        assertEquals("Huile d'olive", findFood("huile d'olive")?.name)
        assertEquals("Huile de colza", findFood("huile de colsa")?.name)
        assertEquals("Huile de colza", findFood("huile colza")?.name)
        assertEquals("Huile de coco", findFood("huile coco")?.name)
        assertEquals("Huile de coco", findFood("huile coprah")?.name)
        assertEquals("Huile de tournesol", findFood("huile de tournesol")?.name)
        assertEquals("Huile de sésame", findFood("huile de sesame")?.name)
        assertEquals("Huile de noix", findFood("huile de noix")?.name)
        assertEquals("Huile de pépins de raisin", findFood("huile pepins de raisin")?.name)
        assertEquals("Huile combinée (mélange)", findFood("isio")?.name)
        assertEquals("Huile d'argan", findFood("huile argan")?.name)
    }

    @Test fun plainNamesStillFindFruitOrSeeds() {
        assertEquals("Olives", findFood("olive")?.name)
        assertEquals("Graines de lin", findFood("lin")?.name)
        assertEquals("Noix de coco (chair fraîche)", findFood("coco")?.name)
        assertEquals("Noix (cerneaux)", findFood("noix")?.name)
        assertEquals("Graines de tournesol", findFood("tournesol")?.name)
        assertEquals("Avocat", findFood("avocat")?.name)
        assertEquals("Graines de sésame", findFood("sesame")?.name)
        assertEquals("Graines de chanvre (décortiquées)", findFood("chanvre")?.name)
        assertEquals("Amandes", findFood("amande")?.name)
        assertEquals("Noisettes", findFood("noisette")?.name)
    }

    @Test fun flaxOilMatchesCiqualAndUserExample() {
        val oil = foodByName("Huile de lin")!!
        assertEquals("Ciqual 17180", oil.source)
        assertEquals(900, oil.kcalPer100g)
        assertEquals(0.0, oil.carbs!!, 0.05)          // Ciqual : 0,018 g, arrondi à 0
        assertEquals(0.0, oil.proteins!!, 0.05)
        assertEquals(100.0, oil.fat!!, 0.05)
        assertEquals(0.0, oil.nutrients["fiber"] ?: 0.0, 0.05)
        val form = FoodFormLogic.pick(FoodForm(), oil)
        val r = KcalLogic.effective(form.kcal100, form.carbs100, form.prot100, form.fat100, form.fiber100, form.alcohol100)
        assertTrue(r.computed)
        assertEquals(900.0, r.kcal100!!, 0.05)        // L×9 = 900
        assertEquals(90, calculate(10.0, r.kcal100, r.carbsUsed, 0.0, 100.0)!!.kcal)  // 1 c. à soupe
    }

    @Test fun allNewAndExistingOilsPresent() {
        val expected = listOf(
            "Huile d'olive" to "17270", "Huile de tournesol" to "17440", "Huile de colza" to "17130",
            "Huile d'arachide" to "17040", "Huile de coco" to "16040", "Huile de lin" to "17180",
            "Huile de sésame" to "17400", "Huile de noix" to "17220", "Huile de pépins de raisin" to "17350",
            "Huile de maïs" to "17190", "Huile de soja" to "17420", "Huile d'avocat" to "17100",
            "Huile de noisette" to "17210", "Huile de palme" to "16150", "Huile de germe de blé" to "17110",
            "Huile combinée (mélange)" to "17700", "Huile olive et graines (mélange)" to "17701",
            "Huile de chanvre" to "17850", "Huile d'argan" to "17900", "Huile de pavot" to "17325",
            "Huile végétale (moyenne)" to "17001", "Huile d'amande" to "17030",
        )
        assertEquals(22, expected.size)
        for ((name, code) in expected) {
            val f = foodByName(name)!!
            assertEquals(name, "Ciqual $code", f.source)
            assertEquals(name, 10.0, f.unitGrams!!, 0.0)
            assertEquals(name, "c. à soupe", f.unitLabel)
            assertNotNull(name, f.fat)
            assertEquals(name, findFood(name)?.name)
        }
        assertTrue(BUILTIN_FOODS.none { it.name.contains("carthame", ignoreCase = true) })
        assertTrue(BUILTIN_FOODS.none { it.name.contains("cameline", ignoreCase = true) })
    }
}
