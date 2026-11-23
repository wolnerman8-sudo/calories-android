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

/** Service en ligne choisi (Groq par défaut) puis, sans clé / sans réseau / en cas d'erreur, le modèle hors-ligne. Hors thread UI. */
fun recognizeFood(ctx: android.content.Context, photoPath: String): RecoResult {
    val settings = SecureSettings(ctx)
    val provider = settings.provider
    val pName = CloudProviders.nameOf(provider)
    val key = settings.loadKey(provider)
    var note: String? = null
    if (key.isNullOrBlank()) {
        note = if (settings.hasKey(provider)) "Clé $pName illisible sur cet appareil : recolle-la dans Profil." else
            "Pas de clé $pName (Profil › Reconnaissance par photo) : mode hors-ligne."
    } else {
        val b64 = Photos.jpegBase64(photoPath, 768)
        if (b64 == null) note = "Photo illisible pour $pName."
        else try {
            val cloud = CloudProviders.byId(provider)
            val (foods, model) = if (cloud != null) CloudClient.recognize(cloud, key, b64) else GeminiClient.recognize(key, b64)
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

/** « Tester la clé » : renvoie un message OK ou l'erreur en français. Hors thread UI. */
fun testProviderKey(provider: String, key: String): Pair<Boolean, String> = try {
    val cloud = CloudProviders.byId(provider)
    val model = if (cloud != null) CloudClient.testKey(cloud, key) else GeminiClient.testKey(key)
    true to "✅ Clé ${CloudProviders.nameOf(provider)} valide (modèle $model)."
} catch (e: RecoError) { false to "❌ ${e.userMessage}" }
catch (e: Exception) { false to "❌ ${e.message ?: e.javaClass.simpleName}" }

/**
 * Bloc « Reconnaître l'aliment » sous la photo. Lance automatiquement la reconnaissance pour chaque nouvelle photo
 * quand le réglage est actif, remplit le formulaire avec la meilleure suggestion (v2.1) ; un appui sur une autre
 * suggestion la remplace. L'ajout au journal reste à confirmer avec « Ajouter ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionPanel(photoPath: String?, onPick: (FoodGuess) -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { SecureSettings(ctx) }
    if (photoPath == null || !settings.recognitionEnabled) return
    val scope = rememberCoroutineScope()
    var busy by remember(photoPath) { mutableStateOf(false) }
    var result by remember(photoPath) { mutableStateOf<RecoResult?>(null) }
    var picked by remember(photoPath) { mutableStateOf<Int?>(null) }
    val pick by rememberUpdatedState(onPick)
    fun run() {
        if (busy) return
        busy = true; result = null; picked = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { recognizeFood(ctx, photoPath) }
            result = r
            busy = false
            // v2.1 : la meilleure suggestion remplit tout de suite le formulaire (l'ajout reste à confirmer)
            if (r.guesses.isNotEmpty()) { picked = 0; pick(r.guesses[0]) }
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
                Text("Suggestions (${r.source}) — la 1re est déjà dans le formulaire ; touche une autre pour la remplacer :",
                    style = MaterialTheme.typography.labelLarge)
                r.guesses.forEachIndexed { i, g ->
                    val sel = picked == i
                    // Carte entièrement cliquable (Surface onClick : zone tactile = toute la carte, effet visuel au toucher)
                    Surface(
                        onClick = { picked = i; pick(g) },
                        shape = MaterialTheme.shapes.small,
                        color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (sel) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((if (sel) "✓ " else "") + g.name, fontWeight = FontWeight.SemiBold)
                                val parts = buildList {
                                    g.grams?.let { add("~${fmt(it)} g") }
                                    add(g.kcal100?.let { "${Math.round(it)} kcal/100 g" } ?: "kcal inconnues")
                                    add("confiance ${Math.round(g.confidence * 100)} %")
                                    if (g.offline) add("hors-ligne, moins précis")
                                }
                                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                            }
                            Text(if (sel) "Choisi" else "Choisir", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private data class ProviderChoice(val id: String, val label: String)

private val CHOICES = listOf(
    ProviderChoice(CloudProviders.GROQ, CloudProviders.Groq.label),
    ProviderChoice(CloudProviders.OPENROUTER, CloudProviders.OpenRouter.label),
    ProviderChoice(CloudProviders.OPENAI, CloudProviders.OpenAi.label),
    ProviderChoice(CloudProviders.GEMINI, "Gemini (non disponible en Martinique)"),
)

/** Carte de réglages dans l'onglet Profil. */
@Composable
fun RecognitionSettingsCard() {
    val ctx = LocalContext.current
    val settings = remember { SecureSettings(ctx) }
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(settings.recognitionEnabled) }
    var provider by remember { mutableStateOf(settings.provider) }
    var version by remember { mutableIntStateOf(0) } // force la relecture de l'état des clés
    val pName = CloudProviders.nameOf(provider)
    val hint = remember(provider, version) { settings.keyHint(provider) }
    val hasKey = remember(provider, version) { settings.hasKey(provider) }
    var input by remember(provider) { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var msg by remember(provider) { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var migration by remember { mutableStateOf(settings.migrationNote) }
    fun choose(id: String) { provider = id; settings.provider = id }
    val detected = CloudProviders.detect(input)
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
            migration?.let {
                Text("ℹ️ $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1565C0))
                TextButton(onClick = { settings.migrationNote = null; migration = null }) { Text("Compris") }
            }
            Text("Service en ligne", style = MaterialTheme.typography.labelLarge)
            CHOICES.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { choose(c.id) }) {
                    RadioButton(selected = provider == c.id, onClick = { choose(c.id) })
                    Text(c.label + if (settings.hasKey(c.id) && provider != c.id) "  · clé enregistrée" else "")
                }
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
            if (detected != null && detected != provider) {
                Text("⚠️ Cette clé ressemble à une clé ${CloudProviders.nameOf(detected)}, pas $pName.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB26A00))
                TextButton(onClick = { choose(detected) }) { Text("Passer à ${CloudProviders.nameOf(detected)}") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = input.length >= 20, onClick = {
                    // Si le préfixe désigne un autre service, la clé va dans le bon emplacement et ce service est choisi
                    val target = detected?.takeIf { it != provider } ?: provider
                    msg = try {
                        settings.saveKey(input, target); input = ""
                        if (target != provider) choose(target)
                        version++
                        if (target != provider) "Clé enregistrée pour ${CloudProviders.nameOf(target)} (service choisi)." else "Clé enregistrée."
                    } catch (e: Exception) { "Impossible d'enregistrer la clé : ${e.message}" }
                }) { Text("Enregistrer la clé") }
                OutlinedButton(enabled = hasKey, onClick = {
                    settings.clearKey(provider); version++; msg = "Clé effacée."
                }) { Text("Effacer la clé") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !testing && (hasKey || input.length >= 20), onClick = {
                    val k = input.takeIf { it.length >= 20 } ?: settings.loadKey(provider)
                    if (k == null) { msg = "❌ Clé illisible : recolle-la."; return@OutlinedButton }
                    testing = true; msg = null
                    val p = provider
                    scope.launch {
                        msg = withContext(Dispatchers.IO) { testProviderKey(p, k) }.second
                        testing = false
                    }
                }) { Text("Tester la clé") }
                if (testing) CircularProgressIndicator(Modifier.size(22.dp))
            }
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("❌")) Color(0xFFC62828) else Color(0xFF2E7D32)) }
            when (provider) {
                CloudProviders.GROQ -> {
                    Text(CloudProviders.Groq.help, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text("Confidentialité : la photo réduite n'est envoyée à Groq que quand tu lances une reconnaissance.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                CloudProviders.OPENROUTER -> {
                    Text(CloudProviders.OpenRouter.help, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text("Confidentialité : la photo réduite passe par OpenRouter puis le fournisseur du modèle gratuit ; " +
                        "les modèles gratuits peuvent conserver les données envoyées.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                CloudProviders.OPENAI -> {
                    Text(CloudProviders.OpenAi.help, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text("Confidentialité : la photo réduite n'est envoyée à OpenAI que quand tu lances une reconnaissance. " +
                        "Selon OpenAI, les données envoyées à l'API ne servent pas à entraîner ses modèles (sauf si tu l'acceptes " +
                        "explicitement) ; elles peuvent être conservées jusqu'à 30 jours pour la détection des abus.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                else -> {
                    Text("Gemini / Google AI Studio n'est pas disponible en Martinique : choisis plutôt Groq (gratuit). " +
                        "Clé sur aistudio.google.com/apikey là où c'est disponible.",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFB26A00))
                    Text("⚠️ Avec l'offre gratuite, Google peut utiliser les photos envoyées pour améliorer ses produits " +
                        "(relecture humaine possible).", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB26A00))
                }
            }
        }
    }
}

/** Version de l'appli (« Version 2.0 »), lue dans le paquet installé. */
@Composable
fun AppVersionText(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val v = remember {
        runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "Version ${pi.versionName}"
        }.getOrDefault("Version ?")
    }
    Text("Calories — $v", style = MaterialTheme.typography.labelLarge, color = Color.Gray, modifier = modifier)
}
