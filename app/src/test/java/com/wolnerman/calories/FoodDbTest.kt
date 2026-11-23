package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

/** v2.3 : recherche tolérante (quantités, pluriels, cuisson) et contrôle de toute la base intégrée. */
class FoodDbTest {
    private fun fill(text: String): Pair<Food?, QtyResult?> {
        val f = findFood(text)
        return f to quantityFor(parseQuantity(text), f)
    }

    @Test fun deuxOeufMajuscule() {   // le bug de la capture v2.2
        val (f, q) = fill("2 Œuf")
        assertEquals("Œuf entier (cru)", f?.name)
        assertEquals(100.0, q!!.grams, 0.0)
        assertEquals("2 × Œuf entier (cru) (≈ 50 g) = 100 g", q.note)
        assertNull(addProblem("2 Œuf", "100", f!!.kcalPer100g.toString()))
    }
    @Test fun pluriels() {
        assertEquals("Œuf entier (cru)", findFood("œufs")?.name)
        assertEquals("Œuf entier (cru)", findFood("oeufs")?.name)
        assertEquals("Œuf dur", findFood("œufs cuits")?.name)
        assertEquals("Œuf dur", findFood("œuf dur")?.name)
        assertEquals("Œuf dur", findFood("des oeufs durs")?.name)
        assertEquals("Banane plantain cuite", findFood("bananes plantain")?.name)
        assertEquals("Frites (friteuse)", findFood("frites")?.name)
    }
    @Test fun deuxOeufsAuPlat() {
        val (f, q) = fill("2 œufs au plat")
        assertEquals("Œuf au plat (avec matière grasse)", f?.name)
        assertEquals(110.0, q!!.grams, 0.0)
    }
    @Test fun grammesDirects() {
        val (f, q) = fill("200 g riz")
        assertEquals("Riz blanc cuit", f?.name)
        assertEquals(200.0, q!!.grams, 0.0)
        assertEquals(150.0, fill("150g de poulet").second!!.grams, 0.0)
        assertEquals(250.0, fill("25 cl jus d'orange").second!!.grams, 0.0)
        assertEquals("Jus d'orange", findFood("25 cl jus d'orange")?.name)
    }
    @Test fun unitesEtFractions() {
        val (pain, q) = fill("3 tranches de pain")
        assertEquals("Pain blanc (baguette)", pain?.name)
        assertEquals(90.0, q!!.grams, 0.0)
        val (avocat, qa) = fill("1/2 avocat")
        assertEquals("Avocat", avocat?.name)
        assertEquals(75.0, qa!!.grams, 0.0)
        assertEquals(240.0, fill("deux bananes").second!!.grams, 0.0)
        assertEquals("Banane", findFood("deux bananes")?.name)
        assertEquals(30.0, fill("2 c. à soupe de crème fraîche").second!!.grams, 0.0)
        assertEquals(15.0, fill("1 cuillère à soupe d'huile d'olive").second!!.grams, 10.0)
        assertEquals(180.0, fill("1 1/2 banane").second!!.grams, 0.0)
    }
    @Test fun sansPoidsUnitaire() {
        val q = parseQuantity("2 crevettes")
        assertTrue(q.hasQuantity)
        assertNull(quantityFor(q, findFood("2 crevettes")))   // pas de poids unitaire : l'utilisateur saisit les grammes
    }
    @Test fun sansQuantite() {
        assertFalse(parseQuantity("œuf dur").hasQuantity)
        assertFalse(parseQuantity("une").hasQuantity)
    }
    @Test fun antillais() {
        assertEquals("Colombo de poulet", findFood("colombo")?.name)
        assertEquals("Féroce d'avocat", findFood("féroce")?.name)
        assertEquals("Riz-pois rouges (riz aux haricots rouges)", findFood("riz pois rouges")?.name)
        assertEquals("Ti-punch", findFood("ti punch")?.name)
        assertEquals("Blaff de poisson", findFood("blaff")?.name)
        assertEquals("Christophine cuite", findFood("chouchou")?.name)
        assertEquals("Boudin créole (antillais)", findFood("boudin créole")?.name)
        assertEquals("Jus / nectar de goyave", findFood("jus de goyave")?.name)
        assertEquals("Giraumon cuit (Martinique)", findFood("giraumon")?.name)
    }
    @Test fun fauteDeFrappe() {
        assertEquals("Omelette nature", findFood("omlette")?.name)
        assertEquals("Christophine cuite", findFood("cristophine")?.name)
    }
    @Test fun suggestionsEnCoursDeFrappe() {
        val s = suggestFoods("oe").map { it.name }
        assertTrue(s.take(5).any { it.startsWith("Œuf") })
        assertTrue(suggestFoods("2 œufs").map { it.name }.containsAll(listOf("Œuf dur", "Œuf poché", "Œuf à la coque")))
        assertEquals("Œuf entier (cru)", suggestFoods("2 œufs").first().name)
    }
    @Test fun ligneDeSuggestion() {
        val f = foodByName("Œuf dur")!!
        assertEquals("134 kcal · G 0,5 · P 14 · L 8,6 (/100 g) · 1 œuf ≈ 50 g", foodLine(f))
    }
    @Test fun messageSousAjouter() {
        assertEquals("Calories inconnues pour « X » : choisis un aliment de la liste ou saisis les kcal pour 100 g.", addProblem("X", "100", ""))
    }

    // ---------------- contrôles de toute la base
    @Test fun tailleEtCategories() {
        assertTrue("base trop petite : ${BUILTIN_FOODS.size}", BUILTIN_FOODS.size >= 350)
        assertTrue(BUILTIN_FOODS.all { it.category.isNotBlank() && it.source.isNotBlank() })
    }
    @Test fun pasDeDoublons() {
        val names = BUILTIN_FOODS.map { fold(it.name) }
        assertEquals(names.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toString(), names.size, names.toSet().size)
        // un alias ne doit pas être le nom exact d'un autre aliment
        val byName = names.toSet()
        BUILTIN_FOODS.forEach { f -> f.aliases.forEach { a -> assertTrue("alias « $a » = nom d'un autre aliment", fold(a) !in byName || fold(a) == fold(f.name)) } }
    }
    @Test fun valeursPlausibles() {
        for (f in BUILTIN_FOODS) {
            assertTrue("kcal ${f.name}", f.kcalPer100g in 0..900)
            listOf(f.carbs, f.proteins, f.fat).forEach { m -> if (m != null) assertTrue("macro ${f.name}", m in 0.0..100.0) }
            val sum = (f.carbs ?: 0.0) + (f.proteins ?: 0.0) + (f.fat ?: 0.0)
            assertTrue("G+P+L > 100 g : ${f.name}", sum <= 101.0)
            // énergie cohérente avec les macros (alcool et fibres exceptés) : macros × Atwater ≤ kcal + marge
            val atwater = 4 * (f.carbs ?: 0.0) + 4 * (f.proteins ?: 0.0) + 9 * (f.fat ?: 0.0)
            assertTrue("kcal incohérentes ${f.name} : ${f.kcalPer100g} vs $atwater", atwater <= f.kcalPer100g * 1.15 + 15)
            f.unitGrams?.let { assertTrue("unité ${f.name}", it > 0 && it <= 500 && f.unitLabel != null) }
            f.nutrients["salt"]?.let { assertTrue("sel ${f.name}", it in 0.0..30.0) }
            f.nutrients["sugars"]?.let { s -> f.carbs?.let { c -> assertTrue("sucres > glucides ${f.name}", s <= c + 1.0) } }
        }
    }
    @Test fun lesNomsSeRetrouvent() {
        // chaque aliment est retrouvé par son propre nom (pas masqué par un autre)
        val ko = BUILTIN_FOODS.filter { findFood(it.name)?.name != it.name }.map { "${it.name} → ${findFood(it.name)?.name}" }
        assertTrue(ko.joinToString("\n"), ko.isEmpty())
    }
    @Test fun anciensNomsToujoursReconnus() {
        listOf("Riz blanc cuit", "Pâtes cuites", "Pain (baguette)", "Pomme de terre cuite", "Blanc de poulet cuit", "Bœuf haché 5% cuit",
            "Saumon cuit", "Thon en conserve (eau)", "Lait demi-écrémé", "Ouassous / crevettes cuites", "Fromage (emmental)",
            "Soda sucré", "Dachine / madère cuite", "Noix de coco (chair)", "Kéfir de lait", "Lambi cuit", "Colombo de poulet")
            .forEach { assertNotNull("ancien nom perdu : $it", foodByName(it)) }
    }
}
