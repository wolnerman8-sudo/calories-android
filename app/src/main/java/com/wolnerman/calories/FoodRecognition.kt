package com.wolnerman.calories

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Une suggestion d'aliment reconnue sur la photo. */
data class FoodGuess(
    val name: String,
    val grams: Double?,          // quantité estimée dans la photo (Gemini seulement)
    val kcal100: Double?,        // kcal pour 100 g
    val confidence: Double,      // 0..1
    val offline: Boolean,        // true = modèle hors-ligne (moins précis)
    val label: String? = null,   // étiquette d'origine (anglais) pour le hors-ligne
    val food: Food? = null,      // aliment intégré correspondant (macros), si trouvé
)

/** Lecture de la réponse JSON de Gemini (generateContent), tolérante aux variantes. */
object GeminiParser {
    /** Extrait le texte JSON du modèle depuis la réponse REST complète. Null si absent (bloqué, vide…). */
    fun modelText(response: String): String? {
        val root = JSONObject(response)
        val cands = root.optJSONArray("candidates") ?: return null
        if (cands.length() == 0) return null
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optBoolean("thought", false)) continue // ignorer les « pensées » éventuelles
            sb.append(p.optString("text", ""))
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    /** Raison de blocage éventuelle (promptFeedback.blockReason ou finishReason SAFETY…). */
    fun blockReason(response: String): String? = runCatching {
        val root = JSONObject(response)
        root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }
            ?: root.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason")
                ?.takeIf { it in setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "IMAGE_SAFETY", "RECITATION") }
    }.getOrNull()

    /** Analyse le JSON produit par le modèle : liste ou objet {"aliments": [...]}; accepte les blocs ```json. */
    fun parseFoods(text: String): List<FoodGuess> {
        var t = text.trim()
        if (t.startsWith("```")) t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()
        val arr: JSONArray = when {
            t.startsWith("[") -> JSONArray(t)
            t.startsWith("{") -> {
                val o = JSONObject(t)
                o.optJSONArray("aliments") ?: o.optJSONArray("foods") ?: o.optJSONArray("items")
                    ?: if (o.has("nom")) JSONArray().put(o) else JSONArray()
            }
            else -> {
                val s = t.indexOf('['); val e = t.lastIndexOf(']')
                if (s >= 0 && e > s) JSONArray(t.substring(s, e + 1)) else throw IllegalArgumentException("pas de JSON")
            }
        }
        val out = ArrayList<FoodGuess>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = (o.optString("nom").ifBlank { o.optString("name") }).trim()
            if (name.isEmpty()) continue
            val grams = num(o, "grammes_estimes", "grammes", "grams")?.takeIf { it > 0 && it <= 5000 }
            val kcal = num(o, "kcal_pour_100g", "kcal_100g", "kcal")?.takeIf { it >= 0 && it <= 950 }
            out += FoodGuess(name.replaceFirstChar { it.uppercase() }, grams?.let { Math.round(it).toDouble() }, kcal,
                confidence(o.opt("confiance") ?: o.opt("confidence")), offline = false,
                food = BUILTIN_FOODS.firstOrNull { fold(it.name) == fold(name) })
        }
        return out.sortedByDescending { it.confidence }
    }

    private fun num(o: JSONObject, vararg keys: String): Double? {
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            val v = o.opt(k)
            val d = when (v) {
                is Number -> v.toDouble()
                is String -> Regex("-?\\d+(?:[.,]\\d+)?").find(v)?.value?.replace(',', '.')?.toDoubleOrNull()
                else -> null
            }
            if (d != null && !d.isNaN()) return d
        }
        return null
    }

    /** Confiance normalisée 0..1 : accepte 0.8, 80, "80 %", "élevée"/"moyenne"/"faible". */
    fun confidence(v: Any?): Double {
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> {
                val f = fold(v)
                when {
                    f.startsWith("elev") || f.startsWith("haut") || f == "high" -> 0.85
                    f.startsWith("moy") || f == "medium" -> 0.6
                    f.startsWith("faib") || f.startsWith("bas") || f == "low" -> 0.3
                    else -> Regex("\\d+(?:[.,]\\d+)?").find(v)?.value?.replace(',', '.')?.toDoubleOrNull() ?: 0.5
                }
            }
            else -> 0.5
        }
        val n = if (d > 1.0) d / 100.0 else d
        return n.coerceIn(0.0, 1.0)
    }
}

/** Erreurs Gemini, avec message en français pour l'utilisateur. */
sealed class GeminiError(val userMessage: String) : Exception(userMessage) {
    object NoKey : GeminiError("Aucune clé API Gemini enregistrée (Profil › Reconnaissance par photo).")
    object NoNetwork : GeminiError("Pas de connexion Internet : reconnaissance hors-ligne utilisée.")
    object Timeout : GeminiError("Gemini ne répond pas (délai dépassé) : reconnaissance hors-ligne utilisée.")
    object InvalidKey : GeminiError("Clé API Gemini invalide. Vérifie-la dans Profil › Reconnaissance par photo.")
    class Denied(detail: String) : GeminiError("Accès refusé par Gemini (clé restreinte ou API non activée). $detail".trim())
    object Quota : GeminiError("Quota Gemini dépassé (trop de demandes). Réessaie dans une minute, ou plus tard aujourd'hui.")
    object ModelUnavailable : GeminiError("Aucun modèle Gemini disponible pour cette clé.")
    class Blocked(reason: String) : GeminiError("Gemini a refusé d'analyser cette photo ($reason).")
    class Server(code: Int) : GeminiError("Service Gemini indisponible (erreur $code). Réessaie plus tard.")
    class BadResponse(detail: String) : GeminiError("Réponse de Gemini illisible. $detail".trim())
    class Other(detail: String) : GeminiError("Erreur Gemini : $detail")
}

/** Appel REST generateContent (sans SDK), clé envoyée dans l'en-tête x-goog-api-key (jamais dans l'URL). */
object GeminiClient {
    /** Modèles essayés dans l'ordre ; on passe au suivant si le modèle n'existe pas (404). Vérifiés le 23/11/2026. */
    val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-flash-latest")
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    const val PROMPT = "Tu es un assistant de nutrition. Identifie chaque aliment ou plat visible sur cette photo de repas. " +
        "Pour chacun, donne : \"nom\" (nom courant en français, précis, ex. « Riz blanc cuit », « Poulet colombo »), " +
        "\"grammes_estimes\" (quantité visible estimée en grammes, nombre), \"kcal_pour_100g\" (valeur énergétique moyenne " +
        "pour 100 g de cet aliment tel que préparé, nombre) et \"confiance\" (entre 0 et 1). " +
        "Réponds uniquement en JSON strict, sans texte autour : {\"aliments\": [{\"nom\": \"...\", \"grammes_estimes\": 0, " +
        "\"kcal_pour_100g\": 0, \"confiance\": 0.0}]}. Au maximum 5 aliments, du plus important au moins important. " +
        "Si aucun aliment n'est visible, renvoie {\"aliments\": []}."

    fun requestBody(jpegBase64: String): String {
        val item = JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject()
                .put("nom", JSONObject().put("type", "STRING"))
                .put("grammes_estimes", JSONObject().put("type", "NUMBER"))
                .put("kcal_pour_100g", JSONObject().put("type", "NUMBER"))
                .put("confiance", JSONObject().put("type", "NUMBER")))
            .put("required", JSONArray(listOf("nom", "grammes_estimes", "kcal_pour_100g", "confiance")))
        val schema = JSONObject().put("type", "OBJECT")
            .put("properties", JSONObject().put("aliments", JSONObject().put("type", "ARRAY").put("items", item)))
            .put("required", JSONArray(listOf("aliments")))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", PROMPT))
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", jpegBase64))))))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseSchema", schema)
                .put("temperature", 0.2))
            .toString()
    }

    /** Traduit une réponse d'erreur HTTP en [GeminiError] (null = 404 → essayer le modèle suivant). */
    fun errorFor(code: Int, body: String?): GeminiError? {
        val err = runCatching { JSONObject(body ?: "").optJSONObject("error") }.getOrNull()
        val msg = err?.optString("message").orEmpty()
        val status = err?.optString("status").orEmpty()
        val reasons = buildString {
            val d = err?.optJSONArray("details")
            if (d != null) for (i in 0 until d.length()) append(d.optJSONObject(i)?.optString("reason").orEmpty()).append(' ')
        }
        return when {
            code == 404 -> null
            reasons.contains("API_KEY_INVALID") || msg.contains("API key not valid", true) || msg.contains("API key expired", true) -> GeminiError.InvalidKey
            code == 401 -> GeminiError.InvalidKey
            code == 403 -> GeminiError.Denied(msg.take(120))
            code == 429 || status == "RESOURCE_EXHAUSTED" -> GeminiError.Quota
            code >= 500 -> GeminiError.Server(code)
            msg.contains("location is not supported", true) -> GeminiError.Other("l'API n'est pas disponible depuis ce pays/région.")
            else -> GeminiError.Other("HTTP $code ${msg.take(160)}".trim())
        }
    }

    /** Appel bloquant (à faire sur Dispatchers.IO). Renvoie les suggestions et l'id du modèle utilisé. */
    fun recognize(apiKey: String, jpegBase64: String): Pair<List<FoodGuess>, String> {
        if (apiKey.isBlank()) throw GeminiError.NoKey
        val body = requestBody(jpegBase64).toByteArray(Charsets.UTF_8)
        for (model in MODELS) {
            val conn = try { URL("$BASE$model:generateContent").openConnection() as HttpURLConnection }
                catch (e: IOException) { throw GeminiError.NoNetwork }
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000; conn.readTimeout = 60_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-goog-api-key", apiKey.trim())
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                if (code in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    GeminiParser.blockReason(resp)?.let { throw GeminiError.Blocked(it) }
                    val text = GeminiParser.modelText(resp) ?: throw GeminiError.BadResponse("(vide)")
                    val foods = try { GeminiParser.parseFoods(text) } catch (e: Exception) { throw GeminiError.BadResponse("") }
                    return foods to model
                }
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw errorFor(code, err) ?: continue
            } catch (e: GeminiError) { throw e }
            catch (e: UnknownHostException) { throw GeminiError.NoNetwork }
            catch (e: SocketTimeoutException) { throw GeminiError.Timeout }
            catch (e: SSLException) { throw GeminiError.NoNetwork }
            catch (e: IOException) { throw GeminiError.NoNetwork }
            finally { conn.disconnect() }
        }
        throw GeminiError.ModelUnavailable
    }
}
