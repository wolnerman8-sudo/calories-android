package com.wolnerman.calories

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** v2.18 : complément alimentaire (type vitamine, oméga-3, minéral…). */
data class Supplement(
    val id: Long,
    val name: String,
    val dose: String = "",
    val hour: Int? = null,
    val minute: Int? = null,
    val reminderEnabled: Boolean = false,
    val kcal: Int? = null,
    val omega3Mg: Double? = null,
    val createdDate: String = "",
    val barcode: String? = null,
    val photo: String? = null,
)

/** Logique pure des compléments alimentaires et du suivi quotidien (testable sans Android). */
object SupplementLogic {
    const val DEFAULT_HOUR = 9
    const val DEFAULT_MINUTE = 0

    fun nextId(existing: List<Supplement>, nowMs: Long = System.currentTimeMillis()): Long {
        val base = nowMs.coerceAtLeast(1L)
        val maxId = existing.maxOfOrNull { it.id } ?: 0L
        return if (base > maxId) base else maxId + 1L
    }

    fun sanitize(s: Supplement): Supplement = s.copy(
        name = s.name.trim(),
        dose = s.dose.trim(),
        hour = s.hour?.coerceIn(0, 23),
        minute = if (s.hour != null) (s.minute ?: 0).coerceIn(0, 59) else null,
        reminderEnabled = s.reminderEnabled && s.hour != null,
        kcal = s.kcal?.takeIf { it >= 0 },
        omega3Mg = s.omega3Mg?.takeIf { !it.isNaN() && it >= 0.0 },
        barcode = SupplementScanLogic.normalizeBarcode(s.barcode ?: ""),
        photo = s.photo?.trim()?.takeIf { it.isNotEmpty() },
    )

    fun upsert(list: List<Supplement>, s: Supplement): List<Supplement> {
        val clean = sanitize(s)
        if (clean.name.isEmpty()) return list
        val idx = list.indexOfFirst { it.id == clean.id }
        return if (idx >= 0) list.toMutableList().apply { set(idx, clean) } else list + clean
    }

    fun delete(list: List<Supplement>, id: Long): List<Supplement> = list.filter { it.id != id }

    fun markTaken(logs: Map<String, Set<Long>>, date: String, id: Long, taken: Boolean): Map<String, Set<Long>> {
        val cur = logs[date].orEmpty()
        val updated = if (taken) cur + id else cur - id
        return if (updated.isEmpty()) logs - date else logs + (date to updated)
    }

    fun purgeFromLogs(logs: Map<String, Set<Long>>, id: Long): Map<String, Set<Long>> =
        logs.mapValues { (_, ids) -> ids - id }.filterValues { it.isNotEmpty() }

    fun isTaken(logs: Map<String, Set<Long>>, date: String, id: Long): Boolean =
        id in logs[date].orEmpty()

    /** Compléments actifs pour une date donnée (créés ce jour ou avant, ou déjà cochés ce jour). */
    fun activeForDate(supplements: List<Supplement>, logs: Map<String, Set<Long>>, date: String): List<Supplement> {
        val takenIds = logs[date].orEmpty()
        return supplements.filter { it.createdDate.isBlank() || date >= it.createdDate || it.id in takenIds }
    }

    fun takenForDate(supplements: List<Supplement>, logs: Map<String, Set<Long>>, date: String): List<Supplement> {
        val takenIds = logs[date].orEmpty()
        return supplements.filter { it.id in takenIds }
    }

    fun notTakenForDate(supplements: List<Supplement>, logs: Map<String, Set<Long>>, date: String): List<Supplement> {
        val takenIds = logs[date].orEmpty()
        return activeForDate(supplements, logs, date).filter { it.id !in takenIds }
    }

    fun kcalForDate(supplements: List<Supplement>, logs: Map<String, Set<Long>>, date: String): Int =
        takenForDate(supplements, logs, date).sumOf { it.kcal ?: 0 }

    fun kcalByDate(supplements: List<Supplement>, logs: Map<String, Set<Long>>): Map<String, Int> {
        if (supplements.isEmpty() || logs.isEmpty()) return emptyMap()
        val byId = supplements.associateBy { it.id }
        val out = LinkedHashMap<String, Int>()
        for ((date, ids) in logs) {
            val k = ids.sumOf { byId[it]?.kcal ?: 0 }
            if (k > 0) out[date] = k
        }
        return out
    }

    fun omega3MgForDate(supplements: List<Supplement>, logs: Map<String, Set<Long>>, date: String): Double =
        takenForDate(supplements, logs, date).sumOf { it.omega3Mg ?: 0.0 }

    fun plannedOmega3Mg(supplements: List<Supplement>, logs: Map<String, Set<Long>>, date: String): Double =
        activeForDate(supplements, logs, date).sumOf { it.omega3Mg ?: 0.0 }

    /** Ex. « 500 mg », « 1000 mg (1 g) », « 1250 mg (1,25 g) ». */
    fun formatOmega3(mg: Double): String {
        val mgStr = fmtNumFr(mg) + " mg"
        return if (mg >= 1000.0) "$mgStr (${fmtNumFr(mg / 1000.0)} g)" else mgStr
    }

    private fun fmtNumFr(d: Double): String {
        val rounded = Math.round(d * 100.0) / 100.0
        var s = String.format(Locale.US, "%.2f", rounded)
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return s.replace('.', ',')
    }

    /** Conversion saisie utilisateur (mg ou g) → mg EPA+DHA. */
    fun parseOmega3ToMg(raw: String, unitIsGrams: Boolean): Double? {
        val v = parseNum(raw)?.takeIf { !it.isNaN() && it >= 0.0 } ?: return null
        return if (unitIsGrams) v * 1000.0 else v
    }

    fun notificationTitle(s: Supplement): String =
        if (s.dose.isNotBlank()) "💊 ${s.name} – ${s.dose}" else "💊 ${s.name}"

    fun notificationBody(s: Supplement): String {
        val extras = mutableListOf<String>()
        if (s.hour != null && s.minute != null) extras += "Prise prévue à ${ReminderLogic.timeLabel(s.hour, s.minute)}"
        s.kcal?.takeIf { it > 0 }?.let { extras += "$it kcal" }
        s.omega3Mg?.takeIf { it > 0.0 }?.let { extras += "Oméga-3 ${formatOmega3(it)}" }
        val head = if (extras.isEmpty()) "Rappel de prise du jour" else extras.joinToString(" · ")
        return "$head\nAppuie sur « Pris » pour le cocher aujourd'hui."
    }

    fun listToJson(list: List<Supplement>): String = JSONArray().also { arr ->
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("n", s.name)
                if (s.dose.isNotBlank()) put("d", s.dose)
                s.hour?.let { put("h", it) }
                s.minute?.let { put("m", it) }
                if (s.reminderEnabled) put("rem", true)
                s.kcal?.let { put("k", it) }
                s.omega3Mg?.let { put("o3", it) }
                if (s.createdDate.isNotBlank()) put("cd", s.createdDate)
                s.barcode?.let { put("bc", it) }
                s.photo?.let { put("ph", it) }
            })
        }
    }.toString()

    fun listFromJson(raw: String?): List<Supplement> = runCatching {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optLong("id", 0L)
            val name = o.optString("n", "").trim()
            if (id <= 0L || name.isEmpty()) null else Supplement(
                id = id,
                name = name,
                dose = o.optString("d", ""),
                hour = if (o.has("h") && !o.isNull("h")) o.optInt("h") else null,
                minute = if (o.has("m") && !o.isNull("m")) o.optInt("m") else null,
                reminderEnabled = o.optBoolean("rem", false),
                kcal = if (o.has("k") && !o.isNull("k")) o.optInt("k") else null,
                omega3Mg = if (o.has("o3") && !o.isNull("o3")) o.optDouble("o3").takeIf { !it.isNaN() } else null,
                createdDate = o.optString("cd", ""),
                barcode = o.optString("bc", "").ifBlank { null },
                photo = o.optString("ph", "").ifBlank { null },
            )
        }
    }.getOrDefault(emptyList())

    fun logsToJson(logs: Map<String, Set<Long>>): String = JSONObject().apply {
        logs.forEach { (date, ids) ->
            if (ids.isNotEmpty()) put(date, JSONArray().apply { ids.sorted().forEach { put(it) } })
        }
    }.toString()

    fun logsFromJson(raw: String?): Map<String, Set<Long>> = runCatching {
        val o = JSONObject(raw ?: "{}")
        val out = LinkedHashMap<String, Set<Long>>()
        o.keys().forEach { date ->
            val arr = o.optJSONArray(date) ?: return@forEach
            val set = (0 until arr.length()).mapNotNull { i -> arr.optLong(i, 0L).takeIf { it > 0L } }.toSet()
            if (set.isNotEmpty()) out[date] = set
        }
        out
    }.getOrDefault(emptyMap())
}


/** Validation du formulaire d'ajout / édition (testable sans Android). */
object SupplementFormLogic {
    const val NAME_REQUIRED = "Saisis au moins un nom pour enregistrer."

    fun canSave(name: String): Boolean = name.trim().isNotEmpty()
    fun blockedReason(name: String): String? = if (canSave(name)) null else NAME_REQUIRED

    /** kcal facultatif : vide ou invalide = ignoré, sans bloquer l'enregistrement. */
    fun parseKcal(raw: String): Int? =
        raw.trim().replace(',', '.').toDoubleOrNull()?.let { kotlin.math.round(it).toInt() }?.takeIf { it >= 0 }

    /**
     * Prépare le complément à enregistrer. Null seulement si le nom est vide.
     * Dose, heure, rappel, kcal, oméga-3, code-barres et photo restent facultatifs.
     * Un rappel sans heure est enregistré mais désactivé (sanitize). L'alarme exacte n'est pas requise.
     */
    fun prepared(s: Supplement): Supplement? {
        val clean = SupplementLogic.sanitize(s)
        return clean.takeIf { it.name.isNotEmpty() }
    }

    fun fromForm(
        id: Long,
        name: String,
        dose: String = "",
        hour: Int? = null,
        minute: Int? = null,
        reminderEnabled: Boolean = false,
        kcalText: String = "",
        omegaText: String = "",
        omegaInGrams: Boolean = false,
        createdDate: String = "",
        barcode: String? = null,
        photo: String? = null,
    ): Supplement? = prepared(
        Supplement(
            id = id,
            name = name,
            dose = dose,
            hour = hour,
            minute = minute,
            reminderEnabled = reminderEnabled,
            kcal = parseKcal(kcalText),
            omega3Mg = SupplementLogic.parseOmega3ToMg(omegaText, omegaInGrams),
            createdDate = createdDate,
            barcode = barcode,
            photo = photo,
        )
    )
}
