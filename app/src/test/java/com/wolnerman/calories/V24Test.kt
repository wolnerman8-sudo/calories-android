package com.wolnerman.calories

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

/** v2.4 : champ Aliment jamais réécrit pendant la frappe, bouton ✕, quantité jamais modifiée par le nom. */
class V24Test {
    private val egg = foodByName("Œuf entier (cru)")!!
    private val L = FoodFormLogic

    /** Simule la frappe lettre par lettre avec une zone de composition comme le clavier Samsung. */
    private fun typeChars(start: FoodForm, text: String): FoodForm {
        var s = start
        val base = s.text
        for (i in 1..text.length) {
            val t = base + text.substring(0, i)
            s = L.type(s, TextFieldValue(t, TextRange(t.length), TextRange(base.length, t.length)), locked = false)
        }
        return s
    }

    @Test fun typingNeverRewritesTheField() {
        val s = typeChars(FoodForm(), "Œuf entie")
        assertEquals("Œuf entie", s.text)
        assertEquals(TextRange(0, 9), s.name.composition)          // composition du clavier conservée
        assertEquals(TextRange(9), s.name.selection)
        assertEquals("Œuf entier (cru)", s.autoFrom)                 // valeurs remplies, texte intact
    }

    @Test fun pickReplacesWholeTextNoConcatenation() {
        val typed = typeChars(FoodForm(), "Œuf entie")
        val p = L.pick(typed, egg)
        assertEquals("Œuf entier (cru)", p.text)
        assertFalse(p.text.contains("Œuf entie("))
        assertEquals(TextRange(p.text.length), p.name.selection)    // curseur à la fin
        assertNull(p.name.composition)                               // composition effacée → rien de recommité
        assertEquals(egg.kcalPer100g.toString(), p.kcal100)
        // Choisir deux fois de suite ne concatène pas non plus
        assertEquals("Œuf entier (cru)", L.pick(p, egg).text)
    }

    @Test fun editingAfterPickKeepsUserText() {
        val p = L.pick(typeChars(FoodForm(), "oeuf"), egg)
        val v = TextFieldValue("Œuf entier (cru) bio", TextRange(20), TextRange(17, 20))
        val s = L.type(p, v, locked = false)
        assertEquals(v, s.name)
    }

    @Test fun clearButtonEmptiesNameAndListValuesButKeepsGrams() {
        var s = L.typeGrams(FoodForm(), "120")
        s = L.pick(typeChars(s, "oeuf"), egg)
        val c = L.clear(s)
        assertEquals("", c.text)
        assertEquals("", c.kcal100); assertEquals("", c.prot100); assertNull(c.autoFrom)
        assertEquals("120", c.grams)
        assertEquals(GramsSource.USER, c.gramsBy)
    }

    @Test fun clearKeepsScaleWeight() {
        val s = L.pick(L.scale(FoodForm(), 87.34), egg)
        val c = L.clear(s)
        assertEquals("87.3", c.grams); assertEquals(GramsSource.SCALE, c.gramsBy)
    }

    @Test fun clearKeepsHandTypedKcal() {
        val s = L.type(FoodForm(kcal100 = "250"), TextFieldValue("Gâteau de mamie"), locked = false)
        assertEquals("250", L.clear(s).kcal100)
    }

    @Test fun leadingNumberDoesNotChangeGrams() {
        val s = typeChars(FoodForm(), "2 Œuf")
        assertEquals("100", s.grams)
        assertEquals("2 Œuf", s.text)
        val t = typeChars(L.typeGrams(FoodForm(), "75"), "3 oeufs")
        assertEquals("75", t.grams)
        val u = typeChars(FoodForm(), "200 g riz")
        assertEquals("100", u.grams)
    }

    @Test fun scaleWeightPreservedWhileTypingAndPicking() {
        var s = L.scale(FoodForm(), 142.0)
        s = typeChars(s, "2 oeufs")
        assertEquals("142", s.grams)
        s = L.pick(s, egg)
        assertEquals("142", s.grams)
        assertEquals(GramsSource.SCALE, s.gramsBy)
    }

    @Test fun chipAppliesQuantityOnlyWhenTapped() {
        val s = typeChars(L.typeGrams(FoodForm(), "50"), "2 Œuf")
        assertEquals("Utiliser 2 × 50 g = 100 g", L.chip(s)!!.chip)
        assertEquals("50", s.grams)                                   // rien d'appliqué avant l'appui
        assertNull(L.chip(typeChars(FoodForm(), "2 Œuf")))            // déjà 100 g : pas de proposition inutile
        val t = typeChars(L.typeGrams(FoodForm(), "30"), "3 oeufs")
        assertEquals("30", t.grams)
        assertEquals("Utiliser 3 × 50 g = 150 g", L.chip(t)!!.chip)
        val applied = L.applyChip(t)
        assertEquals("150", applied.grams); assertEquals(GramsSource.CHIP, applied.gramsBy)
        assertNull(L.chip(applied))                                   // proposition masquée une fois appliquée
        assertEquals("3 oeufs", applied.text)                         // le texte n'est pas touché
    }

    @Test fun chipSurvivesPickFromList() {
        val t = L.pick(typeChars(L.typeGrams(FoodForm(), "30"), "2 oeufs"), egg)
        assertEquals("30", t.grams)
        assertEquals("Utiliser 2 × 50 g = 100 g", L.chip(t)!!.chip)
        assertEquals("100", L.applyChip(t).grams)
    }

    @Test fun gramsChip() {
        val t = typeChars(FoodForm(), "250 g riz")
        assertEquals("Utiliser 250 g", L.chip(t)!!.chip)
        assertEquals("100", t.grams)
    }

    @Test fun entryNameIsChosenFoodOrTypedText() {
        assertEquals("Œuf entier (cru)", L.entryName(L.pick(typeChars(FoodForm(), "2 oeu"), egg)))
        assertEquals("Œuf entier (cru)", L.entryName(typeChars(FoodForm(), "2 Œuf")))
        val free = L.type(FoodForm(kcal100 = "300"), TextFieldValue("  Plat de tatie  "), locked = false)
        assertEquals("Plat de tatie", L.entryName(free))
    }

    @Test fun scannedProductValuesNotTouchedByTyping() {
        val s = L.type(FoodForm(kcal100 = "480"), TextFieldValue("riz"), locked = true)
        assertEquals("480", s.kcal100); assertNull(s.autoFrom)
    }
}
