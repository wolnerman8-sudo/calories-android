package com.wolnerman.calories

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * Export du journal. CSV pour Excel / LibreOffice en français : UTF-8 avec BOM, séparateur « ; »,
 * virgule décimale, une ligne d'en-tête. JSON : nombres avec point (format standard), pour sauvegarde ou autre appli.
 */
object ExportData {
    const val BOM = "\uFEFF"
    private const val SEP = ";"
    private const val NL = "\r\n"

    /** Nombre au format français (« 12,5 »), sans séparateur de milliers ; vide si null. */
    fun num(d: Double?, decimals: Int = 2): String {
        if (d == null || d.isNaN()) return ""
        var s = String.format(Locale.US, "%.${decimals}f", d)
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return (if (s == "-0") "0" else s).replace('.', ',')
    }

    /** Échappement CSV : guillemets si le champ contient ; " ou un retour à la ligne. */
    fun cell(s: String): String =
        if (s.contains(SEP) || s.contains('"') || s.contains('\n') || s.contains('\r')) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun row(vararg cells: String) = cells.joinToString(SEP) { cell(it) } + NL

    val ENTRY_HEADER: List<String> = listOf("Date", "Heure", "Aliment", "Quantité (g)", "kcal/100 g", "kcal",
        "Glucides (g)", "Protéines (g)", "Lipides (g)") + NUTRIENTS.map { "${it.label} (${it.unit})" } + listOf("Fermenté", "Prébiotique")

    private fun sorted(entries: List<Entry>) = entries.sortedWith(compareBy({ it.date }, { it.time }, { it.id }))

    /** Une ligne par aliment, du plus ancien au plus récent. */
    fun entriesCsv(entries: List<Entry>): String = buildString {
        append(BOM); append(row(*ENTRY_HEADER.toTypedArray()))
        for (e in sorted(entries)) {
            val cells = listOf(e.date, e.time, e.name, num(e.grams, 1), num(e.kcalPer100g, 1), e.kcal.toString(),
                if (e.carbs100 != null) num(e.carbs) else "", if (e.proteins100 != null) num(e.proteins) else "",
                if (e.fat100 != null) num(e.fat) else "") +
                NUTRIENTS.map { num(e.nutrient(it.key)) } +
                listOf(if (e.fermented) "oui" else "non", if (e.prebiotic) "oui" else "non")
            append(row(*cells.toTypedArray()))
        }
    }

    val DAILY_HEADER = listOf("Date", "Aliments", "Total kcal", "Objectif kcal", "Écart (kcal)", "Dans l'objectif",
        "Glucides (g)", "Protéines (g)", "Lipides (g)")

    /** Bilan par jour renseigné (objectif = objectif actuel de l'appli, l'historique des objectifs n'est pas conservé). */
    fun dailyCsv(entries: List<Entry>, goal: Int): String = buildString {
        append(BOM); append(row(*DAILY_HEADER.toTypedArray()))
        for (d in dailyRows(entries)) {
            append(row(d.date.toString(), d.count.toString(), d.kcal.toString(), goal.toString(), (d.kcal!! - goal).toString(),
                if (d.kcal <= goal) "oui" else "non", num(d.carbs), num(d.proteins), num(d.fat)))
        }
    }

    /** Jours renseignés seulement, du plus ancien au plus récent. */
    fun dailyRows(entries: List<Entry>): List<DayStat> {
        if (entries.isEmpty()) return emptyList()
        val dates = entries.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        if (dates.isEmpty()) return emptyList()
        return Stats.daily(entries, dates.min(), dates.max()).filter { it.logged }
    }

    /** Export complet JSON (entrées + bilan quotidien). */
    fun json(entries: List<Entry>, goal: Int, appVersion: String, exportedAt: String): String {
        val arr = JSONArray()
        for (e in sorted(entries)) arr.put(JSONObject().apply {
            put("date", e.date); put("heure", e.time); put("aliment", e.name)
            put("quantite_g", e.grams); put("kcal_100g", e.kcalPer100g); put("kcal", e.kcal)
            put("glucides_g", if (e.carbs100 != null) round2(e.carbs) else JSONObject.NULL)
            put("proteines_g", if (e.proteins100 != null) round2(e.proteins) else JSONObject.NULL)
            put("lipides_g", if (e.fat100 != null) round2(e.fat) else JSONObject.NULL)
            put("nutriments", JSONObject().apply { NUTRIENTS.forEach { n -> e.nutrient(n.key)?.let { put(n.key, round2(it)) } } })
            put("fermente", e.fermented); put("prebiotique", e.prebiotic)
        })
        val days = JSONArray()
        for (d in dailyRows(entries)) days.put(JSONObject().apply {
            put("date", d.date.toString()); put("aliments", d.count); put("kcal", d.kcal); put("objectif", goal)
            put("glucides_g", d.carbs?.let { round2(it) } ?: JSONObject.NULL)
            put("proteines_g", d.proteins?.let { round2(it) } ?: JSONObject.NULL)
            put("lipides_g", d.fat?.let { round2(it) } ?: JSONObject.NULL)
        })
        return JSONObject()
            .put("application", "Calories").put("version", appVersion).put("exporte_le", exportedAt)
            .put("objectif_kcal", goal)
            .put("unites_nutriments", JSONObject().apply { NUTRIENTS.forEach { put(it.key, it.unit) } })
            .put("entrees", arr).put("jours", days)
            .toString(2)
    }

    private fun round2(d: Double) = Math.round(d * 100.0) / 100.0
}
