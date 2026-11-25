package com.wolnerman.calories

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** v2.18 : persistance locale et état réactif des compléments alimentaires. */
object Supplements {
    var items by mutableStateOf<List<Supplement>>(emptyList()); private set
    var logs by mutableStateOf<Map<String, Set<Long>>>(emptyMap()); private set

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("supplements", Context.MODE_PRIVATE)

    @Synchronized
    fun init(ctx: Context) {
        val p = prefs(ctx)
        items = SupplementLogic.listFromJson(p.getString("list", "[]"))
        logs = SupplementLogic.logsFromJson(p.getString("logs", "{}"))
    }

    @Synchronized
    fun reloadIfInitialized(ctx: Context) = init(ctx)

    @Synchronized
    fun saveSupplement(ctx: Context, s: Supplement) {
        val withDate = if (s.createdDate.isBlank()) s.copy(createdDate = LocalDate.now().toString()) else s
        val next = SupplementLogic.upsert(items, withDate)
        items = next
        prefs(ctx).edit().putString("list", SupplementLogic.listToJson(next)).apply()
        SupplementScheduler.scheduleAll(ctx)
    }

    @Synchronized
    fun deleteSupplement(ctx: Context, id: Long) {
        val nextItems = SupplementLogic.delete(items, id)
        val nextLogs = SupplementLogic.purgeFromLogs(logs, id)
        items = nextItems
        logs = nextLogs
        prefs(ctx).edit()
            .putString("list", SupplementLogic.listToJson(nextItems))
            .putString("logs", SupplementLogic.logsToJson(nextLogs))
            .apply()
        NotificationManagerCompat.from(ctx.applicationContext).cancel(SupplementScheduler.notifId(id))
        SupplementScheduler.scheduleAll(ctx)
    }

    @Synchronized
    fun setTaken(ctx: Context, date: String, id: Long, taken: Boolean) {
        val nextLogs = SupplementLogic.markTaken(logs, date, id, taken)
        logs = nextLogs
        prefs(ctx).edit().putString("logs", SupplementLogic.logsToJson(nextLogs)).apply()
        if (taken && date == LocalDate.now().toString()) {
            runCatching { NotificationManagerCompat.from(ctx.applicationContext).cancel(SupplementScheduler.notifId(id)) }
        }
    }

    fun isTaken(id: Long, date: String = LocalDate.now().toString()): Boolean =
        SupplementLogic.isTaken(logs, date, id)

    internal fun setForTest(list: List<Supplement>, logMap: Map<String, Set<Long>> = emptyMap()) {
        items = list
        logs = logMap
    }
}

/** Programmation locale des rappels par complément (canal sonore dédié + bouton « Pris »). */
object SupplementScheduler {
    const val CHANNEL = "complements_v218"
    const val ACTION_FIRE = "com.wolnerman.calories.COMPLEMENT_RAPPEL"
    const val ACTION_MARK_TAKEN = "com.wolnerman.calories.COMPLEMENT_PRIS"
    const val EXTRA_SUPP_ID = "supp_id"
    const val EXTRA_DATE = "supp_date"

    fun notifId(suppId: Long): Int = 3000 + ((suppId xor (suppId ushr 32)).toInt() and 0x0FFFFFFF)
    private fun fireReqCode(suppId: Long): Int = 10000 + ((suppId xor (suppId ushr 32)).toInt() and 0x0FFFFFFF)
    private fun takenReqCode(suppId: Long): Int = 20000 + ((suppId xor (suppId ushr 32)).toInt() and 0x0FFFFFFF)

    private fun firePending(ctx: Context, suppId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, fireReqCode(suppId),
            Intent(ctx, SupplementReceiver::class.java)
                .setAction(ACTION_FIRE)
                .setData(Uri.parse("calories://supplement/fire/$suppId"))
                .putExtra(EXTRA_SUPP_ID, suppId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun takenPending(ctx: Context, suppId: Long, date: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, takenReqCode(suppId),
            Intent(ctx, SupplementReceiver::class.java)
                .setAction(ACTION_MARK_TAKEN)
                .setData(Uri.parse("calories://supplement/taken/$suppId"))
                .putExtra(EXTRA_SUPP_ID, suppId)
                .putExtra(EXTRA_DATE, date),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun scheduleAll(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val prefs = app.getSharedPreferences("supplements", Context.MODE_PRIVATE)
        val prevIds = prefs.getStringSet("scheduled_ids", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
        prevIds.forEach { am.cancel(firePending(app, it)) }

        val now = LocalDateTime.now()
        val activeIds = mutableSetOf<String>()
        for (s in Supplements.items) {
            if (!s.reminderEnabled || s.hour == null || s.minute == null) {
                am.cancel(firePending(app, s.id))
                continue
            }
            val next = ReminderLogic.nextTrigger(now, s.hour, s.minute)
            val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val pi = firePending(app, s.id)
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi) }
            catch (e: Exception) { Log.w("Calories", "Rappel complément non programmé", e); am.set(AlarmManager.RTC_WAKEUP, millis, pi) }
            activeIds += s.id.toString()
        }
        prefs.edit().putStringSet("scheduled_ids", activeIds).apply()
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
                    NotificationChannel(CHANNEL, "Compléments alimentaires", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Rappels quotidiens pour les compléments alimentaires, avec son et bouton Pris."
                        setSound(sound, attrs)
                        enableVibration(true)
                    })
            }
        }
    }

    fun show(ctx: Context, s: Supplement, date: String = LocalDate.now().toString()): Boolean {
        if (!ReminderScheduler.canNotify(ctx)) return false
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 4,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(ReminderScheduler.EXTRA_TAB, 0),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = SupplementLogic.notificationTitle(s)
        val body = SupplementLogic.notificationBody(s)
        val markAction = NotificationCompat.Action.Builder(0, "Pris", takenPending(ctx, s.id, date)).build()
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .addAction(markAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try { NotificationManagerCompat.from(ctx).notify(notifId(s.id), n); true }
        catch (e: SecurityException) { false }
    }
}

/** Récepteur pour les rappels de compléments et le bouton « Pris » sur la notification. */
class SupplementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Supplements.init(context)
        val id = intent.getLongExtra(SupplementScheduler.EXTRA_SUPP_ID, -1L)
        if (id <= 0L) return
        when (intent.action) {
            SupplementScheduler.ACTION_MARK_TAKEN -> {
                val date = intent.getStringExtra(SupplementScheduler.EXTRA_DATE) ?: LocalDate.now().toString()
                Supplements.setTaken(context, date, id, true)
                NotificationManagerCompat.from(context).cancel(SupplementScheduler.notifId(id))
            }
            SupplementScheduler.ACTION_FIRE -> {
                try {
                    val today = LocalDate.now().toString()
                    val s = Supplements.items.firstOrNull { it.id == id }
                    if (s != null && s.reminderEnabled && s.hour != null && s.minute != null && !Supplements.isTaken(id, today)) {
                        SupplementScheduler.show(context, s, today)
                    }
                } catch (e: Exception) {
                    Log.w("Calories", "Rappel complément", e)
                } finally {
                    runCatching { SupplementScheduler.scheduleAll(context) }
                }
            }
        }
    }
}
