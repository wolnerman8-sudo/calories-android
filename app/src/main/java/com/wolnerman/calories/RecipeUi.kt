package com.wolnerman.calories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay

/** v2.20 : écran « Recette » — collage d'ingrédients, aperçu, totaux, Mes aliments et journal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(
    initial: CustomFood? = null,
    onClose: () -> Unit,
    onAddToJournal: (Entry) -> Unit,
) {
    BackHandler { onClose() }
    val restored = remember(initial) { initial?.let { RecipeLogic.fromCustomFood(it) } }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var source by remember { mutableStateOf(restored?.first ?: initial?.recipeSource ?: "") }
    var lines by remember { mutableStateOf(restored?.second ?: emptyList()) }
    var portionsTxt by remember { mutableStateOf(restored?.third?.let { RecipeLogic.g1(it) } ?: "1") }
    var eatenTxt by remember { mutableStateOf("1") }
    var meal by remember { mutableStateOf(RecipeLogic.defaultMeal(nowTime())) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickFor by remember { mutableStateOf<Long?>(null) }
    var manualFor by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(source) {
        delay(250)
        lines = RecipeLogic.parse(source, previous = lines)
    }

    val portions = parseNum(portionsTxt)?.takeIf { it > 0 } ?: 1.0
    val totals = RecipeLogic.totals(lines)
    val per = RecipeLogic.perPortion(totals, portions)
    val canSave = RecipeLogic.canSave(name, lines)

    fun setLines(newLines: List<RecipeLine>) {
        lines = newLines
        source = newLines.joinToString("\n") { it.raw }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recette") },
                navigationIcon = { TextButton(onClick = onClose, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, actionIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nom de la recette") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                source, { source = it },
                label = { Text("Ingrédients") },
                placeholder = { Text("100 g de flocons d'avoine, 1 banane, 15 g de graines de chia…") },
                minLines = 5, maxLines = 12, modifier = Modifier.fillMaxWidth()
            )
            Text("Une ligne par ingrédient, ou une liste séparée par des virgules ou des points-virgules. " +
                "Unités acceptées : g, kg, mg, ml, cl, l, c. à soupe, c. à café, pincée, ou une quantité sans unité (1 banane).",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            if (lines.isNotEmpty()) {
                Text("Aperçu", style = MaterialTheme.typography.titleMedium)
                lines.forEach { line ->
                    RecipeLineCard(
                        line = line,
                        picking = pickFor == line.id,
                        manual = manualFor == line.id,
                        onPickToggle = { pickFor = if (pickFor == line.id) null else line.id; manualFor = null },
                        onManualToggle = { manualFor = if (manualFor == line.id) null else line.id; pickFor = null },
                        onChange = { updated -> lines = lines.map { if (it.id == line.id) updated else it } },
                        onDelete = { setLines(lines.filter { it.id != line.id }); pickFor = null; manualFor = null },
                    )
                }
            }

            OutlinedTextField(portionsTxt, { portionsTxt = it.take(6) },
                label = { Text("Nombre de portions") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth())

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Totaux de la recette", style = MaterialTheme.typography.titleMedium)
                    if (totals.included == 0) {
                        Text("Aucun ingrédient reconnu pour le moment. Corrige les lignes en orange ou saisis leurs valeurs.",
                            color = Color(0xFFE65100), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(totalsLine(totals, "Toute la recette"), fontWeight = FontWeight.SemiBold)
                        Text("Poids total : ${RecipeLogic.g1(totals.grams)} g", style = MaterialTheme.typography.bodySmall)
                        if (portions != 1.0 && per != null) {
                            Text(totalsLine(per, "Par portion"), fontWeight = FontWeight.SemiBold)
                            Text("Poids par portion : ${RecipeLogic.g1(per.grams)} g", style = MaterialTheme.typography.bodySmall)
                        } else if (portions == 1.0) {
                            Text("1 portion = toute la recette (${RecipeLogic.g1(totals.grams)} g).",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Text("kcal = (G − fibres)×4 + P×4 + L×9, comme pour un aliment seul (G = glucides totaux, fibres comprises).",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    if (totals.excluded > 0) {
                        Text("${totals.excluded} ligne${if (totals.excluded > 1) "s" else ""} non retenue${if (totals.excluded > 1) "s" else ""} dans le total.",
                            color = Color(0xFFE65100), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            error?.let { Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall) }
            notice?.let { Text(it, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall) }

            Button(
                onClick = {
                    error = null
                    val c = RecipeLogic.toCustomFood(name, source, lines, portions, System.currentTimeMillis())
                    if (c == null) { error = "Donne un nom à la recette et corrige au moins un ingrédient."; return@Button }
                    if (initial?.name?.isNotBlank() == true) MyFoods.edit(initial.name, c) else MyFoods.remember(c)
                    notice = "✓ Enregistré dans Mes aliments : ${c.name} (${fmt(c.kcal100 ?: 0.0)} kcal/100 g, 1 portion ≈ ${RecipeLogic.g1(c.portionGrams ?: 0.0)} g)."
                },
                enabled = canSave, modifier = Modifier.fillMaxWidth()
            ) { Text("Enregistrer dans Mes aliments") }

            HorizontalDivider()
            Text("Ajouter au journal du jour", style = MaterialTheme.typography.titleMedium)
            Text("Repas", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            RecipeLogic.MEALS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { m ->
                        FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m, maxLines = 1) },
                            modifier = Modifier.weight(1f))
                    }
                }
            }
            OutlinedTextField(eatenTxt, { eatenTxt = it.take(6) },
                label = { Text("Portions mangées") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth())
            val eaten = parseNum(eatenTxt)?.takeIf { it > 0 }
            val preview = if (canSave && eaten != null) RecipeLogic.toEntry(name, source, lines, portions, eaten, 0, today(), nowTime()) else null
            preview?.let {
                Text("Soit ${RecipeLogic.g1(it.grams)} g · ${it.kcal} kcal (${meal.lowercase()}).",
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    error = null
                    val e = parseNum(eatenTxt)?.takeIf { it > 0 }
                    if (!canSave || e == null) { error = "Donne un nom, au moins un ingrédient reconnu, et le nombre de portions mangées."; return@Button }
                    val entry = RecipeLogic.toEntry(name, source, lines, portions, e,
                        System.currentTimeMillis(), today(), RecipeLogic.timeForMeal(meal, nowTime()))
                    if (entry == null) { error = "Impossible d'ajouter cette recette au journal."; return@Button }
                    onAddToJournal(entry)
                },
                enabled = canSave && eaten != null, modifier = Modifier.fillMaxWidth()
            ) { Text("Ajouter au journal du jour") }
            Text("Le journal (TDEE, objectif, barres G/P/L, rappels) et Santé Connect sont mis à jour comme pour un aliment normal.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun RecipeLineCard(
    line: RecipeLine,
    picking: Boolean,
    manual: Boolean,
    onPickToggle: () -> Unit,
    onManualToggle: () -> Unit,
    onChange: (RecipeLine) -> Unit,
    onDelete: () -> Unit,
) {
    val nut = RecipeLogic.nutrition(line)
    val orange = Color(0xFFE65100)
    val border = if (line.unresolved) orange else Color.Transparent
    Card(Modifier.fillMaxWidth().then(if (line.unresolved) Modifier.border(1.5.dp, orange, MaterialTheme.shapes.medium) else Modifier)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.raw, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(line.displayName, fontWeight = FontWeight.SemiBold,
                        color = if (line.unresolved) orange else Color.Unspecified)
                    line.gramsNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (nut != null) {
                        Text(lineValues(nut), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            when {
                                line.food != null && line.grams == null -> "Aliment reconnu, mais le poids est à préciser."
                                else -> "Non reconnu : exclus du total. Choisis un aliment ou saisis les valeurs."
                            },
                            color = orange, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    line.grams?.let { RecipeLogic.g1(it) } ?: "",
                    { v ->
                        val g = parseNum(v)
                        onChange(line.copy(grams = g, gramsOverridden = true,
                            gramsNote = g?.let { "${RecipeLogic.g1(it)} g, modifié" } ?: "Poids à préciser, modifiable"))
                    },
                    label = { Text("g") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(110.dp)
                )
                TextButton(onClick = onPickToggle) { Text(if (picking) "Fermer" else "Changer l'aliment") }
                TextButton(onClick = onManualToggle) { Text(if (manual) "Fermer" else "Valeurs à la main") }
            }
            if (picking) FoodPicker(line, onChange)
            if (manual) ManualValues(line, onChange)
        }
    }
}

@Composable
private fun FoodPicker(line: RecipeLine, onChange: (RecipeLine) -> Unit) {
    var q by remember { mutableStateOf(line.food?.name ?: line.raw) }
    val suggestions = remember(q) { suggestFoods(q).take(8) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(q, { q = it }, label = { Text("Rechercher un aliment") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        suggestions.forEach { f ->
            Text(
                "${f.name} — ${foodLine(f)}",
                modifier = Modifier.fillMaxWidth().clickable {
                    val qty = quantityFor(parseQuantity(line.raw), f)
                    onChange(line.copy(
                        food = f, foodOverridden = true,
                        grams = if (line.gramsOverridden) line.grams else qty?.grams,
                        gramsNote = if (line.gramsOverridden && line.grams != null) line.gramsNote
                        else RecipeLogic.weightCaption(parseQuantity(line.raw), f, qty),
                        manualName = null, manualKcal100 = null, manualCarbs100 = null,
                        manualProt100 = null, manualFat100 = null, manualFiber100 = null,
                    ))
                }.padding(vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (suggestions.isEmpty()) Text("Aucun aliment correspondant.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ManualValues(line: RecipeLine, onChange: (RecipeLine) -> Unit) {
    fun t(d: Double?) = d?.let { fmt(it) } ?: ""
    var n by remember { mutableStateOf(line.manualName ?: line.food?.name ?: "") }
    var k by remember { mutableStateOf(t(line.manualKcal100)) }
    var g by remember { mutableStateOf(t(line.manualCarbs100)) }
    var p by remember { mutableStateOf(t(line.manualProt100)) }
    var l by remember { mutableStateOf(t(line.manualFat100)) }
    var fi by remember { mutableStateOf(t(line.manualFiber100)) }
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Valeurs pour 100 g (G = glucides totaux, fibres comprises). Rien n'est inventé : tu saisis toi-même.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        OutlinedTextField(n, { n = it.take(60) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(g, { g = it.take(6) }, label = { Text("G") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
            OutlinedTextField(p, { p = it.take(6) }, label = { Text("P") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
            OutlinedTextField(l, { l = it.take(6) }, label = { Text("L") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
            OutlinedTextField(fi, { fi = it.take(6) }, label = { Text("Fibres") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
        }
        val res = KcalLogic.effective(k, g, p, l, fi)
        res.kcal100?.takeIf { res.computed }?.let {
            Text("= ${fmt(it)} kcal/100 g : ${KcalLogic.formula(res)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
        }
        OutlinedButton(onClick = {
            val c2 = parseNum(g)?.takeIf { it >= 0 }; val p2 = parseNum(p)?.takeIf { it >= 0 }
            val l2 = parseNum(l)?.takeIf { it >= 0 }; val fi2 = parseNum(fi)?.takeIf { it >= 0 && fi.isNotBlank() }
            val kcal = KcalLogic.fromMacros(c2, p2, l2, fi2) ?: parseNum(k)?.takeIf { it in 0.0..1000.0 }
            onChange(line.copy(
                food = null, foodOverridden = true, manualName = n.trim().ifBlank { line.raw },
                manualKcal100 = kcal, manualCarbs100 = c2, manualProt100 = p2, manualFat100 = l2, manualFiber100 = fi2,
            ))
        }, modifier = Modifier.fillMaxWidth()) { Text("Utiliser ces valeurs") }
    }
}

private fun totalsLine(t: RecipeTotals, title: String): String {
    fun m(v: Double?) = v?.let { RecipeLogic.g1(Math.round(it * 10.0) / 10.0) } ?: "—"
    return "$title : ${RecipeLogic.kcalDisplay(t.kcal)} kcal · G ${m(t.carbs)} · P ${m(t.proteins)} · L ${m(t.fat)} · Fibres ${m(t.fiber)}"
}

private fun lineValues(n: LineNutrition): String {
    fun m(v: Double?) = v?.let { RecipeLogic.g1(Math.round(it * 10.0) / 10.0) } ?: "—"
    return "${RecipeLogic.kcalDisplay(n.kcal)} kcal · G ${m(n.carbs)} · P ${m(n.proteins)} · L ${m(n.fat)} · Fibres ${m(n.fiber)}"
}
