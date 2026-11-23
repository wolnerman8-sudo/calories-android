package com.wolnerman.calories

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FoodRecognitionTest {
    private fun wrap(modelJson: String): String = JSONObject().put("candidates", org.json.JSONArray().put(
        JSONObject().put("content", JSONObject().put("role", "model").put("parts",
            org.json.JSONArray().put(JSONObject().put("text", modelJson)))).put("finishReason", "STOP"))).toString()

    @Test fun reponseGeminiComplete() {
        val resp = wrap("""{"aliments":[{"nom":"riz blanc cuit","grammes_estimes":180,"kcal_pour_100g":130,"confiance":0.9},
            {"nom":"Poulet colombo","grammes_estimes":150.4,"kcal_pour_100g":150,"confiance":0.7}]}""")
        val foods = GeminiParser.parseFoods(GeminiParser.modelText(resp)!!)
        assertEquals(2, foods.size)
        assertEquals("Riz blanc cuit", foods[0].name)
        assertEquals(180.0, foods[0].grams!!, 0.0)
        assertEquals(130.0, foods[0].kcal100!!, 0.0)
        assertEquals(0.9, foods[0].confidence, 1e-9)
        assertFalse(foods[0].offline)
        assertEquals("Riz blanc cuit", foods[0].food?.name) // macros de la base intégrée
        assertEquals(150.0, foods[1].grams!!, 0.0) // arrondi
        assertNull(GeminiParser.blockReason(resp))
    }

    @Test fun variantesTolerees() {
        val t = "```json\n[{\"nom\":\"Banane\",\"grammes_estimes\":\"120 g\",\"kcal_pour_100g\":\"89,5\",\"confiance\":\"85 %\"}," +
            "{\"nom\":\"Pain\",\"grammes_estimes\":null,\"kcal_pour_100g\":99999,\"confiance\":\"faible\"},{\"nom\":\"\"}]\n```"
        val foods = GeminiParser.parseFoods(t)
        assertEquals(2, foods.size)
        assertEquals(120.0, foods[0].grams!!, 0.0)
        assertEquals(89.5, foods[0].kcal100!!, 1e-9)
        assertEquals(0.85, foods[0].confidence, 1e-9)
        assertNull(foods[1].grams)
        assertNull(foods[1].kcal100) // valeur absurde ignorée
        assertEquals(0.3, foods[1].confidence, 1e-9)
    }

    @Test fun trieParConfianceEtListeVide() {
        val f = GeminiParser.parseFoods("""{"aliments":[{"nom":"A","confiance":0.2},{"nom":"B","confiance":80}]}""")
        assertEquals(listOf("B", "A"), f.map { it.name })
        assertTrue(GeminiParser.parseFoods("""{"aliments":[]}""").isEmpty())
        assertEquals(1, GeminiParser.parseFoods("""{"nom":"Mangue","kcal_pour_100g":60,"confiance":1}""").size)
    }

    @Test fun reponseBloqueeOuVide() {
        val blocked = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        assertEquals("SAFETY", GeminiParser.blockReason(blocked))
        assertNull(GeminiParser.modelText(blocked))
        val thought = JSONObject().put("candidates", org.json.JSONArray().put(JSONObject().put("content", JSONObject().put("parts",
            org.json.JSONArray().put(JSONObject().put("text", "je réfléchis").put("thought", true))
                .put(JSONObject().put("text", "{\"aliments\":[]}")))))).toString()
        assertEquals("{\"aliments\":[]}", GeminiParser.modelText(thought))
    }

    @Test fun erreursHttp() {
        assertNull(GeminiClient.errorFor(404, """{"error":{"code":404,"message":"models/x is not found","status":"NOT_FOUND"}}"""))
        assertSame(GeminiError.InvalidKey, GeminiClient.errorFor(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID"}]}}"""))
        assertSame(GeminiError.Quota, GeminiClient.errorFor(429, """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}"""))
        assertTrue(GeminiClient.errorFor(403, """{"error":{"code":403,"message":"Method doesn't allow unregistered callers","status":"PERMISSION_DENIED"}}""") is GeminiError.Denied)
        assertTrue(GeminiClient.errorFor(503, null) is GeminiError.Server)
        assertTrue(GeminiClient.errorFor(400, "pas du json") is GeminiError.Other)
    }

    @Test fun corpsDeRequete() {
        val o = JSONObject(GeminiClient.requestBody("QUJD"))
        val parts = o.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertTrue(parts.getJSONObject(0).getString("text").contains("JSON"))
        assertEquals("image/jpeg", parts.getJSONObject(1).getJSONObject("inline_data").getString("mime_type"))
        assertEquals("QUJD", parts.getJSONObject(1).getJSONObject("inline_data").getString("data"))
        assertEquals("application/json", o.getJSONObject("generationConfig").getString("responseMimeType"))
        assertTrue(GeminiClient.MODELS.size >= 2)
        assertFalse(o.toString().contains("key=")) // la clé ne va jamais dans le corps ni l'URL
    }

    @Test fun traductionEtiquettes() {
        val rice = FoodLabels.map("White rice")
        assertEquals("Riz blanc cuit", rice.name); assertEquals(155.0, rice.kcal100!!, 0.0); assertNotNull(rice.food)
        val boudin = FoodLabels.map("Boudin")
        assertEquals("Boudin créole", boudin.name); assertEquals(231.0, boudin.kcal100!!, 0.0) // Ciqual 2025 (boudin antillais)
        val pizza = FoodLabels.map("Pepperoni")
        assertEquals("Pizza pepperoni", pizza.name); assertNull(pizza.food)
        val soup = FoodLabels.map("Lettuce soup")
        assertEquals("Soupe (Lettuce soup)", soup.name); assertEquals(50.0, soup.kcal100!!, 0.0)
        assertEquals("Nouilles (Acorn noodles)", FoodLabels.map("Acorn noodles").name) // pluriel
        val unknown = FoodLabels.map("Pierogi") // « pie » ne doit pas correspondre
        assertEquals("Pierogi", unknown.name); assertNull(unknown.kcal100); assertFalse(unknown.translated)
    }

    @Test fun etiquettesDeLaTableExistentDansLeModele() {
        val f = listOf(File("src/main/assets/aiy_food_labels_en.txt"), File("app/src/main/assets/aiy_food_labels_en.txt")).first { it.exists() }
        val labels = f.readLines().toSet()
        assertEquals(2024, f.readLines().size)
        val missing = FoodLabels.TABLE.keys.filter { it !in labels }
        assertTrue("absentes du modèle : $missing", missing.isEmpty())
    }

    @Test fun topKHorsLigne() {
        val labels = listOf("__background__", "White rice", "Pepperoni", "Pierogi", "Sushi")
        val scores = byteArrayOf(250.toByte(), 10, 200.toByte(), 30, 5)
        val r = OfflineFood.topK(scores, labels, max = 5, minScore = 0.04)
        assertEquals(listOf("Pizza pepperoni", "Pierogi"), r.map { it.name })
        assertTrue(r.all { it.offline })
        assertEquals(200 / 256.0, r[0].confidence, 1e-9)
        assertEquals("Pepperoni", r[0].label)
    }

    @Test fun confianceNormalisee() {
        assertEquals(0.75, GeminiParser.confidence(75), 1e-9)
        assertEquals(0.6, GeminiParser.confidence("moyenne"), 1e-9)
        assertEquals(1.0, GeminiParser.confidence(3000), 1e-9)
        assertEquals(0.5, GeminiParser.confidence(null), 1e-9)
    }
}
