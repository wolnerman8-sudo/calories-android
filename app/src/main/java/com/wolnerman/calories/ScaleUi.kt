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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

private fun blePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

private fun hasAll(ctx: Context, perms: Array<String>) =
    perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

private fun fmtWeight(d: Double, decimals: Int): String =
    String.format(Locale.FRENCH, "%.${decimals}f", d)

/** Panneau « Balance connectée » affiché sous le champ Quantité. [onUse] reçoit un poids en grammes. */
@Composable
fun ScalePanel(onUse: (Double) -> Unit) {
    val ctx = LocalContext.current
    val mgr = remember { ScaleManager.get(ctx) }
    var debug by rememberSaveable { mutableStateOf(false) }
    var auto by rememberSaveable { mutableStateOf(false) }
    var permMsg by remember { mutableStateOf<String?>(null) }
    var lastAuto by remember { mutableStateOf<Double?>(null) }

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (mgr.isBluetoothOn()) mgr.startScan() else permMsg = "Le Bluetooth doit être activé pour utiliser la balance."
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
        else permMsg = if (Build.VERSION.SDK_INT >= 31) "Autorisation « Appareils à proximité » refusée : impossible de trouver la balance. Autorise-la dans Réglages > Applications > Calories > Autorisations."
        else "Autorisation de localisation refusée : Android l'exige pour chercher des appareils Bluetooth. Autorise-la dans les réglages de l'appli."
    }
    fun connectClicked() {
        val perms = blePermissions()
        if (hasAll(ctx, perms)) go() else permLauncher.launch(perms)
    }

    val r = mgr.reading
    LaunchedEffect(r, auto) {
        val g = r?.grams
        if (auto && r != null && r.stable && g != null && g > 0 && (lastAuto == null || Math.abs(g - lastAuto!!) >= 0.5)) { lastAuto = g; onUse(g) }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (mgr.status) {
                ScaleStatus.IDLE, ScaleStatus.ERROR -> {
                    OutlinedButton(onClick = { connectClicked() }, modifier = Modifier.fillMaxWidth()) { Text("⚖️ Connecter une balance") }
                }
                ScaleStatus.SCANNING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { mgr.stopScan(); mgr.disconnect() }, modifier = Modifier.fillMaxWidth()) { Text("Arrêter la recherche") }
                }
                ScaleStatus.CONNECTING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { mgr.disconnect() }, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
                }
                ScaleStatus.CONNECTED -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(mgr.deviceName ?: "Balance", style = MaterialTheme.typography.labelMedium)
                            mgr.driverLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (r == null) Text("En attente du poids…", style = MaterialTheme.typography.bodyMedium)
                            else {
                                Text("${fmtWeight(r.value, r.decimals)} ${r.unitLabel}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                val g = r.grams
                                if (r.unitLabel != "g" && g != null) Text("≈ ${fmtWeight(g, 0)} g", style = MaterialTheme.typography.bodySmall)
                                r.extra?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        if (r != null) {
                            val (txt, col) = if (r.stable) "✓ Stable" to Color(0xFF2E7D32) else "… En mesure" to Color(0xFFEF6C00)
                            Surface(color = col.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                Text(txt, color = col, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                    val g = r?.grams
                    Button(onClick = { g?.let { onUse(it) } }, enabled = g != null && g > 0, modifier = Modifier.fillMaxWidth()) { Text("Utiliser ce poids") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (mgr.canTare) OutlinedButton(onClick = { mgr.tare() }, modifier = Modifier.weight(1f)) { Text("Tare (zéro)") }
                        OutlinedButton(onClick = { mgr.disconnect() }, modifier = Modifier.weight(1f)) { Text("Déconnecter") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { auto = !auto }) {
                        Checkbox(auto, { auto = it })
                        Text("Remplir la quantité automatiquement quand le poids est stable", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            (permMsg ?: mgr.message)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (permMsg != null || mgr.status == ScaleStatus.ERROR) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (mgr.status == ScaleStatus.SCANNING || (mgr.status != ScaleStatus.CONNECTED && mgr.found.isNotEmpty())) {
                val list = mgr.found.filter { it.known || it.name != null || debug }
                    .sortedWith(compareByDescending<FoundDevice> { it.driver?.kitchen == true }.thenByDescending { it.known }.thenByDescending { it.rssi })
                if (list.isNotEmpty()) Text("Appareils à proximité (touche pour connecter) :", style = MaterialTheme.typography.labelMedium)
                list.take(25).forEach { d ->
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().clickable { mgr.connect(d) }) {
                        Column(Modifier.padding(8.dp)) {
                            Text((if (d.known) "⚖️ " else "") + (d.name ?: "Appareil sans nom"), fontWeight = FontWeight.SemiBold)
                            Text(d.typeLabel + (d.driver?.let { if (it.confirmed) "" else " (protocole déduit)" } ?: " · journal seulement"),
                                style = MaterialTheme.typography.bodySmall, color = if (d.known) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${d.address} · signal ${d.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Journal technique", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(debug, { debug = it })
            }
            if (debug) DebugPanel(mgr)
        }
    }
}

@Composable
private fun DebugPanel(mgr: ScaleManager) {
    val ctx = LocalContext.current
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Appareil : ${mgr.deviceName ?: "—"}  MAC : ${mgr.deviceMac ?: "—"}", style = mono)
        Text("Pilote : ${mgr.driverLabel ?: "—"}", style = mono)
        Text("Version appli : 1.5 · chiffrement AiLink : ${AiLinkCrypto.error?.let { "ERREUR $it" } ?: "ok"}", style = mono)
        if (mgr.gattServices.isNotEmpty()) Text("Services : " + mgr.gattServices.joinToString("  "), style = mono)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val text = buildString {
                    appendLine("Calories 1.5 — journal balance")
                    appendLine("Appareil : ${mgr.deviceName} ${mgr.deviceMac} · pilote : ${mgr.driverLabel}")
                    appendLine("Services : ${mgr.gattServices.joinToString("  ")}")
                    mgr.found.forEach { appendLine("Trouvé : ${it.name} ${it.address} type=${it.driver?.id ?: "inconnu"} services=${it.adv.services} ${it.rawHex}") }
                    mgr.log.forEach { appendLine(it) }
                }
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Journal balance", text))
                mgr.addLog("Journal copié dans le presse-papiers")
            }) { Text("Copier le journal") }
            OutlinedButton(onClick = { mgr.log.clear() }) { Text("Effacer") }
        }
        Surface(color = Color(0xFF263238), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            Column(Modifier.padding(6.dp).verticalScroll(rememberScrollState())) {
                if (mgr.log.isEmpty()) Text("(vide)", style = mono, color = Color.White)
                mgr.log.takeLast(120).reversed().forEach { Text(it, style = mono, color = Color(0xFFE0F2F1)) }
            }
        }
    }
}
