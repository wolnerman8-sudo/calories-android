package com.wolnerman.calories

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

/** v2.9 : kcal/100 g calculées en direct depuis G/P/L, prioritaires sur la valeur tapée. */
class V29Test {
    private val L = FoodFormLogic

    @Test fun chiaExampleFromUser() {
        // Quantité 19 g, G 42, P 16,5, L 30,7 ; l'utilisateur avait tapé 19 dans kcal/100 g (→ 4 kcal avant)
        val r = KcalLogic.effective("19", "42", "16,5", "30,7", macrosByUser = true, fromSource = false)
        assertTrue(r.computed)
        assertEquals(510.3, r.kcal100!!, 0.05)                    // 42×4 + 16,5×4 + 30,7×9
        assertTrue(r.kcal100!! in 486.0..511.0)
        assertTrue(r.typedIgnored); assertEquals(19.0, r.typed!!, 0.0)
        val c = calculate(parseNum("19"), r.kcal100, parseNum("42"), parseNum("16,5"), parseNum("30,7"))!!
        assertEquals(97, c.kcal)                                  // ≈ 92–97 kcal attendues
        assertEquals(7.98, c.carbs!!, 0.01); assertEquals(3.135, c.proteins!!, 0.01); assertEquals(5.833, c.fat!!, 0.01)
        assertNull(addProblem("Graines de chia", "19", fmt(r.kcal100!!)))
    }

    @Test fun missingMacrosCountZeroAndDecimalComma() {
        assertEquals(36.0, KcalLogic.effective("", "", "", "4", true, false).kcal100!!, 1e-9)
        assertEquals(4 * 12.5, KcalLogic.effective("", "12,5", "", "", true, false).kcal100!!, 1e-9)
        val none = KcalLogic.effective("250", "", "", "", false, false)
        assertFalse(none.computed); assertEquals(250.0, none.kcal100!!, 0.0)   // sans G/P/L : valeur tapée
        assertNull(KcalLogic.effective("", "", "", "", false, false).kcal100)
    }

    @Test fun macrosWinEvenWhenKcalTypedAfter() {
        var f = L.typeMacro(FoodForm(), 'G', "42")
        f = f.copy(kcal100 = "19")                                // tape ensuite 19 dans kcal
        val r = KcalLogic.effective(f.kcal100, f.carbs100, f.prot100, f.fat100, f.macrosByUser, f.autoFrom != null)
        assertTrue(r.computed); assertEquals(168.0, r.kcal100!!, 1e-9)
        val r2 = L.typeMacro(L.typeMacro(f, 'P', "16,5"), 'L', "30,7").let { KcalLogic.effective(it.kcal100, it.carbs100, it.prot100, it.fat100, it.macrosByUser, false) }
        assertEquals(510.3, r2.kcal100!!, 0.05)
    }

    @Test fun listFoodKeepsItsOwnKcalUntilMacrosEdited() {
        val egg = foodByName("Œuf entier (cru)")!!
        var f = L.pick(L.type(FoodForm(), TextFieldValue("oeuf"), false), egg)
        var r = KcalLogic.effective(f.kcal100, f.carbs100, f.prot100, f.fat100, f.macrosByUser, f.autoFrom != null)
        assertFalse(r.computed); assertEquals(egg.kcalPer100g.toDouble(), r.kcal100!!, 0.0)
        f = L.typeMacro(f, 'L', "12")                             // l'utilisateur corrige L → recalcul
        r = KcalLogic.effective(f.kcal100, f.carbs100, f.prot100, f.fat100, f.macrosByUser, f.autoFrom != null)
        assertTrue(r.computed)
        assertEquals(KcalLogic.fromMacros(egg.carbs, egg.proteins, 12.0)!!, r.kcal100!!, 1e-9)
        assertNull(f.autoFrom)
        assertTrue(L.pick(f, egg).macrosByUser.not())            // nouveau choix dans la liste : valeurs de la liste
    }

    @Test fun scannedProductKeepsLabelKcalUntilEdited() {
        val r = KcalLogic.effective("480", "60", "7", "22", macrosByUser = false, fromSource = true)
        assertFalse(r.computed); assertEquals(480.0, r.kcal100!!, 0.0)
        assertTrue(KcalLogic.effective("480", "60", "7", "22", macrosByUser = true, fromSource = true).computed)
        assertTrue(KcalLogic.effective("", "60", "7", "22", macrosByUser = false, fromSource = true).computed)   // kcal absentes de l'étiquette
    }
}
