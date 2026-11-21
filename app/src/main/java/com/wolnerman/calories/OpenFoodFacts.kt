package com.wolnerman.calories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class ScannedProduct(
    val barcode: String,
    val name: String,
    val kcalPer100g: Double?,
    val proteins: Double?,
    val carbs: Double?,
    val fat: Double?,
    val servingSize: String?,
    val nutr: Map<String, Double> = emptyMap(),
    val fermented: Boolean = false,
    val prebiotic: Boolean = false,
)

private val FERMENT_KEYWORDS = listOf("ferment", "yogurt", "yoghurt", "yaourt", "kefir", "kéfir", "kimchi",
    "sauerkraut", "choucroute", "probiotic", "probiotique", "kombucha", "miso", "tempeh", "bifidus", "lactobacil")

/** Mots-clés prébiotiques (ingrédients) et catégories légumineuses / avoine. */
private val PREBIOTIC_REGEX = Regex(
    "inulin|inuline|\\bfos\\b|fructo-?oligosacchar|\\bgos\\b|galacto-?oligosacchar|chicor[ée]e|chicory|" +
    "en:legumes|en:pulses|lentil|lentille|chickpea|pois-chiche|pois chiche|haricot|en:beans|" +
    "\\boat|avoine|flocons-d-avoine|en:oat")

sealed class LookupResult {
    data class Found(val product: ScannedProduct) : LookupResult()
    data class Error(val message: String) : LookupResult()
}

/** Recherche d'un produit par code-barres sur Open Food Facts (API v2). */
object OpenFoodFacts {
    private const val UA = "Calories-Android/1.2 (wolnerman)"

    suspend fun lookup(barcodeRaw: String): LookupResult = withContext(Dispatchers.IO) {
        val barcode = barcodeRaw.filter(Char::isDigit)
        if (barcode.length !in 8..14) return@withContext LookupResult.Error("Code-barres invalide (8 à 14 chiffres attendus).")
        val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json" +
            "?fields=product_name,product_name_fr,brands,nutriments,serving_size,categories_tags,labels_tags,ingredients_text,ingredients_text_fr")
        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000; readTimeout = 15000
            }
            val code = conn.responseCode
            if (code == 404) return@withContext LookupResult.Error("Produit introuvable dans Open Food Facts. Saisis-le manuellement.")
            if (code != 200) return@withContext LookupResult.Error("Erreur du serveur Open Food Facts ($code). Réessaie plus tard.")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("status", 0) != 1 || !json.has("product"))
                return@withContext LookupResult.Error("Produit introuvable dans Open Food Facts. Saisis-le manuellement.")
            val p = json.getJSONObject("product")
            val n = p.optJSONObject("nutriments") ?: JSONObject()
            fun num(key: String): Double? = if (n.has(key)) n.optDouble(key).takeIf { !it.isNaN() } else null
            val kcal = num("energy-kcal_100g") ?: num("energy_100g")?.let { it / 4.184 }
            val baseName = p.optString("product_name_fr").ifBlank { p.optString("product_name") }.trim()
            val brand = p.optString("brands").split(",").firstOrNull()?.trim().orEmpty()
            val name = when {
                baseName.isBlank() && brand.isBlank() -> "Produit $barcode"
                brand.isBlank() || baseName.contains(brand, ignoreCase = true) -> baseName.ifBlank { brand }
                baseName.isBlank() -> brand
                else -> "$baseName ($brand)"
            }
            // OFF donne minéraux et vitamines en g/100 g : conversion en mg ou µg
            val nutr = buildMap {
                num("fiber_100g")?.let { put("fiber", it) }
                num("sugars_100g")?.let { put("sugars", it) }
                num("salt_100g")?.let { put("salt", it) }
                num("saturated-fat_100g")?.let { put("satfat", it) }
                num("calcium_100g")?.let { put("calcium", it * 1000) }
                num("iron_100g")?.let { put("iron", it * 1000) }
                num("vitamin-c_100g")?.let { put("vitc", it * 1000) }
                num("vitamin-d_100g")?.let { put("vitd", it * 1_000_000) }
            }
            val tags = buildString {
                append(name.lowercase()); append(' ')
                listOf("categories_tags", "labels_tags").forEach { k ->
                    p.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) { append(a.optString(i).lowercase()); append(' ') } }
                }
            }
            val fermented = FERMENT_KEYWORDS.any { tags.contains(it) }
            val ingredients = (p.optString("ingredients_text_fr") + " " + p.optString("ingredients_text")).lowercase()
            val prebiotic = PREBIOTIC_REGEX.containsMatchIn("$tags $ingredients")
            LookupResult.Found(ScannedProduct(
                barcode = barcode, name = name,
                kcalPer100g = kcal?.let { (it * 10).roundToInt() / 10.0 },
                proteins = num("proteins_100g"), carbs = num("carbohydrates_100g"), fat = num("fat_100g"),
                servingSize = p.optString("serving_size").ifBlank { null },
                nutr = nutr, fermented = fermented, prebiotic = prebiotic,
            ))
        } catch (e: IOException) {
            LookupResult.Error("Pas de connexion Internet. Vérifie ton réseau ou saisis l'aliment manuellement.")
        } catch (e: Exception) {
            LookupResult.Error("Réponse inattendue d'Open Food Facts. Saisis l'aliment manuellement.")
        } finally {
            conn?.disconnect()
        }
    }
}
