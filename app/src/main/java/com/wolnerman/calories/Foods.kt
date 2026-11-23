package com.wolnerman.calories

/**
 * Aliment de référence, valeurs pour 100 g. Macros (glucides, protéines, lipides en g) null si inconnues.
 * [unitGrams]/[unitLabel] : poids typique d'une unité (« 2 œufs » → 2 × 50 g). [aliases] : autres noms pour la recherche.
 */
data class Food(
    val name: String, val kcalPer100g: Int, val carbs: Double?, val proteins: Double?, val fat: Double?,
    val fermented: Boolean = false,
    val prebiotic: Boolean = false,
    val nutrients: Map<String, Double> = emptyMap(),   // clés de NUTRIENTS, seulement les valeurs connues
    val unitGrams: Double? = null,
    val unitLabel: String? = null,
    val aliases: List<String> = emptyList(),
    val category: String = "",
    val source: String = "",
)

/** Lecture d'une ligne de FoodData (voir l'en-tête de FoodData.kt). */
internal fun parseFoodRow(line: String): Food {
    val c = line.split('|')
    require(c.size == 19) { "Ligne d'aliment invalide (${c.size} colonnes) : $line" }
    fun d(i: Int): Double? = c[i].takeIf { it.isNotBlank() }?.toDouble()
    val nutr = NUTRIENTS.mapIndexedNotNull { i, n -> d(6 + i)?.let { n.key to it } }.toMap()
    return Food(
        name = c[0], category = c[1], kcalPer100g = c[2].toInt(), carbs = d(3), proteins = d(4), fat = d(5),
        nutrients = nutr, unitGrams = d(14), unitLabel = c[15].ifBlank { null },
        aliases = c[16].split(';').map { it.trim() }.filter { it.isNotEmpty() },
        fermented = 'F' in c[17], prebiotic = 'P' in c[17], source = c[18],
    )
}

/**
 * Base intégrée : valeurs Ciqual 2025 (Anses) pour 100 g, plats antillais calculés à partir des ingrédients Ciqual.
 * Ordre = ordre d'affichage (les plus courants d'abord dans chaque catégorie).
 */
val BUILTIN_FOODS: List<Food> by lazy {
    FoodData.TSV.flatMap { block -> block.lines().filter { it.isNotBlank() }.map { parseFoodRow(it.trim()) } }
}

/** Aliment par nom exact ou alias (accents et casse ignorés). */
fun foodByName(name: String, foods: List<Food> = BUILTIN_FOODS): Food? {
    val f = fold(name)
    return foods.firstOrNull { fold(it.name) == f } ?: foods.firstOrNull { it.aliases.any { a -> fold(a) == f } }
}

/** Définition d'un nutriment suivi. isLimit = à ne pas dépasser (sucres, sel, graisses saturées). */
data class Nutrient(val key: String, val label: String, val unit: String, val isLimit: Boolean = false, val short: String = label)

val NUTRIENTS = listOf(
    Nutrient("fiber", "Fibres", "g"),
    Nutrient("sugars", "Sucres", "g", isLimit = true),
    Nutrient("salt", "Sel", "g", isLimit = true),
    Nutrient("satfat", "Graisses saturées", "g", isLimit = true, short = "AG saturés"),
    Nutrient("calcium", "Calcium", "mg"),
    Nutrient("iron", "Fer", "mg"),
    Nutrient("vitc", "Vitamine C", "mg", short = "Vit. C"),
    Nutrient("vitd", "Vitamine D", "µg", short = "Vit. D"),
)

/** Repères journaliers adultes (indicatifs). Limites à 10 % des kcal calculées sur l'objectif. */
fun referenceIntake(key: String, male: Boolean?, goalKcal: Int): Double = when (key) {
    "fiber" -> 30.0
    "sugars" -> goalKcal * 0.10 / 4.0
    "salt" -> 5.0
    "satfat" -> goalKcal * 0.10 / 9.0
    "calcium" -> 950.0
    "iron" -> if (male == true) 11.0 else 16.0
    "vitc" -> 110.0
    "vitd" -> 15.0
    else -> 0.0
}
