package com.wolnerman.calories

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storage = Storage(applicationContext)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFFFFB300))) {
                Surface(Modifier.fillMaxSize()) { CaloriesApp(storage) }
            }
        }
    }
}

private fun today(): String = LocalDate.now().toString()
private fun nowTime(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
private fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.FRENCH, "%.1f", d).replace(',', '.')
private fun g1(d: Double): String = String.format(Locale.FRENCH, "%.1f", d)

private fun prettyDate(iso: String): String {
    val d = LocalDate.parse(iso)
    return when (d) {
        LocalDate.now() -> "Aujourd'hui"
        LocalDate.now().minusDays(1) -> "Hier"
        else -> d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.FRENCH))
            .replaceFirstChar { it.uppercase() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaloriesApp(storage: Storage) {
    var entries by remember { mutableStateOf(storage.loadEntries()) }
    var goal by remember { mutableIntStateOf(storage.dailyGoal) }
    var tab by remember { mutableIntStateOf(0) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Entry?>(null) }

    fun update(newList: List<Entry>) { entries = newList; storage.saveEntries(newList) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calories") },
                actions = { TextButton(onClick = { showGoalDialog = true }) { Text("Objectif") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("🍽") }, label = { Text("Aujourd'hui") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("📅") }, label = { Text("Historique") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                    icon = { Text("🥦") }, label = { Text("Nutriments") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Text("👤") }, label = { Text("Profil") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) TodayScreen(entries, goal,
                onAdd = { update(entries + it) }, onDelete = { toDelete = it })
            else if (tab == 1) HistoryScreen(entries, goal, onDelete = { toDelete = it })
            else if (tab == 3) NutrientsScreen(entries, goal, storage.loadProfile().male)
            else ProfileScreen(storage, goal, onUseGoal = { goal = it; storage.dailyGoal = it })
        }
    }

    if (showGoalDialog) {
        var txt by remember { mutableStateOf(goal.toString()) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Objectif journalier") },
            text = {
                OutlinedTextField(txt, { txt = it.filter(Char::isDigit).take(5) },
                    label = { Text("kcal par jour") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = {
                TextButton(onClick = {
                    txt.toIntOrNull()?.takeIf { it > 0 }?.let { goal = it; storage.dailyGoal = it }
                    showGoalDialog = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showGoalDialog = false }) { Text("Annuler") } }
        )
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer ?") },
            text = { Text("${e.name} — ${e.kcal} kcal") },
            confirmButton = { TextButton(onClick = { Photos.delete(e.photo); update(entries.filter { it.id != e.id }); toDelete = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } }
        )
    }
}

@Composable
fun TodayScreen(entries: List<Entry>, goal: Int, onAdd: (Entry) -> Unit, onDelete: (Entry) -> Unit) {
    val todays = entries.filter { it.date == today() }.sortedByDescending { it.id }
    val total = todays.sumOf { it.kcal }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SummaryCard(total, goal, todays.macros()) }
        item { AddEntryCard(onAdd) }
        if (todays.isNotEmpty()) item { Text("Repas du jour", style = MaterialTheme.typography.titleMedium) }
        else item { Text("Aucun aliment ajouté aujourd'hui.", color = Color.Gray) }
        items(todays, key = { it.id }) { EntryRow(it, onDelete) }
    }
}

@Composable
fun SummaryCard(total: Int, goal: Int, macros: Macros) {
    val ratio = if (goal > 0) total.toFloat() / goal else 0f
    val over = total > goal
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Total aujourd'hui", style = MaterialTheme.typography.labelLarge)
            Text("$total kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = if (over) Color(0xFFC62828) else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                if (over) "Objectif dépassé de ${total - goal} kcal (objectif : $goal)"
                else "Reste ${goal - total} kcal sur $goal",
                color = if (over) Color(0xFFC62828) else Color.Unspecified
            )
            Spacer(Modifier.height(10.dp))
            MacroTotals(macros)
            MacroSplitBar(macros)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryCard(onAdd: (Entry) -> Unit) {
    var name by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("100") }
    var kcal100 by remember { mutableStateOf("") }
    var carbs100 by remember { mutableStateOf("") }
    var prot100 by remember { mutableStateOf("") }
    var fat100 by remember { mutableStateOf("") }
    val nutrTxt = remember { mutableStateMapOf<String, String>() }
    var showNutr by remember { mutableStateOf(false) }
    var fermented by remember { mutableStateOf(false) }
    var prebiotic by remember { mutableStateOf(false) }
    fun fillNutr(m: Map<String, Double>) { nutrTxt.clear(); m.forEach { (k, v) -> nutrTxt[k] = fmt(v) } }
    // Nom de l'aliment intégré dont les valeurs ont été copiées automatiquement (null = saisie manuelle / scan)
    var autoFrom by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var barcode by remember { mutableStateOf("") }
    var scanned by remember { mutableStateOf<ScannedProduct?>(null) }
    var scanMsg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoPath by remember { mutableStateOf<String?>(null) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var photoBusy by remember { mutableStateOf(false) }

    fun setPhoto(newPath: String?) { if (photoPath != newPath) Photos.delete(photoPath); photoPath = newPath }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCameraPath?.let { File(it) }; pendingCameraPath = null
        if (f == null) return@rememberLauncherForActivityResult
        if (!ok || !f.exists() || f.length() == 0L) { f.delete(); return@rememberLauncherForActivityResult }
        photoBusy = true
        scope.launch {
            val good = withContext(Dispatchers.IO) { Photos.compress(f) }
            if (good) setPhoto(f.absolutePath) else { f.delete(); scanMsg = "Impossible d'enregistrer la photo." }
            photoBusy = false
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        photoBusy = true
        scope.launch {
            val f = withContext(Dispatchers.IO) { Photos.importFromUri(context, uri) }
            if (f != null) setPhoto(f.absolutePath) else scanMsg = "Impossible d'importer cette image."
            photoBusy = false
        }
    }

    fun lookup(code: String) {
        barcode = code; scanMsg = null; scanned = null; loading = true
        scope.launch {
            when (val r = OpenFoodFacts.lookup(code)) {
                is LookupResult.Found -> {
                    scanned = r.product
                    name = r.product.name
                    r.product.kcalPer100g?.let { kcal100 = fmt(it) }
                    carbs100 = r.product.carbs?.let { fmt(it) } ?: ""
                    prot100 = r.product.proteins?.let { fmt(it) } ?: ""
                    fat100 = r.product.fat?.let { fmt(it) } ?: ""
                    fillNutr(r.product.nutr); fermented = r.product.fermented; prebiotic = r.product.prebiotic
                    autoFrom = null
                    if (r.product.kcalPer100g == null) scanMsg = "Calories non renseignées pour ce produit : saisis les kcal/100 g (voir l'étiquette)."
                }
                is LookupResult.Error -> scanMsg = r.message
            }
            loading = false
        }
    }

    fun startScan() {
        scanMsg = null
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { lookup(it) } ?: run { scanMsg = "Code-barres illisible, réessaie." } }
            .addOnCanceledListener { }
            .addOnFailureListener {
                scanMsg = "Scanner indisponible (Google Play services requis ou module en cours de téléchargement). Saisis le code à la main."
            }
    }

    // Nom de l'aliment intégré dont les valeurs ont été copiées automatiquement (null = saisie manuelle / scan)
    fun fillFood(f: Food) {
        kcal100 = f.kcalPer100g.toString()
        carbs100 = fmt(f.carbs); prot100 = fmt(f.proteins); fat100 = fmt(f.fat)
        fillNutr(f.nutrients); fermented = f.fermented; prebiotic = f.prebiotic
        autoFrom = f.name
    }
    fun clearAuto() {
        kcal100 = ""; carbs100 = ""; prot100 = ""; fat100 = ""; nutrTxt.clear()
        fermented = false; prebiotic = false; autoFrom = null
    }
    /** Saisie du nom : remplit automatiquement si le texte correspond à un aliment intégré. */
    fun onNameChange(newName: String) {
        name = newName
        val manual = autoFrom == null && (kcal100.isNotBlank() || scanned != null)
        if (manual) return // ne jamais écraser des valeurs tapées à la main ou venant d'un scan
        val m = findFood(newName)
        if (m != null) { if (m.name != autoFrom) fillFood(m) } else if (autoFrom != null) clearAuto()
    }

    val suggestions = remember(name) { suggestFoods(name) }
    val g = parseNum(grams)
    val k = parseNum(kcal100)
    val calc = calculate(g, k, parseNum(carbs100), parseNum(prot100), parseNum(fat100))
    val problem: String? = when {
        name.isBlank() -> "Saisis ou choisis un aliment."
        k == null -> "Aliment inconnu : saisis les kcal pour 100 g ou scanne le code-barres."
        g == null || g <= 0 -> "Indique la quantité en grammes."
        else -> null
    }
    val valid = problem == null && calc != null

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ajouter un aliment", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { startScan() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("📷 Scanner un code-barres") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(barcode, { barcode = it.filter(Char::isDigit).take(14) },
                    label = { Text("Code-barres (saisie)", maxLines = 1) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { lookup(barcode) }, enabled = !loading && barcode.length >= 8) { Text("Chercher") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            scanMsg?.let { Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall) }
            scanned?.let { p ->
                val parts = listOfNotNull(
                    p.proteins?.let { "Protéines ${fmt(it)} g" },
                    p.carbs?.let { "Glucides ${fmt(it)} g" },
                    p.fat?.let { "Lipides ${fmt(it)} g" })
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Text("Open Food Facts : ${p.name}", fontWeight = FontWeight.SemiBold)
                        if (parts.isNotEmpty()) Text("Pour 100 g : " + parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        p.servingSize?.let { Text("Portion indiquée : $it", style = MaterialTheme.typography.bodySmall) }
                        Text("Indique la quantité en grammes puis appuie sur Ajouter.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { onNameChange(it); expanded = true },
                    label = { Text("Aliment (liste ou saisie libre)") },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }) {
                    suggestions.forEach { f ->
                        DropdownMenuItem(
                            text = { Text("${f.name}  ·  ${f.kcalPer100g} kcal/100 g") },
                            onClick = {
                                // Choix explicite : remplace toujours les valeurs
                                scanned = null
                                name = f.name; fillFood(f)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(grams, { grams = it.take(6) }, label = { Text("Quantité g", maxLines = 1) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(kcal100, { kcal100 = it.take(6); autoFrom = null }, label = { Text("kcal/100 g", maxLines = 1) }, singleLine = true,
                    isError = name.isNotBlank() && k == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            autoFrom?.let { Text("Valeurs de la liste : $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            Text("Pour 100 g (facultatif) : G = glucides, P = protéines, L = lipides", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(carbs100, { carbs100 = it.take(6); autoFrom = null }, label = { Text("G (g)", maxLines = 1) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(prot100, { prot100 = it.take(6); autoFrom = null }, label = { Text("P (g)", maxLines = 1) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(fat100, { fat100 = it.take(6); autoFrom = null }, label = { Text("L (g)", maxLines = 1) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            // Aperçu en direct
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    if (calc != null) {
                        val parts = buildList {
                            add("= ${calc.kcal} kcal")
                            calc.carbs?.let { add("G ${g1(it)} g") }
                            calc.proteins?.let { add("P ${g1(it)} g") }
                            calc.fat?.let { add("L ${g1(it)} g") }
                        }
                        Text(parts.joinToString(" · "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    } else {
                        Text("= — kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = fermented, onCheckedChange = { fermented = it })
                Text("Fermenté / probiotique", modifier = Modifier.weight(1f))
                TextButton(onClick = { showNutr = !showNutr }) { Text(if (showNutr) "Masquer nutriments" else "Nutriments…") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = prebiotic, onCheckedChange = { prebiotic = it })
                Text("Riche en prébiotiques")
            }
            if (showNutr) {
                Text("Nutriments pour 100 g (facultatif)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                NUTRIENTS.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { n ->
                            OutlinedTextField(nutrTxt[n.key] ?: "", { nutrTxt[n.key] = it.take(8) },
                                label = { Text("${n.short} ${n.unit}", maxLines = 1) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !photoBusy, onClick = {
                    val f = Photos.newFile(context); pendingCameraPath = f.absolutePath
                    try { takePicture.launch(Photos.uriFor(context, f)) }
                    catch (e: Exception) { pendingCameraPath = null; f.delete(); scanMsg = "Aucune application appareil photo disponible." }
                }) { Text("📸 Photo") }
                OutlinedButton(enabled = !photoBusy, onClick = {
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("🖼 Galerie") }
                if (photoBusy) CircularProgressIndicator(Modifier.size(24.dp))
                photoPath?.let { path ->
                    Thumbnail(path, 56)
                    TextButton(onClick = { setPhoto(null) }) { Text("Retirer") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (calc != null) "= ${calc.kcal} kcal" else (problem ?: ""),
                    style = if (calc != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodySmall,
                    color = if (calc != null) Color.Unspecified else Color(0xFFC62828), modifier = Modifier.weight(1f))
                Button(enabled = valid, onClick = {
                    onAdd(Entry(System.currentTimeMillis(), today(), nowTime(), name.trim(), g!!, k!!, photoPath,
                        parseNum(carbs100)?.takeIf { it >= 0 }, parseNum(prot100)?.takeIf { it >= 0 }, parseNum(fat100)?.takeIf { it >= 0 },
                        nutr = nutrTxt.mapNotNull { (k, v) -> parseNum(v)?.takeIf { it >= 0 }?.let { k to it } }.toMap(),
                        fermented = fermented, prebiotic = prebiotic))
                    photoPath = null
                    name = ""; grams = "100"; kcal100 = ""; carbs100 = ""; prot100 = ""; fat100 = ""; nutrTxt.clear(); fermented = false; prebiotic = false; barcode = ""; scanned = null; scanMsg = null; autoFrom = null
                }) { Text("Ajouter") }
            }
        }
    }
}

@Composable
fun EntryRow(e: Entry, onDelete: (Entry) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            e.photo?.let { Thumbnail(it, 48); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(e.name + (if (e.fermented) " 🦠" else "") + (if (e.prebiotic) " 🌱" else ""), fontWeight = FontWeight.SemiBold)
                val gTxt = if (e.grams % 1.0 == 0.0) e.grams.toInt().toString() else e.grams.toString()
                val kTxt = if (e.kcalPer100g % 1.0 == 0.0) e.kcalPer100g.toInt().toString() else e.kcalPer100g.toString()
                Text("${e.time}  ·  $gTxt g × $kTxt kcal/100 g", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (e.hasMacros) Text("G ${g1(e.carbs)} g · P ${g1(e.proteins)} g · L ${g1(e.fat)} g",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text("${e.kcal} kcal", fontWeight = FontWeight.Bold)
            IconButton(onClick = { onDelete(e) }) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer") }
        }
    }
}

@Composable
fun HistoryScreen(entries: List<Entry>, goal: Int, onDelete: (Entry) -> Unit) {
    val byDay = entries.groupBy { it.date }.toSortedMap(compareByDescending { it })
    if (byDay.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Historique vide.", color = Color.Gray) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        byDay.forEach { (day, list) ->
            val total = list.sumOf { it.kcal }
            item(key = "h$day") {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(prettyDate(day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("$total / $goal kcal", fontWeight = FontWeight.Bold,
                            color = if (total > goal) Color(0xFFC62828) else MaterialTheme.colorScheme.primary)
                    }
                    val m = list.macros()
                    Text("Glucides ${g1(m.carbs)} g · Protéines ${g1(m.proteins)} g · Lipides ${g1(m.fat)} g",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            items(list.sortedByDescending { it.id }, key = { it.id }) { EntryRow(it, onDelete) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(storage: Storage, goal: Int, onUseGoal: (Int) -> Unit) {
    val initial = remember { storage.loadProfile() }
    fun numTxt(d: Double?) = d?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
    var age by remember { mutableStateOf(initial.age?.toString() ?: "") }
    var weight by remember { mutableStateOf(numTxt(initial.weightKg)) }
    var height by remember { mutableStateOf(numTxt(initial.heightCm)) }
    var male by remember { mutableStateOf(initial.male) }
    var activity by remember { mutableIntStateOf(initial.activity) }
    var actExpanded by remember { mutableStateOf(false) }

    val profile = Profile(
        age = age.trim().toIntOrNull()?.takeIf { it in 10..120 },
        weightKg = parseNum(weight)?.takeIf { it in 20.0..400.0 },
        heightCm = parseNum(height)?.takeIf { it in 80.0..250.0 },
        male = male, activity = activity,
    )
    // Sauvegarde automatique à chaque modification
    LaunchedEffect(profile) { storage.saveProfile(profile) }
    val suggested = profile.suggestedGoal()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mon profil", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text("Âge (ans)") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(weight, { weight = it.take(6) }, label = { Text("Poids (kg)") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(height, { height = it.take(6) }, label = { Text("Taille (cm)") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f))
                    }
                    Text("Sexe", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = male == true, onClick = { male = true }); Text("Homme")
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = male == false, onClick = { male = false }); Text("Femme")
                    }
                    ExposedDropdownMenuBox(expanded = actExpanded, onExpandedChange = { actExpanded = it }) {
                        OutlinedTextField(
                            value = "${ACTIVITY_LEVELS[activity].first} (×${ACTIVITY_LEVELS[activity].second})",
                            onValueChange = {}, readOnly = true, label = { Text("Niveau d'activité") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(actExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = actExpanded, onDismissRequest = { actExpanded = false }) {
                            ACTIVITY_LEVELS.forEachIndexed { i, (label, f) ->
                                DropdownMenuItem(text = { Text("$label (×$f)") }, onClick = { activity = i; actExpanded = false })
                            }
                        }
                    }
                    Text("Enregistré automatiquement sur le téléphone.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Objectif suggéré", style = MaterialTheme.typography.titleMedium)
                    if (suggested != null) {
                        Text("Métabolisme de base : ${Math.round(profile.bmr()!!)} kcal/jour")
                        Text("$suggested kcal / jour", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Formule Mifflin-St Jeor × facteur d'activité. Estimation indicative, pas un avis médical.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Button(onClick = { onUseGoal(suggested) }, enabled = suggested != goal) {
                            Text(if (suggested == goal) "Objectif déjà appliqué" else "Utiliser comme objectif")
                        }
                    } else {
                        Text("Remplis âge, poids, taille et sexe pour obtenir une suggestion.", color = Color.Gray)
                    }
                    Text("Objectif actuel : $goal kcal (modifiable via « Objectif » en haut).",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Miniature cliquable : un appui ouvre la photo en plein écran. */
@Composable
fun Thumbnail(path: String, sizeDp: Int) {
    var full by remember { mutableStateOf(false) }
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { Photos.load(path, 256)?.asImageBitmap() }
    }
    Box(Modifier.size(sizeDp.dp).clip(MaterialTheme.shapes.small).background(Color(0xFFE0E0E0))
        .clickable { full = true }, contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, contentDescription = "Photo du repas", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Text("📷")
    }
    if (full) {
        val big by produceState<ImageBitmap?>(null, path) {
            value = withContext(Dispatchers.IO) { Photos.load(path, 2048)?.asImageBitmap() }
        }
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { full = false }, contentAlignment = Alignment.Center) {
                big?.let { Image(it, contentDescription = "Photo du repas", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                    ?: CircularProgressIndicator()
            }
        }
    }
}

private val C_CARBS = Color(0xFF1E88E5)
private val C_PROT = Color(0xFFE53935)
private val C_FAT = Color(0xFFFFB300)

@Composable
fun MacroTotals(m: Macros) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MacroCell("Glucides", m.carbs, C_CARBS)
        MacroCell("Protéines", m.proteins, C_PROT)
        MacroCell("Lipides", m.fat, C_FAT)
    }
}

@Composable
private fun MacroCell(label: String, grams: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${g1(grams)} g", fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Répartition des calories issues des macros (4 kcal/g glucides et protéines, 9 kcal/g lipides). */
@Composable
fun MacroSplitBar(m: Macros) {
    val c = m.carbs * 4; val p = m.proteins * 4; val f = m.fat * 9
    val sum = c + p + f
    if (sum <= 0.0) return
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small)) {
        if (c > 0) Box(Modifier.weight(c.toFloat()).fillMaxHeight().background(C_CARBS))
        if (p > 0) Box(Modifier.weight(p.toFloat()).fillMaxHeight().background(C_PROT))
        if (f > 0) Box(Modifier.weight(f.toFloat()).fillMaxHeight().background(C_FAT))
    }
    Spacer(Modifier.height(4.dp))
    fun pct(x: Double) = Math.round(x * 100 / sum)
    Text("Répartition des kcal : G ${pct(c)} % · P ${pct(p)} % · L ${pct(f)} %",
        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
}

/** Écran Nutriments : totaux du jour vs repères, moyenne 7 jours, probiotiques. */
@Composable
fun NutrientsScreen(entries: List<Entry>, goal: Int, male: Boolean?) {
    val todayStr = today()
    val last7 = (0..6).map { LocalDate.now().minusDays(it.toLong()).toString() }.toSet()
    val todays = entries.filter { it.date == todayStr }
    val week = entries.filter { it.date in last7 }
    val daysWithData = week.map { it.date }.distinct().size

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aujourd'hui", style = MaterialTheme.typography.titleMedium)
                    if (todays.isEmpty()) Text("Aucune entrée aujourd'hui.", color = Color.Gray)
                    NUTRIENTS.forEach { n ->
                        val ref = referenceIntake(n.key, male, goal)
                        val total = todays.sumOf { it.nutrient(n.key) ?: 0.0 }
                        val missing = todays.count { it.nutr[n.key] == null }
                        NutrientBar(n, total, ref, missing)
                    }
                    if (male == null) Text("Astuce : renseigne ton sexe dans Profil pour ajuster le repère en fer.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Moyenne sur 7 jours", style = MaterialTheme.typography.titleMedium)
                    if (daysWithData == 0) Text("Pas encore de données sur les 7 derniers jours.", color = Color.Gray)
                    else {
                        Text("Calculée sur $daysWithData jour(s) avec des entrées.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        NUTRIENTS.forEach { n ->
                            val ref = referenceIntake(n.key, male, goal)
                            val avg = week.sumOf { it.nutrient(n.key) ?: 0.0 } / daysWithData
                            val ratio = if (ref > 0) avg / ref else 0.0
                            val (tag, color) = when {
                                n.isLimit && ratio > 1.0 -> "au-dessus de la limite" to Color(0xFFC62828)
                                !n.isLimit && ratio < 0.7 -> "apport faible" to Color(0xFFEF6C00)
                                else -> "ok" to Color(0xFF2E7D32)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(n.label, modifier = Modifier.weight(1f))
                                Text("${g1(avg)} ${n.unit}/j  ", style = MaterialTheme.typography.bodySmall)
                                Text(tag, color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        val incomplete = week.count { e -> NUTRIENTS.any { e.nutr[it.key] == null } }
                        if (incomplete > 0) Text("Données incomplètes : $incomplete entrée(s) sans certains nutriments, les totaux sont sous-estimés.",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF6C00))
                    }
                }
            }
        }
        item { ProbioticsCard(week) }
        item { PrebioticsCard(week) }
        item {
            Text("Repères adultes indicatifs (fibres 30 g, calcium 950 mg, fer 11 mg homme / 16 mg femme, vitamine C 110 mg, " +
                "vitamine D 15 µg ; sucres et graisses saturées < 10 % des kcal de ton objectif, sel < 5 g). " +
                "Ces informations sont indicatives et ne remplacent pas un avis médical.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun NutrientBar(n: Nutrient, total: Double, ref: Double, missing: Int) {
    val ratio = if (ref > 0) (total / ref).toFloat() else 0f
    val over = n.isLimit && ratio > 1f
    Column {
        Row {
            Text(n.label + if (n.isLimit) " (limite)" else "", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${g1(total)} / ${g1(ref)} ${n.unit}", style = MaterialTheme.typography.bodySmall,
                color = if (over) Color(0xFFC62828) else Color.Unspecified)
        }
        LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp),
            color = when { over -> Color(0xFFC62828); n.isLimit -> Color(0xFFFFB300); else -> MaterialTheme.colorScheme.primary })
        if (missing > 0) Text("Données incomplètes ($missing entrée(s) sans cette valeur)",
            style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
private fun ProbioticsCard(week: List<Entry>) {
    var showInfo by remember { mutableStateOf(false) }
    val days = week.filter { it.fermented }.map { it.date }.distinct().size
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Probiotiques 🦠", style = MaterialTheme.typography.titleMedium)
            Text("$days jour(s) sur 7 avec au moins un aliment fermenté", fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { days / 7f }, modifier = Modifier.fillMaxWidth().height(6.dp))
            if (days <= 2) Text("Suggestion : un yaourt nature, un kéfir ou un peu de choucroute crue de temps en temps peut " +
                "diversifier ton alimentation.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showInfo = !showInfo }) { Text(if (showInfo) "Masquer les infos" else "En savoir plus") }
            if (showInfo) {
                Text("• Les probiotiques sont des micro-organismes vivants (bactéries lactiques, levures) présents dans certains " +
                    "aliments fermentés.\n" +
                    "• Sources : yaourt, kéfir, choucroute crue, kimchi, miso, tempeh, fromages au lait cru, kombucha.\n" +
                    "• La pasteurisation et la cuisson détruisent une grande partie des cultures vivantes (ex. choucroute cuite, " +
                    "miso bouilli).\n" +
                    "• Le kombucha et certains yaourts aromatisés contiennent des sucres ajoutés : regarde l'étiquette.\n" +
                    "• Ces informations ne sont pas un avis médical. Demande conseil à un médecin avant de prendre des " +
                    "compléments probiotiques, surtout si tu es immunodéprimé(e).",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PrebioticsCard(week: List<Entry>) {
    var showInfo by remember { mutableStateOf(false) }
    val days = week.filter { it.prebiotic }.map { it.date }.toSet()
    val proDays = week.filter { it.fermented }.map { it.date }.toSet()
    val synergy = days.intersect(proDays).size
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Prébiotiques 🌱", style = MaterialTheme.typography.titleMedium)
            Text("${days.size} jour(s) sur 7 avec au moins un aliment riche en prébiotiques", fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { days.size / 7f }, modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Color(0xFF7CB342))
            Text("Synergie : $synergy jour(s) avec à la fois un aliment probiotique et un prébiotique",
                style = MaterialTheme.typography.bodySmall)
            if (days.size <= 2) Text("Suggestion : un peu d'oignon, de poireau, de lentilles ou de flocons d'avoine dans la semaine " +
                "peut nourrir ton microbiote.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showInfo = !showInfo }) { Text(if (showInfo) "Masquer les infos" else "En savoir plus") }
            if (showInfo) {
                Text("• Les prébiotiques sont des fibres non digérées qui nourrissent les bonnes bactéries de l'intestin " +
                    "(le microbiote) : inuline, FOS (fructo-oligosaccharides), GOS (galacto-oligosaccharides), amidon résistant.\n" +
                    "• Sources : ail, oignon, poireau, asperge, artichaut, topinambour, banane peu mûre, chicorée, " +
                    "légumineuses (lentilles, pois chiches, haricots), avoine.\n" +
                    "• Pommes de terre, riz ou pâtes cuits puis refroidis contiennent davantage d'amidon résistant.\n" +
                    "• Augmente progressivement : trop d'un coup peut provoquer ballonnements et gaz.\n" +
                    "• En cas de syndrome de l'intestin irritable (SII), beaucoup de ces aliments sont riches en FODMAP " +
                    "et peuvent aggraver les symptômes : parles-en à un professionnel de santé.\n" +
                    "• Ces informations ne sont pas un avis médical.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
