package com.wolnerman.calories

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.Device
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Conversion d'une entrée du journal en NutritionRecord (pur, testable sur JVM). */
object HealthMapping {
    /** Identifiant stable côté appli : permet la mise à jour (upsert) et la suppression. */
    fun clientId(e: Entry) = "calories-${e.id}"

    /** Repas selon l'heure : <10:30 petit-déjeuner, <15:00 déjeuner, <18:00 collation, sinon dîner. */
    fun mealType(time: String): Int {
        val t = runCatching { LocalTime.parse(time) }.getOrNull() ?: return MealType.MEAL_TYPE_UNKNOWN
        return when {
            t < LocalTime.of(4, 0) -> MealType.MEAL_TYPE_SNACK
            t < LocalTime.of(10, 30) -> MealType.MEAL_TYPE_BREAKFAST
            t < LocalTime.of(15, 0) -> MealType.MEAL_TYPE_LUNCH
            t < LocalTime.of(18, 0) -> MealType.MEAL_TYPE_SNACK
            else -> MealType.MEAL_TYPE_DINNER
        }
    }

    fun start(e: Entry, zone: ZoneId): ZonedDateTime {
        val d = runCatching { LocalDate.parse(e.date) }.getOrElse { LocalDate.now(zone) }
        val t = runCatching { LocalTime.parse(e.time) }.getOrElse { LocalTime.NOON }
        return LocalDateTime.of(d, t).atZone(zone)
    }

    fun toRecord(e: Entry, zone: ZoneId = ZoneId.systemDefault()): NutritionRecord {
        val s = start(e, zone)
        val end = s.plusMinutes(1)
        return NutritionRecord(
            startTime = s.toInstant(), startZoneOffset = s.offset,
            endTime = end.toInstant(), endZoneOffset = end.offset,
            energy = Energy.kilocalories(e.grams * e.kcalPer100g / 100.0),
            totalCarbohydrate = e.carbs100?.let { Mass.grams(it * e.grams / 100.0) },
            protein = e.proteins100?.let { Mass.grams(it * e.grams / 100.0) },
            totalFat = e.fat100?.let { Mass.grams(it * e.grams / 100.0) },
            name = "${e.name} (${fmtG(e.grams)} g)",
            mealType = mealType(e.time),
            metadata = Metadata(clientRecordId = clientId(e), clientRecordVersion = e.id),
        )
    }

    private fun fmtG(g: Double) = if (g % 1.0 == 0.0) g.toLong().toString() else String.format(java.util.Locale.FRENCH, "%.1f", g)
}

/** Données lues dans Santé Connect pour l'onglet Profil. */
data class HealthSummary(val weightKg: Double?, val weightTime: Instant?, val stepsToday: Long?, val error: String? = null)

/** Intégration Santé Connect (Health Connect). Toutes les fonctions suspend sont à appeler depuis une coroutine. */
object HealthSync {
    const val PROVIDER = "com.google.android.apps.healthdata"

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),   // v2.5 : pesées de la balance pèse-personne
        HealthPermission.getReadPermission(StepsRecord::class),
    )
    val WRITE_WEIGHT: String = HealthPermission.getWritePermission(WeightRecord::class)
    val READ_WEIGHT: String = HealthPermission.getReadPermission(WeightRecord::class)

    enum class WeightWrite { WRITTEN, DUPLICATE }

    /** Raisons d'échec affichées telles quelles (françaises, non bloquantes). */
    class WeightError(msg: String, val needsPermission: Boolean = false) : Exception(msg)

    /**
     * v2.5 : écrit une pesée (WeightRecord) dans Santé Connect, origine = Calories, appareil = balance.
     * Pas de doublon : si un poids identique (± 0,05 kg) existe déjà à moins de 2 minutes, rien n'est écrit.
     */
    suspend fun writeWeight(ctx: Context, kg: Double, at: Instant, model: String): Result<WeightWrite> = runCatching {
        if (!available(ctx)) throw WeightError("Santé Connect n'est pas disponible sur ce téléphone.")
        val c = client(ctx)
        val g = c.permissionController.getGrantedPermissions()
        if (WRITE_WEIGHT !in g) throw WeightError("autorisation « Poids » (écriture) non accordée à Calories dans Santé Connect.", needsPermission = true)
        if (READ_WEIGHT in g) {
            val near = c.readRecords(ReadRecordsRequest(WeightRecord::class,
                TimeRangeFilter.between(at.minusMillis(WeighInRules.DEDUPE_MS), at.plusMillis(WeighInRules.DEDUPE_MS)))).records
            if (WeighInRules.isDuplicate(kg, at.toEpochMilli(), near.map { WeighIn(it.time.toEpochMilli(), it.weight.inKilograms, "") }))
                return@runCatching WeightWrite.DUPLICATE
        }
        val zone = ZoneId.systemDefault().rules.getOffset(at)
        c.insertRecords(listOf(WeightRecord(time = at, zoneOffset = zone, weight = Mass.kilograms(kg),
            metadata = Metadata(clientRecordId = "calories-weight-${at.toEpochMilli()}", clientRecordVersion = 1,
                device = Device(manufacturer = "OKOK / Chipsea", model = model.take(40), type = Device.TYPE_SCALE),
                recordingMethod = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED))))
        HealthRefresh.tick++
        WeightWrite.WRITTEN
    }

    /** SDK_AVAILABLE, SDK_UNAVAILABLE ou SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED (Santé Connect à installer / mettre à jour). */
    fun status(ctx: Context): Int =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) HealthConnectClient.SDK_UNAVAILABLE // Android 9 minimum
        else runCatching { HealthConnectClient.getSdkStatus(ctx, PROVIDER) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    fun available(ctx: Context) = status(ctx) == HealthConnectClient.SDK_AVAILABLE

    /** Lien Play Store pour installer / mettre à jour Santé Connect (Android 9 à 13). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")).apply {
        setPackage("com.android.vending"); putExtra("overlay", true); putExtra("callerId", "com.wolnerman.calories")
    }
    fun installWebIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER"))

    private fun client(ctx: Context) = HealthConnectClient.getOrCreate(ctx, PROVIDER)

    suspend fun granted(ctx: Context): Set<String> =
        if (!available(ctx)) emptySet() else runCatching { client(ctx).permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    private fun enabled(ctx: Context) = SecureSettings(ctx).healthEnabled && available(ctx)

    /** Écrit (ou met à jour) les entrées dans Santé Connect. Renvoie le nombre écrit, ou un message d'erreur. */
    suspend fun write(ctx: Context, entries: List<Entry>): Result<Int> = runCatching {
        if (!enabled(ctx) || entries.isEmpty()) return@runCatching 0
        val c = client(ctx)
        val write = HealthPermission.getWritePermission(NutritionRecord::class)
        if (write !in c.permissionController.getGrantedPermissions()) error("autorisation d'écriture refusée")
        // v2.8 : une entrée « poids seul » (kcal inconnues) n'est pas envoyée (elle fausserait l'énergie du jour)
        val known = entries.filter { !it.kcalUnknown }
        known.chunked(100).forEach { batch -> c.insertRecords(batch.map { HealthMapping.toRecord(it) }) }
        known.size
    }

    /** Supprime les enregistrements correspondant à ces entrées (par identifiant client). */
    suspend fun delete(ctx: Context, entries: List<Entry>): Result<Unit> = runCatching {
        if (!enabled(ctx) || entries.isEmpty()) return@runCatching
        client(ctx).deleteRecords(NutritionRecord::class, emptyList(), entries.map { HealthMapping.clientId(it) })
    }

    /** Dernier poids (90 derniers jours) et pas d'aujourd'hui. */
    suspend fun summary(ctx: Context): HealthSummary {
        if (!available(ctx)) return HealthSummary(null, null, null, "Santé Connect indisponible")
        return try {
            val c = client(ctx)
            val g = c.permissionController.getGrantedPermissions()
            val now = Instant.now()
            var w: WeightRecord? = null
            if (HealthPermission.getReadPermission(WeightRecord::class) in g) {
                w = c.readRecords(ReadRecordsRequest(WeightRecord::class,
                    TimeRangeFilter.between(now.minus(90, ChronoUnit.DAYS), now), ascendingOrder = false, pageSize = 1)).records.firstOrNull()
            }
            var steps: Long? = null
            if (HealthPermission.getReadPermission(StepsRecord::class) in g) {
                val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                steps = c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL),
                    TimeRangeFilter.between(startOfDay, now)))[StepsRecord.COUNT_TOTAL] ?: 0L
            }
            HealthSummary(w?.weight?.inKilograms, w?.time, steps)
        } catch (e: Exception) {
            HealthSummary(null, null, null, e.message ?: e.javaClass.simpleName)
        }
    }
}

/** v2.5 : incrémenté après chaque pesée écrite, pour que la carte Santé Connect relise le dernier poids. */
object HealthRefresh {
    var tick by mutableIntStateOf(0)
}
