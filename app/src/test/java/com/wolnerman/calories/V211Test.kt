package com.wolnerman.calories

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

/** v2.11 : graines courantes (Ciqual) dans la liste ; « grane Lin » trouve les graines de lin. */
class V211Test {
    private fun pick(name: String) = FoodFormLogic.pick(FoodFormLogic.type(FoodForm(), TextFieldValue(name), false), foodByName(name)!!)
    private fun eff(f: FoodForm) = KcalLogic.effective(f.kcal100, f.carbs100, f.prot100, f.fat100, f.fiber100, f.alcohol100)

    @Test fun typoGraneLinFindsLinseed() {
        assertEquals("Graines de lin", findFood("grane Lin")?.name)
        assertEquals("Graines de lin", findFood("lin")?.name)
        assertEquals("Graines de lin", findFood("graine de lin")?.name)
        assertEquals("Graines de lin", findFood("graines lin")?.name)
        assertEquals("Graines de lin", findFood("linseed")?.name)
        assertEquals("Graines de lin", findFood("flaxseed")?.name)
        assertTrue(suggestFoods("grane Lin").any { it.name == "Graines de lin" })
        assertEquals("Graines de lin brunes", findFood("lin brun")?.name)
        assertEquals("Courge butternut cuite", findFood("courge")?.name)   // pas les graines (alias « courge » inchangé)
    }

    @Test fun linseedKcalComputedFromCiqualMacrosAndFibres() {
        val lin = foodByName("Graines de lin")!!
        assertEquals("Ciqual 15034", lin.source)
        assertEquals(528, lin.kcalPer100g)          // fiche Ciqual (fibres à 2 kcal/g)
        assertEquals(6.5, lin.carbs!!, 0.05)
        assertEquals(19.0, lin.proteins!!, 0.05)
        assertEquals(40.3, lin.fat!!, 0.05)
        assertEquals(24.4, lin.nutrients["fiber"]!!, 0.05)
        val f = FoodFormLogic.pick(FoodForm(), lin)
        assertEquals("24.4", f.fiber100)            // fibres préremplies
        assertEquals(KcalLogic.totalCarbs(6.5, 24.4)!!, parseNum(f.carbs100)!!, 1e-9)  // G = glucides + fibres
        val r = eff(f)
        assertTrue(r.computed); assertTrue(r.fiberUsed)
        assertEquals(6.5, r.carbsUsed!!, 0.06)
        assertEquals(6.5 * 4 + 19.0 * 4 + 40.3 * 9, r.kcal100!!, 0.5)   // (G − fibres)×4 + P×4 + L×9 ≈ 465
        assertTrue(r.kcal100!! in 460.0..470.0)
        assertEquals(93, calculate(20.0, r.kcal100, r.carbsUsed, 19.0, 40.3)!!.kcal)  // 20 g ≈ 93 kcal
    }

    @Test fun chiaIsInTheListWithUserExampleMacros() {
        val chia = findFood("chia")!!
        assertEquals("Graines de chia", chia.name)
        assertEquals("Ciqual 15047", chia.source)
        assertEquals(16.5, chia.proteins!!, 0.05)
        assertEquals(30.7, chia.fat!!, 0.05)
        assertEquals(34.4, chia.nutrients["fiber"]!!, 0.05)
        val r = eff(FoodFormLogic.pick(FoodForm(), chia))
        // G Ciqual 7,7 + fibres 34,4 = 42,1 (l'exemple utilisateur avait G 42 → 372,7)
        assertEquals(373.1, r.kcal100!!, 0.2)
        assertEquals(71, calculate(19.0, r.kcal100, r.carbsUsed, 16.5, 30.7)!!.kcal)
    }

    @Test fun allNewSeedsPresentWithFibreAndCiqualSource() {
        val expected = mapOf(
            "Graines de lin" to "15034",
            "Graines de lin brunes" to "15052",
            "Graines de sésame" to "15010",
            "Graines de sésame décortiquées" to "15035",
            "Graines de tournesol" to "15011",
            "Graines de tournesol grillées salées" to "15045",
            "Graines de chia" to "15047",
            "Graines de chanvre (décortiquées)" to "15063",
            "Graines de pavot" to "11061",
            "Graines de courge" to "15064",
        )
        for ((name, code) in expected) {
            val f = foodByName(name)!!
            assertEquals("Ciqual $code", f.source)
            assertNotNull(name, f.nutrients["fiber"])
            assertNotNull(name, f.carbs); assertNotNull(name, f.proteins); assertNotNull(name, f.fat)
            val r = eff(FoodFormLogic.pick(FoodForm(), f))
            assertTrue(name, r.computed)
            assertEquals(name, findFood(name)?.name)
        }
        assertEquals("Graines de sésame", findFood("sesame")?.name)
        assertEquals("Graines de tournesol", findFood("tournesol")?.name)
        assertEquals("Graines de chanvre (décortiquées)", findFood("chanvre")?.name)
        assertEquals("Graines de pavot", findFood("pavot")?.name)
        assertEquals("Graines de courge", findFood("graines de courge")?.name)
        assertEquals("Graines de courge", findFood("pepita")?.name)
    }
}
