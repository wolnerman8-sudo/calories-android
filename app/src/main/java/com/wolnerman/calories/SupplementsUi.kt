package com.wolnerman.calories

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val SUPP_GREEN = Color(0xFF2E7D32)

/** Résumé compact d'un complément : « 1000 UI · 08:00 🔔 · 10 kcal · Oméga-3 500 mg ». */
fun supplementSubtitle(s: Supplement): String {
    val parts = mutableListOf<String>()
    if (s.dose.isNotBlank()) parts += s.dose
    if (s.hour != null && s.minute != null) {
        val t = ReminderLogic.timeLabel(s.hour, s.minute)
        parts += if (s.reminderEnabled) "$t 🔔" else t
    } else if (s.reminderEnabled) {
        parts += "🔔"
    }
    s.kcal?.takeIf { it > 0 }?.let { parts += "$it kcal" }
    s.omega3Mg?.takeIf { it > 0.0 }?.let { parts += "Oméga-3 ${SupplementLogic.formatOmega3(it)}" }
    return parts.joinToString(" · ")
}

/**
 * Scan depuis la carte : si le code est déjà connu, on coche « Pris » aujourd'hui
 * (ou on ouvre la fiche s'il est déjà pris). Sinon on ouvre le dialogue prérempli (ou vide + message).
 */
private suspend fun handleCardScan(
    ctx: Context,
    raw: String,
    todayIso: String,
): Pair<Supplement?, String?> {
    val known = SupplementScanLogic.findByBarcode(Supplements.items, raw)
    if (known != null) {
        return if (!Supplements.isTaken(known.id, todayIso)) {
            Supplements.setTaken(ctx, todayIso, known.id, true)
            null to "« ${known.name} » coché comme pris aujourd'hui."
        } else {
            known to "Déjà pris aujourd'hui. Tu peux modifier le complément."
        }
    }
    val draft = SupplementScanLogic.lookup(raw)
    val prefill = SupplementScanLogic.toSupplement(draft)
    val msg = if (draft.recognized) {
        listOfNotNull(
            draft.source?.let { "Fiche trouvée sur $it. Vérifie et enregistre." },
        ).joinToString(" ").ifBlank { null }
    } else draft.message ?: SupplementScanLogic.UNRECOGNIZED
    return prefill to msg
}

/**
 * Section « Compléments » dans Aujourd'hui (séparée des aliments).
 * Permet de cocher les prises du jour, d'ajouter un complément et (via Gérer) modifier/supprimer.
 */
@Composable
fun SupplementsTodayCard(todayIso: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = Supplements.items
    val logs = Supplements.logs
    var managing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Supplement?>(null) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Supplement?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }

    fun launchScan() {
        startSupplementBarcodeScan(ctx, onCode = { code ->
            scanning = true
            info = "Recherche du produit…"
            scope.launch {
                val (supp, msg) = handleCardScan(ctx, code, todayIso)
                scanning = false
                if (supp != null) {
                    editing = supp
                    scanMessage = msg
                    info = null
                } else {
                    info = msg
                    scanMessage = null
                }
            }
        }, onError = { err -> info = err })
    }

    val takenCount = items.count { SupplementLogic.isTaken(logs, todayIso, it.id) }
    val kcalTaken = SupplementLogic.kcalForDate(items, logs, todayIso)
    val omega3Taken = SupplementLogic.omega3MgForDate(items, logs, todayIso)
    val omega3Planned = SupplementLogic.plannedOmega3Mg(items, logs, todayIso)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("💊 Compléments", style = MaterialTheme.typography.titleMedium)
                    if (items.isNotEmpty()) {
                        Text("$takenCount / ${items.size} pris aujourd'hui",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (takenCount == items.size) SUPP_GREEN else Color.Gray)
                    }
                }
                if (items.isNotEmpty()) {
                    TextButton(onClick = { managing = !managing }) {
                        Text(if (managing) "OK" else "Gérer")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { launchScan() },
                    enabled = !scanning,
                    modifier = Modifier.weight(1f),
                ) { Text(if (scanning) "Recherche…" else "📷 Scanner") }
                Button(
                    onClick = { editing = Supplement(0L, ""); scanMessage = null },
                    enabled = !scanning,
                    modifier = Modifier.weight(1f),
                ) { Text("+ Ajouter") }
            }

            if (items.isEmpty()) {
                Text("Aucun complément enregistré (Vitamine D, Oméga-3, Magnésium…). Scanne le code-barres ou ajoute-les ici pour les cocher chaque jour.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                items.forEach { s ->
                    val checked = SupplementLogic.isTaken(logs, todayIso, s.id)
                    val sub = supplementSubtitle(s)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { Supplements.setTaken(ctx, todayIso, s.id, !checked) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { Supplements.setTaken(ctx, todayIso, s.id, it) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.name,
                                fontWeight = FontWeight.SemiBold,
                                color = if (checked) SUPP_GREEN else Color.Unspecified,
                            )
                            if (sub.isNotBlank()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        if (managing) {
                            IconButton(onClick = {
                                info = if (SupplementScheduler.show(ctx, s, todayIso)) "Test envoyé pour « ${s.name} »."
                                else "Notifications bloquées : autorise-les dans les réglages Android."
                            }) { Icon(Icons.Filled.Notifications, contentDescription = "Tester le rappel") }
                            IconButton(onClick = { editing = s; scanMessage = null }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Modifier")
                            }
                            IconButton(onClick = { deleting = s }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                            }
                        }
                    }
                }
            }

            if (omega3Planned > 0.0 || omega3Taken > 0.0) {
                val txt = if (omega3Planned > omega3Taken)
                    "Oméga-3 (EPA+DHA) aujourd'hui : ${SupplementLogic.formatOmega3(omega3Taken)} / ${SupplementLogic.formatOmega3(omega3Planned)}"
                else
                    "Oméga-3 (EPA+DHA) aujourd'hui : ${SupplementLogic.formatOmega3(omega3Taken)}"
                Text(txt, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = SUPP_GREEN)
            }
            if (kcalTaken > 0) {
                Text("+$kcalTaken kcal de compléments incluses dans le total du jour.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    editing?.let { current ->
        SupplementEditDialog(
            initial = current,
            scanMessage = scanMessage,
            onDismiss = { editing = null; scanMessage = null },
            onSave = { saved ->
                val final = if (saved.id <= 0L) saved.copy(id = SupplementLogic.nextId(Supplements.items)) else saved
                Supplements.saveSupplement(ctx, final)
                editing = null
                scanMessage = null
            },
        )
    }

    deleting?.let { target ->
        SupplementDeleteDialog(
            supplement = target,
            onDismiss = { deleting = null },
            onConfirm = {
                Supplements.deleteSupplement(ctx, target.id)
                deleting = null
            },
        )
    }
}

/** Carte « Compléments alimentaires » dans Profil : gestion complète + activation rapide des rappels. */
@Composable
fun SupplementsManageCard() {
    val ctx = LocalContext.current
    val items = Supplements.items
    var editing by remember { mutableStateOf<Supplement?>(null) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Supplement?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var pendingToggleId by remember { mutableStateOf<Long?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val id = pendingToggleId; pendingToggleId = null
        if (!ok) {
            info = "Sans autorisation de notifications, les rappels ne peuvent pas s'afficher."
        } else if (id != null) {
            items.firstOrNull { it.id == id }?.let { s ->
                val h = s.hour ?: SupplementLogic.DEFAULT_HOUR
                val m = s.minute ?: SupplementLogic.DEFAULT_MINUTE
                Supplements.saveSupplement(ctx, s.copy(hour = h, minute = m, reminderEnabled = true))
            }
        }
    }
    fun needsPerm() = Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.canNotify(ctx)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("💊 Compléments alimentaires", style = MaterialTheme.typography.titleMedium)
            Text("Vitamines, oméga-3, minéraux… Tu peux cocher tes prises dans « Aujourd'hui » et activer un rappel sonore par complément. Le bouton Scanner est dans Aujourd'hui et dans le formulaire d'ajout.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (items.isEmpty()) {
                Text("Aucun complément pour l'instant.", color = Color.Gray)
            } else {
                items.forEach { s ->
                    val sub = supplementSubtitle(s)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.SemiBold)
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = s.reminderEnabled,
                            onCheckedChange = { on ->
                                if (on && needsPerm()) {
                                    pendingToggleId = s.id
                                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val h = s.hour ?: SupplementLogic.DEFAULT_HOUR
                                    val m = s.minute ?: SupplementLogic.DEFAULT_MINUTE
                                    Supplements.saveSupplement(
                                        ctx,
                                        if (on) s.copy(hour = h, minute = m, reminderEnabled = true)
                                        else s.copy(reminderEnabled = false),
                                    )
                                }
                            },
                        )
                        IconButton(onClick = { editing = s; scanMessage = null }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifier")
                        }
                        IconButton(onClick = { deleting = s }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                        }
                    }
                }
            }
            OutlinedButton(onClick = { editing = Supplement(0L, ""); scanMessage = null }) {
                Text("+ Ajouter un complément")
            }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    editing?.let { current ->
        SupplementEditDialog(
            initial = current,
            scanMessage = scanMessage,
            onDismiss = { editing = null; scanMessage = null },
            onSave = { saved ->
                val final = if (saved.id <= 0L) saved.copy(id = SupplementLogic.nextId(Supplements.items)) else saved
                Supplements.saveSupplement(ctx, final)
                editing = null
                scanMessage = null
            },
        )
    }

    deleting?.let { target ->
        SupplementDeleteDialog(
            supplement = target,
            onDismiss = { deleting = null },
            onConfirm = {
                Supplements.deleteSupplement(ctx, target.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun SupplementDeleteDialog(supplement: Supplement, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer ce complément ?") },
        text = { Text("« ${supplement.name} » sera retiré de ta liste de compléments et son rappel sera annulé.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun SupplementEditDialog(
    initial: Supplement,
    scanMessage: String? = null,
    onDismiss: () -> Unit,
    onSave: (Supplement) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(initial.id, initial.barcode, initial.name) { mutableStateOf(initial.name) }
    var dose by remember(initial.id, initial.barcode, initial.dose) { mutableStateOf(initial.dose) }
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    var reminderEnabled by remember { mutableStateOf(initial.reminderEnabled) }
    var kcalText by remember(initial.id, initial.barcode, initial.kcal) { mutableStateOf(initial.kcal?.toString() ?: "") }
    val initInGrams = initial.omega3Mg != null && initial.omega3Mg >= 1000.0 && initial.omega3Mg % 1000.0 == 0.0
    var omegaInGrams by remember { mutableStateOf(initInGrams) }
    var omegaText by remember(initial.id, initial.barcode, initial.omega3Mg) {
        mutableStateOf(
            initial.omega3Mg?.let { fmt(if (initInGrams) it / 1000.0 else it) } ?: "",
        )
    }
    var barcode by remember(initial.id, initial.barcode) { mutableStateOf(initial.barcode.orEmpty()) }
    var testMsg by remember { mutableStateOf(scanMessage) }
    var scanning by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) {
            reminderEnabled = false
            testMsg = "Autorise les notifications dans les réglages Android pour activer le rappel."
        }
    }
    fun needsPerm() = Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.canNotify(ctx)

    fun applyDraft(draft: ScannedSupplementDraft) {
        barcode = draft.barcode
        if (draft.recognized) {
            if (draft.name.isNotBlank()) name = draft.name
            if (draft.dose.isNotBlank()) dose = draft.dose
            draft.kcal?.let { kcalText = it.toString() }
            draft.omega3Mg?.let { mg ->
                val grams = mg >= 1000.0 && mg % 1000.0 == 0.0
                omegaInGrams = grams
                omegaText = fmt(if (grams) mg / 1000.0 else mg)
            }
            testMsg = draft.source?.let { "Fiche trouvée sur $it. Vérifie et enregistre." }
        } else {
            testMsg = draft.message ?: SupplementScanLogic.UNRECOGNIZED
        }
    }

    fun buildCandidate(): Supplement = Supplement(
        id = if (initial.id > 0L) initial.id else 1L,
        name = name.trim(),
        dose = dose.trim(),
        hour = hour,
        minute = minute,
        reminderEnabled = reminderEnabled,
        kcal = kcalText.trim().toIntOrNull()?.takeIf { it >= 0 },
        omega3Mg = SupplementLogic.parseOmega3ToMg(omegaText, omegaInGrams),
        createdDate = initial.createdDate,
        barcode = barcode.trim().ifBlank { null },
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id <= 0L) "Nouveau complément" else "Modifier le complément") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        startSupplementBarcodeScan(ctx, onCode = { code ->
                            scanning = true
                            testMsg = "Recherche du produit…"
                            scope.launch {
                                val draft = SupplementScanLogic.lookup(code)
                                applyDraft(draft)
                                scanning = false
                            }
                        }, onError = { testMsg = it })
                    },
                    enabled = !scanning,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (scanning) "Recherche…" else "📷 Scanner le code-barres") }

                if (barcode.isNotBlank()) {
                    Text("Code-barres : $barcode", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom (ex. Vitamine D, Oméga-3…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text("Dose (ex. 1 gélule, 1000 UI, 500 mg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Heure de prise (facultatif)", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val h0 = hour ?: SupplementLogic.DEFAULT_HOUR
                        val m0 = minute ?: SupplementLogic.DEFAULT_MINUTE
                        TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m }, h0, m0, true).show()
                    }) {
                        Text(if (hour != null && minute != null) ReminderLogic.timeLabel(hour!!, minute!!) else "Choisir une heure")
                    }
                    if (hour != null) {
                        IconButton(onClick = { hour = null; minute = null; reminderEnabled = false }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Effacer l'heure")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rappel sonore à cette heure", Modifier.weight(1f))
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { on ->
                            if (on) {
                                if (hour == null) {
                                    hour = SupplementLogic.DEFAULT_HOUR
                                    minute = SupplementLogic.DEFAULT_MINUTE
                                }
                                reminderEnabled = true
                                if (needsPerm()) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                reminderEnabled = false
                            }
                        },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = kcalText,
                        onValueChange = { kcalText = it.filter(Char::isDigit).take(4) },
                        label = { Text("kcal / prise") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = omegaText,
                        onValueChange = { omegaText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                        label = { Text("Oméga-3 EPA+DHA") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Unité oméga-3 :", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    FilterChip(
                        selected = !omegaInGrams,
                        onClick = { omegaInGrams = false },
                        label = { Text("mg") },
                    )
                    FilterChip(
                        selected = omegaInGrams,
                        onClick = { omegaInGrams = true },
                        label = { Text("g") },
                    )
                }

                if (name.isNotBlank()) {
                    OutlinedButton(onClick = {
                        val preview = buildCandidate()
                        testMsg = if (SupplementScheduler.show(ctx, preview))
                            "Notification de test envoyée (« ${SupplementLogic.notificationTitle(preview)} »)."
                        else "Notifications bloquées : autorise-les dans les réglages Android."
                    }) { Text("Tester la notification") }
                }
                testMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(buildCandidate().copy(id = initial.id)) },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
