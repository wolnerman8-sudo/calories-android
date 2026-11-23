package com.wolnerman.calories

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HC_EXPLAIN =
    "Calories peut partager ton journal avec Santé Connect, pour que tes autres applis santé " +
    "(par exemple « Vivre en équilibre ») voient ce que tu manges.\n\n" +
    "• Écriture « Nutrition » : chaque aliment ajouté (nom, kcal, glucides/protéines/lipides si connus, type de repas). " +
    "Supprimer une entrée dans Calories la supprime aussi dans Santé Connect.\n" +
    "• Lecture « Nutrition » : uniquement pour vérifier la synchronisation.\n" +
    "• Lecture « Poids » et « Pas » : afficher ton dernier poids et tes pas du jour dans l'onglet Profil.\n" +
    "• Écriture « Poids » : chaque pesée de ta balance pèse-personne Bluetooth (carte « Balance pèse-personne »), " +
    "pour que tes autres applis voient ton poids actuel.\n\n" +
    "Les données restent sur ton téléphone : Calories ne les envoie à aucun serveur. " +
    "Tu peux retirer ces autorisations à tout moment dans Santé Connect."

/** Carte « Santé Connect » de l'onglet Profil. */
@Composable
fun HealthConnectCard(entries: List<Entry>, onUseWeight: (Double) -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { SecureSettings(ctx) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableIntStateOf(HealthSync.status(ctx)) }
    var enabled by remember { mutableStateOf(settings.healthEnabled) }
    var explain by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<HealthSummary?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    suspend fun syncAll() {
        HealthSync.write(ctx, entries).fold(
            onSuccess = { n -> msg = "Santé Connect : $n entrée(s) du journal partagée(s)." },
            onFailure = { e -> msg = "Partage impossible : ${e.message}" })
    }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract(HealthSync.PROVIDER)) { granted ->
        val write = androidx.health.connect.client.permission.HealthPermission
            .getWritePermission(androidx.health.connect.client.records.NutritionRecord::class)
        if (write in granted) {
            settings.healthEnabled = true; enabled = true
            scope.launch { syncAll(); refresh++ }
        } else {
            settings.healthEnabled = false; enabled = false
            msg = "Autorisation « Nutrition » refusée : rien n'est partagé. Tu peux l'accorder dans Santé Connect."
        }
    }
    LaunchedEffect(enabled, refresh, HealthRefresh.tick) {
        status = HealthSync.status(ctx)
        summary = if (enabled && status == HealthConnectClient.SDK_AVAILABLE) HealthSync.summary(ctx) else null
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Santé Connect", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Partager avec Santé Connect", Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { on ->
                    msg = null
                    if (!on) { settings.healthEnabled = false; enabled = false; msg = "Partage arrêté (les données déjà partagées restent dans Santé Connect)." }
                    else if (HealthSync.status(ctx) != HealthConnectClient.SDK_AVAILABLE) { status = HealthSync.status(ctx) }
                    else explain = true
                })
            }
            when (status) {
                HealthConnectClient.SDK_UNAVAILABLE -> Text(
                    "Santé Connect n'est pas disponible sur ce téléphone (Android 9 minimum).",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB26A00))
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    Text("Santé Connect doit être installé ou mis à jour (Android 13 et moins : appli « Santé Connect » sur le Play Store).",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFB26A00))
                    OutlinedButton(onClick = {
                        try { ctx.startActivity(HealthSync.installIntent()) }
                        catch (e: ActivityNotFoundException) { runCatching { ctx.startActivity(HealthSync.installWebIntent()) } }
                    }) { Text("Installer Santé Connect") }
                    TextButton(onClick = { refresh++ }) { Text("J'ai installé, vérifier") }
                }
                else -> {}
            }
            if (enabled && status == HealthConnectClient.SDK_AVAILABLE) {
                val s = summary
                when {
                    s == null -> Text("Lecture…", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    s.error != null -> Text("Lecture impossible : ${s.error}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                    else -> {
                        val wTxt = s.weightKg?.let { kg ->
                            val d = s.weightTime?.atZone(ZoneId.systemDefault())
                                ?.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)) ?: ""
                            String.format(Locale.FRENCH, "%.1f kg", kg) + if (d.isNotEmpty()) " (le $d)" else ""
                        } ?: "aucun (ou lecture non autorisée)"
                        Text("Dernier poids : $wTxt")
                        Text("Pas aujourd'hui : ${s.stepsToday?.let { String.format(Locale.FRENCH, "%,d", it) } ?: "lecture non autorisée"}")
                        s.weightKg?.let { kg ->
                            OutlinedButton(onClick = { onUseWeight(Math.round(kg * 10.0) / 10.0) }) { Text("Utiliser ce poids dans mon profil") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { syncAll(); refresh++ } }) { Text("Resynchroniser le journal") }
                    TextButton(onClick = { launcher.launch(HealthSync.PERMISSIONS) }) { Text("Autorisations") }
                }
            }
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32)) }
            Text("Les aliments du journal sont écrits dans Santé Connect (énergie, glucides, protéines, lipides, nom, repas), " +
                "lisibles par tes autres applis santé.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }

    if (explain) AlertDialog(
        onDismissRequest = { explain = false },
        title = { Text("Partager avec Santé Connect") },
        text = { Text(HC_EXPLAIN, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { explain = false; launcher.launch(HealthSync.PERMISSIONS) }) { Text("Continuer") } },
        dismissButton = { TextButton(onClick = { explain = false }) { Text("Annuler") } },
    )
}

/**
 * Écran « Confidentialité » ouvert par Santé Connect (lien depuis l'écran des autorisations).
 * Obligatoire : ACTION_SHOW_PERMISSIONS_RATIONALE (Android ≤ 13) et VIEW_PERMISSION_USAGE (Android 14+).
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Calories — confidentialité et Santé Connect", style = MaterialTheme.typography.titleLarge)
                        Text(HC_EXPLAIN)
                        Text("Calories n'a pas de compte ni de serveur : le journal, le profil et les photos sont stockés uniquement sur le téléphone. " +
                            "Seule exception, si tu l'actives : la reconnaissance photo envoie la photo réduite au service choisi (OpenAI ou Google Gemini) avec ta propre clé API.",
                            style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { finish() }) { Text("Fermer") }
                    }
                }
            }
        }
    }
}
