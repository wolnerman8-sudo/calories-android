package com.wolnerman.calories

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Erreurs OpenAI, avec message en français pour l'utilisateur. */
sealed class OpenAiError(userMessage: String) : RecoError(userMessage) {
    object NoKey : OpenAiError("Aucune clé API OpenAI enregistrée (Profil › Reconnaissance par photo) : mode hors-ligne.")
    object NoNetwork : OpenAiError("Pas de connexion Internet : reconnaissance hors-ligne utilisée.")
    object Timeout : OpenAiError("OpenAI ne répond pas (délai dépassé) : reconnaissance hors-ligne utilisée.")
    object InvalidKey : OpenAiError("Clé API OpenAI invalide ou révoquée. Vérifie-la dans Profil › Reconnaissance par photo.")
    object NoCredit : OpenAiError("Crédit OpenAI épuisé ou non ajouté : ajoute du crédit sur platform.openai.com")
    object SpendLimit : OpenAiError("Limite de dépenses OpenAI atteinte : augmente-la sur platform.openai.com (Limits).")
    object RateLimit : OpenAiError("Trop de demandes envoyées à OpenAI : réessaie dans une minute.")
    object Region : OpenAiError("OpenAI n'est pas accessible depuis ce pays ou ce réseau.")
    object ModelUnavailable : OpenAiError("Aucun modèle OpenAI disponible pour cette clé.")
    object Refused : OpenAiError("OpenAI a refusé d'analyser cette photo.")
    class Server(code: Int) : OpenAiError("Service OpenAI indisponible (erreur $code). Réessaie plus tard.")
    class BadResponse(detail: String) : OpenAiError("Réponse d'OpenAI illisible. $detail".trim())
    class Other(detail: String) : OpenAiError("Erreur OpenAI : $detail")
}

/**
 * Chat Completions (REST, sans SDK) avec image en data URL base64 et sortie JSON stricte (json_schema).
 * Clé envoyée uniquement dans l'en-tête Authorization: Bearer.
 */
object OpenAiClient {
    /** Modèle et effort de raisonnement (null = paramètre non envoyé). Vérifiés le 23/11/2026 sur developers.openai.com. */
    data class ModelConf(val id: String, val reasoning: String?)
    val MODELS = listOf(
        ModelConf("gpt-6-luna", "low"),     // le moins cher : 0,10 $ / 0,50 $ par million de jetons
        ModelConf("gpt-5.4-nano", "low"),   // secours : 0,20 $ / 1,25 $
        ModelConf("gpt-4.1-mini", null),    // secours ancien (pas de paramètre de raisonnement)
    )
    private const val URL_CHAT = "https://api.openai.com/v1/chat/completions"

    fun schema(): JSONObject {
        val item = JSONObject().put("type", "object")
            .put("properties", JSONObject()
                .put("nom", JSONObject().put("type", "string"))
                .put("grammes_estimes", JSONObject().put("type", "number"))
                .put("kcal_pour_100g", JSONObject().put("type", "number"))
                .put("confiance", JSONObject().put("type", "number")))
            .put("required", JSONArray(listOf("nom", "grammes_estimes", "kcal_pour_100g", "confiance")))
            .put("additionalProperties", false)
        return JSONObject().put("type", "object")
            .put("properties", JSONObject().put("aliments", JSONObject().put("type", "array").put("items", item)))
            .put("required", JSONArray(listOf("aliments")))
            .put("additionalProperties", false)
    }

    fun requestBody(m: ModelConf, jpegBase64: String): String {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", GeminiClient.PROMPT))
            .put(JSONObject().put("type", "image_url").put("image_url",
                JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64").put("detail", "auto")))
        return JSONObject()
            .put("model", m.id)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("response_format", JSONObject().put("type", "json_schema").put("json_schema",
                JSONObject().put("name", "aliments").put("strict", true).put("schema", schema())))
            .put("max_completion_tokens", 2000)
            .put("store", false)
            .apply { m.reasoning?.let { put("reasoning_effort", it) } }
            .toString()
    }

    /** Texte JSON de la réponse (choices[0].message.content) ; lève Refused si le modèle refuse. */
    fun modelText(response: String): String? {
        val msg = JSONObject(response).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: return null
        if (msg.has("refusal") && !msg.isNull("refusal") && msg.optString("refusal").isNotBlank()) throw OpenAiError.Refused
        return if (msg.isNull("content")) null else msg.optString("content").takeIf { it.isNotBlank() }
    }

    /** Résultat d'une réponse d'erreur HTTP. */
    sealed class ErrAction {
        object NextModel : ErrAction()
        object RetryWithoutReasoning : ErrAction()
        class Fail(val error: OpenAiError) : ErrAction()
    }

    fun errorFor(code: Int, body: String?): ErrAction {
        val err = runCatching { JSONObject(body ?: "").optJSONObject("error") }.getOrNull()
        val ecode = err?.optString("code").orEmpty()
        val type = err?.optString("type").orEmpty()
        val param = err?.optString("param").orEmpty()
        val msg = err?.optString("message").orEmpty()
        return when {
            code == 404 || ecode == "model_not_found" -> ErrAction.NextModel
            code == 400 && param == "reasoning_effort" -> ErrAction.RetryWithoutReasoning
            code == 401 -> ErrAction.Fail(OpenAiError.InvalidKey)
            code == 403 && (ecode == "unsupported_country_region_territory" || msg.contains("country", true)) -> ErrAction.Fail(OpenAiError.Region)
            code == 403 && msg.contains("model", true) -> ErrAction.NextModel // modèle non autorisé pour ce projet
            code == 429 && (ecode == "insufficient_quota" || ecode == "credit_balance_exhausted" || type == "insufficient_quota" && ecode !in SPEND) ->
                ErrAction.Fail(OpenAiError.NoCredit)
            code == 429 && ecode in SPEND -> ErrAction.Fail(OpenAiError.SpendLimit)
            code == 429 -> ErrAction.Fail(OpenAiError.RateLimit)
            code >= 500 -> ErrAction.Fail(OpenAiError.Server(code))
            else -> ErrAction.Fail(OpenAiError.Other("HTTP $code ${msg.take(160)}".trim()))
        }
    }
    private val SPEND = setOf("organization_spend_limit_exceeded", "project_spend_limit_exceeded", "organization_usage_limit_exceeded")

    /** Appel bloquant (Dispatchers.IO). Renvoie les suggestions et l'id du modèle utilisé. */
    fun recognize(apiKey: String, jpegBase64: String): Pair<List<FoodGuess>, String> {
        if (apiKey.isBlank()) throw OpenAiError.NoKey
        val queue = ArrayDeque(MODELS)
        while (queue.isNotEmpty()) {
            val m = queue.removeFirst()
            val conn = try { URL(URL_CHAT).openConnection() as HttpURLConnection } catch (e: IOException) { throw OpenAiError.NoNetwork }
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000; conn.readTimeout = 90_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                conn.outputStream.use { it.write(requestBody(m, jpegBase64).toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val text = modelText(resp) ?: throw OpenAiError.BadResponse("(vide)")
                    val foods = try { GeminiParser.parseFoods(text) } catch (e: Exception) { throw OpenAiError.BadResponse("") }
                    return foods to m.id
                }
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                when (val a = errorFor(code, err)) {
                    ErrAction.NextModel -> {}
                    ErrAction.RetryWithoutReasoning -> queue.addFirst(m.copy(reasoning = null))
                    is ErrAction.Fail -> throw a.error
                }
            } catch (e: RecoError) { throw e }
            catch (e: UnknownHostException) { throw OpenAiError.NoNetwork }
            catch (e: SocketTimeoutException) { throw OpenAiError.Timeout }
            catch (e: SSLException) { throw OpenAiError.NoNetwork }
            catch (e: IOException) { throw OpenAiError.NoNetwork }
            finally { conn.disconnect() }
        }
        throw OpenAiError.ModelUnavailable
    }
}
