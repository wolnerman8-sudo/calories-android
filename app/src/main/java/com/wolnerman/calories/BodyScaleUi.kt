package com.wolnerman.calories

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun bodyBlePerms(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

private fun granted(ctx: Context, perms: Array<String>) =
    perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

/** v2.5 : carte « Balance pèse-personne » (OKOK International / Chipsea…) de l'onglet Profil. */
@Composable
fun BodyScaleCard(age: Int?, onUseWeight: (Double) -> Unit) {
    val ctx = LocalContext.current
    val mgr = remember { BodyScaleManager.get(ctx) }
    var auto by remember { mutableStateOf(mgr.store.autoSave) }
    var debug by rememberSaveable { mutableStateOf(false) }
    var permMsg by remember { mutableStateOf<String?>(null) }
    val df = remember { SimpleDateFormat("EEE d MMM HH:mm", Locale.FRENCH) }
    LaunchedEffect(debug) { mgr.logAll = debug }
    // v2.13 : une pesée enregistrée pendant que cet écran est ouvert → rafraîchir le champ Poids
    val syncAtOpen = remember { mgr.profileSync.first }
    LaunchedEffect(mgr.profileSync.first) {
        if (mgr.profileSync.first > syncAtOpen) onUseWeight(mgr.profileSync.second)
    }

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (mgr.isBluetoothOn()) mgr.startScan() else permMsg = "Le Bluetooth doit être activé pour lire la balance."
    }
    fun go() {
        permMsg = null
        if (!mgr.hasBle()) { permMsg = "Ce téléphone n'a pas de Bluetooth basse consommation."; return }
        if (!mgr.isBluetoothOn()) {
            try { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            catch (e: Exception) { permMsg = "Active le Bluetooth dans les réglages, puis réessaie." }
            return
        }
        mgr.startScan()
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) go()
        else permMsg = if (Build.VERSION.SDK_INT >= 31) "Autorisation « Appareils à proximité » refusée : impossible de lire la balance. Autorise-la dans Réglages > Applications > Calories > Autorisations."
        else "Autorisation de localisation refusée : Android l'exige pour chercher des appareils Bluetooth."
    }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract(HealthSync.PROVIDER)) { g ->
        if (HealthSync.WRITE_WEIGHT in g) mgr.retryHealth()
        else permMsg = "Autorisation « Poids » refusée dans Santé Connect : les pesées restent seulement dans Calories."
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Balance pèse-personne", style = MaterialTheme.typography.titleMedium)
            Text("Balances Bluetooth OKOK International / Chipsea (et QN, norme Bluetooth). Calories lit le poids directement, " +
                "l'enregistre et l'écrit dans Santé Connect. Ferme l'appli OKOK pendant la pesée.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            when (mgr.status) {
                ScaleStatus.SCANNING, ScaleStatus.CONNECTING, ScaleStatus.CONNECTED -> {
                    if (mgr.status != ScaleStatus.CONNECTED || mgr.live == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { mgr.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Arrêter") }
                }
                else -> Button(onClick = { val p = bodyBlePerms(); if (granted(ctx, p)) go() else permLauncher.launch(p) },
                    modifier = Modifier.fillMaxWidth()) { Text("⚖️ Rechercher la balance / me peser") }
            }
            mgr.deviceLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium) }

            val r = mgr.live
            if (r != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(String.format(Locale.FRENCH, "%.1f kg", r.kg), fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val (txt, col) = if (r.stable == true) "✓ Stable" to Color(0xFF2E7D32) else "… En mesure" to Color(0xFFEF6C00)
                    Surface(color = col.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                        Text(txt, color = col, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                r.extra?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { mgr.record(r) }, enabled = r.stable == true, modifier = Modifier.fillMaxWidth()) {
                    Text("Enregistrer cette pesée")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enregistrer automatiquement quand le poids est stable", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(auto, { auto = it; mgr.store.autoSave = it })
            }
            Text("Chaque pesée enregistrée (automatique ou non) met aussi à jour le poids du profil, pour recalculer les objectifs kcal et protéines.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            mgr.profileNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32)) }

            (permMsg ?: mgr.message)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (permMsg != null || mgr.status == ScaleStatus.ERROR) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            mgr.saveMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                color = if (it.contains("pas dans Santé Connect") || it.contains("erreur")) Color(0xFFB26A00) else Color(0xFF2E7D32)) }
            // v2.6 : repères de composition après une pesée stable / enregistrée, avec les valeurs de CETTE mesure
            val saved = mgr.lastSaved
            if (r?.stable == true || saved != null) {
                val (fat, muscle) = if (r?.stable == true) compositionOf(r) else (saved!!.fatPct to saved.musclePct)
                CompositionGuideCard(age = age, fat = fat, muscle = muscle)
            }
            if (mgr.needsHealthPermission) OutlinedButton(onClick = {
                runCatching { hcLauncher.launch(setOf(HealthSync.WRITE_WEIGHT, HealthSync.READ_WEIGHT)) }
                    .onFailure { permMsg = "Impossible d'ouvrir Santé Connect : ${it.message}" }
            }) { Text("Autoriser l'écriture du poids dans Santé Connect") }

            val last = mgr.history.firstOrNull()
            if (last != null) OutlinedButton(onClick = { onUseWeight(Math.round(last.kg * 10.0) / 10.0) }) {
                Text("Utiliser ce poids dans mon profil (${WeighInRules.fmtKg(last.kg)})")
            }

            // Liste des appareils trouvés (touche pour choisir)
            if (mgr.status == ScaleStatus.SCANNING && mgr.found.isNotEmpty()) {
                Text("Appareils trouvés (touche pour choisir) :", style = MaterialTheme.typography.labelMedium)
                mgr.found.sortedByDescending { it.rssi }.take(15).forEach { d ->
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { mgr.choose(d) }) {
                        Column(Modifier.padding(8.dp)) {
                            Text((if (d.kind != BodyKind.CANDIDATE) "⚖️ " else "") + (d.name ?: "Appareil sans nom"), fontWeight = FontWeight.SemiBold)
                            Text(d.kind.label, style = MaterialTheme.typography.bodySmall,
                                color = if (d.kind != BodyKind.CANDIDATE) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${d.address} · signal ${d.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (mgr.history.isNotEmpty()) {
                Text("Dernières pesées", style = MaterialTheme.typography.labelLarge)
                mgr.history.take(8).forEach { w ->
                    val hc = when (w.health) { "ok" -> "Santé Connect ✓"; "dup" -> "déjà dans Santé Connect"; "err" -> "pas dans Santé Connect"; else -> "…" }
                    Text("${df.format(Date(w.timeMs))} · ${WeighInRules.fmtKg(w.kg)} · $hc", style = MaterialTheme.typography.bodySmall)
                }
                if (mgr.history.any { it.health == "err" }) TextButton(onClick = { mgr.retryHealth() }) { Text("Renvoyer vers Santé Connect") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Journal technique", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(debug, { debug = it })
            }
            if (debug) BodyDebugPanel(mgr)
            if (mgr.history.isNotEmpty() || mgr.status != ScaleStatus.IDLE)
                TextButton(onClick = { mgr.forgetDevice() }) { Text("Oublier la balance mémorisée") }
        }
    }
}

@Composable
private fun BodyDebugPanel(mgr: BodyScaleManager) {
    val ctx = LocalContext.current
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Si ta balance n'est pas reconnue : laisse ce journal activé, touche « Rechercher », monte sur la balance jusqu'à la fin de la pesée, " +
            "puis « Copier le journal » et colle-le dans un message.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val text = buildString {
                    appendLine("Calories 2.5 — journal balance pèse-personne (Android ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL})")
                    mgr.log.forEach { appendLine(it) }
                }
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Journal balance", text))
            }) { Text("Copier le journal") }
            OutlinedButton(onClick = { mgr.log.clear() }) { Text("Effacer") }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).background(Color(0xFF263238)).padding(6.dp).verticalScroll(rememberScrollState())) {
            if (mgr.log.isEmpty()) Text("(vide)", style = mono, color = Color.White)
            mgr.log.takeLast(150).reversed().forEach { Text(it, style = mono, color = Color(0xFFE0F2F1)) }
        }
    }
}
