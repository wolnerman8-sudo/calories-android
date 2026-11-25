package com.wolnerman.calories

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Réglages des rappels (préférences simples, non sensibles). */
class ReminderSettings(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("reminder", Context.MODE_PRIVATE)
    /** Rappel du soir (existant, désactivé par défaut). */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) { prefs.edit().putBoolean("enabled", v).apply() }
    var hour: Int
        get() = prefs.getInt("hour", ReminderLogic.DEFAULT_HOUR)
        set(v) { prefs.edit().putInt("hour", v).apply() }
    var minute: Int
        get() = prefs.getInt("minute", ReminderLogic.DEFAULT_MINUTE)
        set(v) { prefs.edit().putInt("minute", v).apply() }
    /** Rappel du matin : objectifs du jour (désactivé par défaut). */
    var morningEnabled: Boolean
        get() = prefs.getBoolean("morning_enabled", false)
        set(v) { prefs.edit().putBoolean("morning_enabled", v).apply() }
    var morningHour: Int
        get() = prefs.getInt("morning_hour", ReminderLogic.DEFAULT_MORNING_HOUR)
        set(v) { prefs.edit().putInt("morning_hour", v).apply() }
    var morningMinute: Int
        get() = prefs.getInt("morning_minute", ReminderLogic.DEFAULT_MORNING_MINUTE)
        set(v) { prefs.edit().putInt("morning_minute", v).apply() }
}

enum class ReminderKind { EVENING, MORNING }

/**
 * Programmation locale avec AlarmManager (alarme inexacte autorisée en veille : pas besoin de SCHEDULE_EXACT_ALARM ;
 * Android peut la décaler de quelques minutes). Le récepteur reprogramme le lendemain à chaque déclenchement.
 */
object ReminderScheduler {
    /** Nouveau canal v2.17 : importance et son figés à la création, d'où un nouvel identifiant. */
    const val CHANNEL = "rappels_v217"
    private const val ACTION_EVENING = "com.wolnerman.calories.RAPPEL_SOIR"
    private const val ACTION_MORNING = "com.wolnerman.calories.RAPPEL_MATIN"
    private const val NOTIF_EVENING = 2001
    private const val NOTIF_MORNING = 2002
    const val EXTRA_TAB = "tab"

    private fun pending(ctx: Context, code: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun pendingEvening(ctx: Context) = pending(ctx, 1, ACTION_EVENING)
    private fun pendingMorning(ctx: Context) = pending(ctx, 3, ACTION_MORNING)

    private fun setAlarm(am: AlarmManager, at: LocalDateTime, pi: PendingIntent) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi) }
        catch (e: Exception) { Log.w("Calories", "Rappel non programmé", e); am.set(AlarmManager.RTC_WAKEUP, millis, pi) }
    }

    /** Programme (ou annule) les deux rappels. Renvoie l'heure du prochain rappel du soir, ou null. */
    fun schedule(ctx: Context): LocalDateTime? {
        val s = ReminderSettings(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return null
        val now = LocalDateTime.now()
        am.cancel(pendingEvening(ctx))
        val nextEvening = if (s.enabled) {
            val next = ReminderLogic.nextTrigger(now, s.hour, s.minute)
            setAlarm(am, next, pendingEvening(ctx))
            next
        } else null
        am.cancel(pendingMorning(ctx))
        if (s.morningEnabled) setAlarm(am, ReminderLogic.nextTrigger(now, s.morningHour, s.morningMinute), pendingMorning(ctx))
        return nextEvening
    }

    fun nextMorning(ctx: Context): LocalDateTime? {
        val s = ReminderSettings(ctx)
        if (!s.morningEnabled) return null
        return ReminderLogic.nextTrigger(LocalDateTime.now(), s.morningHour, s.morningMinute)
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Rappels (calories et macros)", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Rappel du matin (objectifs du jour) et du soir (ce qu'il te manque), avec son."
                        setSound(sound, attrs)
                        enableVibration(true)
                    })
            }
        }
    }

    fun canNotify(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Affiche la notification (appui → appli ouverte sur « Aujourd'hui »). Renvoie false si interdit. */
    fun show(ctx: Context, text: String, title: String = "Calories — objectif du jour", id: Int = NOTIF_EVENING): Boolean {
        if (!canNotify(ctx)) return false
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(ctx, 2,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_TAB, 0),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try { NotificationManagerCompat.from(ctx).notify(id, n); true }
        catch (e: SecurityException) { false }
    }

    fun currentSnapshot(ctx: Context): Triple<Int, Macros, MacroTargets> {
        val storage = Storage(ctx.applicationContext)
        val today = LocalDate.now()
        val entries = storage.loadEntries()
        val kcal = storage.currentGoal().kcal
        val profile = storage.loadProfile()
        val lastWeighIn = runCatching { BodyScaleManager.get(ctx.applicationContext).history.firstOrNull()?.kg }.getOrNull()
        val weight = ProteinLogic.currentWeight(profile.weightKg, lastWeighIn)
        val targets = MacroGoalLogic.targets(kcal, weight)
        Supplements.init(ctx.applicationContext)
        val suppKcal = SupplementLogic.kcalForDate(Supplements.items, Supplements.logs, today.toString())
        return Triple(ReminderLogic.todayTotal(entries, today) + suppKcal, ReminderLogic.todayMacros(entries, today), targets)
    }

    /** Vérifie le total du jour et notifie selon le type de rappel. Renvoie le texte envoyé, ou null. */
    fun checkNow(ctx: Context, kind: ReminderKind = ReminderKind.EVENING): String? {
        val (total, macros, targets) = currentSnapshot(ctx)
        val (msg, title, id) = when (kind) {
            ReminderKind.MORNING -> Triple(ReminderLogic.morningMessage(targets), "Calories — objectifs du jour", NOTIF_MORNING)
            ReminderKind.EVENING -> Triple(ReminderLogic.eveningMessage(total, targets, macros), "Calories — ce qu'il te manque", NOTIF_EVENING)
        }
        if (msg != null) show(ctx, msg, title, id)
        return msg
    }

    /** Texte de test (toujours non null) : format du soir, ou message positif + objectifs si tout est atteint. */
    fun testEveningText(ctx: Context): Pair<String, String> {
        val (total, macros, targets) = currentSnapshot(ctx)
        val miss = ReminderLogic.eveningMessage(total, targets, macros)
        return if (miss != null) miss to "Calories — ce qu'il te manque"
        else {
            val reached = ReminderLogic.reachedMessage(targets, total)
            val obj = ReminderLogic.morningMessage(targets)
            (if (obj != null) "$reached $obj" else reached) to "Calories — objectif atteint"
        }
    }

    fun testMorningText(ctx: Context): String? {
        val (_, _, targets) = currentSnapshot(ctx)
        return ReminderLogic.morningMessage(targets)
    }
}

/** Déclenchement quotidien (matin ou soir, selon l'action). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = ReminderSettings(context)
        try {
            when (intent.action) {
                "com.wolnerman.calories.RAPPEL_MATIN" -> if (s.morningEnabled) ReminderScheduler.checkNow(context, ReminderKind.MORNING)
                else -> if (s.enabled) ReminderScheduler.checkNow(context, ReminderKind.EVENING)
            }
        } catch (e: Exception) { Log.w("Calories", "Rappel", e) }
        finally { runCatching { ReminderScheduler.schedule(context) } }
    }
}

/** Reprogramme après redémarrage, mise à jour de l'appli ou changement d'heure. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { ReminderScheduler.schedule(context) }
        runCatching { Supplements.init(context); SupplementScheduler.scheduleAll(context) }
    }
}
