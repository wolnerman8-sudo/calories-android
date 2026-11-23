package com.wolnerman.calories

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudRecognitionTest {
    private val G = CloudProviders.Groq
    private val R = CloudProviders.OpenRouter
    private val O = CloudProviders.OpenAi

    // ---- Détection du préfixe de clé ----
    @Test fun prefixes() {
        assertEquals(CloudProviders.GROQ, CloudProviders.detect("gsk_abcdefghijklmnopqrstuvwxyz0123"))
        assertEquals(CloudProviders.OPENROUTER, CloudProviders.detect("sk-or-v1-0123456789abcdef"))
        assertEquals(CloudProviders.OPENAI, CloudProviders.detect("sk-proj-0123456789abcdef"))
        assertEquals(CloudProviders.OPENAI, CloudProviders.detect("  sk-0123456789abcdef  "))
        assertEquals(CloudProviders.GEMINI, CloudProviders.detect("AIzaSyA-0123456789"))
        assertNull(CloudProviders.detect("abcdef"))
        assertNull(CloudProviders.detect(""))
    }

    // ---- Migration v2.0 ----
    @Test fun migrationCleGroqDansGemini() {
        val p = SecureSettings.planMigration(mapOf("gemini" to "gsk_SECRET1234567890", "openai" to null, "groq" to null, "openrouter" to null), "gemini")
        assertEquals("gsk_SECRET1234567890", p.keys["groq"])
        assertNull(p.keys["gemini"])
        assertEquals("groq", p.provider)
        assertTrue(p.note!!.contains("Groq"))
        assertFalse(p.note!!.contains("SECRET")) // jamais la clé dans le message
    }

    @Test fun migrationAutresPrefixes() {
        val or = SecureSettings.planMigration(mapOf("gemini" to "sk-or-v1-abc", "openai" to null), null)
        assertEquals("sk-or-v1-abc", or.keys["openrouter"]); assertEquals("openrouter", or.provider)
        val oa = SecureSettings.planMigration(mapOf("gemini" to "sk-proj-abc", "openai" to null), "gemini")
        assertEquals("sk-proj-abc", oa.keys["openai"]); assertNull(oa.keys["gemini"]); assertEquals("openai", oa.provider)
        // clé Groq collée dans le champ OpenAI (v1.9)
        val g2 = SecureSettings.planMigration(mapOf("openai" to "gsk_x", "gemini" to null), "openai")
        assertEquals("gsk_x", g2.keys["groq"]); assertNull(g2.keys["openai"]); assertEquals("groq", g2.provider)
    }

    @Test fun migrationSansChangement() {
        // vraie clé Gemini : reste en place, choix conservé
        val g = SecureSettings.planMigration(mapOf("gemini" to "AIzaXYZ", "openai" to null), "gemini")
        assertEquals("AIzaXYZ", g.keys["gemini"]); assertEquals("gemini", g.provider); assertNull(g.note)
        // installation neuve : Groq par défaut
        val n = SecureSettings.planMigration(mapOf("gemini" to null, "openai" to null), null)
        assertEquals("groq", n.provider); assertNull(n.note)
        // OpenAI choisi en v1.9 avec une vraie clé : conservé
        val o = SecureSettings.planMigration(mapOf("openai" to "sk-proj-1", "gemini" to null), "openai")
        assertEquals("openai", o.provider)
        // OpenAI par défaut (jamais choisi) sans clé → Groq
        assertEquals("groq", SecureSettings.planMigration(mapOf("openai" to null), "openai").provider)
        // emplacement cible déjà occupé : on ne l'écrase pas
        val busy = SecureSettings.planMigration(mapOf("gemini" to "gsk_old", "groq" to "gsk_new"), "groq")
        assertEquals("gsk_new", busy.keys["groq"]); assertEquals("gsk_old", busy.keys["gemini"])
        // préfixe inconnu : rien ne bouge
        assertNull(SecureSettings.planMigration(mapOf("gemini" to "xyz"), null).note)
    }

    // ---- Extraction JSON ----
    @Test fun extractionJson() {
        assertEquals("{\"aliments\":[]}", GeminiParser.extractJson("<think>je regarde la photo {pas ça}</think>\n{\"aliments\":[]}"))
        assertEquals("{\"a\":1}", GeminiParser.extractJson("Voici :\n```json\n{\"a\":1}\n```\nBon appétit"))
        assertEquals("[{\"nom\":\"X\"}]", GeminiParser.extractJson("```\n[{\"nom\":\"X\"}]\n```"))
        assertEquals("{\"a\":{\"b\":2}}", GeminiParser.extractJson("Résultat : {\"a\":{\"b\":2}} fin"))
        assertEquals("{\"ok\":1}", GeminiParser.extractJson("<THINK>long…</THINK>{\"ok\":1}"))
        try { GeminiParser.extractJson("<think>réflexion coupée {\"a\":"); fail() } catch (e: IllegalArgumentException) {}
        try { GeminiParser.extractJson("aucun aliment"); fail() } catch (e: IllegalArgumentException) {}
        val foods = GeminiParser.parseFoods("<think>riz ?</think>```json\n{\"aliments\":[{\"nom\":\"riz blanc cuit\",\"grammes_estimes\":150,\"kcal_pour_100g\":130,\"confiance\":0.9}]}\n```")
        assertEquals("Riz blanc cuit", foods.single().name)
    }

    private fun chat(content: String?, refusal: String? = null): String = JSONObject()
        .put("choices", JSONArray().put(JSONObject().put("index", 0).put("finish_reason", "stop")
            .put("message", JSONObject().put("role", "assistant").put("content", content ?: JSONObject.NULL)
                .put("refusal", refusal ?: JSONObject.NULL)))).toString()

    @Test fun reponses() {
        val resp = chat("{\"aliments\":[{\"nom\":\"Poulet colombo\",\"grammes_estimes\":220,\"kcal_pour_100g\":150,\"confiance\":0.8}]}")
        assertEquals("Poulet colombo", GeminiParser.parseFoods(CloudClient.modelText(resp)!!).single().name)
        try { CloudClient.modelText(chat(null, "Non.")); fail() } catch (e: CloudError) { assertEquals(CloudError.Kind.REFUSED, e.kind) }
        assertNull(CloudClient.modelText(chat(null)))
        assertNull(CloudClient.modelText("{\"choices\":[]}"))
    }

    // ---- Corps de requête ----
    @Test fun requetes() {
        val g = JSONObject(CloudClient.requestBody(G, G.models.first(), "QUJD"))
        assertEquals("qwen/qwen3.8-27b", g.getString("model"))
        assertEquals("json_object", g.getJSONObject("response_format").getString("type"))
        assertEquals("none", g.getString("reasoning_effort"))
        assertFalse(g.has("store"))
        assertTrue(g.has("max_completion_tokens"))
        val c = g.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("data:image/jpeg;base64,QUJD", c.getJSONObject(1).getJSONObject("image_url").getString("url"))

        val r = JSONObject(CloudClient.requestBody(R, R.models.first(), "QUJD"))
        assertEquals("nvidia/nemotron-3.5-super-vl:free", r.getString("model"))
        assertTrue(r.has("max_tokens"))
        assertFalse(r.getJSONObject("reasoning").getBoolean("enabled"))
        val r2 = JSONObject(CloudClient.requestBody(R, R.models[1], "QUJD"))
        assertFalse(r2.has("response_format"))

        val o = JSONObject(CloudClient.requestBody(O, O.models.first(), "QUJD"))
        assertEquals("json_schema", o.getJSONObject("response_format").getString("type"))
        assertFalse(o.getBoolean("store"))
        // test de clé : texte seul, pas d'image, json_object même pour OpenAI
        val t = JSONObject(CloudClient.requestBody(O, O.models.first(), null, 64))
        assertTrue(t.getJSONArray("messages").getJSONObject(0).get("content") is String)
        assertEquals("json_object", t.getJSONObject("response_format").getString("type"))
        assertEquals(64, t.getInt("max_completion_tokens"))
        assertFalse(t.toString().contains("gsk_") || t.toString().contains("Bearer"))
    }

    // ---- Erreurs ----
    private fun err(p: CloudProvider, code: Int, ecode: String?, type: String = "invalid_request_error", param: String? = null, msg: String = "x") =
        CloudClient.errorFor(p, code, JSONObject().put("error", JSONObject().put("message", msg).put("type", type)
            .put("param", param ?: JSONObject.NULL).put("code", ecode ?: JSONObject.NULL)).toString())
    private fun failOf(a: CloudClient.ErrAction): CloudError = (a as CloudClient.ErrAction.Fail).error

    @Test fun erreurs() {
        assertSame(CloudClient.ErrAction.NextModel, err(G, 404, "model_not_found"))
        assertSame(CloudClient.ErrAction.NextModel, err(G, 400, "model_decommissioned", msg = "The model has been decommissioned"))
        assertSame(CloudClient.ErrAction.NextModel, err(R, 404, null, msg = "No endpoints found for nvidia/nemotron-3.5-super-vl:free."))
        assertEquals("reasoning_effort", (err(G, 400, null, param = "reasoning_effort") as CloudClient.ErrAction.RetryWithout).param)
        assertEquals("response_format", (err(R, 400, null, msg = "Parameter `response_format` is not supported") as CloudClient.ErrAction.RetryWithout).param)
        val inv = failOf(err(G, 401, "invalid_api_key", msg = "Invalid API Key"))
        assertEquals(CloudError.Kind.INVALID_KEY, inv.kind); assertTrue(inv.userMessage.contains("Groq"))
        val credit = failOf(err(O, 429, "insufficient_quota", type = "insufficient_quota"))
        assertEquals("Crédit OpenAI épuisé ou non ajouté : ajoute du crédit sur platform.openai.com", credit.userMessage)
        assertEquals(CloudError.Kind.SPEND_LIMIT, failOf(err(O, 429, "project_spend_limit_exceeded", type = "insufficient_quota")).kind)
        val groqLimit = failOf(err(G, 429, "rate_limit_exceeded", type = "tokens"))
        assertEquals(CloudError.Kind.RATE_LIMIT, groqLimit.kind); assertTrue(groqLimit.userMessage.contains("Groq"))
        assertTrue(failOf(err(R, 429, "429", msg = "Rate limit exceeded: free-models-per-day")).userMessage.contains("50"))
        assertEquals(CloudError.Kind.NO_CREDIT, failOf(err(R, 402, "402", msg = "Insufficient credits")).kind)
        assertEquals(CloudError.Kind.REGION, failOf(err(O, 403, "unsupported_country_region_territory", msg = "Country, region, or territory not supported")).kind)
        assertEquals(CloudError.Kind.SERVER, failOf(CloudClient.errorFor(G, 503, null)).kind)
        assertEquals(CloudError.Kind.OTHER, failOf(CloudClient.errorFor(G, 400, "pas du json")).kind)
    }

    @Test fun libelles() {
        assertEquals("Groq (gratuit, recommandé)", G.label)
        assertEquals("OpenRouter (gratuit, 50 photos/jour)", R.label)
        assertEquals("OpenAI (payant)", O.label)
        assertEquals("https://api.groq.com/openai/v1", G.baseUrl)
        assertEquals("https://openrouter.ai/api/v1", R.baseUrl)
        assertTrue(R.models.none { it.id.startsWith("google/") }) // routés via Google AI Studio, bloqué en Martinique
        assertTrue(G.models.size >= 2 && R.models.size >= 2)
    }
}
