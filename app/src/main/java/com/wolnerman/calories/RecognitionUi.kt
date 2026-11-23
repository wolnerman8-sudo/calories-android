package com.wolnerman.calories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Résultat d'une reconnaissance : suggestions + d'où elles viennent + message éventuel. */
data class RecoResult(val guesses: List<FoodGuess>, val source: String, val message: String?)

/** Fournisseur en ligne choisi (OpenAI par défaut) puis, sans clé / sans réseau / en cas d'erreur, le modèle hors-ligne. Hors thread UI. */
fun recognizeFood(ctx: android.content.Context, photoPath: String): RecoResult {
    val settings = SecureSettings(ctx)
    val provider = settings.provider
    val openai = provider != SecureSettings.PROVIDER_GEMINI
    val pName = if (openai) "OpenAI" else "Gemini"
    val key = settings.loadKey(provider)
    var note: String? = null
    if (key.isNullOrBlank()) {
        note = if (settings.hasKey(provider)) "Clé $pName illisible sur cet appareil : recolle-la dans Profil." else
            "Pas de clé $pName (Profil › Reconnaissance par photo) : mode hors-ligne."
    } else {
        val b64 = Photos.jpegBase64(photoPath, 768)
        if (b64 == null) note = "Photo illisible pour $pName."
        else try {
            val (foods, model) = if (openai) OpenAiClient.recognize(key, b64) else GeminiClient.recognize(key, b64)
            if (foods.isNotEmpty()) return RecoResult(foods, "$pName ($model)", null)
            note = "$pName n'a reconnu aucun aliment sur la photo."
        } catch (e: RecoError) { note = e.userMessage }
        catch (e: Exception) { note = "Erreur $pName : ${e.message ?: e.javaClass.simpleName}" }
    }
    return try {
        val off = OfflineFood.recognize(ctx, photoPath)
        RecoResult(off, "hors-ligne, moins précis",
            listOfNotNull(note, if (off.isEmpty()) "Aucun plat reconnu hors-ligne." else null).joinToString(" ").ifBlank { null })
    } catch (e: Throwable) {
        RecoResult(emptyList(), "hors-ligne", "${note ?: ""} Modèle hors-ligne indisponible (${e.message ?: e.javaClass.simpleName}).")
    }
}

/**
 * Bloc « Reconnaître l'aliment » sous la photo. Lance automatiquement la reconnaissance pour chaque nouvelle photo
 * quand le réglage est actif. Un appui sur une suggestion remplit le formulaire ; l'ajout reste à confirmer (« Ajouter »).
 */
@Composable
fun RecognitionPanel(photoPath: String?, onPick: (FoodGuess) -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { SecureSettings(ctx) }
    if (photoPath == null || !settings.recognitionEnabled) return
    val scope = rememberCoroutineScope()
    var busy by remember(photoPath) { mutableStateOf(false) }
    var result by remember(photoPath) { mutableStateOf<RecoResult?>(null) }
    var picked by remember(photoPath) { mutableStateOf<String?>(null) }
    fun run() {
        if (busy) return
        busy = true; result = null; picked = null
        scope.launch {
            result = withContext(Dispatchers.IO) { recognizeFood(ctx, photoPath) }
            busy = false
        }
    }
    LaunchedEffect(photoPath) { run() } // automatique pour chaque nouvelle photo

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { run() }) { Text("🔍 Reconnaître l'aliment") }
            if (busy) { CircularProgressIndicator(Modifier.size(22.dp)); Text("Analyse…", style = MaterialTheme.typography.bodySmall) }
        }
        result?.let { r ->
            r.message?.let { Text(it.trim(), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB26A00)) }
            if (r.guesses.isNotEmpty()) {
                Text("Suggestions (${r.source}) — touche la bonne :", style = MaterialTheme.typography.labelLarge)
                r.guesses.forEach { g ->
                    val sel = picked == g.name
                    Surface(
                        tonalElevation = if (sel) 6.dp else 1.dp, shape = MaterialTheme.shapes.small,
                        color = if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().clickable { picked = g.name; onPick(g) }
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text((if (sel) "✓ " else "") + g.name, fontWeight = FontWeight.SemiBold)
                            val parts = buildList {
                                g.grams?.let { add("~${fmt(it)} g") }
                                add(g.kcal100?.let { "${Math.round(it)} kcal/100 g" } ?: "kcal inconnues")
                                add("confiance ${Math.round(g.confidence * 100)} %")
                                if (g.offline) add("hors-ligne, moins précis")
                            }
                            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
                if (picked != null) Text("Vérifie le nom, la quantité et les kcal, puis touche « Ajouter ».",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
            }
        }
    }
}

/** Carte de réglages dans l'onglet Profil. */
@Composable
fun RecognitionSettingsCard() {
    val ctx = LocalContext.current
    val settings = remember { SecureSettings(ctx) }
    var enabled by remember { mutableStateOf(settings.recognitionEnabled) }
    var provider by remember { mutableStateOf(settings.provider) }
    var version by remember { mutableIntStateOf(0) } // force la relecture de l'état de la clé
    val openai = provider != SecureSettings.PROVIDER_GEMINI
    val pName = if (openai) "OpenAI" else "Gemini"
    val hint = remember(provider, version) { settings.keyHint(provider) }
    val hasKey = remember(provider, version) { settings.hasKey(provider) }
    var input by remember(provider) { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var msg by remember(provider) { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reconnaissance par photo", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reconnaissance par photo", Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it; settings.recognitionEnabled = it })
            }
            Text("Après « Photo » ou « Galerie », l'appli propose les aliments reconnus. Avec une clé API : " +
                "analyse en ligne (nom, quantité, kcal). Sans clé ou sans réseau : modèle hors-ligne intégré, moins précis.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("Service en ligne", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                .clickable { provider = SecureSettings.PROVIDER_OPENAI; settings.provider = provider }) {
                RadioButton(selected = openai, onClick = { provider = SecureSettings.PROVIDER_OPENAI; settings.provider = provider })
                Text("OpenAI (recommandé)")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                .clickable { provider = SecureSettings.PROVIDER_GEMINI; settings.provider = provider }) {
                RadioButton(selected = !openai, onClick = { provider = SecureSettings.PROVIDER_GEMINI; settings.provider = provider })
                Text("Gemini (non disponible aux Antilles)")
            }
            Text("Clé API $pName", style = MaterialTheme.typography.labelLarge)
            Text(when {
                hint != null -> "Clé enregistrée (chiffrée sur le téléphone) : $hint"
                hasKey -> "Clé enregistrée mais illisible sur cet appareil : recolle-la."
                else -> "Aucune clé enregistrée."
            }, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(input, { input = it.trim(); msg = null }, label = { Text("Coller la clé API $pName") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Masquer" else "Voir") } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = input.length >= 20, onClick = {
                    msg = try { settings.saveKey(input, provider); input = ""; version++; "Clé enregistrée." }
                    catch (e: Exception) { "Impossible d'enregistrer la clé : ${e.message}" }
                }) { Text("Enregistrer la clé") }
                OutlinedButton(enabled = hasKey, onClick = {
                    settings.clearKey(provider); version++; msg = "Clé effacée."
                }) { Text("Effacer la clé") }
            }
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32)) }
            if (openai) {
                Text("Clé sur platform.openai.com/api-keys (crédit prépayé requis, moins d'un centime par photo).",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text("Confidentialité : la photo réduite n'est envoyée à OpenAI que quand tu lances une reconnaissance. " +
                    "Selon OpenAI, les données envoyées à l'API ne servent pas à entraîner ses modèles (sauf si tu l'acceptes " +
                    "explicitement) ; elles peuvent être conservées jusqu'à 30 jours pour la détection des abus.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                Text("Gemini / Google AI Studio n'est pas proposé en Guadeloupe et en Martinique : préfère OpenAI. " +
                    "Clé sur aistudio.google.com/apikey là où c'est disponible.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB26A00))
                Text("⚠️ Avec l'offre gratuite, Google peut utiliser les photos envoyées pour améliorer ses produits " +
                    "(relecture humaine possible). N'envoie pas de photos personnelles ou sensibles.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB26A00))
            }
        }
    }
}
