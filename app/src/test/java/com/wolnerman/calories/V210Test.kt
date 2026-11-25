package com.wolnerman.calories

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

/** v2.10 : champ Fibres (glucides utilisés = G − fibres), calcul pour tous les aliments sans exception. */
class V210Test {
    private val L = FoodFormLogic
    private fun eff(f: FoodForm) = KcalLogic.effective(f.kcal100, f.carbs100, f.prot100, f.fat100, f.fiber100, f.alcohol100)

    @Test fun chiaWithAndWithoutFibres() {
        val no = KcalLogic.effective("", "42", "16,5", "30,7", "")
        assertEquals(510.3, no.kcal100!!, 0.05); assertFalse(no.fiberUsed)
        assertEquals(97, calculate(19.0, no.kcal100, no.carbsUsed, 16.5, 30.7)!!.kcal)
        val fi = KcalLogic.effective("", "42", "16,5", "30,7", "34,4")
        assertEquals(372.7, fi.kcal100!!, 0.05)                  // (42 − 34,4)×4 + 16,5×4 + 30,7×9
        assertTrue(fi.fiberUsed); assertEquals(7.6, fi.carbsUsed!!, 1e-9)
        val c = calculate(19.0, fi.kcal100, fi.carbsUsed, 16.5, 30.7)!!
        assertEquals(71, c.kcal)                                  // 372,7 × 0,19 = 70,8
        assertEquals(1.444, c.carbs!!, 0.001)
        assertEquals("(G − fibres)×4 + P×4 + L×9", KcalLogic.formula(fi))
        assertEquals("G×4 + P×4 + L×9", KcalLogic.formula(no))
    }

    @Test fun fibresAboveCarbsGiveZeroCarbs() {
        val r = KcalLogic.effective("", "5", "2", "1", "8")
        assertEquals(0.0, r.carbsUsed!!, 0.0)
        assertEquals(2 * 4 + 9.0, r.kcal100!!, 1e-9)
    }

    @Test fun allEmptyMeansNoCalcEvenWithFibres() {
        val r = KcalLogic.effective("250", "", "", "", "10")
        assertFalse(r.computed); assertEquals(250.0, r.kcal100!!, 0.0)
        assertNull(KcalLogic.effective("", "", "", "", "10").kcal100)
    }

    @Test fun listFoodComputedImmediatelyWithFibrePrefilled() {
        val lent = foodByName("Lentilles corail cuites")!!
        val fib = lent.nutrients["fiber"]!!
        val f = L.pick(L.type(FoodForm(), TextFieldValue("lentilles corail"), false), lent)
        assertEquals(fmt(fib), f.fiber100)                       // fibres préremplies (Ciqual)
        assertEquals(KcalLogic.totalCarbs(lent.carbs, fib)!!, parseNum(f.carbs100)!!, 1e-9)   // G = glucides + fibres
        val r = eff(f)
        assertTrue(r.computed)                                    // calcul immédiat, sans modifier G/P/L
        assertEquals(lent.carbs!!, r.carbsUsed!!, 0.051)          // G − fibres = glucides Ciqual
        assertEquals(lent.carbs!! * 4 + lent.proteins!! * 4 + lent.fat!! * 9, r.kcal100!!, 0.3)
        // modifier L recalcule
        val r2 = eff(L.typeMacro(f, 'L', "5"))
        assertEquals(lent.carbs!! * 4 + lent.proteins!! * 4 + 45, r2.kcal100!!, 0.3)
        // effacer l'aliment vide aussi les fibres
        assertEquals("", L.clear(f).fiber100)
    }

    @Test fun eggStaysCloseToCiqual() {
        val egg = foodByName("Œuf entier (cru)")!!
        val r = eff(L.pick(FoodForm(), egg))
        assertTrue(r.computed); assertEquals(140.0, r.kcal100!!, 3.0)
    }

    @Test fun alcoholOfListDrinksCounted() {
        val rum = foodByName("Rhum")!!
        assertTrue(rum.alcohol!! > 30)
        val r = eff(L.pick(FoodForm(), rum))
        assertEquals(rum.kcalPer100g.toDouble(), r.kcal100!!, 5.0)   // 33,4 g × 7 ≈ 234 kcal, pas 0
        assertTrue(KcalLogic.formula(r).contains("alcool"))
    }

    @Test fun myFoodsStoreFibresAndComputedKcal() {
        val c = CustomFood("Chia maison", KcalLogic.fromMacros(42.0, 16.5, 30.7, 34.4), 42.0, 16.5, 30.7, 1, 34.4)
        val back = MyFoodsLogic.fromJson(MyFoodsLogic.toJson(listOf(c))).single()
        assertEquals(34.4, back.fiber!!, 0.0); assertEquals(372.7, back.kcal100!!, 0.05)
        val f = L.fill(FoodForm(), MyFoodsLogic.toFood(back))
        assertEquals("42", f.carbs100); assertEquals("34.4", f.fiber100)   // G gardé tel quel pour Mes aliments
        assertEquals(372.7, eff(f).kcal100!!, 0.05)
        // une mise à jour sans fibres garde les fibres connues
        assertEquals(34.4, MyFoodsLogic.upsert(listOf(back), CustomFood("chia maison", 400.0)).single().fiber!!, 0.0)
    }

    @Test fun builtinParsingHasAllFoods() {
        assertEquals(562, BUILTIN_FOODS.size)
        assertTrue(BUILTIN_FOODS.count { it.nutrients["fiber"] != null } > 450)
    }
}
