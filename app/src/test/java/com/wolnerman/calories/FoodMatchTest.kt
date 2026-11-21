package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

class FoodMatchTest {
    @Test fun rizCompletTapeSansToucherLaSuggestion() {
        assertEquals("Riz complet cuit", findFood("riz complet")?.name)
        assertEquals("Riz complet cuit", findFood("  Riz Complet ")?.name)
    }
    @Test fun rizSeulDonneRizBlanc() = assertEquals("Riz blanc cuit", findFood("riz")?.name)
    @Test fun exactPrefereAuPartiel() = assertEquals("Banane", findFood("banane")?.name)
    @Test fun accentsEtLigatures() {
        assertEquals("Œuf", findFood("oeuf")?.name)
        assertEquals("Pâtes complètes cuites", findFood("pates completes")?.name)
        assertEquals("Dachine / madère cuite", findFood("madere")?.name)
        assertEquals("Christophine cuite", findFood("christophine")?.name)
    }
    @Test fun contientUnMot() {
        assertEquals("Blanc de poulet cuit", findFood("poulet")?.name)
        assertEquals("Accras de morue", findFood("acras")?.name ?: findFood("accras")?.name)
    }
    @Test fun inconnuOuTropCourt() {
        assertNull(findFood("xyzzy"))
        assertNull(findFood("r"))
        assertNull(findFood(""))
    }
    @Test fun calcul() {
        val c = calculate(150.0, 123.0, 25.6, 2.7, 1.0)!!
        assertEquals(185, c.kcal)
        assertEquals(38.4, c.carbs!!, 0.01)
        assertEquals(4.05, c.proteins!!, 0.01)
        assertEquals(1.5, c.fat!!, 0.01)
    }
    @Test fun calculImpossibleSansKcal() {
        assertNull(calculate(100.0, null))
        assertNull(calculate(0.0, 100.0))
        assertNull(calculate(null, 100.0))
    }
    @Test fun virguleFrancaise() = assertEquals(12.5, parseNum("12,5")!!, 0.0)
    @Test fun toutesLesValeursIntegreesCompletes() {
        BUILTIN_FOODS.forEach { assertEquals("nutriments manquants : ${it.name}", NUTRIENTS.size, it.nutrients.size) }
        assertEquals(BUILTIN_FOODS.size, BUILTIN_FOODS.map { fold(it.name) }.toSet().size)
    }
    @Test fun suggestions() {
        assertTrue(suggestFoods("riz").map { it.name }.containsAll(listOf("Riz blanc cuit", "Riz complet cuit")))
        assertEquals("Riz complet cuit", suggestFoods("riz complet").first().name)
    }
}
