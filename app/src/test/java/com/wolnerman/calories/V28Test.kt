package com.wolnerman.calories

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

/** v2.8 : pesée d'un aliment hors liste — la balance remplit toujours « Quantité g », ajout possible avec nom + poids. */
class V28Test {
    private val L = FoodFormLogic

    @Test fun stableWeightFillsWhateverTheName() {
        val a = ScaleAutoFill()
        assertNull(a.onReading(182.4, false))                    // pas encore stable
        val w = a.onReading(182.4, true)!!
        // Aliment vide, texte libre ou aliment de la liste : même règle
        for (name in listOf("", "Gâteau de mamie", "Riz")) {
            val f = L.scale(L.type(FoodForm(), TextFieldValue(name), locked = false), w)
            assertEquals("182.4", f.grams); assertEquals(GramsSource.SCALE, f.gramsBy)
        }
    }

    @Test fun typingOrPickingNeverOverwritesScaleWeight() {
        var f = L.scale(FoodForm(), 182.4)
        f = L.type(f, TextFieldValue("Gâteau de mamie"), locked = false)
        assertEquals("182.4", f.grams)
        f = L.type(f, TextFieldValue("2 riz"), locked = false)    // un nombre dans le nom ne change rien (v2.4)
        assertEquals("182.4", f.grams)
        f = L.pick(f, foodByName("Riz blanc cuit") ?: BUILTIN_FOODS.first())
        assertEquals("182.4", f.grams)
        f = L.clear(f)
        assertEquals("182.4", f.grams)
    }

    @Test fun sameWeightNotReappliedButNewWeighingIs() {
        val a = ScaleAutoFill()
        assertEquals(182.4, a.onReading(182.4, true)!!, 0.0)
        assertNull(a.onReading(182.6, true))                     // même pesée (bruit < 0,5 g) : la saisie manuelle reste
        assertEquals(250.0, a.onReading(250.0, true)!!, 0.0)    // nouvel article
        assertNull(a.onReading(0.0, true))                       // plateau vidé
        assertEquals(250.0, a.onReading(250.0, true)!!, 0.0)    // même poids re-pesé après vidage : réappliqué
        assertNull(a.onReading(null, true))
    }

    @Test fun kcalDerivedFromMacros() {
        assertEquals(4 * 10.0 + 4 * 5.0 + 9 * 2.0, KcalLogic.fromMacros(10.0, 5.0, 2.0)!!, 1e-9)
        assertEquals(36.0, KcalLogic.fromMacros(null, null, 4.0)!!, 1e-9)
        assertNull(KcalLogic.fromMacros(null, null, null))
        assertNull(addProblem("Gâteau de mamie", "182.4", fmt(KcalLogic.fromMacros(50.0, 6.0, 20.0)!!)))
    }

    @Test fun weightOnlyAllowedWhenOnlyKcalMissing() {
        assertTrue(KcalLogic.canAddWeightOnly("Gâteau de mamie", "182.4", ""))
        assertFalse(KcalLogic.canAddWeightOnly("", "182.4", ""))          // il faut un nom
        assertFalse(KcalLogic.canAddWeightOnly("Gâteau", "", ""))
        assertFalse(KcalLogic.canAddWeightOnly("Gâteau", "0", ""))
        assertFalse(KcalLogic.canAddWeightOnly("Gâteau", "182", "250"))   // kcal connues : « Ajouter » normal
        assertEquals(kcalMissingMessage("Gâteau"), addProblem("Gâteau", "182", ""))   // la raison rouge reste
    }

    @Test fun weightOnlyEntryCountsZeroAndIsSkippedForHealthConnect() {
        val e = Entry(1, "2026-11-24", "10:00", "Gâteau de mamie", 182.4, 0.0, kcalUnknown = true)
        assertEquals(0, e.kcal)
        assertTrue(e.kcalUnknown)
        assertFalse(Entry(2, "2026-11-24", "10:00", "Riz", 100.0, 130.0).kcalUnknown)
    }
}

/** v2.8 (suite) : nom tapé conservé, « Mes aliments ». */
class MyFoodsTest {
    private val L = FoodFormLogic

    @Test fun typedFreeNameIsKeptWithScaleWeight() {
        var f = L.scale(FoodForm(), 182.4)
        f = L.type(f, TextFieldValue("Gâteau de mamie"), locked = false)
        assertEquals("Gâteau de mamie", L.entryName(f))       // même si une valeur proche a été reprise de la liste
        assertEquals("182.4", f.grams)
        assertEquals("riz basmati maison", L.entryName(L.type(FoodForm(), TextFieldValue("200 g de riz basmati maison"), false)))
        assertEquals("desserts", L.stripLeadingQuantity("2 desserts"))   // « des » n'est retiré que s'il est un mot
        assertEquals("Œuf", L.stripLeadingQuantity("2 Œuf"))
    }

    @Test fun explicitPickStillNamesTheListFood() {
        val egg = foodByName("Œuf entier (cru)")!!
        assertEquals("Œuf entier (cru)", L.entryName(L.pick(L.type(FoodForm(), TextFieldValue("oeu"), false), egg)))
    }

    @Test fun upsertMergeRenameDelete() {
        var l = MyFoodsLogic.upsert(emptyList(), CustomFood("Gâteau de mamie", 350.0, 50.0, 5.0, 15.0, 1))
        l = MyFoodsLogic.upsert(l, CustomFood("gateau de MAMIE", null, null, null, null, 2))   // poids seul : valeurs gardées
        assertEquals(1, l.size); assertEquals(350.0, l[0].kcal100!!, 0.0); assertEquals("gateau de MAMIE", l[0].name)
        l = MyFoodsLogic.replace(l, "gateau de MAMIE", CustomFood("Gâteau coco", null, null, null, null, 3))
        assertEquals(listOf("Gâteau coco"), l.map { it.name }); assertNull(l[0].kcal100)      // édition : champ vidé = vidé
        assertTrue(MyFoodsLogic.remove(l, "GATEAU COCO").isEmpty())
        assertEquals(l, MyFoodsLogic.fromJson(MyFoodsLogic.toJson(l)))
        assertTrue(MyFoodsLogic.fromJson("pas du json").isEmpty())
    }

    @Test fun rememberOnlyFreeNames() {
        assertTrue(MyFoodsLogic.shouldRemember("Gâteau de mamie", pickedBuiltin = false, scanned = false))
        assertFalse(MyFoodsLogic.shouldRemember("Œuf entier (cru)", pickedBuiltin = true, scanned = false))
        assertFalse(MyFoodsLogic.shouldRemember("Œuf entier (cru)", pickedBuiltin = false, scanned = false))  // déjà dans la liste
        assertFalse(MyFoodsLogic.shouldRemember("Biscuits XY", pickedBuiltin = false, scanned = true))
        assertFalse(MyFoodsLogic.shouldRemember(" ", false, false))
    }

    @Test fun myFoodsAppearInSuggestionsAndFillForm() {
        try {
            MyFoods.setForTest(listOf(CustomFood("Gâteau de mamie", 350.0, 50.0, 5.0, 15.0), CustomFood("Sauce chien de tatie")))
            val s = suggestFoods("gateau de mam")
            assertEquals("Gâteau de mamie", s.first().name); assertTrue(MyFoodsLogic.isMine(s.first()))
            val f = L.pick(L.scale(FoodForm(), 182.4), s.first())
            assertEquals("350", f.kcal100); assertEquals("182.4", f.grams); assertEquals("Gâteau de mamie", L.entryName(f))
            val unknown = L.pick(FoodForm(), foodByName("Sauce chien de tatie")!!)
            assertEquals("", unknown.kcal100)                                  // kcal inconnues → champ vide, pas « -1 »
            assertTrue(foodLine(foodByName("Sauce chien de tatie")!!).startsWith("kcal inconnues"))
        } finally { MyFoods.setForTest(emptyList()) }
    }
}
