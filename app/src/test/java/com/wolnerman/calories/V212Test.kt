package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

/** v2.12 : épices et herbes séchées d'après Ciqual 2025 ; fautes « canelle » / « epise » ; pas d'écrasement des aliments déjà là. */
class V212Test {
    private fun eff(f: Food) = KcalLogic.effective(
        f.kcalPer100g.toString(),
        FoodFormLogic.fill(FoodForm(), f).carbs100,
        FoodFormLogic.fill(FoodForm(), f).prot100,
        FoodFormLogic.fill(FoodForm(), f).fat100,
        FoodFormLogic.fill(FoodForm(), f).fiber100,
        f.alcohol?.takeIf { it > 0 },
    )

    @Test fun typoCanelleAndEpice() {
        assertEquals("Cannelle (poudre)", findFood("canelle")?.name)
        assertEquals("Cannelle (poudre)", findFood("cannelle")?.name)
        assertEquals("Cannelle (poudre)", findFood("cannelle moulue")?.name)
        assertEquals("Cannelle (poudre)", findFood("cinnamon")?.name)
        assertEquals("Épices (moyenne)", findFood("epise")?.name)
        assertEquals("Épices (moyenne)", findFood("epice")?.name)
        assertEquals("Épices (moyenne)", findFood("épices")?.name)
        assertTrue(suggestFoods("canelle").any { it.name == "Cannelle (poudre)" })
    }

    @Test fun doesNotHijackExistingFoods() {
        assertEquals("Gingembre frais", findFood("gingembre")?.name)
        assertEquals("Gingembre moulu", findFood("gingembre moulu")?.name)
        assertEquals("Gingembre moulu", findFood("gingembre en poudre")?.name)
        assertEquals("Poulet curry coco", findFood("curry")?.name)
        assertEquals("Curry (poudre)", findFood("curry en poudre")?.name)
        assertEquals("Curry (poudre)", findFood("poudre de curry")?.name)
        assertEquals("Colombo de poulet", findFood("colombo")?.name)
        assertEquals("Piment (cru)", findFood("piment")?.name)
        assertEquals("Piment de Cayenne (poudre)", findFood("cayenne")?.name)
        assertEquals("Piment de Cayenne (poudre)", findFood("piment de cayenne")?.name)
        assertEquals("Persil frais", findFood("persil")?.name)
        assertEquals("Persil séché", findFood("persil seche")?.name)
        assertEquals("Ail cru", findFood("ail")?.name)
        assertEquals("Ail en poudre", findFood("ail en poudre")?.name)
        assertEquals("Fenouil cru", findFood("fenouil")?.name)
        assertEquals("Graines de fenouil", findFood("graines de fenouil")?.name)
    }

    @Test fun cinnamonKcalComputedFromCiqualMacros() {
        val c = foodByName("Cannelle (poudre)")!!
        assertEquals("Ciqual 11025", c.source)
        assertEquals(243, c.kcalPer100g)
        assertEquals(27.5, c.carbs!!, 0.05)
        assertEquals(4.0, c.proteins!!, 0.05)
        assertEquals(1.2, c.fat!!, 0.05)
        assertEquals(53.1, c.nutrients["fiber"]!!, 0.05)
        val form = FoodFormLogic.pick(FoodForm(), c)
        assertEquals("53.1", form.fiber100)
        assertEquals(KcalLogic.totalCarbs(27.5, 53.1)!!, parseNum(form.carbs100)!!, 1e-9)
        val r = KcalLogic.effective(form.kcal100, form.carbs100, form.prot100, form.fat100, form.fiber100, form.alcohol100)
        assertTrue(r.computed); assertTrue(r.fiberUsed)
        assertEquals(27.5, r.carbsUsed!!, 0.06)
        assertEquals(27.5 * 4 + 4.0 * 4 + 1.2 * 9, r.kcal100!!, 0.5)   // ≈ 136,8 (fibres non comptées à 2 kcal/g)
        assertEquals(3, calculate(2.5, r.kcal100, r.carbsUsed, 4.0, 1.2)!!.kcal)  // 1 c. à café ≈ 2,5 g
    }

    @Test fun allNewSpicesPresentWithCiqualSource() {
        val expected = listOf(
            "Cannelle (poudre)" to "11025", "Curcuma (poudre)" to "11089", "Graines de cumin" to "11042",
            "Gingembre moulu" to "11006", "Poivre noir" to "11015", "Poivre blanc" to "11019",
            "Paprika" to "11049", "Piment de Cayenne (poudre)" to "11088", "Muscade" to "11048",
            "Clous de girofle" to "11052", "Cardamome (poudre)" to "11075", "Graines de coriandre" to "11026",
            "Curry (poudre)" to "11005", "Quatre-épices" to "11056", "Safran" to "11039",
            "Laurier (feuille)" to "11053", "Graines de carvi" to "11064", "Graines de fenouil" to "11066",
            "Graines de fenugrec" to "11077", "Épices (moyenne)" to "11081",
            "Extrait de vanille (alcoolique)" to "11065", "Extrait de vanille (aqueux)" to "11098",
            "Herbes de Provence" to "11060", "Origan séché" to "11035", "Thym séché" to "11038",
            "Basilic séché" to "11032", "Romarin séché" to "11036", "Persil séché" to "11024",
            "Ail en poudre" to "11023", "Menthe séchée" to "11029", "Marjolaine séchée" to "11034",
            "Sauge séchée" to "11037", "Estragon séché" to "11050", "Aneth séché" to "11063",
            "Sarriette séchée" to "11062", "Cerfeuil séché" to "10999",
        )
        assertEquals(36, expected.size)
        for ((name, code) in expected) {
            val f = foodByName(name)!!
            assertEquals(name, "Ciqual $code", f.source)
            assertNotNull(name, f.carbs); assertNotNull(name, f.proteins); assertNotNull(name, f.fat)
            assertTrue(name, eff(f).computed)
            assertEquals(name, findFood(name)?.name)
        }
        val van = foodByName("Extrait de vanille (alcoolique)")!!
        assertTrue(van.alcohol!! > 30)
        val rv = FoodFormLogic.pick(FoodForm(), van).let {
            KcalLogic.effective(it.kcal100, it.carbs100, it.prot100, it.fat100, it.fiber100, it.alcohol100)
        }
        assertEquals(240.0, rv.kcal100!!, 1.0)   // alcool × 7 conservé
        assertEquals("Poivre noir", findFood("poivre")?.name)
        assertEquals("Extrait de vanille (alcoolique)", findFood("vanille")?.name)
        assertEquals("Herbes de Provence", findFood("herbes de provence")?.name)
        assertEquals("Thym séché", findFood("thym")?.name)
        assertEquals("Origan séché", findFood("origan")?.name)
        assertNull(foodByName("Poudre de colombo"))
        assertTrue(BUILTIN_FOODS.none { it.name.startsWith("Vanille (gousse)") || it.name == "Gousse de vanille" })
    }
}
