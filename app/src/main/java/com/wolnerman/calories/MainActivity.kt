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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
        MyFoods.init(applicationContext)   // v2.8 : « Mes aliments »
        Supplements.init(applicationContext) // v2.18 : compléments alimentaires
        // v2.0 : range une clé API collée dans le mauvais champ (ex. clé Groq dans Gemini) — une seule fois
        runCatching { SecureSettings(applicationContext).migrateV2() }
        // v2.2 : (re)programme le rappel du soir (au cas où l'alarme aurait été perdue, ex. arrêt forcé)
        runCatching { ReminderScheduler.schedule(applicationContext) }
        runCatching { SupplementScheduler.scheduleAll(applicationContext) }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFFFFB300))) {
                Surface(Modifier.fillMaxSize()) { CaloriesApp(storage) }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) { ScaleManager.shutdown(); BodyScaleManager.shutdown() }
        super.onDestroy()
    }
}

internal fun today(): String = LocalDate.now().toString()
internal fun nowTime(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
/** v2.4 : propriété locale déléguée vers un champ de FoodForm (le reste du formulaire garde sa syntaxe). */
internal class FormField<T>(private val st: MutableState<FoodForm>, private val get: (FoodForm) -> T, private val set: (FoodForm, T) -> FoodForm) {
    operator fun getValue(thisRef: Any?, p: kotlin.reflect.KProperty<*>): T = get(st.value)
    operator fun setValue(thisRef: Any?, p: kotlin.reflect.KProperty<*>, v: T) { st.value = set(st.value, v) }
}
internal fun <T> formField(st: MutableState<FoodForm>, get: (FoodForm) -> T, set: (FoodForm, T) -> FoodForm) = FormField(st, get, set)

internal fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.FRENCH, "%.1f", d).replace(',', '.')
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
    var plan by remember { mutableStateOf(storage.loadGoalPlan()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Entry?>(null) }
    var showRecipe by remember { mutableStateOf(false) }
    var recipeEdit by remember { mutableStateOf<CustomFood?>(null) }
    val profile = storage.loadProfile()
    val outcome = EnergyLogic.plan(EnergyLogic.compute(profile), plan, profile.male)
    val goal = outcome.kcal
    fun setPlan(p: GoalPlan) { plan = p; storage.saveGoalPlan(p) }

    fun update(newList: List<Entry>) { entries = newList; storage.saveEntries(newList) }
    val appCtx = LocalContext.current.applicationContext
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) Supplements.reloadIfInitialized(appCtx)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val supplements = Supplements.items
    val supplementLogs = Supplements.logs
    val suppKcalByDay = remember(supplements, supplementLogs) { SupplementLogic.kcalByDate(supplements, supplementLogs) }
    val hcScope = rememberCoroutineScope()
    /** Santé Connect (si activé) : écrit les entrées ajoutées, supprime les entrées retirées. */
    fun syncHealth(added: List<Entry> = emptyList(), removed: List<Entry> = emptyList()) {
        if (runCatching { !SecureSettings(appCtx).healthEnabled }.getOrDefault(true)) return
        // Jamais bloquant : l'entrée est déjà enregistrée ; un échec Santé Connect donne juste un avis discret
        hcScope.launch {
            try {
                val err = listOfNotNull(
                    if (added.isNotEmpty()) HealthSync.write(appCtx, added).exceptionOrNull() else null,
                    if (removed.isNotEmpty()) HealthSync.delete(appCtx, removed).exceptionOrNull() else null,
                ).firstOrNull()
                if (err != null) {
                    android.util.Log.w("Calories", "Santé Connect", err)
                    android.widget.Toast.makeText(appCtx, "Santé Connect : non synchronisé (${err.message ?: "erreur"}). " +
                        "L'aliment est bien dans ton journal.", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Throwable) { android.util.Log.w("Calories", "Santé Connect", e) }
        }
    }

    if (showRecipe) {
        RecipeScreen(
            initial = recipeEdit,
            onClose = { showRecipe = false; recipeEdit = null },
            onAddToJournal = { e ->
                update(entries + e); syncHealth(added = listOf(e))
                showRecipe = false; recipeEdit = null
            },
        )
        return
    }

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
                NavigationBarItem(selected = tab == 4, onClick = { tab = 4 },
                    icon = { Text("📊") }, label = { Text("Graphiques", maxLines = 1, softWrap = false) })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                    icon = { Text("🥦") }, label = { Text("Nutriments", maxLines = 1, softWrap = false) })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Text("👤") }, label = { Text("Profil") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) TodayScreen(entries, outcome, supplements, supplementLogs,
                onPlanChange = { setPlan(it) },
                onAdd = { update(entries + it); syncHealth(added = listOf(it)) }, onDelete = { toDelete = it },
                onOpenRecipe = { showRecipe = true; recipeEdit = null })
            else if (tab == 1) HistoryScreen(entries, goal, supplements, supplementLogs, onDelete = { toDelete = it })
            else if (tab == 4) ChartsScreen(entries, goal, suppKcalByDay)
            else if (tab == 3) NutrientsScreen(entries, goal, profile.male)
            else ProfileScreen(storage, outcome, entries, supplements, supplementLogs, onPlanChange = { setPlan(it) },
                onOpenRecipe = { c -> showRecipe = true; recipeEdit = c })
        }
    }

    if (showGoalDialog) {
        GoalDialog(outcome, profile.male, onChange = { setPlan(it) }, onDismiss = { showGoalDialog = false })
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer ?") },
            text = { Text("${e.name} — ${e.kcal} kcal") },
            confirmButton = { TextButton(onClick = { Photos.delete(e.photo); update(entries.filter { it.id != e.id }); syncHealth(removed = listOf(e)); toDelete = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } }
        )
    }
}

@Composable
fun TodayScreen(
    entries: List<Entry>,
    outcome: GoalOutcome,
    supplements: List<Supplement> = emptyList(),
    supplementLogs: Map<String, Set<Long>> = emptyMap(),
    onPlanChange: (GoalPlan) -> Unit,
    onAdd: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onOpenRecipe: () -> Unit = {},
) {
    val todayStr = today()
    val todays = entries.filter { it.date == todayStr }.sortedByDescending { it.id }
    val suppKcal = SupplementLogic.kcalForDate(supplements, supplementLogs, todayStr)
    val total = todays.sumOf { it.kcal } + suppKcal
    // v2.7 / v2.14 : protéines et TDEE d'après le profil (poids éventuellement mis à jour par la balance)
    val ctx = LocalContext.current
    val profile = Storage(ctx).loadProfile()
    val lastWeighIn = BodyScaleManager.get(ctx).history.firstOrNull()?.kg
    val weight = ProteinLogic.currentWeight(profile.weightKg, lastWeighIn)
    val proteinGoal = ProteinLogic.proteinGoal(weight)
    val targets = MacroGoalLogic.targets(outcome.kcal, weight)
    val macros = todays.macros()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SummaryCard(total, outcome, macros, targets, onPlanChange, suppKcal) }
        item { SupplementsTodayCard(todayStr) }
        item { ProteinDistributionHint(proteinGoal) { p -> onAdd(ProteinLogic.wheyEntry(p, System.currentTimeMillis(), today(), nowTime())) } }
        item {
            OutlinedButton(onClick = onOpenRecipe, modifier = Modifier.fillMaxWidth()) {
                Text("📋 Recette — coller une liste d'ingrédients")
            }
        }
        item { AddEntryCard(onAdd) }
        if (todays.isNotEmpty()) item { Text("Repas du jour", style = MaterialTheme.typography.titleMedium) }
        else item { Text("Aucun aliment ajouté aujourd'hui.", color = Color.Gray) }
        items(todays, key = { it.id }) { EntryRow(it, onDelete) }
    }
}

@Composable
fun SummaryCard(total: Int, outcome: GoalOutcome, macros: Macros,
                targets: MacroTargets = MacroGoalLogic.targets(outcome.kcal, null),
                onPlanChange: (GoalPlan) -> Unit = {},
                suppKcal: Int = 0) {
    val energy = outcome.energy
    val maintain = energy?.tdee
    val goal = outcome.kcal
    val ratio = if (goal > 0) total.toFloat() / goal else 0f
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (maintain != null) {
                Text("Pour maintenir votre poids : ${maintain} kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(energy!!.formulaLine, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            GoalSelector(outcome, onPlanChange)
            Text("Consommé : $total kcal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (suppKcal > 0) {
                Text("Dont compléments : +$suppKcal kcal", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (goal > 0) {
                val (restLine, over) = EnergyLogic.remainingLine(total, goal, vsMaintain = outcome.plan.target == EnergyTarget.MAINTAIN)
                Text(restLine, color = if (over) Color(0xFFC62828) else Color.Unspecified,
                    fontWeight = if (over) FontWeight.SemiBold else FontWeight.Normal)
                LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = if (total > goal) Color(0xFFC62828) else MaterialTheme.colorScheme.primary)
            } else if (maintain == null) {
                Text("Total aujourd'hui", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(4.dp))
            MacroTotals(macros)
            MacroSplitBar(macros)
            Spacer(Modifier.height(4.dp))
            MacroGoalsProgress(macros, targets)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AddEntryCard(onAdd: (Entry) -> Unit) {
    // v2.4 : un seul état pur (FoodForm) ; le champ Aliment est un TextFieldValue jamais réécrit pendant la frappe
    val formState = remember { mutableStateOf(FoodForm()) }
    var form by formState
    val name: String = form.text
    var grams by formField(formState, { it.grams }) { s, v -> s.copy(grams = v) }
    var kcal100 by formField(formState, { it.kcal100 }) { s, v -> s.copy(kcal100 = v) }
    var carbs100 by formField(formState, { it.carbs100 }) { s, v -> s.copy(carbs100 = v) }
    var prot100 by formField(formState, { it.prot100 }) { s, v -> s.copy(prot100 = v) }
    var fat100 by formField(formState, { it.fat100 }) { s, v -> s.copy(fat100 = v) }
    val fiber100: String = form.fiber100
    val nutrTxt = remember { mutableStateMapOf<String, String>() }
    var showNutr by remember { mutableStateOf(false) }
    var fermented by remember { mutableStateOf(false) }
    var prebiotic by remember { mutableStateOf(false) }
    fun fillNutr(m: Map<String, Double>) { nutrTxt.clear(); m.forEach { (k, v) -> nutrTxt[k] = fmt(v) } }
    // Nom de l'aliment intégré dont les valeurs ont été copiées automatiquement (null = saisie manuelle / scan)
    var autoFrom by formField(formState, { it.autoFrom }) { s, v -> s.copy(autoFrom = v, picked = if (v == null) false else s.picked) }
    var expanded by remember { mutableStateOf(false) }   // liste de suggestions (en ligne, pas de popup)
    var barcode by remember { mutableStateOf("") }
    var scanned by remember { mutableStateOf<ScannedProduct?>(null) }
    var scanMsg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoPath by remember { mutableStateOf<String?>(null) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var photoBusy by remember { mutableStateOf(false) }
    var filledNote by remember { mutableStateOf<String?>(null) }   // bandeau vert après remplissage par la photo
    val addMsg = remember { BringIntoViewRequester() }
    var addError by remember { mutableStateOf<String?>(null) }     // raison rouge après un appui sur « Ajouter »
    var addNotice by remember { mutableStateOf<String?>(null) }    // avis non bloquant (photo…)
    val formTop = remember { BringIntoViewRequester() }
    val addRow = remember { BringIntoViewRequester() }

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
                    form = FoodFormLogic.setName(form, r.product.name)
                    r.product.kcalPer100g?.let { kcal100 = fmt(it) }
                    // v2.10 : glucides UE (sans fibres) + fibres = G total ; G − fibres redonne les glucides de l'étiquette
                    val fib = r.product.nutr["fiber"]
                    carbs100 = KcalLogic.totalCarbs(r.product.carbs, fib)?.let { fmt(it) } ?: ""
                    prot100 = r.product.proteins?.let { fmt(it) } ?: ""
                    fat100 = r.product.fat?.let { fmt(it) } ?: ""
                    fillNutr(r.product.nutr); fermented = r.product.fermented; prebiotic = r.product.prebiotic
                    autoFrom = null; form = form.copy(fiber100 = fib?.let { fmt(it) } ?: "", alcohol100 = null)
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
    /** Nutriments / cases à cocher suivent l'aliment de la liste (kcal et macros sont dans FoodFormLogic). */
    fun syncExtras(before: String?) {
        val now = form.autoFrom
        if (now == before) return
        val f = now?.let { foodByName(it) }
        if (f != null) { fillNutr(f.nutrients); fermented = f.fermented; prebiotic = f.prebiotic }
        else if (before != null) { nutrTxt.clear(); fermented = false; prebiotic = false }
    }
    fun fillFood(f: Food) { val b = form.autoFrom; form = FoodFormLogic.fill(form, f); syncExtras(b); if (b == f.name) { fillNutr(f.nutrients); fermented = f.fermented; prebiotic = f.prebiotic } }
    fun clearAuto() { val b = form.autoFrom; form = FoodFormLogic.unfill(form); syncExtras(b) }
    /** Frappe dans Aliment : on garde exactement ce que le clavier envoie (texte + composition). */
    fun onNameChange(v: TextFieldValue) {
        val b = form.autoFrom
        form = FoodFormLogic.type(form, v, locked = scanned != null)
        syncExtras(b)
    }
    /** Choix explicite dans la liste : remplace tout le texte, curseur à la fin. */
    fun pickFood(f: Food) {
        val b = form.autoFrom
        scanned = null
        form = FoodFormLogic.pick(form, f)
        if (b == f.name) { fillNutr(f.nutrients); fermented = f.fermented; prebiotic = f.prebiotic } else syncExtras(b)
        expanded = false; addError = null
    }

    val suggestions = remember(name) { suggestFoods(name) }
    val g = parseNum(grams)
    // v2.8 : kcal vides mais G/P/L connus → kcal/100 g estimées (4/4/9)
    // v2.9 : dès que G/P/L sont renseignés, kcal/100 g = G×4 + P×4 + L×9, prioritaires sur la valeur tapée
    // v2.10 : pour TOUS les aliments (liste, scan compris) ; fibres facultatives : glucides utilisés = G − fibres
    val kcalRes = KcalLogic.effective(kcal100, carbs100, prot100, fat100, fiber100, form.alcohol100)
    val fromSource = autoFrom != null || scanned != null
    val derivedKcal = kcalRes.kcal100?.takeIf { kcalRes.computed }
    val kcalEff = kcalRes.kcal100?.let { if (kcalRes.computed) fmt(it) else kcal100 } ?: kcal100
    val k = parseNum(kcalEff)
    val carbsUsed = kcalRes.carbsUsed?.takeIf { it >= 0 }
    val calc = calculate(g, k, carbsUsed, parseNum(prot100), parseNum(fat100))
    val weightOnly = KcalLogic.canAddWeightOnly(name, grams, kcalEff)
    // v2.1 : chaque refus a une raison affichée (addProblem couvre tous les cas où calc serait null)
    val problem: String? = addProblem(name, grams, kcalEff) ?: if (calc == null) "Valeurs invalides : vérifie quantité et kcal." else null
    val valid = problem == null && calc != null

    /** Ajoute l'entrée du formulaire au journal ; [unknownKcal] = « poids seul » (kcal inconnues, comptées 0). */
    fun addNow(unknownKcal: Boolean) {
                    val photo = photoPath?.takeIf { p -> runCatching { File(p).exists() }.getOrDefault(false) }
                    if (photoPath != null && photo == null) addNotice = "Photo introuvable : aliment ajouté sans photo."
                    val entry = try {
                        // v2.4 : nom = aliment de la liste dont viennent les valeurs, sinon le texte tapé
                        val entryName = FoodFormLogic.entryName(form)
                        Entry(System.currentTimeMillis(), today(), nowTime(), entryName, g!!, if (unknownKcal) 0.0 else k!!, photo,
                            carbsUsed, parseNum(prot100)?.takeIf { it >= 0 }, parseNum(fat100)?.takeIf { it >= 0 },
                            // v2.10 : les fibres du champ « Fibres (g) » vont dans les nutriments
                            nutr = (nutrTxt.filterKeys { it != "fiber" } + ("fiber" to fiber100))
                                .mapNotNull { (key, v) -> parseNum(v)?.takeIf { it >= 0 && v.isNotBlank() }?.let { key to it } }.toMap(),
                            fermented = fermented, prebiotic = prebiotic, kcalUnknown = unknownKcal)
                    } catch (e: Exception) {
                        android.util.Log.e("Calories", "Entrée invalide", e)
                        addError = "Impossible d'ajouter : ${e.message ?: e.javaClass.simpleName}"; return
                    }
                    try { onAdd(entry) } catch (e: Exception) {
                        android.util.Log.e("Calories", "Ajout au journal", e)
                        addError = "Impossible d'enregistrer : ${e.message ?: e.javaClass.simpleName}"; return
                    }
                    // v2.8 : nom libre → mémorisé dans « Mes aliments » (nom, kcal/100 g, G/P/L si connus)
                    val pickedBuiltin = form.picked && form.autoFrom?.let { n -> BUILTIN_FOODS.any { it.name == n } } == true
                    if (MyFoodsLogic.shouldRemember(entry.name, pickedBuiltin, scanned != null)) runCatching {
                        MyFoods.remember(CustomFood(entry.name, if (unknownKcal) null else k, parseNum(carbs100)?.takeIf { it >= 0 },
                            parseNum(prot100)?.takeIf { it >= 0 }, parseNum(fat100)?.takeIf { it >= 0 }, System.currentTimeMillis(),
                            parseNum(fiber100)?.takeIf { it >= 0 && fiber100.isNotBlank() }))
                    }
                    photoPath = null
                    form = FoodForm(); nutrTxt.clear(); fermented = false; prebiotic = false; barcode = ""; scanned = null; scanMsg = null
                    filledNote = null; addError = null; expanded = false
                    addNotice = (addNotice?.let { "$it " } ?: "") + "✓ Ajouté : ${entry.name} (" +
                        (if (entry.kcalUnknown) "${fmt(entry.grams)} g, kcal inconnues" else "${entry.kcal} kcal") + ")."
    }

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
            filledNote?.let {
                Surface(color = Color(0xFFE8F5E9), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text(it, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20), fontWeight = FontWeight.SemiBold)
                }
            }
            // v2.4 : TextFieldValue (la composition du clavier Samsung est conservée) + liste EN LIGNE
            // (l'ancien menu déroulant ouvrait une fenêtre popup qui prenait le focus pendant la frappe).
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> onNameChange(v); if (v.text != name) expanded = true; addError = null },
                label = { Text("Aliment (liste ou saisie libre)") },
                singleLine = true,
                trailingIcon = if (name.isNotEmpty()) {
                    {
                        IconButton(onClick = { val b = form.autoFrom; form = FoodFormLogic.clear(form); syncExtras(b); expanded = false; addError = null }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Effacer l'aliment")
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(formTop)
            )
            if (expanded && name.isNotBlank() && suggestions.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        suggestions.take(15).forEach { f ->
                            Column(Modifier.fillMaxWidth().clickable { pickFood(f) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(if (MyFoodsLogic.isMine(f)) "⭐ ${f.name}" else f.name)
                                Text((if (MyFoodsLogic.isMine(f)) "Mes aliments · " else "") + foodLine(f), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(grams, { form = FoodFormLogic.typeGrams(form, it.take(8)); addError = null }, label = { Text("Quantité g", maxLines = 1) }, singleLine = true,
                    isError = name.isNotBlank() && (g == null || g <= 0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                // v2.9 : quand G/P/L sont renseignés, le champ affiche la valeur calculée (non modifiable : G/P/L priment)
                OutlinedTextField(if (derivedKcal != null) fmt(derivedKcal) else kcal100, { kcal100 = it.take(8); autoFrom = null; addError = null },
                    label = { Text(if (derivedKcal != null) "kcal/100 g (calculé)" else "kcal/100 g", maxLines = 1) }, singleLine = true,
                    readOnly = derivedKcal != null,
                    isError = name.isNotBlank() && derivedKcal == null && (k == null || k < 0),
                    supportingText = if (derivedKcal != null) {
                        { Text("calculé G/P/L" + (if (kcalRes.fiberUsed) " − fibres" else "") +
                            (if (kcalRes.typedIgnored) (if (fromSource) " (fiche : ${fmt(kcalRes.typed!!)})" else " (${fmt(kcalRes.typed!!)} tapé ignoré)") else ""),
                            color = Color(0xFF2E7D32)) }
                    } else if (name.isNotBlank() && kcal100.isBlank()) {
                        { Text("kcal inconnues : saisis-les", color = Color(0xFFC62828)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            // v2.4 : un nombre dans le nom ne change JAMAIS la quantité tout seul : simple proposition à toucher
            FoodFormLogic.chip(form)?.let { r ->
                AssistChip(onClick = { form = FoodFormLogic.applyChip(form) }, label = { Text(r.chip) })
            }
            ScalePanel(onUse = { w -> form = FoodFormLogic.scale(form, w) })
            autoFrom?.let { Text("Valeurs de la liste : $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            Text("Pour 100 g (facultatif) : G = glucides, P = protéines, L = lipides, Fibres. Remplis-les et les kcal/100 g sont calculées : G×4 + P×4 + L×9 (G − fibres si les fibres sont saisies).", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            // v2.10 : 2 × 2 (G, P / L, Fibres) pour tenir sur la largeur d'un téléphone Samsung
            val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(carbs100, { form = FoodFormLogic.typeMacro(form, 'G', it.take(6)) }, label = { Text("G (g)", maxLines = 1) }, singleLine = true,
                    keyboardOptions = dec, modifier = Modifier.weight(1f))
                OutlinedTextField(prot100, { form = FoodFormLogic.typeMacro(form, 'P', it.take(6)) }, label = { Text("P (g)", maxLines = 1) }, singleLine = true,
                    keyboardOptions = dec, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fat100, { form = FoodFormLogic.typeMacro(form, 'L', it.take(6)) }, label = { Text("L (g)", maxLines = 1) }, singleLine = true,
                    keyboardOptions = dec, modifier = Modifier.weight(1f))
                OutlinedTextField(fiber100, { form = FoodFormLogic.typeMacro(form, 'F', it.take(6)) }, label = { Text("Fibres (g)", maxLines = 1) }, singleLine = true,
                    keyboardOptions = dec, modifier = Modifier.weight(1f))
            }
            if (kcalRes.fiberUsed) kcalRes.carbsUsed?.let {
                Text("Glucides comptés : G − fibres = ${fmt(it)} g / 100 g", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                        Text("${fmt(k!!)} kcal/100 g × ${fmt(g!!)} g" + (if (derivedKcal != null) " · kcal calculées : ${KcalLogic.formula(kcalRes)}" else ""),
                            style = MaterialTheme.typography.bodySmall)
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
                NUTRIENTS.filter { it.key != "fiber" }.chunked(2).forEach { pair ->   // fibres : champ « Fibres (g) » au-dessus
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
            RecognitionPanel(photoPath) { guess ->
                // Remplit le formulaire (sans rien ajouter au journal : l'utilisateur confirme avec « Ajouter »)
                val r = ScaleManager.get(context).let { m -> if (m.status == ScaleStatus.CONNECTED) m.reading else null }
                val scaleG = r?.grams?.takeIf { r.stable && it > 0 }
                val f = fillFromGuess(guess, scaleG)
                scanned = null; scanMsg = null; addError = null; expanded = false
                if (f.food != null) fillFood(f.food) else {
                    clearAuto()
                    kcal100 = f.kcal100; carbs100 = f.carbs100; prot100 = f.prot100; fat100 = f.fat100
                }
                form = FoodFormLogic.setName(form, f.name)
                // v2.4 : une quantité tapée ou venue de la balance n'est pas écrasée (sauf pesée stable de la balance)
                val keepGrams = scaleG == null && (form.gramsBy == GramsSource.USER || form.gramsBy == GramsSource.SCALE)
                if (!keepGrams) form = form.copy(grams = f.grams, gramsBy = if (scaleG != null) GramsSource.SCALE else GramsSource.PHOTO)
                filledNote = "✓ Rempli par la photo : ${f.name} · " +
                    (if (keepGrams) "${grams} g (quantité gardée)" else "${f.grams} g (${f.gramsSource})") +
                    (if (f.kcalMissing) " · kcal inconnues, saisis-les" else " · ${kcal100} kcal/100 g") +
                    ". Vérifie puis touche « Ajouter »."
                scope.launch { runCatching { formTop.bringIntoView() } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (calc != null) "= ${calc.kcal} kcal" else "= — kcal",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (calc != null) Color.Unspecified else Color.Gray, modifier = Modifier.weight(1f))
                // v2.1 : toujours cliquable ; ajoute, ou affiche la raison en rouge
                Button(onClick = {
                    if (!valid) {
                        addError = problem ?: "Impossible d'ajouter : vérifie le formulaire."
                        // v2.3 : le message est juste sous le bouton ; on le fait défiler à l'écran
                        scope.launch { kotlinx.coroutines.delay(50); runCatching { addMsg.bringIntoView() } }
                        return@Button
                    }
                    addNow(false)
                }, modifier = Modifier.bringIntoViewRequester(addRow),
                    colors = if (valid) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = Color(0xFF9E9E9E))) { Text("Ajouter") }
            }
            // v2.3 : raison en rouge directement sous « Ajouter » (calories inconnues affichées même avant l'appui)
            val underAdd = addError ?: if (name.isNotBlank() && kcalEff.isBlank()) kcalMissingMessage(name) else null
            underAdd?.let {
                Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(addMsg))
            }
            // v2.8 : aliment hors liste sans kcal : on peut quand même enregistrer le poids (kcal comptées 0, signalées)
            if (weightOnly) OutlinedButton(onClick = { addNow(true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ajouter le poids seul (${grams.trim()} g, kcal inconnues)")
            }
            addNotice?.let {
                Text(it, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
                LaunchedEffect(it) { kotlinx.coroutines.delay(6000); addNotice = null }
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
                if (e.kcalUnknown) Text("${e.time}  ·  $gTxt g · kcal inconnues (comptées 0)", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB26A00))
                else Text("${e.time}  ·  $gTxt g × $kTxt kcal/100 g", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (e.hasMacros) Text("G ${g1(e.carbs)} g · P ${g1(e.proteins)} g · L ${g1(e.fat)} g",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text(if (e.kcalUnknown) "? kcal" else "${e.kcal} kcal", fontWeight = FontWeight.Bold)
            IconButton(onClick = { onDelete(e) }) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer") }
        }
    }
}

@Composable
fun HistoryScreen(
    entries: List<Entry>,
    goal: Int,
    supplements: List<Supplement> = emptyList(),
    supplementLogs: Map<String, Set<Long>> = emptyMap(),
    onDelete: (Entry) -> Unit,
) {
    val byDay = entries.groupBy { it.date }
    val allDays = (byDay.keys + supplementLogs.keys).sortedDescending()
    if (allDays.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Historique vide.", color = Color.Gray) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        allDays.forEach { day ->
            val list = byDay[day].orEmpty()
            val suppKcal = SupplementLogic.kcalForDate(supplements, supplementLogs, day)
            val total = list.sumOf { it.kcal } + suppKcal
            val taken = SupplementLogic.takenForDate(supplements, supplementLogs, day)
            val notTaken = SupplementLogic.notTakenForDate(supplements, supplementLogs, day)
            val omega3 = SupplementLogic.omega3MgForDate(supplements, supplementLogs, day)
            item(key = "h$day") {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(prettyDate(day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("$total / $goal kcal", fontWeight = FontWeight.Bold,
                            color = if (total > goal) Color(0xFFC62828) else MaterialTheme.colorScheme.primary)
                    }
                    if (list.isNotEmpty()) {
                        val m = list.macros()
                        Text("Glucides ${g1(m.carbs)} g · Protéines ${g1(m.proteins)} g · Lipides ${g1(m.fat)} g",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    if (taken.isNotEmpty() || notTaken.isNotEmpty()) {
                        val suppParts = mutableListOf<String>()
                        if (taken.isNotEmpty()) suppParts += "Pris : ${taken.joinToString(", ") { it.name }}"
                        if (notTaken.isNotEmpty()) suppParts += "Non pris : ${notTaken.joinToString(", ") { it.name }}"
                        if (omega3 > 0.0) suppParts += "Oméga-3 : ${SupplementLogic.formatOmega3(omega3)}"
                        Text("💊 ${suppParts.joinToString(" · ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (taken.isNotEmpty()) Color(0xFF2E7D32) else Color.Gray)
                    }
                }
            }
            items(list.sortedByDescending { it.id }, key = { it.id }) { EntryRow(it, onDelete) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    storage: Storage,
    outcome: GoalOutcome,
    entries: List<Entry>,
    supplements: List<Supplement> = emptyList(),
    supplementLogs: Map<String, Set<Long>> = emptyMap(),
    onPlanChange: (GoalPlan) -> Unit,
    onOpenRecipe: (CustomFood?) -> Unit = {},
) {
    val initial = remember { storage.loadProfile() }
    var age by remember { mutableStateOf(initial.age?.toString() ?: "") }
    var weight by remember { mutableStateOf(ProfileFmt.numberOrEmpty(initial.weightKg)) }
    var height by remember { mutableStateOf(ProfileFmt.numberOrEmpty(initial.heightCm)) }
    var male by remember { mutableStateOf(initial.male) }
    var activity by remember { mutableIntStateOf(initial.activity) }
    var actExpanded by remember { mutableStateOf(false) }
    var fat by remember { mutableStateOf(ProfileFmt.numberOrEmpty(initial.fatPct)) }

    val profile = Profile(
        age = age.trim().toIntOrNull()?.takeIf { it in 10..120 },
        weightKg = parseNum(weight)?.takeIf { it in 20.0..400.0 },
        heightCm = parseNum(height)?.takeIf { it in 80.0..250.0 },
        male = male, activity = activity,
        fatPct = parseNum(fat)?.takeIf { it in 0.0..100.0 },
    )
    // Sauvegarde automatique à chaque modification
    LaunchedEffect(profile) { storage.saveProfile(profile) }
    val energy = EnergyLogic.compute(profile)
    val liveOutcome = EnergyLogic.plan(energy, outcome.plan, profile.male)
    val suggested = energy?.tdee
    val fatIgnored = fat.isNotBlank() && EnergyLogic.validFatPct(profile.fatPct) == null

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AppVersionText() }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mon profil", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text("Âge (ans)") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, label = { Text("Poids (kg)") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(height, { height = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, label = { Text("Taille (cm)") },
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
                            value = EnergyLogic.activityLine(activity),
                            onValueChange = {}, readOnly = true, label = { Text("Niveau d'activité") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(actExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = actExpanded, onDismissRequest = { actExpanded = false }) {
                            ACTIVITY_LEVELS.indices.forEach { i ->
                                DropdownMenuItem(text = { Text(EnergyLogic.activityLine(i)) }, onClick = { activity = i; actExpanded = false })
                            }
                        }
                    }
                    OutlinedTextField(fat, { fat = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                        label = { Text("% de masse grasse") }, singleLine = true,
                        isError = fatIgnored,
                        supportingText = {
                            Text(when {
                                fatIgnored -> "Saisis une valeur entre 3 et 70 % ; sinon Mifflin-St Jeor est utilisée."
                                EnergyLogic.validFatPct(profile.fatPct) != null -> "Katch-McArdle : BMR = 370 + 21,6 × masse maigre."
                                else -> "Facultatif. Si renseigné (3 à 70 %), le calcul passe à Katch-McArdle ; sinon Mifflin-St Jeor."
                            }, color = if (fatIgnored) Color(0xFFC62828) else Color.Gray)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth())
                    Text("Ce pourcentage n'est pas repris automatiquement de la balance (souvent absent ou peu fiable) : saisis-le toi-même si tu le connais.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text("Enregistré automatiquement sur le téléphone.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Besoin pour maintenir le poids (TDEE)", style = MaterialTheme.typography.titleMedium)
                    if (energy != null) {
                        Text("Pour maintenir votre poids :")
                        Text("${energy.tdee} kcal / jour", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("BMR : ${Math.round(energy.bmr)} kcal/jour · ${energy.formulaLine}",
                            style = MaterialTheme.typography.bodySmall)
                        energy.leanMassKg?.let {
                            Text("Masse maigre : ${ProfileFmt.number(it)} kg (à ${ProfileFmt.number(energy.fatPct!!)} % de masse grasse)",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Estimation indicative, pas un avis médical.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        Text("Remplis âge, poids, taille et sexe (Mifflin-St Jeor), ou le poids et le % de masse grasse (Katch-McArdle).", color = Color.Gray)
                    }
                    GoalSelector(liveOutcome, onPlanChange)
                    Text("Le rappel du soir, la barre et les graphiques suivent cet objectif. Tu peux aussi le changer via « Objectif » en haut.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { ReminderCard() }
        item { SupplementsManageCard() }
        item { ExportCard(entries, liveOutcome.kcal, supplements, supplementLogs) }
        item { HealthConnectCard(entries) { kg -> weight = ProfileFmt.number(kg) } }
        item { BodyScaleCard(profile.age) { kg -> weight = ProfileFmt.number(kg) } }
        item {
            // v2.6 : repères consultables à tout moment, avec la dernière masse grasse / musculaire connue
            val hist = BodyScaleManager.get(LocalContext.current).history
            val lastComp = hist.firstOrNull { it.fatPct != null || it.musclePct != null }
            CompositionGuideCard(age = profile.age, fat = lastComp?.fatPct, muscle = lastComp?.musclePct,
                measuredAt = lastComp?.timeMs)
        }
        item { MyFoodsCard(onOpenRecipe) }
        item { RecognitionSettingsCard() }
        item { AppVersionText(Modifier.padding(top = 4.dp)) }
        item {
            Text("Valeurs nutritionnelles de la liste : Anses, table Ciqual 2025 (doi.org/10.57745/RDMHWY) ; " +
                "plats antillais calculés à partir des ingrédients Ciqual.",
                style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
