package com.wolnerman.calories

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OpenAiRecognitionTest {
    private fun chat(content: String?, refusal: String? = null): String = JSONObject()
        .put("id", "chatcmpl-1").put("object", "chat.completion").put("model", "gpt-6-luna")
        .put("choices", JSONArray().put(JSONObject().put("index", 0).put("finish_reason", "stop")
            .put("message", JSONObject().put("role", "assistant").put("content", content ?: JSONObject.NULL)
                .put("refusal", refusal ?: JSONObject.NULL))))
        .put("usage", JSONObject().put("prompt_tokens", 900).put("completion_tokens", 120)).toString()

    @Test fun reponseComplete() {
        val resp = chat("""{"aliments":[{"nom":"Poulet colombo","grammes_estimes":220,"kcal_pour_100g":150,"confiance":0.8},
            {"nom":"riz blanc cuit","grammes_estimes":150,"kcal_pour_100g":130,"confiance":0.9}]}""")
        val foods = GeminiParser.parseFoods(OpenAiClient.modelText(resp)!!)
        assertEquals(listOf("Riz blanc cuit", "Poulet colombo"), foods.map { it.name })
        assertEquals(150.0, foods[0].grams!!, 0.0)
        assertEquals(130.0, foods[0].kcal100!!, 0.0)
    }

    @Test fun refusEtContenuVide() {
        try { OpenAiClient.modelText(chat(null, "Je ne peux pas aider.")); fail() } catch (e: OpenAiError) { assertSame(OpenAiError.Refused, e) }
        assertNull(OpenAiClient.modelText(chat(null)))
        assertNull(OpenAiClient.modelText("""{"choices":[]}"""))
    }

    private fun err(code: Int, ecode: String?, type: String = "invalid_request_error", param: String? = null, msg: String = "x") =
        OpenAiClient.errorFor(code, JSONObject().put("error", JSONObject().put("message", msg).put("type", type)
            .put("param", param ?: JSONObject.NULL).put("code", ecode ?: JSONObject.NULL)).toString())

    private fun fail(a: OpenAiClient.ErrAction): OpenAiError = (a as OpenAiClient.ErrAction.Fail).error

    @Test fun erreurs() {
        assertSame(OpenAiClient.ErrAction.NextModel, err(404, "model_not_found", msg = "The model `gpt-6-luna` does not exist or you do not have access to it."))
        assertSame(OpenAiClient.ErrAction.NextModel, err(400, "model_not_found"))
        assertSame(OpenAiClient.ErrAction.RetryWithoutReasoning, err(400, "unsupported_parameter", param = "reasoning_effort"))
        assertSame(OpenAiError.InvalidKey, fail(err(401, "invalid_api_key", msg = "Incorrect API key provided: sk-...")))
        val credit = fail(err(429, "insufficient_quota", type = "insufficient_quota"))
        assertSame(OpenAiError.NoCredit, credit)
        assertEquals("Crédit OpenAI épuisé ou non ajouté : ajoute du crédit sur platform.openai.com", credit.userMessage)
        assertSame(OpenAiError.NoCredit, fail(err(429, "credit_balance_exhausted", type = "insufficient_quota")))
        assertSame(OpenAiError.SpendLimit, fail(err(429, "project_spend_limit_exceeded", type = "insufficient_quota")))
        assertSame(OpenAiError.RateLimit, fail(err(429, "rate_limit_exceeded", type = "requests")))
        assertSame(OpenAiError.Region, fail(err(403, "unsupported_country_region_territory", msg = "Country, region, or territory not supported")))
        assertTrue(fail(err(500, null, type = "server_error")) is OpenAiError.Server)
        assertTrue(fail(OpenAiClient.errorFor(503, null)) is OpenAiError.Server)
        assertTrue(fail(OpenAiClient.errorFor(400, "pas du json")) is OpenAiError.Other)
    }

    @Test fun corpsDeRequete() {
        val m = OpenAiClient.MODELS.first()
        val o = JSONObject(OpenAiClient.requestBody(m, "QUJD"))
        assertEquals("gpt-6-luna", o.getString("model"))
        assertEquals("low", o.getString("reasoning_effort"))
        val content = o.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertTrue(content.getJSONObject(0).getString("text").contains("JSON"))
        assertEquals("data:image/jpeg;base64,QUJD", content.getJSONObject(1).getJSONObject("image_url").getString("url"))
        val rf = o.getJSONObject("response_format")
        assertEquals("json_schema", rf.getString("type"))
        assertTrue(rf.getJSONObject("json_schema").getBoolean("strict"))
        val item = rf.getJSONObject("json_schema").getJSONObject("schema").getJSONObject("properties")
            .getJSONObject("aliments").getJSONObject("items")
        assertEquals(4, item.getJSONArray("required").length())
        assertFalse(item.getBoolean("additionalProperties"))
        assertFalse(o.getBoolean("store"))
        // modèle de secours sans raisonnement
        val old = JSONObject(OpenAiClient.requestBody(OpenAiClient.MODELS.last(), "QUJD"))
        assertFalse(old.has("reasoning_effort"))
        assertTrue(OpenAiClient.MODELS.size >= 2)
    }
}
