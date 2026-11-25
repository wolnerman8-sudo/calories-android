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
fun ExportCard(entries: List<Entry>, goal: Int) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    val stamp = { LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Export des données", style = MaterialTheme.typography.titleMedium)
            Text("Tout ton historique en fichier, à envoyer par mail, Drive, WhatsApp… " +
                "Le CSV s'ouvre directement dans Excel / LibreOffice (séparateur « ; », virgule décimale).",
                style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            if (entries.isEmpty()) {
                Text("Aucune donnée à exporter pour l'instant : ajoute d'abord des aliments.", color = Color.Gray)
            } else {
                val days = entries.map { it.date }.distinct().size
                Text("${entries.size} aliment(s) sur $days jour(s).", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    msg = shareExport(ctx, "calories_${stamp()}.csv", ExportData.entriesCsv(entries), "text/csv")
                }, modifier = Modifier.fillMaxWidth()) { Text("Exporter les repas (CSV)") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        msg = shareExport(ctx, "calories_jours_${stamp()}.csv", ExportData.dailyCsv(entries, goal), "text/csv")
                    }, modifier = Modifier.weight(1f)) { Text("Bilan par jour (CSV)") }
                    OutlinedButton(onClick = {
                        msg = shareExport(ctx, "calories_${stamp()}.json",
                            ExportData.json(entries, goal, appVersion(ctx), LocalDateTime.now().toString()), "application/json")
                    }, modifier = Modifier.weight(1f)) { Text("Tout (JSON)") }
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
    var enabled by remember { mutableStateOf(settings.enabled) }
    var hour by remember { mutableIntStateOf(settings.hour) }
    var minute by remember { mutableIntStateOf(settings.minute) }
    var info by remember { mutableStateOf<String?>(null) }
    var pendingTest by remember { mutableStateOf(false) }

    fun nextLabel(): String? = ReminderScheduler.schedule(ctx)?.let { n ->
        val day = if (n.toLocalDate() == java.time.LocalDate.now()) "aujourd'hui" else "demain"
        "Prochain rappel : $day à ${ReminderLogic.timeLabel(n.hour, n.minute)} (seulement si l'objectif n'est pas atteint)."
    }
    fun test() {
        val storage = Storage(ctx.applicationContext)
        val total = ReminderLogic.todayTotal(storage.loadEntries(), java.time.LocalDate.now())
        val goal = storage.currentGoal().kcal
        val text = ReminderLogic.message(total, goal)
            ?: "Test : objectif atteint aujourd'hui ($total / $goal kcal), le vrai rappel ne serait pas envoyé."
        info = if (ReminderScheduler.show(ctx, text)) "Notification de test envoyée."
        else "Notifications bloquées : autorise-les dans les réglages Android de l'appli."
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) info = "Sans l'autorisation des notifications, le rappel ne peut pas s'afficher. " +
            "Tu peux l'autoriser plus tard dans Réglages › Applis › Calories › Notifications."
        else info = nextLabel()
        if (pendingTest) { pendingTest = false; if (ok) test() }
    }
    fun needsPermission() = Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.canNotify(ctx)

    LaunchedEffect(Unit) { ReminderScheduler.ensureChannel(ctx); if (enabled) info = nextLabel() }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rappel du soir", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { on ->
                    enabled = on; settings.enabled = on
                    if (on) {
                        info = nextLabel()
                        if (needsPermission()) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else { ReminderScheduler.schedule(ctx); info = "Rappel désactivé." }
                })
            }
            Text("Chaque jour à l'heure choisie, si tu n'as pas encore atteint ton objectif de calories, " +
                "une notification te dit combien il te manque. Tout reste sur le téléphone.",
                style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Heure : ", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m ->
                        hour = h; minute = m; settings.hour = h; settings.minute = m
                        if (enabled) info = nextLabel()
                    }, hour, minute, true).show()
                }) { Text(ReminderLogic.timeLabel(hour, minute)) }
            }
            OutlinedButton(onClick = {
                if (needsPermission()) { pendingTest = true; permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } else test()
            }) { Text("Tester la notification") }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Android peut décaler le rappel de quelques minutes pour économiser la batterie.",
                style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

fun appVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" }.getOrDefault("?")
