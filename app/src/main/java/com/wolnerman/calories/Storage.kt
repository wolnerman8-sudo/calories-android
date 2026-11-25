package com.wolnerman.calories

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Une entrée du journal alimentaire. */
data class Entry(
    val id: Long,
    val date: String,        // AAAA-MM-JJ
    val time: String,        // HH:mm
    val name: String,
    val grams: Double,
    val kcalPer100g: Double,
    val photo: String? = null, // chemin absolu de la photo du repas (optionnel)
    val carbs100: Double? = null,    // glucides g/100 g (optionnel)
    val proteins100: Double? = null, // protéines g/100 g (optionnel)
    val fat100: Double? = null,      // lipides g/100 g (optionnel)
    val nutr: Map<String, Double> = emptyMap(), // nutriments pour 100 g (clés de NUTRIENTS)
    val fermented: Boolean = false,  // aliment fermenté / probiotique
    val prebiotic: Boolean = false,  // riche en prébiotiques (v1.1, absent = false)
    val kcalUnknown: Boolean = false, // v2.8 : ajouté avec le poids seul (kcal inconnues, comptées 0)
) {
    /** Quantité du nutriment pour cette entrée, null si non renseigné. */
    fun nutrient(key: String): Double? = nutr[key]?.let { it * grams / 100.0 }
    val kcal: Int get() = (grams * kcalPer100g / 100.0).roundToInt()
    val carbs: Double get() = (carbs100 ?: 0.0) * grams / 100.0
    val proteins: Double get() = (proteins100 ?: 0.0) * grams / 100.0
    val fat: Double get() = (fat100 ?: 0.0) * grams / 100.0
    val hasMacros: Boolean get() = carbs100 != null || proteins100 != null || fat100 != null
}

/** Totaux de macronutriments (en grammes). */
data class Macros(val carbs: Double, val proteins: Double, val fat: Double)
fun List<Entry>.macros() = Macros(sumOf { it.carbs }, sumOf { it.proteins }, sumOf { it.fat })

private fun JSONObject.optNum(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

/** Persistance locale simple : SharedPreferences + JSON (hors ligne, sans compte). */
class Storage(context: Context) {
    private val profPrefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    fun profilePrefs() = profPrefs
    private val prefs = context.getSharedPreferences("calories", Context.MODE_PRIVATE)

    fun loadEntries(): List<Entry> {
        val raw = prefs.getString("entries", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    id = o.getLong("id"),
                    date = o.getString("date"),
                    time = o.optString("time", ""),
                    name = o.getString("name"),
                    grams = o.getDouble("grams"),
                    kcalPer100g = o.getDouble("kcal100"),
                    photo = o.optString("photo", "").ifBlank { null }, // anciennes entrées : pas de photo
                    carbs100 = o.optNum("c100"),
                    proteins100 = o.optNum("p100"),
                    fat100 = o.optNum("f100"),
                    nutr = o.optJSONObject("n")?.let { n -> n.keys().asSequence().mapNotNull { k -> n.optNum(k)?.let { k to it } }.toMap() } ?: emptyMap(),
                    fermented = o.optBoolean("ferm", false),
                    prebiotic = o.optBoolean("preb", false),
                    kcalUnknown = o.optBoolean("kcalUnk", false),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveEntries(entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("date", e.date); put("time", e.time)
                put("name", e.name); put("grams", e.grams); put("kcal100", e.kcalPer100g)
                e.photo?.let { put("photo", it) }
                e.carbs100?.let { put("c100", it) }
                e.proteins100?.let { put("p100", it) }
                e.fat100?.let { put("f100", it) }
                if (e.nutr.isNotEmpty()) put("n", JSONObject().apply { e.nutr.forEach { (k, v) -> put(k, v) } })
                if (e.fermented) put("ferm", true)
                if (e.prebiotic) put("preb", true)
                if (e.kcalUnknown) put("kcalUnk", true)
            })
        }
        prefs.edit().putString("entries", arr.toString()).apply()
    }

    var dailyGoal: Int
        get() = prefs.getInt("goal", 2000)
        set(v) { prefs.edit().putInt("goal", v).apply() }

    /** v2.15 : une seule fois, classer l'ancien objectif en maintien (≈ TDEE) ou personnalisé. */
    fun migrateGoalIfNeeded() {
        if (prefs.getBoolean("goal_v215", false)) return
        val plan = EnergyLogic.migrate(dailyGoal, EnergyLogic.compute(loadProfile())?.tdee)
        prefs.edit()
            .putString("goal_mode", plan.target.name)
            .putInt("goal_delta", plan.delta)
            .putBoolean("goal_v215", true)
            .apply()
    }

    fun loadGoalPlan(): GoalPlan {
        migrateGoalIfNeeded()
        val mode = prefs.getString("goal_mode", null)?.let { runCatching { EnergyTarget.valueOf(it) }.getOrNull() }
            ?: EnergyTarget.CUSTOM
        val delta = prefs.getInt("goal_delta", EnergyLogic.DEFAULT_DELTA)
        return GoalPlan(mode, delta, dailyGoal.takeIf { it > 0 })
    }

    fun saveGoalPlan(p: GoalPlan) {
        val clamped = p.copy(delta = EnergyLogic.clampDelta(p.delta))
        val ed = prefs.edit()
            .putString("goal_mode", clamped.target.name)
            .putInt("goal_delta", clamped.delta)
            .putBoolean("goal_v215", true)
        clamped.customKcal?.takeIf { it > 0 }?.let { ed.putInt("goal", it) }
        ed.apply()
        // Met aussi à jour « goal » pour les modes calculés, afin qu'un export ou un ancien chemin voie la bonne valeur
        val out = currentGoal(clamped)
        if (out.kcal > 0) dailyGoal = out.kcal
    }

    /** Objectif effectif (recalculé d'après le profil actuel, sauf en personnalisé). */
    fun currentGoal(plan: GoalPlan = loadGoalPlan()): GoalOutcome {
        migrateGoalIfNeeded()
        val p = loadProfile()
        return EnergyLogic.plan(EnergyLogic.compute(p), plan, p.male)
    }
}

/** Profil utilisateur (BMR / TDEE : voir [EnergyLogic]). */
data class Profile(
    val age: Int? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val male: Boolean? = null,
    val activity: Int = 0, // index dans ACTIVITY_LEVELS
    val fatPct: Double? = null, // v2.14 : % de masse grasse, facultatif (Katch-McArdle si 3–70)
) {
    /** Métabolisme de base (kcal/jour), null si le profil est incomplet. */
    fun bmr(): Double? = EnergyLogic.compute(this)?.bmr
    /** Besoin de maintien (TDEE) arrondi, à proposer comme objectif. */
    fun suggestedGoal(): Int? = EnergyLogic.compute(this)?.tdee
}

/**
 * Coefficients inchangés par rapport aux versions précédentes (même index).
 * v2.14 : libellés alignés sur la convention habituelle —
 * l'ancien « Actif (×1,725) » s'affiche désormais « Très actif » ; l'ancien « Très actif (×1,9) » devient « Extrême ».
 */
val ACTIVITY_LEVELS = listOf(
    "Sédentaire" to 1.2,
    "Léger" to 1.375,
    "Modéré" to 1.55,
    "Très actif" to 1.725,
    "Extrême" to 1.9,
)

fun Storage.loadProfile(): Profile = profilePrefs().let { p ->
    Profile(
        age = p.getInt("age", -1).takeIf { it > 0 },
        weightKg = p.getFloat("weight", -1f).takeIf { it > 0 }?.toDouble(),
        heightCm = p.getFloat("height", -1f).takeIf { it > 0 }?.toDouble(),
        male = if (p.contains("male")) p.getBoolean("male", true) else null,
        activity = p.getInt("activity", 0).coerceIn(0, ACTIVITY_LEVELS.lastIndex),
        fatPct = p.getFloat("fat_pct", -1f).takeIf { it > 0 }?.toDouble(),
    )
}

fun Storage.saveProfile(pr: Profile) {
    profilePrefs().edit().apply {
        if (pr.age != null) putInt("age", pr.age) else remove("age")
        if (pr.weightKg != null) putFloat("weight", (Math.round(pr.weightKg * 10.0) / 10.0).toFloat()) else remove("weight")
        if (pr.heightCm != null) putFloat("height", (Math.round(pr.heightCm * 10.0) / 10.0).toFloat()) else remove("height")
        if (pr.male != null) putBoolean("male", pr.male) else remove("male")
        putInt("activity", pr.activity)
        if (pr.fatPct != null) putFloat("fat_pct", (Math.round(pr.fatPct * 10.0) / 10.0).toFloat()) else remove("fat_pct")
    }.apply()
}
