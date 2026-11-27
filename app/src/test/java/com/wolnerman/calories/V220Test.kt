package com.wolnerman.calories

import androidx.health.connect.client.records.MealType
import org.junit.Assert.*
import org.junit.Test

/** v2.20 : parseur de recettes, correspondance Ciqual / Mes aliments, totaux et portions (KcalLogic v2.10). */
class V220Test {

    private val example = "100 g de flocons d’avoine, 1 banane, 15 g de graines de chia…"

    @Test fun splitExactUserExample() {
        val parts = RecipeLogic.splitIngredients(example)
        assertEquals(listOf("100 g de flocons d’avoine", "1 banane", "15 g de graines de chia"), parts)
    }

    @Test fun splitNewlinesSemicolonsBulletsAndDecimalComma() {
        val text = """
            • 100 g de flocons d'avoine
            - 1 banane
            * 15 g de graines de chia
            1,5 kg de farine ; 2 œufs
        """.trimIndent()
        val parts = RecipeLogic.splitIngredients(text)
        assertEquals(5, parts.size)
        assertEquals("100 g de flocons d'avoine", parts[0])
        assertEquals("1 banane", parts[1])
        assertEquals("15 g de graines de chia", parts[2])
        assertEquals("1,5 kg de farine", parts[3])
        assertEquals("2 œufs", parts[4])
        val farine = RecipeLogic.parseLine("1,5 kg de farine")
        assertEquals(1500.0, farine.grams!!, 0.0)
    }

    @Test fun parseUnitsFractionsPinchAndMg() {
        assertEquals(0.2, parseQuantity("200 mg de sel").grams!!, 1e-9)
        assertEquals(0.5, quantityFor(parseQuantity("une pincée de sel"), null)!!.grams, 0.0)
        assertEquals(RecipeLogic.PINCH_GRAMS, 0.5, 0.0)
        // l'huile a 10 g / c. à soupe dans la base (prioritaire sur le 15 g par défaut)
        val oil = foodByName("Huile d'olive")!!
        assertEquals(10.0, quantityFor(parseQuantity("1 c. à soupe d'huile d'olive"), oil)!!.grams, 0.0)
        assertEquals(10.0, quantityFor(parseQuantity("1 c.à.s d'huile d'olive"), oil)!!.grams, 0.0)
        assertEquals(15.0, quantityFor(parseQuantity("1 c.à.s de crème fraîche"), null)!!.grams, 0.0)
        assertEquals(60.0, quantityFor(parseQuantity("½ banane"), foodByName("Banane"))!!.grams, 0.0)
        assertEquals(60.0, quantityFor(parseQuantity("1/2 banane"), foodByName("Banane"))!!.grams, 0.0)
        assertEquals(180.0, quantityFor(parseQuantity("1 1/2 banane"), foodByName("Banane"))!!.grams, 0.0)
        assertEquals(180.0, quantityFor(parseQuantity("1½ banane"), foodByName("Banane"))!!.grams, 0.0)
        assertEquals(5.0, quantityFor(parseQuantity("1 cc de sucre"), null)!!.grams, 0.0)
        assertEquals(15.0, quantityFor(parseQuantity("1 cs de sucre"), null)!!.grams, 0.0)
    }

    @Test fun exactExampleMatchesCiqualAndTotals() {
        val lines = RecipeLogic.parse(example)
        assertEquals(3, lines.size)
        assertEquals("Flocons d'avoine", lines[0].food?.name)
        assertEquals("Ciqual 32140", lines[0].food?.source)
        assertEquals(100.0, lines[0].grams!!, 0.0)
        assertEquals("Banane", lines[1].food?.name)
        assertEquals("Ciqual 13005", lines[1].food?.source)
        assertEquals(120.0, lines[1].grams!!, 0.0)
        assertTrue(lines[1].gramsNote!!.contains("120"))
        assertTrue(lines[1].gramsNote!!.contains("modifiable"))
        assertEquals("Graines de chia", lines[2].food?.name)
        assertEquals("Ciqual 15047", lines[2].food?.source)
        assertEquals(15.0, lines[2].grams!!, 0.0)
        lines.forEach { assertFalse(it.unresolved) }

        val oats = lines[0].food!!; val banana = lines[1].food!!; val chia = lines[2].food!!
        fun expected(f: Food, g: Double): LineNutrition {
            val kcal100 = KcalLogic.forFood(f)!!
            val fib = f.nutrients["fiber"]
            val totalG = KcalLogic.totalCarbs(f.carbs, fib)!!
            val used = KcalLogic.digestibleCarbs(totalG, fib)!!
            val k = g / 100.0
            return LineNutrition(g, kcal100 * k, totalG * k, used * k, f.proteins!! * k, f.fat!! * k, fib!! * k)
        }
        val e0 = expected(oats, 100.0)
        val e1 = expected(banana, 120.0)
        val e2 = expected(chia, 15.0)
        val n0 = RecipeLogic.nutrition(lines[0])!!
        val n1 = RecipeLogic.nutrition(lines[1])!!
        val n2 = RecipeLogic.nutrition(lines[2])!!
        assertEquals(e0.kcal, n0.kcal, 1e-6)
        assertEquals(e1.kcal, n1.kcal, 1e-6)
        assertEquals(e2.kcal, n2.kcal, 1e-6)
        // G affiché = glucides Ciqual (disponibles) + fibres, comme FoodForm
        assertEquals(KcalLogic.totalCarbs(oats.carbs, oats.nutrients["fiber"])!!, n0.carbs!!, 1e-6)

        val t = RecipeLogic.totals(lines)
        assertEquals(235.0, t.grams, 0.0)
        assertEquals(3, t.included)
        assertEquals(0, t.excluded)
        assertEquals(e0.kcal + e1.kcal + e2.kcal, t.kcal, 1e-6)
        assertEquals(e0.carbs!! + e1.carbs!! + e2.carbs!!, t.carbs!!, 1e-6)
        assertEquals(e0.proteins!! + e1.proteins!! + e2.proteins!!, t.proteins!!, 1e-6)
        assertEquals(e0.fat!! + e1.fat!! + e2.fat!!, t.fat!!, 1e-6)
        assertEquals(e0.fiber!! + e1.fiber!! + e2.fiber!!, t.fiber!!, 1e-6)
        // valeurs affichées (arrondi) : servent de référence pour les notes de version
        assertEquals(499, RecipeLogic.kcalDisplay(t.kcal))
        assertEquals(102.1, Math.round(t.carbs!! * 10.0) / 10.0, 0.0)
        assertEquals(14.4, Math.round(t.proteins!! * 10.0) / 10.0, 0.0)
        assertEquals(12.4, Math.round(t.fat!! * 10.0) / 10.0, 0.0)
        assertEquals(19.6, Math.round(t.fiber!! * 10.0) / 10.0, 0.0)
    }

    @Test fun portionsSplitTotalsAndWeight() {
        val t = RecipeLogic.totals(RecipeLogic.parse(example))
        val per = RecipeLogic.perPortion(t, 2.0)!!
        assertEquals(t.grams / 2, per.grams, 1e-9)
        assertEquals(t.kcal / 2, per.kcal, 1e-9)
        assertEquals(t.carbs!! / 2, per.carbs!!, 1e-9)
        assertNull(RecipeLogic.perPortion(t, 0.0))
    }

    @Test fun unrecognizedLineIsExcludedUntilCorrected() {
        val lines = RecipeLogic.parse("100 g de flocons d'avoine, 1 trucinexistantxyz, 15 g de graines de chia")
        assertEquals(3, lines.size)
        assertFalse(lines[0].unresolved)
        assertTrue(lines[1].unresolved)
        assertNull(lines[1].food)
        assertFalse(lines[2].unresolved)
        val t = RecipeLogic.totals(lines)
        assertEquals(2, t.included)
        assertEquals(1, t.excluded)
        assertEquals(115.0, t.grams, 0.0)
        // correction manuelle : valeurs pour 100 g, sans inventer côté appli
        val fixed = lines[1].copy(
            grams = 50.0, gramsOverridden = true,
            manualName = "Ingrédient libre", manualCarbs100 = 10.0, manualProt100 = 2.0,
            manualFat100 = 1.0, manualFiber100 = 3.0,
            manualKcal100 = KcalLogic.fromMacros(10.0, 2.0, 1.0, 3.0),
        )
        assertFalse(fixed.unresolved)
        val t2 = RecipeLogic.totals(listOf(lines[0], fixed, lines[2]))
        assertEquals(3, t2.included)
        assertEquals(0, t2.excluded)
        val nut = RecipeLogic.nutrition(fixed)!!
        assertEquals(KcalLogic.fromMacros(10.0, 2.0, 1.0, 3.0)!! * 0.5, nut.kcal, 1e-6)
    }

    @Test fun saveToMyFoodsAndReopen() {
        val lines = RecipeLogic.parse(example)
        val c = RecipeLogic.toCustomFood("Porridge chia", example, lines, 2.0, 42)!!
        assertEquals("Porridge chia", c.name)
        assertEquals(235.0 / 2.0, c.portionGrams!!, 1e-9)
        assertFalse(c.recipeSource.isNullOrBlank())
        assertFalse(c.recipeJson.isNullOrBlank())
        assertTrue(MyFoodsLogic.isRecipe(c))
        val f = MyFoodsLogic.toFood(c)
        assertEquals("portion", f.unitLabel)
        assertEquals(c.portionGrams, f.unitGrams)
        // G/100 g = totaux fibres comprises ; kcal recalculées par KcalLogic
        assertEquals(KcalLogic.fromMacros(c.carbs, c.proteins, c.fat, c.fiber)!!, c.kcal100!!, 1e-6)
        val json = MyFoodsLogic.toJson(listOf(c))
        val back = MyFoodsLogic.fromJson(json).single()
        assertEquals(c.name, back.name)
        assertEquals(c.recipeSource, back.recipeSource)
        assertEquals(c.portionGrams, back.portionGrams)
        val restored = RecipeLogic.fromCustomFood(back)!!
        assertEquals(2.0, restored.third, 0.0)
        assertEquals(listOf("Flocons d'avoine", "Banane", "Graines de chia"), restored.second.map { it.food?.name })
        // upsert d'une édition sans recette conserve la liste
        val kept = MyFoodsLogic.upsert(listOf(back), CustomFood("Porridge chia", 200.0)).single()
        assertEquals(back.recipeSource, kept.recipeSource)
        assertEquals(back.recipeJson, kept.recipeJson)
        // JSON ancien (sans rs/rj/pg) toujours lu
        val old = MyFoodsLogic.fromJson("""[{"n":"Vieux","t":1,"k":100.0,"c":10.0,"p":2.0,"f":1.0,"fi":3.0}]""").single()
        assertEquals("Vieux", old.name)
        assertNull(old.recipeSource)
        assertFalse(MyFoodsLogic.isRecipe(old))
    }

    @Test fun addToJournalUsesSameMacrosAndMealTime() {
        val lines = RecipeLogic.parse(example)
        val e = RecipeLogic.toEntry("Porridge chia", example, lines, 2.0, 1.0, 99, "2026-11-27",
            RecipeLogic.timeForMeal("Déjeuner", "21:00"))!!
        assertEquals("Porridge chia", e.name)
        assertEquals(117.5, e.grams, 1e-9)
        assertEquals("12:30", e.time)
        assertEquals(MealType.MEAL_TYPE_LUNCH, HealthMapping.mealType(e.time))
        val c = RecipeLogic.toCustomFood("Porridge chia", example, lines, 2.0)!!
        assertEquals(KcalLogic.digestibleCarbs(c.carbs, c.fiber), e.carbs100)
        assertEquals(c.proteins, e.proteins100)
        assertEquals(c.fat, e.fat100)
        assertEquals(c.fiber, e.nutr["fiber"])
        // 1 portion mangée alors que le repas courant est déjà le déjeuner : on garde l'heure actuelle
        assertEquals("12:45", RecipeLogic.timeForMeal("Déjeuner", "12:45"))
        assertEquals("Petit-déjeuner", RecipeLogic.defaultMeal("08:00"))
        assertTrue(e.kcal > 0)
        assertFalse(e.kcalUnknown)
    }

    @Test fun myFoodsAreMatchedInRecipes() {
        MyFoods.setForTest(listOf(CustomFood("Mélange maison", 200.0, 20.0, 10.0, 8.0, 1, 5.0)))
        try {
            val line = RecipeLogic.parseLine("50 g de mélange maison")
            assertEquals("Mélange maison", line.food?.name)
            assertEquals(50.0, line.grams!!, 0.0)
            val nut = RecipeLogic.nutrition(line)!!
            // Mes aliments : G déjà totaux, pas de re-ajout des fibres
            assertEquals(10.0, nut.carbs!!, 1e-6)  // 20 g/100 g × 50 g
            assertEquals(KcalLogic.forFood(line.food!!)!! * 0.5, nut.kcal, 1e-6)
        } finally {
            MyFoods.setForTest(emptyList())
        }
    }

    @Test fun searchRegressionsOliveLinCoco() {
        assertEquals("Olives", findFood("olive")?.name)
        assertEquals("Graines de lin", findFood("lin")?.name)
        assertEquals("Noix de coco (chair fraîche)", findFood("coco")?.name)
        assertEquals("Noix (cerneaux)", findFood("noix")?.name)
        assertEquals("Graines de tournesol", findFood("tournesol")?.name)
        assertEquals("Huile d'olive", findFood("huile d'olive")?.name)
        assertEquals("Huile de lin", findFood("huile de lin")?.name)
        assertEquals("Huile de coco", findFood("huile coco")?.name)
        assertEquals("Flocons d'avoine", findFood("flocons d avoine")?.name)
        assertEquals("Graines de chia", findFood("graines de chia")?.name)
        assertEquals("Banane", findFood("banane")?.name)
    }

    @Test fun noNutritionIsInvented() {
        val line = RecipeLogic.parseLine("2 crevettes")
        // aliment éventuellement reconnu, mais sans poids unitaire : pas de valeurs inventées
        assertNull(RecipeLogic.nutrition(line))
        assertTrue(line.unresolved)
        assertFalse(RecipeLogic.canSave("Recette", listOf(line)))
        assertNull(RecipeLogic.toCustomFood("Recette", "2 crevettes", listOf(line), 1.0))
    }
}
