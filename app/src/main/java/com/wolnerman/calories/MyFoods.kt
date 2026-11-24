package com.wolnerman.calories

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** v2.8 : aliment personnel (« Mes aliments »), valeurs pour 100 g ; kcal null = inconnues. */
data class CustomFood(
    val name: String,
    val kcal100: Double? = null,
    val carbs: Double? = null,
    val proteins: Double? = null,
    val fat: Double? = null,
    val updatedMs: Long = 0,
    val fiber: Double? = null,   // v2.10 : fibres g/100 g (G = glucides totaux, fibres comprises)
)

object MyFoodsLogic {
    const val CATEGORY = "Mes aliments"

    fun key(name: String) = fold(name).replace(Regex("\\s+"), " ").trim()

    /** Ajoute ou met à jour (même nom, sans tenir compte des majuscules / accents). Valeurs absentes = gardées. */
    fun upsert(list: List<CustomFood>, c: CustomFood): List<CustomFood> {
        val name = c.name.trim(); if (name.isEmpty()) return list
        val old = list.firstOrNull { key(it.name) == key(name) }
        val merged = if (old == null) c.copy(name = name) else CustomFood(name,
            c.kcal100 ?: old.kcal100, c.carbs ?: old.carbs, c.proteins ?: old.proteins, c.fat ?: old.fat, c.updatedMs, c.fiber ?: old.fiber)
        return listOf(merged) + list.filter { key(it.name) != key(name) }
    }

    /** Remplace un aliment (édition, y compris renommage). */
    fun replace(list: List<CustomFood>, oldName: String, c: CustomFood): List<CustomFood> =
        upsert(list.filter { key(it.name) != key(oldName) }, c)

    fun remove(list: List<CustomFood>, name: String) = list.filter { key(it.name) != key(name) }

    /** Conversion pour la recherche : kcal inconnues → -1 (le formulaire laisse alors le champ vide). */
    fun toFood(c: CustomFood) = Food(c.name, c.kcal100?.roundToInt() ?: -1, c.carbs, c.proteins, c.fat,
        nutrients = c.fiber?.let { mapOf("fiber" to it) } ?: emptyMap(), category = CATEGORY, source = CATEGORY)

    fun isMine(f: Food) = f.category == CATEGORY

    /** Faut-il mémoriser ce que l'utilisateur vient d'ajouter ? Oui pour un nom libre (pas un aliment de la liste intégrée, pas un scan). */
    fun shouldRemember(entryName: String, pickedBuiltin: Boolean, scanned: Boolean): Boolean =
        entryName.isNotBlank() && !pickedBuiltin && !scanned && BUILTIN_FOODS.none { key(it.name) == key(entryName) }

    fun toJson(l: List<CustomFood>): String = JSONArray().also { a ->
        l.forEach { c -> a.put(JSONObject().put("n", c.name).put("t", c.updatedMs).apply {
            c.kcal100?.let { put("k", it) }; c.carbs?.let { put("c", it) }; c.proteins?.let { put("p", it) }; c.fat?.let { put("f", it) }; c.fiber?.let { put("fi", it) } }) }
    }.toString()

    fun fromJson(s: String?): List<CustomFood> = runCatching {
        val a = JSONArray(s ?: "[]")
        fun JSONObject.d(k: String) = if (has(k)) optDouble(k).takeIf { !it.isNaN() } else null
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> CustomFood(o.getString("n"), o.d("k"), o.d("c"), o.d("p"), o.d("f"), o.optLong("t"), o.d("fi")) } }
    }.getOrDefault(emptyList())
}

/** Mes aliments, mémorisés sur le téléphone ; [foods] alimente la recherche avec la liste intégrée. */
object MyFoods {
    var items by mutableStateOf<List<CustomFood>>(emptyList()); private set
    private var prefs: android.content.SharedPreferences? = null

    val foods: List<Food> get() = items.map { MyFoodsLogic.toFood(it) }

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("my_foods", Context.MODE_PRIVATE)
        prefs = p; items = MyFoodsLogic.fromJson(p.getString("list", "[]"))
    }
    private fun save(l: List<CustomFood>) { items = l; prefs?.edit()?.putString("list", MyFoodsLogic.toJson(l))?.apply() }

    fun remember(c: CustomFood) = save(MyFoodsLogic.upsert(items, c))
    fun edit(oldName: String, c: CustomFood) = save(MyFoodsLogic.replace(items, oldName, c))
    fun delete(name: String) = save(MyFoodsLogic.remove(items, name))
    /** Pour les tests. */
    internal fun setForTest(l: List<CustomFood>) { items = l }
}

/** Liste utilisée par la recherche : mes aliments d'abord, puis la liste intégrée. */
object FoodCatalog {
    fun all(): List<Food> = if (MyFoods.items.isEmpty()) BUILTIN_FOODS else MyFoods.foods + BUILTIN_FOODS
}
