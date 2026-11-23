package com.wolnerman.calories

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Reproduction du bug v2.0 : après reconnaissance Groq, « Ajouter » restait gris (formulaire vide). */
class AddFromPhotoTest {
    /** Réponse Groq réaliste (qwen/qwen3.8-27b, JSON mode) : décimales, unités, virgule française, kcal nulles. */
    private val groqResponse = JSONObject()
        .put("id", "chatcmpl-x").put("object", "chat.completion").put("model", "qwen/qwen3.8-27b")
        .put("choices", JSONArray().put(JSONObject().put("index", 0).put("finish_reason", "stop")
            .put("message", JSONObject().put("role", "assistant").put("content", """
                <think></think>
                ```json
                {"aliments": [
                  {"nom": "Garniture de tortilla", "grammes_estimes": "150.0", "kcal_pour_100g": 250, "confiance": 0.95},
                  {"nom": "riz blanc cuit", "grammes_estimes": "120 g", "kcal_pour_100g": "130,5 kcal", "confiance": "80 %"},
                  {"nom": "Sauce maison", "grammes_estimes": "~ 1 200", "kcal_pour_100g": null, "confiance": 0.4},
                  {"nom": "Avocat", "grammes_estimes": 0, "kcal_pour_100g": "160", "confiance": 0.3}
                ]}
                ```""".trimIndent())))).toString()

    private fun guesses() = GeminiParser.parseFoods(CloudClient.modelText(groqResponse)!!)

    @Test fun avantCorrectifLeFormulaireVideBloque() {
        // État observé sur la capture : suggestion affichée mais formulaire non rempli
        assertEquals("Saisis ou choisis un aliment.", addProblem("", "100", ""))
    }

    @Test fun meilleureSuggestionRemplitUnFormulaireValide() {
        val top = guesses().first()
        assertEquals("Garniture de tortilla", top.name)
        val f = fillFromGuess(top, scaleStableGrams = null)
        assertEquals("Garniture de tortilla", f.name)
        assertEquals("150", f.grams)
        assertEquals("estimation", f.gramsSource)
        assertEquals("250", f.kcal100)
        assertNull(addProblem(f.name, f.grams, f.kcal100)) // « Ajouter » ajoute
        assertEquals(375, calculate(parseNum(f.grams), parseNum(f.kcal100))!!.kcal)
    }

    @Test fun balanceStablePrioritaire() {
        val f = fillFromGuess(guesses().first(), scaleStableGrams = 143.4)
        assertEquals("143.4", f.grams); assertEquals("balance", f.gramsSource)
        assertNull(addProblem(f.name, f.grams, f.kcal100))
    }

    @Test fun aliementDeLaBaseAvecVirguleEtUnites() {
        val riz = guesses().first { it.name == "Riz blanc cuit" }
        assertEquals(120.0, riz.grams!!, 0.0)
        assertEquals(130.5, riz.kcal100!!, 1e-9)
        val f = fillFromGuess(riz, null)
        assertNotNull(f.food)                 // valeurs + macros de la base intégrée
        assertEquals("155", f.kcal100)   // Ciqual 2025 : riz blanc cuit
        assertEquals("33.2", f.carbs100)
        assertNull(addProblem(f.name, f.grams, f.kcal100))
    }

    @Test fun kcalInconnuesRaisonClaire() {
        val sauce = guesses().first { it.name == "Sauce maison" }
        assertEquals(1200.0, sauce.grams!!, 0.0) // « ~ 1 200 » lu correctement
        assertNull(sauce.kcal100)
        val f = fillFromGuess(sauce, null)
        assertTrue(f.kcalMissing)
        val why = addProblem(f.name, f.grams, f.kcal100)!!
        assertTrue(why.startsWith("Calories inconnues pour « Sauce maison »"))
    }

    @Test fun quantiteToujoursRemplie() {
        val avocat = guesses().first { it.name == "Avocat" }
        assertNull(avocat.grams) // 0 g ignoré
        val f = fillFromGuess(avocat, null)
        assertEquals("100", f.grams); assertEquals("par défaut", f.gramsSource)
        assertNull(addProblem(f.name, f.grams, f.kcal100))
    }

    @Test fun lectureTolerante() {
        assertEquals(150.0, parseLooseNumber("150.0")!!, 0.0)
        assertEquals(150.5, parseLooseNumber("150,5 g")!!, 1e-9)
        assertEquals(1200.0, parseLooseNumber("1 200 kcal")!!, 0.0)
        assertEquals(1200.5, parseLooseNumber("1\u202F200,5")!!, 1e-9)
        assertEquals(120.0, parseLooseNumber("~120")!!, 0.0)
        assertNull(parseLooseNumber("beaucoup"))
        assertEquals(12.5, parseNum("12,5")!!, 1e-9)
        assertEquals(150.0, parseNum("150 g")!!, 0.0)
    }

    @Test fun chaqueRefusADeuxRaison() {
        assertNotNull(addProblem("Riz", "", "130"))
        assertNotNull(addProblem("Riz", "0", "130"))
        assertNotNull(addProblem("Riz", "abc", "130"))
        assertNotNull(addProblem("Riz", "100", "-5"))
        assertNotNull(addProblem("Riz", "100", "abc"))
        assertNotNull(addProblem("Riz", "100", "5000"))
        assertNotNull(addProblem("  ", "100", "130"))
        assertNull(addProblem("Riz", "100", "0")) // 0 kcal (eau, thé…) accepté
    }
}
