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
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Réglages du rappel (préférences simples, non sensibles). */
class ReminderSettings(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("reminder", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) { prefs.edit().putBoolean("enabled", v).apply() }
    var hour: Int
        get() = prefs.getInt("hour", ReminderLogic.DEFAULT_HOUR)
        set(v) { prefs.edit().putInt("hour", v).apply() }
    var minute: Int
        get() = prefs.getInt("minute", ReminderLogic.DEFAULT_MINUTE)
        set(v) { prefs.edit().putInt("minute", v).apply() }
}

/**
 * Programmation locale avec AlarmManager (alarme inexacte autorisée en veille : pas besoin de SCHEDULE_EXACT_ALARM ;
 * Android peut la décaler de quelques minutes). Le récepteur reprogramme le lendemain à chaque déclenchement.
 */
object ReminderScheduler {
    const val CHANNEL = "rappel_soir"
    private const val ACTION = "com.wolnerman.calories.RAPPEL_SOIR"
    private const val NOTIF_ID = 2001
    const val EXTRA_TAB = "tab"

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 1, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Programme (ou annule) selon les réglages. Renvoie l'heure du prochain rappel, ou null. */
    fun schedule(ctx: Context): LocalDateTime? {
        val s = ReminderSettings(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return null
        am.cancel(pending(ctx))
        if (!s.enabled) return null
        val next = ReminderLogic.nextTrigger(LocalDateTime.now(), s.hour, s.minute)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(ctx)) }
        catch (e: Exception) { Log.w("Calories", "Rappel non programmé", e); am.set(AlarmManager.RTC_WAKEUP, millis, pending(ctx)) }
        return next
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Rappel du soir", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Rappel quotidien quand l'objectif de calories n'est pas atteint"
                })
        }
    }

    fun canNotify(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Affiche la notification (appui → appli ouverte sur « Aujourd'hui »). Renvoie false si interdit. */
    fun show(ctx: Context, text: String): Boolean {
        if (!canNotify(ctx)) return false
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(ctx, 2,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_TAB, 0),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Calories — objectif du jour")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n); true }
        catch (e: SecurityException) { false }
    }

    /** Vérifie le total du jour et notifie si l'objectif n'est pas atteint. */
    fun checkNow(ctx: Context): String? {
        val storage = Storage(ctx.applicationContext)
        val msg = ReminderLogic.message(ReminderLogic.todayTotal(storage.loadEntries(), LocalDate.now()), storage.dailyGoal)
        if (msg != null) show(ctx, msg)
        return msg
    }
}

/** Déclenchement quotidien. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try { if (ReminderSettings(context).enabled) ReminderScheduler.checkNow(context) }
        catch (e: Exception) { Log.w("Calories", "Rappel", e) }
        finally { runCatching { ReminderScheduler.schedule(context) } }
    }
}

/** Reprogramme après redémarrage, mise à jour de l'appli ou changement d'heure. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { ReminderScheduler.schedule(context) }
    }
}
