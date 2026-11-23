package com.wolnerman.calories

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Erreur d'un service compatible OpenAI (Groq, OpenRouter, OpenAI), message en français. */
class CloudError(userMessage: String, val kind: Kind = Kind.OTHER) : RecoError(userMessage) {
    enum class Kind { NO_KEY, NO_NETWORK, TIMEOUT, INVALID_KEY, NO_CREDIT, SPEND_LIMIT, RATE_LIMIT, REGION, NO_MODEL, REFUSED, SERVER, BAD_RESPONSE, OTHER }
}

/** Format de sortie demandé au modèle. */
enum class JsonMode { SCHEMA, OBJECT, NONE }

/** Un modèle et ses paramètres propres (effort de raisonnement, format JSON, champs supplémentaires). */
data class ModelConf(
    val id: String,
    val reasoningEffort: String? = null,
    val json: JsonMode = JsonMode.OBJECT,
    val extra: Map<String, Any> = emptyMap(),
)

/** Un service en ligne compatible avec l'API Chat Completions d'OpenAI. */
data class CloudProvider(
    val id: String,             // identifiant stocké dans les réglages
    val name: String,           // nom affiché (« Groq »)
    val label: String,          // libellé du choix dans Profil
    val baseUrl: String,
    val keyUrl: String,
    val help: String,
    val models: List<ModelConf>,
    val maxTokensParam: String = "max_completion_tokens",
    val sendStoreFalse: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)

object CloudProviders {
    const val GROQ = "groq"
    const val OPENROUTER = "openrouter"
    const val OPENAI = "openai"
    const val GEMINI = "gemini" // client séparé (GeminiClient)

    /** Vérifiés le 23/11/2026 : console.groq.com/docs/vision et /docs/reasoning. */
    val Groq = CloudProvider(
        GROQ, "Groq", "Groq (gratuit, recommandé)", "https://api.groq.com/openai/v1", "console.groq.com/keys",
        "Clé gratuite sur console.groq.com/keys (sans carte bancaire, environ 1000 photos/jour).",
        listOf(
            ModelConf("qwen/qwen3.8-27b", reasoningEffort = "none", json = JsonMode.OBJECT),
            ModelConf("meta-models/muse-spark-open", reasoningEffort = "low", json = JsonMode.OBJECT),
        ),
    )

    /** Vérifiés le 23/11/2026 sur openrouter.ai/api/v1/models (modèles gratuits avec entrée image, hors Google). */
    val OpenRouter = CloudProvider(
        OPENROUTER, "OpenRouter", "OpenRouter (gratuit, 50 photos/jour)", "https://openrouter.ai/api/v1", "openrouter.ai/keys",
        "Clé gratuite sur openrouter.ai/keys (modèles « :free », environ 50 photos/jour).",
        listOf(
            ModelConf("nvidia/nemotron-3.5-super-vl:free", json = JsonMode.OBJECT, extra = mapOf("reasoning" to mapOf("enabled" to false))),
            ModelConf("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", json = JsonMode.NONE, extra = mapOf("reasoning" to mapOf("exclude" to true))),
        ),
        maxTokensParam = "max_tokens",
        headers = mapOf("HTTP-Referer" to "https://github.com/wolnerman8-sudo/calories-android", "X-Title" to "Calories"),
    )

    /** Vérifiés le 23/11/2026 sur developers.openai.com (modèles et tarifs). */
    val OpenAi = CloudProvider(
        OPENAI, "OpenAI", "OpenAI (payant)", "https://api.openai.com/v1", "platform.openai.com/api-keys",
        "Clé sur platform.openai.com/api-keys (crédit prépayé requis, moins d'un centime par photo).",
        listOf(
            ModelConf("gpt-6-luna", reasoningEffort = "low", json = JsonMode.SCHEMA),
            ModelConf("gpt-5.4-nano", reasoningEffort = "low", json = JsonMode.SCHEMA),
            ModelConf("gpt-4.1-mini", json = JsonMode.SCHEMA),
        ),
        sendStoreFalse = true,
    )

    val ALL = listOf(Groq, OpenRouter, OpenAi)
    fun byId(id: String): CloudProvider? = ALL.firstOrNull { it.id == id }

    /** Service correspondant au préfixe d'une clé collée (null si inconnu). */
    fun detect(key: String): String? {
        val k = key.trim()
        return when {
            k.startsWith("gsk_") -> GROQ
            k.startsWith("sk-or-") -> OPENROUTER
            k.startsWith("sk-") -> OPENAI
            k.startsWith("AIza") -> GEMINI
            else -> null
        }
    }

    fun nameOf(id: String) = when (id) { GEMINI -> "Gemini"; else -> byId(id)?.name ?: id }
}

/** Client Chat Completions générique (Groq, OpenRouter, OpenAI). */
object CloudClient {
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

    private fun toJson(v: Any): Any = when (v) {
        is Map<*, *> -> JSONObject().apply { v.forEach { (k, x) -> put(k.toString(), toJson(x!!)) } }
        is List<*> -> JSONArray().apply { v.forEach { put(toJson(it!!)) } }
        else -> v
    }

    /** Corps de requête ; [jpegBase64] null = petit test de la clé (texte seul). */
    fun requestBody(p: CloudProvider, m: ModelConf, jpegBase64: String?, maxTokens: Int = 2000): String {
        val content: Any = if (jpegBase64 == null) "Réponds uniquement par le JSON {\"ok\": true}." else JSONArray()
            .put(JSONObject().put("type", "text").put("text", GeminiClient.PROMPT))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64")))
        val o = JSONObject()
            .put("model", m.id)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put(p.maxTokensParam, maxTokens)
        m.reasoningEffort?.let { o.put("reasoning_effort", it) }
        if (p.sendStoreFalse) o.put("store", false)
        when (if (jpegBase64 == null && m.json == JsonMode.SCHEMA) JsonMode.OBJECT else m.json) {
            JsonMode.SCHEMA -> o.put("response_format", JSONObject().put("type", "json_schema").put("json_schema",
                JSONObject().put("name", "aliments").put("strict", true).put("schema", schema())))
            JsonMode.OBJECT -> o.put("response_format", JSONObject().put("type", "json_object"))
            JsonMode.NONE -> {}
        }
        m.extra.forEach { (k, v) -> o.put(k, toJson(v)) }
        return o.toString()
    }

    /** Texte de la réponse (choices[0].message.content) ; CloudError REFUSED si le modèle refuse. */
    fun modelText(response: String): String? {
        val msg = JSONObject(response).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: return null
        if (msg.has("refusal") && !msg.isNull("refusal") && msg.optString("refusal").isNotBlank())
            throw CloudError("Le service a refusé d'analyser cette photo.", CloudError.Kind.REFUSED)
        return if (msg.isNull("content")) null else msg.optString("content").takeIf { it.isNotBlank() }
    }

    sealed class ErrAction {
        object NextModel : ErrAction()
        class RetryWithout(val param: String) : ErrAction()
        class Fail(val error: CloudError) : ErrAction()
    }
    private val SPEND = setOf("organization_spend_limit_exceeded", "project_spend_limit_exceeded", "organization_usage_limit_exceeded")
    private val OPTIONAL = setOf("reasoning_effort", "response_format", "reasoning", "store")

    /** Traduit une réponse d'erreur HTTP (format { "error": { message, type, code, param } }). */
    fun errorFor(p: CloudProvider, code: Int, body: String?): ErrAction {
        val err = runCatching { JSONObject(body ?: "").optJSONObject("error") }.getOrNull()
        val ecode = err?.opt("code")?.toString().orEmpty()
        val type = err?.optString("type").orEmpty()
        val param = err?.optString("param").orEmpty()
        val msg = err?.optString("message").orEmpty()
        val n = p.name
        fun fail(m: String, k: CloudError.Kind) = ErrAction.Fail(CloudError(m, k))
        // Paramètre optionnel refusé par ce modèle : on réessaie sans lui
        val badParam = OPTIONAL.firstOrNull { it == param } ?: if (code == 400) OPTIONAL.firstOrNull { msg.contains("`$it`") || msg.contains("'$it'") || msg.contains("\"$it\"") } else null
        return when {
            code == 404 || ecode == "model_not_found" || ecode == "model_decommissioned" -> ErrAction.NextModel
            code == 400 && badParam != null -> ErrAction.RetryWithout(badParam)
            code == 401 -> fail("Clé API $n invalide ou révoquée. Vérifie-la dans Profil › Reconnaissance par photo.", CloudError.Kind.INVALID_KEY)
            code == 402 -> fail(if (p.id == CloudProviders.OPENROUTER) "Crédit OpenRouter insuffisant pour ce modèle : choisis un modèle gratuit ou ajoute du crédit sur openrouter.ai."
                else "Crédit $n épuisé : ajoute du crédit sur le site de $n.", CloudError.Kind.NO_CREDIT)
            code == 403 && (ecode == "unsupported_country_region_territory" || msg.contains("country", true) || msg.contains("region", true)) ->
                fail("$n n'est pas accessible depuis ce pays ou ce réseau.", CloudError.Kind.REGION)
            code == 403 && msg.contains("model", true) -> ErrAction.NextModel // modèle non autorisé pour ce compte
            code == 429 && p.id == CloudProviders.OPENAI && (ecode == "insufficient_quota" || ecode == "credit_balance_exhausted" || type == "insufficient_quota" && ecode !in SPEND) ->
                fail("Crédit OpenAI épuisé ou non ajouté : ajoute du crédit sur platform.openai.com", CloudError.Kind.NO_CREDIT)
            code == 429 && ecode in SPEND -> fail("Limite de dépenses $n atteinte : augmente-la dans les réglages de ton compte $n.", CloudError.Kind.SPEND_LIMIT)
            code == 429 && p.id == CloudProviders.OPENROUTER -> fail("Limite gratuite OpenRouter atteinte (environ 50 photos par jour) : réessaie demain ou choisis Groq.", CloudError.Kind.RATE_LIMIT)
            code == 429 && p.id == CloudProviders.GROQ -> fail("Limite gratuite Groq atteinte : réessaie dans une minute (ou demain si le quota du jour est épuisé).", CloudError.Kind.RATE_LIMIT)
            code == 429 -> fail("Trop de demandes envoyées à $n : réessaie dans une minute.", CloudError.Kind.RATE_LIMIT)
            code == 413 -> fail("Photo trop lourde pour $n.", CloudError.Kind.OTHER)
            code >= 500 -> fail("Service $n indisponible (erreur $code). Réessaie plus tard.", CloudError.Kind.SERVER)
            else -> fail("Erreur $n : HTTP $code ${msg.take(160)}".trim(), CloudError.Kind.OTHER)
        }
    }

    private fun post(p: CloudProvider, key: String, body: String, readTimeout: Int): Pair<Int, String?> {
        val conn = try { URL("${p.baseUrl}/chat/completions").openConnection() as HttpURLConnection }
            catch (e: IOException) { throw CloudError("Pas de connexion Internet.", CloudError.Kind.NO_NETWORK) }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000; conn.readTimeout = readTimeout
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer ${key.trim()}")
            p.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }
            return code to text
        } catch (e: RecoError) { throw e }
        catch (e: UnknownHostException) { throw CloudError("Pas de connexion Internet : reconnaissance hors-ligne utilisée.", CloudError.Kind.NO_NETWORK) }
        catch (e: SocketTimeoutException) { throw CloudError("${p.name} ne répond pas (délai dépassé) : reconnaissance hors-ligne utilisée.", CloudError.Kind.TIMEOUT) }
        catch (e: SSLException) { throw CloudError("Connexion sécurisée impossible (réseau).", CloudError.Kind.NO_NETWORK) }
        catch (e: IOException) { throw CloudError("Pas de connexion Internet : reconnaissance hors-ligne utilisée.", CloudError.Kind.NO_NETWORK) }
        finally { conn.disconnect() }
    }

    /** Essaie les modèles dans l'ordre ; retire un paramètre optionnel refusé. Renvoie (réponse, modèle). */
    private fun run(p: CloudProvider, key: String, jpegBase64: String?, maxTokens: Int, readTimeout: Int): Pair<String, String> {
        if (key.isBlank()) throw CloudError("Aucune clé API ${p.name} enregistrée (Profil › Reconnaissance par photo) : mode hors-ligne.", CloudError.Kind.NO_KEY)
        val queue = ArrayDeque(p.models)
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 10) {
            val m = queue.removeFirst()
            val (code, body) = post(p, key, requestBody(p, m, jpegBase64, maxTokens), readTimeout)
            if (code in 200..299) return (body ?: "") to m.id
            when (val a = errorFor(p, code, body)) {
                ErrAction.NextModel -> {}
                is ErrAction.RetryWithout -> queue.addFirst(when (a.param) {
                    "reasoning_effort" -> m.copy(reasoningEffort = null)
                    "response_format" -> m.copy(json = JsonMode.NONE)
                    else -> m.copy(extra = m.extra - a.param)
                })
                is ErrAction.Fail -> throw a.error
            }
        }
        throw CloudError("Aucun modèle ${p.name} disponible pour cette clé.", CloudError.Kind.NO_MODEL)
    }

    /** Reconnaissance (bloquant, Dispatchers.IO). Renvoie les suggestions et l'id du modèle utilisé. */
    fun recognize(p: CloudProvider, key: String, jpegBase64: String): Pair<List<FoodGuess>, String> {
        val (resp, model) = run(p, key, jpegBase64, 2000, 90_000)
        val text = try { modelText(resp) } catch (e: RecoError) { throw e } catch (e: Exception) { null }
            ?: throw CloudError("Réponse de ${p.name} vide ou illisible.", CloudError.Kind.BAD_RESPONSE)
        val foods = try { GeminiParser.parseFoods(text) } catch (e: Exception) {
            throw CloudError("Réponse de ${p.name} illisible (JSON invalide).", CloudError.Kind.BAD_RESPONSE)
        }
        return foods to model
    }

    /** « Tester la clé » : petite requête texte. Renvoie l'id du modèle qui a répondu, ou lève CloudError. */
    fun testKey(p: CloudProvider, key: String): String = run(p, key, null, 64, 30_000).second
}
