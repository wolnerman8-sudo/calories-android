package com.wolnerman.calories

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

/** v2.9 / v2.10 : kcal/100 g calculées en direct depuis G/P/L (+ fibres), prioritaires pour TOUS les aliments. */
class V29Test {
    private val L = FoodFormLogic

    @Test fun chiaExampleFromUser() {
        // Quantité 19 g, G 42, P 16,5, L 30,7 ; l'utilisateur avait tapé 19 dans kcal/100 g (→ 4 kcal avant)
        val r = KcalLogic.effective("19", "42", "16,5", "30,7")
        assertTrue(r.computed)
        assertEquals(510.3, r.kcal100!!, 0.05)                    // 42×4 + 16,5×4 + 30,7×9
        assertTrue(r.typedIgnored); assertEquals(19.0, r.typed!!, 0.0)
        val c = calculate(parseNum("19"), r.kcal100, r.carbsUsed, parseNum("16,5"), parseNum("30,7"))!!
        assertEquals(97, c.kcal)
        assertEquals(7.98, c.carbs!!, 0.01); assertEquals(3.135, c.proteins!!, 0.01); assertEquals(5.833, c.fat!!, 0.01)
        assertNull(addProblem("Graines de chia", "19", fmt(r.kcal100!!)))
    }

    @Test fun missingMacrosCountZeroAndDecimalComma() {
        assertEquals(36.0, KcalLogic.effective("", "", "", "4").kcal100!!, 1e-9)
        assertEquals(4 * 12.5, KcalLogic.effective("", "12,5", "", "").kcal100!!, 1e-9)
        val none = KcalLogic.effective("250", "", "", "")
        assertFalse(none.computed); assertEquals(250.0, none.kcal100!!, 0.0)   // sans G/P/L : valeur tapée
        assertNull(KcalLogic.effective("", "", "", "").kcal100)
    }

    @Test fun macrosWinEvenWhenKcalTypedAfter() {
        var f = L.typeMacro(FoodForm(), 'G', "42")
        f = f.copy(kcal100 = "19")
        val r = KcalLogic.effective(f.kcal100, f.carbs100, f.prot100, f.fat100, f.fiber100)
        assertTrue(r.computed); assertEquals(168.0, r.kcal100!!, 1e-9)
        val f2 = L.typeMacro(L.typeMacro(f, 'P', "16,5"), 'L', "30,7")
        assertEquals(510.3, KcalLogic.effective(f2.kcal100, f2.carbs100, f2.prot100, f2.fat100, f2.fiber100).kcal100!!, 0.05)
    }

    @Test fun scannedProductIsComputedToo() {
        // v2.10 : plus d'exception pour les scans : G/P/L de l'étiquette → calcul immédiat
        val r = KcalLogic.effective("480", "60", "7", "22")
        assertTrue(r.computed); assertEquals(60 * 4 + 7 * 4 + 22 * 9.0, r.kcal100!!, 1e-9)
        assertTrue(r.typedIgnored)
    }
}
