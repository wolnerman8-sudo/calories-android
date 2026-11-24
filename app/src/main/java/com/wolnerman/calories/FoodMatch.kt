package com.wolnerman.calories

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Minuscule, sans accents, espaces normalisés (« Œuf » → « oeuf »). */
fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.FRENCH)
        .replace("œ", "oe").replace("æ", "ae").trim().replace(Regex("\\s+"), " ")

// ---------------------------------------------------------------- mots

/** Mots vides ignorés dans la recherche (articles, liaisons). */
private val STOP = setOf("de", "d", "du", "des", "la", "le", "les", "l", "un", "une", "au", "aux", "a", "en", "et", "avec", "pour")
/** Mots de cuisson / précision : tolérés s'ils ne correspondent à rien (« œufs cuits » trouve quand même un œuf). */
private val MODIFIERS = setOf("cuit", "cuite", "cru", "crue", "nature", "frais", "fraiche", "maison", "entier", "entiere",
    "chaud", "chaude", "froid", "froide", "grand", "grande", "petit", "petite", "gros", "grosse", "bio", "simple", "moyen",
    "moyenne", "classique", "normal", "normale", "pays", "local", "locale")

/** Singulier approximatif : retire le s / x final (des deux côtés, donc cohérent : « pois » ↔ « poi »). */
private fun singular(w: String): String = if (w.length > 3 && (w.endsWith("s") || w.endsWith("x"))) w.dropLast(1) else w

/** Mots significatifs d'un texte : sans accents, sans ponctuation, sans mots vides, au singulier. */
fun searchTokens(s: String): List<String> =
    fold(s).replace(Regex("[^a-z0-9%]+"), " ").split(' ')
        .filter { it.isNotEmpty() && it !in STOP }
        .map { singular(it) }

private fun isModifier(t: String) = t in MODIFIERS || singular(t) in MODIFIERS.map { singular(it) }

/** Distance d'édition bornée (Damerau simplifiée) pour tolérer une faute de frappe. */
internal fun editDistance(a: String, b: String, max: Int = 2): Int {
    if (abs(a.length - b.length) > max) return max + 1
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        var v = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, d[i - 2][j - 2] + 1)
        d[i][j] = v
    }
    return d[a.length][b.length]
}

// ---------------------------------------------------------------- quantités en tête de saisie

/** Quantité lue au début du texte : « 2 œufs », « 200 g riz », « 1/2 avocat », « 3 tranches de pain ». */
data class QtyParse(
    val count: Double? = null,       // nombre d'unités (« 2 » œufs)
    val grams: Double? = null,       // grammes directs (« 200 g », « 25 cl » ≈ 250 g)
    val measureGrams: Double? = null, // poids d'une mesure ménagère (c. à soupe = 15 g…), si précisée
    val measure: String? = null,     // libellé de la mesure ou de l'unité saisie
    val rest: String,                // texte restant = aliment recherché
) {
    val hasQuantity: Boolean get() = count != null || grams != null
}

private val NUMBER_WORDS = mapOf("un" to 1.0, "une" to 1.0, "deux" to 2.0, "trois" to 3.0, "quatre" to 4.0, "cinq" to 5.0,
    "six" to 6.0, "sept" to 7.0, "huit" to 8.0, "neuf" to 9.0, "dix" to 10.0, "douze" to 12.0, "demi" to 0.5, "demie" to 0.5)
/** Unités de masse / volume → grammes (liquides : 1 ml ≈ 1 g). */
private val MASS = mapOf("g" to 1.0, "gr" to 1.0, "gramme" to 1.0, "grammes" to 1.0, "kg" to 1000.0, "kilo" to 1000.0, "kilos" to 1000.0,
    "ml" to 1.0, "cl" to 10.0, "dl" to 100.0, "l" to 1000.0, "litre" to 1000.0, "litres" to 1000.0)
/** Mesures ménagères au poids fixe (sauf si l'aliment a cette unité comme unité propre). */
private val MEASURES = listOf(
    Regex("^(cuilleres?|cuillers?|c\\.?) ?(a|à)? ?(soupe|s\\.?)\\b|^cs\\b|^cas\\b") to ("c. à soupe" to 15.0),
    Regex("^(cuilleres?|cuillers?|c\\.?) ?(a|à)? ?(cafe|c\\.?)\\b|^cc\\b|^cac\\b") to ("c. à café" to 5.0),
    Regex("^verres?\\b") to ("verre" to 200.0),
    Regex("^tasses?\\b") to ("tasse" to 200.0),
    Regex("^bols?\\b") to ("bol" to 250.0),
    Regex("^canettes?\\b") to ("canette" to 330.0),
    Regex("^poignees?\\b") to ("poignée" to 30.0),
)
/** Mots d'unité « comptable » : le poids vient de l'aliment (tranche de pain = 30 g, part de pizza = 120 g…). */
private val COUNT_WORDS = Regex("^(tranches?|parts?|portions?|morceaux?|pieces?|unites?|pots?|boules?|carres?|assiettes?|filets?|steaks?|cuisses?|barres?|sachets?|paquets?|rondelles?|gousses?|feuilles?|fruits?|bouteilles?|coupes?|x)\\b")

fun parseQuantity(input: String): QtyParse {
    var s = fold(input).replace('½', ' ').trim()
    if (input.contains('½')) s = "0.5 " + s
    var count: Double? = null
    var grams: Double? = null
    // nombre : « 2 », « 2,5 », « 1/2 », « 1 1/2 » ou en lettres
    val num = Regex("^(\\d+(?:[.,]\\d+)?)(?:\\s+(\\d+)/(\\d+))?(?:/(\\d+))?").find(s)
    if (num != null) {
        var v = num.groupValues[1].replace(',', '.').toDouble()
        if (num.groupValues[4].isNotEmpty()) { val den = num.groupValues[4].toDouble(); if (den > 0) v /= den }
        if (num.groupValues[2].isNotEmpty()) { val den = num.groupValues[3].toDouble(); if (den > 0) v += num.groupValues[2].toDouble() / den }
        count = v; s = s.substring(num.range.last + 1).trim()
    } else {
        val w = s.substringBefore(' ')
        val v = NUMBER_WORDS[w]
        // « une » seul ou « un œuf » : seulement si un mot suit (sinon c'est peut-être le nom)
        if (v != null && s.contains(' ')) { count = v; s = s.substringAfter(' ').trim() }
    }
    if (count == null) return QtyParse(rest = input.trim())
    // « demi » après un nombre (« 1 demi ») ou « et demi »
    if (s.startsWith("et demi")) { count += 0.5; s = s.removePrefix("et demie").removePrefix("et demi").trim() }
    // unité de masse collée ou séparée : « 200g », « 200 g de riz »
    val unit = Regex("^([a-z]+)\\b\\.?").find(s)?.groupValues?.get(1)
    if (unit != null && unit in MASS) {
        grams = count * MASS.getValue(unit); s = s.substring(unit.length).trimStart('.', ' ')
        return QtyParse(grams = grams, measure = unit, rest = stripLinkers(s))
    }
    for ((re, m) in MEASURES) {
        val mm = re.find(s) ?: continue
        s = s.substring(mm.range.last + 1).trim()
        return QtyParse(count = count, measureGrams = m.second, measure = m.first, rest = stripLinkers(s))
    }
    val cw = COUNT_WORDS.find(s)
    var measure: String? = null
    if (cw != null) { measure = cw.value; s = s.substring(cw.range.last + 1).trim() }
    return QtyParse(count = count, measure = measure, rest = stripLinkers(s))
}

private fun stripLinkers(s: String) = s.replace(Regex("^(de |d'|d’|du |des |d )"), "").trim()

/** Quantité en grammes et note explicative, ou null si on ne peut pas la déduire (aliment sans poids unitaire). */
data class QtyResult(val grams: Double, val note: String, val chip: String)

private fun g0(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.FRENCH, "%.1f", d)

fun quantityFor(q: QtyParse, food: Food?): QtyResult? {
    q.grams?.let { return QtyResult(it, "Quantité lue : ${g0(it)} g", "Utiliser ${g0(it)} g") }
    val n = q.count ?: return null
    if (n <= 0) return null
    val unitLabel = food?.unitLabel
    // mesure ménagère, sauf si l'aliment a justement cette unité (verre de lait = 250 g dans la base)
    if (q.measureGrams != null) {
        val per = if (food?.unitGrams != null && unitLabel != null && fold(unitLabel).startsWith(fold(q.measure ?: "?"))) food.unitGrams else q.measureGrams
        val total = n * per
        return QtyResult(total, "${g0(n)} × ${q.measure} (≈ ${g0(per)} g) = ${g0(total)} g", "Utiliser ${g0(n)} × ${g0(per)} g = ${g0(total)} g")
    }
    val per = food?.unitGrams ?: return null
    val total = (n * per * 10).roundToInt() / 10.0
    val what = if (q.measure != null && q.measure != "x") "${q.measure} de ${food.name}" else food.name
    return QtyResult(total, "${g0(n)} × $what (≈ ${g0(per)} g) = ${g0(total)} g", "Utiliser ${g0(n)} × ${g0(per)} g = ${g0(total)} g")
}

// ---------------------------------------------------------------- recherche

private data class Scored(val food: Food, val score: Double, val index: Int)

/** Score d'un libellé (nom ou alias) pour les mots saisis ; null si un mot important ne correspond à rien. */
private fun scoreLabel(q: List<String>, label: String, lastIsPrefix: Boolean): Double? {
    val t = searchTokens(label)
    if (t.isEmpty()) return null
    var score = 0.0
    val used = BooleanArray(t.size)
    for ((i, w) in q.withIndex()) {
        var best = 0.0; var bestJ = -1
        for ((j, x) in t.withIndex()) {
            val s = when {
                x == w -> 3.0
                x.startsWith(w) && (w.length >= 3 || (lastIsPrefix && i == q.lastIndex && w.length >= 2)) -> 2.0
                w.length >= 5 && x.length >= 4 && editDistance(w, x, 1) <= 1 -> 1.5   // faute de frappe
                w.length >= 4 && x.startsWith(w.dropLast(1)) && i == q.lastIndex && lastIsPrefix -> 1.0
                else -> 0.0
            }
            if (s > best || (s == best && s > 0 && used.getOrElse(bestJ) { true } && !used[j])) { best = s; bestJ = j }
        }
        if (best == 0.0) { if (isModifier(w)) { score -= 0.5; continue } else return null }
        used[bestJ] = true
        score += best
        if (i == 0 && bestJ == 0) score += 1.0       // le premier mot saisi est le premier mot du nom
    }
    val extra = used.count { !it }
    score -= 0.35 * extra                             // noms plus précis que la saisie : légèrement moins bien classés
    if (extra == 0 && q.size == t.size) score += 4.0  // correspondance exacte (au pluriel près)
    return score
}

private fun rank(query: String, foods: List<Food>, prefixMode: Boolean): List<Scored> {
    val q = searchTokens(query)
    if (q.isEmpty()) return emptyList()
    val out = ArrayList<Scored>()
    foods.forEachIndexed { idx, f ->
        var best: Double? = scoreLabel(q, f.name, prefixMode)
        for (a in f.aliases) {
            val s = scoreLabel(q, a, prefixMode)?.minus(0.1) ?: continue   // le nom affiché gagne à égalité
            if (best == null || s > best) best = s
        }
        if (best != null && best > 0) out += Scored(f, best, idx)
    }
    return out.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.food.name.length }.thenBy { it.index })
}

/**
 * Aliment intégré correspondant au texte saisi (sans quantité en tête), pour remplir le formulaire automatiquement.
 * Tolère pluriels, accents, articles, mots de cuisson, une faute de frappe. Null si rien de sûr.
 */
fun findFood(query: String, foods: List<Food> = FoodCatalog.all()): Food? {
    val rest = parseQuantity(query).rest
    if (fold(rest).replace(" ", "").length < 2) return null
    return rank(rest, foods, prefixMode = false).firstOrNull()?.food
}

/** Suggestions pour la liste déroulante (le dernier mot peut être incomplet, en cours de frappe). */
fun suggestFoods(query: String, foods: List<Food> = FoodCatalog.all(), limit: Int = 40): List<Food> {
    val rest = parseQuantity(query).rest
    if (fold(rest).isEmpty()) return foods.take(limit)
    return rank(rest, foods, prefixMode = true).take(limit).map { it.food }
}

/** « 155 kcal · G 1 · P 13 · L 11 (/100 g) » pour la liste déroulante. */
fun foodLine(f: Food): String {
    fun m(v: Double?) = v?.let { if (it >= 10 || it % 1.0 == 0.0) it.roundToInt().toString() else String.format(Locale.FRENCH, "%.1f", it) }
    val macros = listOfNotNull(m(f.carbs)?.let { "G $it" }, m(f.proteins)?.let { "P $it" }, m(f.fat)?.let { "L $it" })
    return (if (f.kcalPer100g < 0) "kcal inconnues" else "${f.kcalPer100g} kcal") + (if (macros.isEmpty()) "" else " · " + macros.joinToString(" · ")) + " (/100 g)" +
        (f.unitGrams?.let { u -> " · 1 ${f.unitLabel ?: "unité"} ≈ ${g0(u)} g" } ?: "")
}

/** Résultat du calcul pour une quantité donnée. */
data class Calc(val kcal: Int, val carbs: Double?, val proteins: Double?, val fat: Double?)

/** kcal et macros pour [grams] g, à partir des valeurs pour 100 g. Null si quantité ou kcal manquants. */
fun calculate(grams: Double?, kcal100: Double?, carbs100: Double? = null, prot100: Double? = null, fat100: Double? = null): Calc? {
    if (grams == null || grams <= 0 || kcal100 == null || kcal100 < 0) return null
    val f = grams / 100.0
    return Calc((kcal100 * f).roundToInt(), carbs100?.let { it * f }, prot100?.let { it * f }, fat100?.let { it * f })
}

/** Lit un nombre saisi au clavier français ou anglais (« 12,5 », « 12.5 », « 150 g », « 1 200 »). */
fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull() ?: parseLooseNumber(s)
