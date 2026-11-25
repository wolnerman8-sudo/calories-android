package com.wolnerman.calories

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Écrit le fichier dans le cache et ouvre la feuille de partage Android. */
fun shareExport(ctx: Context, fileName: String, content: String, mime: String): String? = try {
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // un seul export à la fois dans le cache
    val f = File(dir, fileName)
    f.writeText(content, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Calories — export $fileName")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Exporter avec…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    null
} catch (e: Exception) { "Export impossible : ${e.message ?: e.javaClass.simpleName}" }

@Composable
fun ExportCard(
    entries: List<Entry>,
    goal: Int,
    supplements: List<Supplement> = emptyList(),
    supplementLogs: Map<String, Set<Long>> = emptyMap(),
) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    val stamp = { LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Export des données", style = MaterialTheme.typography.titleMedium)
            Text("Tout ton historique en fichier, à envoyer par mail, Drive, WhatsApp… " +
                "Le CSV s'ouvre directement dans Excel / LibreOffice (séparateur « ; », virgule décimale).",
                style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            if (entries.isEmpty() && supplements.isEmpty()) {
                Text("Aucune donnée à exporter pour l'instant : ajoute d'abord des aliments ou compléments.", color = Color.Gray)
            } else {
                val days = entries.map { it.date }.distinct().size
                if (entries.isNotEmpty()) {
                    Text("${entries.size} aliment(s) sur $days jour(s).", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        msg = shareExport(ctx, "calories_${stamp()}.csv", ExportData.entriesCsv(entries), "text/csv")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Exporter les repas (CSV)") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entries.isNotEmpty() || supplementLogs.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            msg = shareExport(ctx, "calories_jours_${stamp()}.csv",
                                ExportData.dailyCsv(entries, goal, supplements, supplementLogs), "text/csv")
                        }, modifier = Modifier.weight(1f)) { Text("Bilan par jour (CSV)") }
                    }
                    OutlinedButton(onClick = {
                        msg = shareExport(ctx, "calories_${stamp()}.json",
                            ExportData.json(entries, goal, appVersion(ctx), LocalDateTime.now().toString(), supplements, supplementLogs),
                            "application/json")
                    }, modifier = Modifier.weight(1f)) { Text("Tout (JSON)") }
                }
                if (supplements.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        msg = shareExport(ctx, "calories_complements_${stamp()}.csv",
                            ExportData.supplementsCsv(supplements, supplementLogs, java.time.LocalDate.now().toString()), "text/csv")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Compléments (CSV)") }
                }
            }
            msg?.let { Text(it, color = Color(0xFFC62828)) }
        }
    }
}

@Composable
fun ReminderCard() {
    val ctx = LocalContext.current
    val settings = remember { ReminderSettings(ctx) }
    var eveningOn by remember { mutableStateOf(settings.enabled) }
    var morningOn by remember { mutableStateOf(settings.morningEnabled) }
    var hour by remember { mutableIntStateOf(settings.hour) }
    var minute by remember { mutableIntStateOf(settings.minute) }
    var morningHour by remember { mutableIntStateOf(settings.morningHour) }
    var morningMinute by remember { mutableIntStateOf(settings.morningMinute) }
    var info by remember { mutableStateOf<String?>(null) }
    var pendingTest by remember { mutableStateOf<String?>(null) } // "evening" ou "morning"

    fun labelFor(next: java.time.LocalDateTime, suffix: String): String {
        val day = if (next.toLocalDate() == java.time.LocalDate.now()) "aujourd'hui" else "demain"
        return "$suffix $day à ${ReminderLogic.timeLabel(next.hour, next.minute)}"
    }
    fun refreshLabels(): String? {
        ReminderScheduler.schedule(ctx)
        val parts = mutableListOf<String>()
        if (settings.morningEnabled) ReminderScheduler.nextMorning(ctx)?.let {
            parts += labelFor(it, "Rappel du matin :")
        }
        if (settings.enabled) ReminderScheduler.schedule(ctx)?.let {
            parts += labelFor(it, "Rappel du soir :") + " (seulement ce qu'il te manque)."
        }
        return parts.joinToString("\n").ifBlank { null }
    }
    fun testEvening() {
        val (text, title) = ReminderScheduler.testEveningText(ctx)
        info = if (ReminderScheduler.show(ctx, text, title)) "Notification de test envoyée."
        else "Notifications bloquées : autorise-les dans les réglages Android de l'appli."
    }
    fun testMorning() {
        val text = ReminderScheduler.testMorningText(ctx)
        info = if (text == null) "Pas d'objectif kcal défini : le rappel du matin ne serait pas envoyé."
        else if (ReminderScheduler.show(ctx, text, "Calories — objectifs du jour", 2002)) "Notification de test envoyée."
        else "Notifications bloquées : autorise-les dans les réglages Android de l'appli."
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) info = "Sans l'autorisation des notifications, le rappel ne peut pas s'afficher. " +
            "Tu peux l'autoriser plus tard dans Réglages › Applis › Calories › Notifications."
        else info = refreshLabels()
        val t = pendingTest; pendingTest = null
        if (ok && t == "evening") testEvening()
        if (ok && t == "morning") testMorning()
    }
    fun needsPermission() = Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.canNotify(ctx)

    LaunchedEffect(Unit) { ReminderScheduler.ensureChannel(ctx); if (eveningOn || morningOn) info = refreshLabels() }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rappels", style = MaterialTheme.typography.titleMedium)
            Text("Les notifications reprennent ton objectif effectif (Maintien, Perte, Prise ou personnalisé) " +
                "ainsi que les repères de glucides, protéines et lipides. Elles sont sonores. Tout reste sur le téléphone.",
                style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)

            Text("Rappel du matin : objectifs du jour", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activer le rappel du matin", modifier = Modifier.weight(1f))
                Switch(checked = morningOn, onCheckedChange = { on ->
                    morningOn = on; settings.morningEnabled = on
                    if (on) {
                        info = refreshLabels()
                        if (needsPermission()) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else { ReminderScheduler.schedule(ctx); info = refreshLabels() ?: "Rappel du matin désactivé." }
                })
            }
            Text("Une notification avec tes objectifs du jour (kcal, glucides, protéines, lipides). Désactivé par défaut.",
                style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Heure : ", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m ->
                        morningHour = h; morningMinute = m; settings.morningHour = h; settings.morningMinute = m
                        if (morningOn) info = refreshLabels()
                    }, morningHour, morningMinute, true).show()
                }) { Text(ReminderLogic.timeLabel(morningHour, morningMinute)) }
            }
            OutlinedButton(onClick = {
                if (needsPermission()) { pendingTest = "morning"; permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } else testMorning()
            }) { Text("Tester le rappel du matin") }

            Divider()

            Text("Rappel du soir : ce qu'il te manque", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activer le rappel du soir", modifier = Modifier.weight(1f))
                Switch(checked = eveningOn, onCheckedChange = { on ->
                    eveningOn = on; settings.enabled = on
                    if (on) {
                        info = refreshLabels()
                        if (needsPermission()) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else { ReminderScheduler.schedule(ctx); info = refreshLabels() ?: "Rappel du soir désactivé." }
                })
            }
            Text("Si une partie de l'objectif n'est pas encore atteinte, la notification indique ce qu'il te manque. " +
                "Les macros déjà atteintes sont omises. Si tout est atteint, rien n'est envoyé.",
                style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Heure : ", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m ->
                        hour = h; minute = m; settings.hour = h; settings.minute = m
                        if (eveningOn) info = refreshLabels()
                    }, hour, minute, true).show()
                }) { Text(ReminderLogic.timeLabel(hour, minute)) }
            }
            OutlinedButton(onClick = {
                if (needsPermission()) { pendingTest = "evening"; permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } else testEvening()
            }) { Text("Tester la notification") }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Android peut décaler le rappel de quelques minutes pour économiser la batterie.",
                style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

fun appVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" }.getOrDefault("?")
