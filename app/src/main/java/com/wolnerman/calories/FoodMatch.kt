package com.wolnerman.calories

import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/** Minuscule, sans accents, espaces normalisés (« Œuf » → « oeuf »). */
fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.FRENCH)
        .replace("œ", "oe").replace("æ", "ae").trim().replace(Regex("\\s+"), " ")

/**
 * Trouve l'aliment intégré correspondant au texte saisi, sans avoir à toucher la suggestion.
 * Priorité : égalité exacte > nom qui commence par le texte > un mot qui commence par le texte
 * > nom qui contient le texte > tous les mots saisis présents dans le nom.
 */
fun findFood(query: String, foods: List<Food> = BUILTIN_FOODS): Food? {
    val q = fold(query)
    if (q.length < 2) return null
    val folded = foods.map { it to fold(it.name) }
    folded.firstOrNull { it.second == q }?.let { return it.first }
    folded.firstOrNull { it.second.startsWith(q) }?.let { return it.first }
    folded.firstOrNull { (_, n) -> n.split(' ', '(', '/', '-').any { w -> w.startsWith(q) } }?.let { return it.first }
    folded.firstOrNull { it.second.contains(q) }?.let { return it.first }
    val tokens = q.split(' ').filter { it.length >= 2 }
    if (tokens.size >= 2) folded.firstOrNull { (_, n) -> tokens.all { n.contains(it) } }?.let { return it.first }
    return null
}

/** Suggestions pour la liste déroulante (même logique, plusieurs résultats). */
fun suggestFoods(query: String, foods: List<Food> = BUILTIN_FOODS): List<Food> {
    val q = fold(query)
    if (q.isEmpty()) return foods
    val tokens = q.split(' ').filter { it.isNotEmpty() }
    return foods.filter { f -> val n = fold(f.name); tokens.all { n.contains(it) } }
        .sortedBy { f -> val n = fold(f.name); when { n == q -> 0; n.startsWith(q) -> 1; else -> 2 } }
}

/** Résultat du calcul pour une quantité donnée. */
data class Calc(val kcal: Int, val carbs: Double?, val proteins: Double?, val fat: Double?)

/** kcal et macros pour [grams] g, à partir des valeurs pour 100 g. Null si quantité ou kcal manquants. */
fun calculate(grams: Double?, kcal100: Double?, carbs100: Double? = null, prot100: Double? = null, fat100: Double? = null): Calc? {
    if (grams == null || grams <= 0 || kcal100 == null || kcal100 < 0) return null
    val f = grams / 100.0
    return Calc((kcal100 * f).roundToInt(), carbs100?.let { it * f }, prot100?.let { it * f }, fat100?.let { it * f })
}

/** Lit un nombre saisi au clavier français ou anglais (« 12,5 » ou « 12.5 »). */
fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
