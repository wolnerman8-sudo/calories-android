package com.wolnerman.calories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Brouillon prérempli après un scan de complément (à confirmer dans le dialogue). */
data class ScannedSupplementDraft(
    val barcode: String,
    val name: String = "",
    val dose: String = "",
    val kcal: Int? = null,
    val omega3Mg: Double? = null,
    val recognized: Boolean = false,
    val message: String? = null,
    val source: String? = null,
)

/** Analyse (sans réseau) et recherche d'un complément par code-barres : Open Food Facts, puis Open Products / Beauty Facts. */
object SupplementScanLogic {
    const val UNRECOGNIZED = "Produit non reconnu : prends l'étiquette en photo pour préremplir, ou saisis le nom et la dose."
    private const val UA = "Calories-Android/2.18 (wolnerman)"
    private val HOSTS = listOf(
        "https://world.openfoodfacts.org" to "Open Food Facts",
        "https://world.openproductsfacts.org" to "Open Products Facts",
        "https://world.openbeautyfacts.org" to "Open Beauty Facts",
    )
    private const val FIELDS =
        "product_name,product_name_fr,generic_name,generic_name_fr,brands,nutriments,serving_size,quantity"

    internal val COUNTABLE = listOf(
        "gélules" to "gélule", "gelules" to "gélule", "gelule" to "gélule", "gélule" to "gélule",
        "capsules" to "capsule", "capsule" to "capsule",
        "comprimés" to "comprimé", "comprimes" to "comprimé", "comprimé" to "comprimé", "comprime" to "comprimé",
        "softgels" to "softgel", "softgel" to "softgel",
        "tablettes" to "tablette", "tablette" to "tablette",
        "perles" to "perle", "perle" to "perle",
        "sachets" to "sachet", "sachet" to "sachet",
        "ampoules" to "ampoule", "ampoule" to "ampoule",
        "gummies" to "gummy", "gummy" to "gummy",
        "gommes" to "gomme", "gomme" to "gomme",
    )

    fun normalizeBarcode(raw: String): String? {
        val d = raw.filter(Char::isDigit)
        return d.takeIf { it.length in 8..14 }
    }

    fun findByBarcode(list: List<Supplement>, barcodeRaw: String): Supplement? {
        val code = normalizeBarcode(barcodeRaw) ?: return null
        return list.firstOrNull { normalizeBarcode(it.barcode ?: "") == code }
    }

    /** Nom affiché : product_name (+ marque si elle n'est pas déjà dans le nom). */
    fun displayName(productName: String?, brand: String?): String {
        val base = productName?.trim().orEmpty()
        val b = brand?.split(",")?.firstOrNull()?.trim().orEmpty()
        return when {
            base.isBlank() && b.isBlank() -> ""
            b.isBlank() || base.contains(b, ignoreCase = true) -> base.ifBlank { b }
            base.isBlank() -> b
            else -> "$base ($b)"
        }
    }

    /**
     * Dose dérivée, au mieux, sans inventer :
     * 1) serving_size s'il a l'air d'une prise (unité ou nombre + unité) ;
     * 2) sinon quantity du type « 60 gélules » → « 1 gélule » ;
     * 3) sinon UI/mg clairement indiqué dans le nom (ex. Vitamine D 1000 UI).
     */
    fun deriveDose(servingSize: String?, quantity: String?, vararg nameParts: String?): String? {
        doseFromServing(servingSize)?.let { return it }
        doseFromPackCount(quantity)?.let { return it }
        nameParts.forEach { doseFromName(it)?.let { d -> return d } }
        return null
    }

    fun doseFromServing(raw: String?): String? {
        val s = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (s.isEmpty() || s == "-" || s == "0") return null
        // Un nombre tout seul, sans unité : trop ambigu, on n'invente pas.
        if (Regex("^\\d+([.,]\\d+)?$").matches(s)) return null
        return s
    }

    fun doseFromPackCount(raw: String?): String? {
        val s = raw?.trim()?.lowercase()?.replace(Regex("\\s+"), " ") ?: return null
        if (s.isEmpty()) return null
        // « 60 gélules » (emballage) → prise par défaut « 1 gélule ». Pas les masses/volumes de flacon (« 60 g », « 15 ml »).
        for ((form, singular) in COUNTABLE.sortedByDescending { it.first.length }) {
            if (Regex("""(?i)(\d+)\s*""" + Regex.escape(form) + """\b""").containsMatchIn(s)) return "1 $singular"
        }
        return null
    }

    /**
     * Ex. « Vitamine D3 1000 UI », « Vitamin D 2000 IU », « Magnésium 300 mg ».
     * Si plusieurs doses distinctes apparaissent (ex. C 500 mg + Zinc 15 mg), on n'en choisit aucune.
     */
    fun doseFromName(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val ui = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(u\.?\s*i\.?|i\.?\s*u\.?)\b""").findAll(s).toList()
        val mg = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(mg|µg|ug|mcg)\b""").findAll(s).toList()
        val countable = COUNTABLE.map { it.first }.distinct().joinToString("|") { Regex.escape(it) }
        val packs = Regex("""(?i)(\d+)\s*($countable)s?\b""").findAll(s).toList()
        val doses = (ui.map { normalizeUi(it.groupValues[1]) + " UI" } +
            mg.map { it.groupValues[1].replace('.', ',') + " " + normalizeMicro(it.groupValues[2]) } +
            packs.map { "1 " + singularOf(it.groupValues[2]) }).distinct()
        // Plusieurs doses chiffrées différentes (hors simple compte d'unités d'emballage) : on n'invente pas.
        val nutrientDoses = (ui + mg).map { it.groupValues[1].replace(',', '.') + it.groupValues[2].lowercase() }.distinct()
        if (nutrientDoses.size > 1) return null
        return when {
            ui.size == 1 -> normalizeUi(ui[0].groupValues[1]) + " UI"
            mg.size == 1 && ui.isEmpty() -> mg[0].groupValues[1].replace('.', ',') + " " + normalizeMicro(mg[0].groupValues[2])
            packs.size == 1 && ui.isEmpty() && mg.isEmpty() -> "1 " + singularOf(packs[0].groupValues[2])
            else -> null
        }
    }

    private fun normalizeUi(n: String) = n.replace(',', '.').let { if (it.endsWith(".0")) it.dropLast(2) else it.replace('.', ',') }
    private fun normalizeMicro(u: String) = when (u.lowercase()) {
        "ug", "mcg", "µg" -> "µg"
        else -> "mg"
    }
    private fun singularOf(unit: String): String =
        COUNTABLE.firstOrNull { it.first.equals(unit, ignoreCase = true) }?.second
            ?: COUNTABLE.firstOrNull { unit.startsWith(it.second, ignoreCase = true) }?.second
            ?: unit.lowercase()

    fun kcalFromNutriments(n: JSONObject): Int? {
        val v = num(n, "energy-kcal_serving") ?: return null
        if (v < 0.0) return null
        return v.roundToInt().takeIf { it >= 0 }
    }

    /**
     * Oméga-3 d'une prise, en mg. OFF exprime en général [omega-3-fat_serving] en grammes ;
     * on respecte l'unité si elle est fournie, et on ignore une valeur aberrante (> 50 g sans unité mg).
     */
    fun omega3MgFromNutriments(n: JSONObject): Double? {
        servingToMg(n, "omega-3-fat")?.let { return it }
        val epa = servingToMg(n, "eicosapentaenoic-acid")
        val dha = servingToMg(n, "docosahexaenoic-acid")
        if (epa == null && dha == null) return null
        val sum = (epa ?: 0.0) + (dha ?: 0.0)
        return sum.takeIf { it > 0.0 }
    }

    private fun servingToMg(n: JSONObject, key: String): Double? {
        val v = num(n, "${key}_serving") ?: return null
        if (v <= 0.0 || v.isNaN()) return null
        val unit = n.optString("${key}_serving_unit").ifBlank { n.optString("${key}_unit") }.lowercase()
        return when {
            unit == "mg" || unit == "milligram" || unit == "milligrams" -> v
            unit == "µg" || unit == "ug" || unit == "mcg" || unit == "microgram" -> v / 1000.0
            unit == "g" || unit == "gram" || unit == "grams" -> v * 1000.0
            unit.isBlank() && v > 50.0 -> null // ne pas traiter 1000 comme 1000 g
            else -> v * 1000.0 // défaut OFF : grammes
        }
    }

    private fun num(n: JSONObject, key: String): Double? =
        if (n.has(key) && !n.isNull(key)) n.optDouble(key).takeIf { !it.isNaN() } else null

    fun parseProduct(product: JSONObject, barcode: String, source: String? = "Open Food Facts"): ScannedSupplementDraft {
        val baseName = sequenceOf("product_name_fr", "product_name", "generic_name_fr", "generic_name")
            .map { product.optString(it).trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val brand = product.optString("brands")
        val name = displayName(baseName, brand)
        val nutr = product.optJSONObject("nutriments") ?: JSONObject()
        val serving = product.optString("serving_size").ifBlank { null }
        val quantity = product.optString("quantity").ifBlank { null }
        val dose = deriveDose(serving, quantity, baseName, name)?.trim().orEmpty()
        return ScannedSupplementDraft(
            barcode = barcode,
            name = name,
            dose = dose,
            kcal = kcalFromNutriments(nutr),
            omega3Mg = omega3MgFromNutriments(nutr),
            recognized = name.isNotBlank(),
            message = if (name.isBlank()) UNRECOGNIZED else null,
            source = source,
        )
    }

    fun unrecognized(barcode: String) = ScannedSupplementDraft(barcode = barcode, message = UNRECOGNIZED)

    fun toSupplement(draft: ScannedSupplementDraft, existing: Supplement? = null): Supplement {
        val base = existing ?: Supplement(0L, "")
        return base.copy(
            name = draft.name,
            dose = draft.dose,
            kcal = draft.kcal,
            omega3Mg = draft.omega3Mg,
            barcode = draft.barcode,
        )
    }

    /** Recherche réseau : OFF, puis OPF / OBF. Jamais d'exception vers l'UI. */
    suspend fun lookup(barcodeRaw: String): ScannedSupplementDraft = withContext(Dispatchers.IO) {
        val barcode = normalizeBarcode(barcodeRaw)
            ?: return@withContext ScannedSupplementDraft(barcode = barcodeRaw.filter(Char::isDigit), message = UNRECOGNIZED)
        var sawNetworkError = false
        for ((host, label) in HOSTS) {
            when (val r = fetch(host, barcode)) {
                is Fetch.Found -> return@withContext parseProduct(r.product, barcode, label).let {
                    if (it.recognized) it else it.copy(message = UNRECOGNIZED, recognized = false)
                }
                Fetch.Missing -> { /* essayer l'hôte suivant */ }
                Fetch.Network -> sawNetworkError = true
            }
        }
        unrecognized(barcode).copy(message = UNRECOGNIZED)
            .also { if (sawNetworkError) { /* message unique demandé, même hors-ligne */ } }
    }

    private sealed class Fetch {
        data class Found(val product: JSONObject) : Fetch()
        data object Missing : Fetch()
        data object Network : Fetch()
    }

    private fun fetch(host: String, barcode: String): Fetch {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$host/api/v2/product/$barcode.json?fields=$FIELDS")
            conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000; readTimeout = 15000
            }
            val code = conn.responseCode
            if (code == 404) return Fetch.Missing
            if (code != 200) return Fetch.Missing
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("status", 0) != 1 || !json.has("product")) Fetch.Missing
            else Fetch.Found(json.getJSONObject("product"))
        } catch (e: IOException) {
            Fetch.Network
        } catch (e: Exception) {
            Fetch.Missing
        } finally {
            conn?.disconnect()
        }
    }
}
