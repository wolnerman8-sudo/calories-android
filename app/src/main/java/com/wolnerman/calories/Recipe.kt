package com.wolnerman.calories

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * v2.20 : recette saisie en liste d'ingrédients (collée ou tapée).
 * Quantités via [parseQuantity] / [quantityFor], correspondance via [findFood],
 * kcal et macros via [KcalLogic] (même convention v2.10 que FoodForm, jamais une formule parallèle).
 */
data class RecipeLine(
    val id: Long,
    val raw: String,
    val food: Food? = null,
    val grams: Double? = null,
    val gramsNote: String? = null,
    val foodOverridden: Boolean = false,
    val gramsOverridden: Boolean = false,
    val manualName: String? = null,
    val manualKcal100: Double? = null,
    val manualCarbs100: Double? = null,   // G totaux / 100 g (fibres comprises), comme FoodForm
    val manualProt100: Double? = null,
    val manualFat100: Double? = null,
    val manualFiber100: Double? = null,
) {
    /** Ligne sans aliment reconnu ni valeurs manuelles : exclue du total, à corriger. */
    val unresolved: Boolean get() = RecipeLogic.nutrition(this) == null
    val displayName: String get() = food?.name ?: manualName?.takeIf { it.isNotBlank() } ?: raw
}

data class LineNutrition(
    val grams: Double,
    val kcal: Double,
    val carbs: Double?,       // G totaux (fibres comprises)
    val carbsUsed: Double?,   // glucides comptés (G − fibres si fibres connues)
    val proteins: Double?,
    val fat: Double?,
    val fiber: Double?,
)

data class RecipeTotals(
    val grams: Double,
    val kcal: Double,
    val carbs: Double?,
    val carbsUsed: Double?,
    val proteins: Double?,
    val fat: Double?,
    val fiber: Double?,
    val included: Int,
    val excluded: Int,
)

object RecipeLogic {
    /** Poids retenu pour une pincée (estimation cuisine documentée, pas une valeur Ciqual). */
    const val PINCH_GRAMS = 0.5

    val MEALS = listOf("Petit-déjeuner", "Déjeuner", "Collation", "Dîner")

    private val QTY_AFTER_SEP = Regex(
        """(?<!\d)\s*[,;]\s*(?=[-–—*•·]?\s*(?:\d|[½¼¾⅓⅔]|(?:un|une|deux|trois|quatre|cinq|six|sept|huit|neuf|dix|douze|demi|demie)\b))""",
        RegexOption.IGNORE_CASE,
    )
    private val BULLET = Regex("""^[\s]*[-–—*•·]\s*""")

    /** Découpe une liste : retours à la ligne, points-virgules, virgules devant une nouvelle quantité (pas « 1,5 kg »). */
    fun splitIngredients(text: String): List<String> {
        val cleaned = text.replace("\u2026", " ").replace("...", " ")
        return cleaned.split(Regex("""[\r\n;]+"""))
            .flatMap { chunk -> chunk.split(QTY_AFTER_SEP) }
            .map { BULLET.replace(it, "").trim() }
            .filter { it.isNotBlank() }
    }

    fun parse(
        text: String,
        foods: List<Food> = FoodCatalog.all(),
        previous: List<RecipeLine> = emptyList(),
    ): List<RecipeLine> {
        val raws = splitIngredients(text)
        val unused = previous.toMutableList()
        var nextId = (previous.maxOfOrNull { it.id } ?: 0L) + 1
        return raws.map { raw ->
            val prev = unused.indexOfFirst { fold(it.raw) == fold(raw) }.takeIf { it >= 0 }?.let { unused.removeAt(it) }
            val base = parseLine(raw, foods, prev?.id ?: nextId++)
            if (prev == null) base else merge(base, prev)
        }
    }

    fun parseLine(raw: String, foods: List<Food> = FoodCatalog.all(), id: Long = 1L): RecipeLine {
        val text = BULLET.replace(raw, "").trim()
        val q = parseQuantity(text)
        val food = findFood(text, foods)
        val qty = quantityFor(q, food)
        return RecipeLine(
            id = id,
            raw = text,
            food = food,
            grams = qty?.grams,
            gramsNote = weightCaption(q, food, qty),
        )
    }

    internal fun weightCaption(q: QtyParse, food: Food?, qty: QtyResult?): String? {
        if (qty == null) {
            return when {
                food == null -> null
                q.hasQuantity && food.unitGrams == null ->
                    "Poids à préciser (pas de poids unitaire dans la base), modifiable"
                else -> "Poids à préciser, modifiable"
            }
        }
        val g = g1(qty.grams)
        if (q.grams != null) return "$g g"
        val n = q.count ?: return "$g g, modifiable"
        val what = when {
            q.measure != null && q.measureGrams != null -> q.measure
            food != null -> food.unitLabel?.takeIf { it.isNotBlank() && !it.equals("portion", true) } ?: food.name.lowercase()
            else -> "unité"
        }
        return "${g1(n)} $what ≈ $g g, modifiable"
    }

    private fun merge(fresh: RecipeLine, prev: RecipeLine): RecipeLine {
        val food = if (prev.foodOverridden) prev.food else fresh.food
        val grams = if (prev.gramsOverridden) prev.grams else fresh.grams
        val note = if (prev.gramsOverridden && prev.grams != null) "${g1(prev.grams)} g, modifié"
        else if (prev.foodOverridden && !prev.gramsOverridden) {
            val q = parseQuantity(fresh.raw)
            weightCaption(q, food, quantityFor(q, food))
        } else fresh.gramsNote
        val gramsResolved = if (prev.foodOverridden && !prev.gramsOverridden) quantityFor(parseQuantity(fresh.raw), food)?.grams else grams
        return fresh.copy(
            food = food,
            grams = gramsResolved,
            gramsNote = note,
            foodOverridden = prev.foodOverridden,
            gramsOverridden = prev.gramsOverridden,
            manualName = prev.manualName,
            manualKcal100 = prev.manualKcal100,
            manualProt100 = prev.manualProt100,
            manualCarbs100 = prev.manualCarbs100,
            manualFat100 = prev.manualFat100,
            manualFiber100 = prev.manualFiber100,
        )
    }

    /** kcal et macros de la ligne pour la quantité retenue ; null = exclue du total (non reconnue ou incomplète). */
    fun nutrition(line: RecipeLine): LineNutrition? {
        val g = line.grams?.takeIf { it > 0 } ?: return null
        val factor = g / 100.0
        val manual = line.manualCarbs100 != null || line.manualProt100 != null || line.manualFat100 != null ||
            line.manualKcal100 != null || line.manualFiber100 != null
        if (manual && line.food == null) {
            val kcal100 = KcalLogic.fromMacros(line.manualCarbs100, line.manualProt100, line.manualFat100, line.manualFiber100)
                ?: line.manualKcal100 ?: return null
            val used = KcalLogic.digestibleCarbs(line.manualCarbs100, line.manualFiber100)
            return LineNutrition(
                grams = g, kcal = kcal100 * factor,
                carbs = line.manualCarbs100?.times(factor), carbsUsed = used?.times(factor),
                proteins = line.manualProt100?.times(factor), fat = line.manualFat100?.times(factor),
                fiber = line.manualFiber100?.times(factor),
            )
        }
        val f = line.food ?: return null
        val kcal100 = KcalLogic.forFood(f) ?: return null
        val fib = f.nutrients["fiber"]
        val totalG = if (MyFoodsLogic.isMine(f)) f.carbs else KcalLogic.totalCarbs(f.carbs, fib)
        val used = KcalLogic.digestibleCarbs(totalG, fib)
        return LineNutrition(
            grams = g, kcal = kcal100 * factor,
            carbs = totalG?.times(factor), carbsUsed = used?.times(factor),
            proteins = f.proteins?.times(factor), fat = f.fat?.times(factor),
            fiber = fib?.times(factor),
        )
    }

    fun totals(lines: List<RecipeLine>): RecipeTotals {
        val nuts = lines.map { it to nutrition(it) }
        val included = nuts.mapNotNull { it.second }
        fun sum(sel: (LineNutrition) -> Double?): Double? {
            val vs = included.mapNotNull(sel)
            return if (vs.isEmpty()) null else vs.sum()
        }
        return RecipeTotals(
            grams = included.sumOf { it.grams },
            kcal = included.sumOf { it.kcal },
            carbs = sum { it.carbs },
            carbsUsed = sum { it.carbsUsed },
            proteins = sum { it.proteins },
            fat = sum { it.fat },
            fiber = sum { it.fiber },
            included = included.size,
            excluded = nuts.count { it.second == null },
        )
    }

    fun perPortion(t: RecipeTotals, portions: Double): RecipeTotals? {
        val n = portions.takeIf { it > 0 } ?: return null
        fun d(v: Double?) = v?.div(n)
        return t.copy(
            grams = t.grams / n, kcal = t.kcal / n,
            carbs = d(t.carbs), carbsUsed = d(t.carbsUsed),
            proteins = d(t.proteins), fat = d(t.fat), fiber = d(t.fiber),
        )
    }

    fun canSave(name: String, lines: List<RecipeLine>): Boolean {
        val t = totals(lines)
        return name.isNotBlank() && t.included > 0 && t.grams > 0
    }

    /**
     * Enregistrement dans Mes aliments : valeurs pour 100 g (G totaux, fibres comprises)
     * et poids d'une portion. La liste d'ingrédients est conservée pour réouverture.
     */
    fun toCustomFood(
        name: String,
        source: String,
        lines: List<RecipeLine>,
        portions: Double,
        updatedMs: Long = 0L,
    ): CustomFood? {
        val t = totals(lines)
        val trimmed = name.trim()
        if (trimmed.isEmpty() || t.included == 0 || t.grams <= 0) return null
        val p = portions.takeIf { it > 0 } ?: 1.0
        val g100 = t.carbs?.let { it / t.grams * 100.0 }
        val p100 = t.proteins?.let { it / t.grams * 100.0 }
        val l100 = t.fat?.let { it / t.grams * 100.0 }
        val fi100 = t.fiber?.let { it / t.grams * 100.0 }
        val kcal100 = KcalLogic.fromMacros(g100, p100, l100, fi100) ?: (t.kcal / t.grams * 100.0)
        return CustomFood(
            name = trimmed,
            kcal100 = kcal100,
            carbs = g100,
            proteins = p100,
            fat = l100,
            updatedMs = updatedMs,
            fiber = fi100,
            recipeSource = source.trim().ifBlank { lines.joinToString("\n") { it.raw } },
            recipeJson = toJson(lines, p),
            portionGrams = t.grams / p,
        )
    }

    /** Entrée de journal : [eatenPortions] portions de la recette, comme un aliment normal. */
    fun toEntry(
        name: String,
        source: String,
        lines: List<RecipeLine>,
        recipePortions: Double,
        eatenPortions: Double,
        id: Long,
        date: String,
        time: String,
    ): Entry? {
        val food = toCustomFood(name, source, lines, recipePortions) ?: return null
        val eaten = (food.portionGrams ?: return null) * eatenPortions
        if (eaten <= 0) return null
        val used100 = KcalLogic.digestibleCarbs(food.carbs, food.fiber)
        return Entry(
            id = id, date = date, time = time, name = food.name, grams = eaten,
            kcalPer100g = food.kcal100 ?: return null,
            carbs100 = used100, proteins100 = food.proteins, fat100 = food.fat,
            nutr = food.fiber?.let { mapOf("fiber" to it) } ?: emptyMap(),
        )
    }

    fun defaultMeal(time: String): String = when (HealthMapping.mealType(time)) {
        androidx.health.connect.client.records.MealType.MEAL_TYPE_BREAKFAST -> "Petit-déjeuner"
        androidx.health.connect.client.records.MealType.MEAL_TYPE_LUNCH -> "Déjeuner"
        androidx.health.connect.client.records.MealType.MEAL_TYPE_DINNER -> "Dîner"
        else -> "Collation"
    }

    /** Heure représentative du repas choisi, pour que Santé Connect reçoive le bon type de repas. */
    fun timeForMeal(meal: String, now: String): String {
        if (defaultMeal(now) == meal) return now
        return when (meal) {
            "Petit-déjeuner" -> "08:00"
            "Déjeuner" -> "12:30"
            "Collation" -> "16:30"
            "Dîner" -> "19:30"
            else -> now
        }
    }

    fun toJson(lines: List<RecipeLine>, portions: Double): String = JSONObject().put("p", portions).put("l", JSONArray().also { a ->
        lines.forEach { line ->
            a.put(JSONObject().put("r", line.raw).apply {
                line.food?.name?.let { put("f", it) }
                line.grams?.let { put("g", it) }
                if (line.foodOverridden) put("fo", true)
                if (line.gramsOverridden) put("go", true)
                line.manualName?.let { put("mn", it) }
                line.manualKcal100?.let { put("mk", it) }
                line.manualCarbs100?.let { put("mc", it) }
                line.manualProt100?.let { put("mp", it) }
                line.manualFat100?.let { put("ml", it) }
                line.manualFiber100?.let { put("mfi", it) }
            })
        }
    }).toString()

    fun fromCustomFood(c: CustomFood, foods: List<Food> = FoodCatalog.all()): Triple<String, List<RecipeLine>, Double>? {
        if (!MyFoodsLogic.isRecipe(c)) return null
        val portions: Double
        val lines: List<RecipeLine>
        if (!c.recipeJson.isNullOrBlank()) {
            val o = runCatching { JSONObject(c.recipeJson) }.getOrNull() ?: return fallbackFromSource(c, foods)
            portions = o.optDouble("p", 1.0).takeIf { it > 0 } ?: 1.0
            val a = o.optJSONArray("l") ?: return fallbackFromSource(c, foods)
            fun JSONObject.d(k: String) = if (has(k)) optDouble(k).takeIf { !it.isNaN() } else null
            lines = (0 until a.length()).map { i ->
                val x = a.getJSONObject(i)
                val raw = x.optString("r")
                val foodName = x.optString("f").takeIf { it.isNotBlank() }
                val food = foodName?.let { foodByName(it, foods) }
                RecipeLine(
                    id = i + 1L, raw = raw, food = food, grams = x.d("g"),
                    gramsNote = x.d("g")?.let { "${g1(it)} g" },
                    foodOverridden = x.optBoolean("fo"), gramsOverridden = x.optBoolean("go"),
                    manualName = x.optString("mn").takeIf { it.isNotBlank() },
                    manualKcal100 = x.d("mk"), manualCarbs100 = x.d("mc"),
                    manualProt100 = x.d("mp"), manualFat100 = x.d("ml"), manualFiber100 = x.d("mfi"),
                )
            }
        } else {
            val parsed = fallbackFromSource(c, foods) ?: return null
            return parsed
        }
        val source = c.recipeSource?.takeIf { it.isNotBlank() } ?: lines.joinToString("\n") { it.raw }
        return Triple(source, lines, portions)
    }

    private fun fallbackFromSource(c: CustomFood, foods: List<Food>): Triple<String, List<RecipeLine>, Double>? {
        val source = c.recipeSource?.takeIf { it.isNotBlank() } ?: return null
        return Triple(source, parse(source, foods), 1.0)
    }

    internal fun g1(d: Double): String =
        if (d % 1.0 == 0.0) d.toLong().toString() else String.format(java.util.Locale.FRENCH, "%.1f", d)

    fun kcalDisplay(kcal: Double): Int = kcal.roundToInt()
}
