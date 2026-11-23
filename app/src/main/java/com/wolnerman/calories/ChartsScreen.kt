package com.wolnerman.calories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max

private val GREEN = Color(0xFF43A047)
private val RED = Color(0xFFE53935)
private val GOAL = Color(0xFF1565C0)
private val C_CARBS = Color(0xFFFFB300)
private val C_PROT = Color(0xFF8E24AA)
private val C_FAT = Color(0xFF00897B)
private val GRID = Color(0x22000000)

private fun f1(d: Double) = String.format(Locale.FRENCH, "%.0f", d)

/** Onglet « Graphiques » : évolution des calories et macros, moyennes hebdo, détail d'un jour. */
@Composable
fun ChartsScreen(entries: List<Entry>, goal: Int) {
    var weeks by remember { mutableIntStateOf(1) }
    var offset by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val today = LocalDate.now()
    val (start, end) = Stats.window(weeks, offset, today)
    val days = remember(entries, start, end) { Stats.daily(entries, start, end) }
    val sum = remember(days, goal) { Stats.summary(days, goal) }
    val weekly = remember(days) { Stats.weekly(days) }
    LaunchedEffect(weeks, offset) { selected = null }

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Pas encore de données : ajoute des aliments dans « Aujourd'hui » pour voir tes graphiques.",
                color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Stats.PERIODS.forEach { w ->
                    FilterChip(selected = weeks == w, onClick = { weeks = w; offset = 0 },
                        label = { Text(if (w == 1) "1 sem." else "$w sem.") })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { offset++ }) { Text(if (weeks == 1) "◀ Sem. préc." else "◀ Préc.") }
                Text("${Stats.dayMonth(start)} – ${Stats.dayMonth(end)}", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { offset-- }, enabled = offset > 0) { Text(if (weeks == 1) "Sem. suiv. ▶" else "Suiv. ▶") }
            }
        }
        item { StatsCard(sum, goal) }
        item {
            ChartCard("Calories par jour", "Touche une barre pour voir le détail du jour.") {
                Legend(listOf(GREEN to "≤ objectif", RED to "au-dessus", GOAL to "objectif $goal kcal"))
                KcalBars(days, goal, weeks, selected) { d -> selected = if (selected == d) null else d }
            }
        }
        selected?.let { d -> item { DayDetail(d, entries, goal) { selected = null } } }
        item {
            ChartCard("Macronutriments par jour (g)", "Jours sans macros renseignées : pas de barre.") {
                Legend(listOf(C_CARBS to "Glucides", C_PROT to "Protéines", C_FAT to "Lipides"))
                MacroBars(days, weeks, selected) { d -> selected = if (selected == d) null else d }
            }
        }
        if (weeks > 1) item {
            ChartCard("Moyenne par semaine (kcal/jour)", "Moyenne des jours renseignés de chaque semaine.") {
                WeeklyBars(weekly, goal)
            }
        }
        item {
            Text("Jours de la période", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        }
        days.reversed().forEach { d ->
            item(key = "d${d.date}") {
                val over = d.kcal != null && goal > 0 && d.kcal > goal
                Surface(onClick = { selected = if (selected == d.date) null else d.date },
                    color = if (selected == d.date) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${Stats.dayName(d.date)} ${Stats.dayMonth(d.date)}", Modifier.width(96.dp), fontWeight = FontWeight.SemiBold)
                        Text(d.kcal?.let { "$it kcal" } ?: "—  non renseigné", Modifier.weight(1f),
                            color = when { d.kcal == null -> Color.Gray; over -> RED; else -> Color.Unspecified })
                        if (d.kcal != null && goal > 0) Text(if (over) "+${d.kcal - goal}" else "−${goal - d.kcal}",
                            style = MaterialTheme.typography.bodySmall, color = if (over) RED else GREEN)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(s: PeriodSummary, goal: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Résumé de la période", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth()) {
                StatBox("Moyenne", s.avgKcal?.let { "$it kcal/j" } ?: "—", Modifier.weight(1f))
                StatBox("Dans l'objectif", if (s.daysLogged > 0) "${s.daysInGoal} / ${s.daysLogged} j" else "—", Modifier.weight(1f))
                StatBox("Jours renseignés", "${s.daysLogged} / ${s.days}", Modifier.weight(1f))
            }
            if (s.avgCarbs != null || s.avgProteins != null || s.avgFat != null)
                Text("Macros moyennes/jour : G ${s.avgCarbs?.let { f1(it) } ?: "—"} g · P ${s.avgProteins?.let { f1(it) } ?: "—"} g · L ${s.avgFat?.let { f1(it) } ?: "—"} g",
                    style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            if (s.daysOver > 0) Text("${s.daysOver} jour(s) au-dessus de l'objectif ($goal kcal).",
                style = MaterialTheme.typography.bodySmall, color = RED)
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
private fun ChartCard(title: String, hint: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            content()
        }
    }
}

@Composable
private fun Legend(items: List<Pair<Color, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(c, CircleShape)); Spacer(Modifier.width(4.dp))
                Text(t, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Étiquettes sous les barres : tous les jours en mode 1 semaine (lun. 3/11), sinon quelques dates. */
@Composable
private fun XLabels(days: List<LocalDate>, weeks: Int) {
    if (weeks == 1) {
        Row(Modifier.fillMaxWidth().padding(start = 36.dp)) {
            days.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Stats.dayName(d), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Text(Stats.dayMonth(d), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    } else {
        val step = when (weeks) { 2 -> 2; 4 -> 7; 8 -> 14; else -> 21 }
        Row(Modifier.fillMaxWidth().padding(start = 36.dp)) {
            days.forEachIndexed { i, d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopStart) {
                    if (i % step == 0) Text(Stats.dayMonth(d), style = MaterialTheme.typography.labelSmall, color = Color.Gray, softWrap = false)
                }
            }
        }
    }
}

/** Axe Y à gauche (3 graduations). */
private fun DrawScope.yGrid(maxV: Float, left: Float, chartH: Float, labels: (Float) -> String, paint: android.graphics.Paint) {
    for (i in 0..2) {
        val v = maxV * i / 2f
        val y = chartH - chartH * (v / maxV)
        drawLine(GRID, Offset(left, y), Offset(size.width, y), 1f)
        drawContext.canvas.nativeCanvas.drawText(labels(v), 0f, y + if (i == 0) 0f else paint.textSize, paint)
    }
}

private fun labelPaint(density: Float) = android.graphics.Paint().apply {
    color = android.graphics.Color.GRAY; textSize = 10f * density; isAntiAlias = true
}

/** Indice de barre touché. */
private fun indexAt(x: Float, left: Float, width: Float, n: Int): Int? {
    if (x < left || n == 0) return null
    return (((x - left) / ((width - left) / n)).toInt()).coerceIn(0, n - 1)
}

@Composable
private fun KcalBars(days: List<DayStat>, goal: Int, weeks: Int, selected: LocalDate?, onTap: (LocalDate) -> Unit) {
    val maxV = max(goal.toFloat() * 1.15f, (days.mapNotNull { it.kcal }.maxOrNull() ?: 0).toFloat() * 1.05f).coerceAtLeast(100f)
    val tap by rememberUpdatedState(onTap)
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    Canvas(Modifier.fillMaxWidth().height(180.dp).pointerInput(days) {
        detectTapGestures { p -> indexAt(p.x, 36.dp.toPx(), size.width.toFloat(), days.size)?.let { tap(days[it].date) } }
    }) {
        val left = 36.dp.toPx(); val h = size.height
        val paint = labelPaint(dens)
        yGrid(maxV, left, h, { f1(it.toDouble()) }, paint)
        val slot = (size.width - left) / days.size
        val bw = (slot * 0.7f).coerceAtLeast(1.5f)
        days.forEachIndexed { i, d ->
            val x = left + i * slot + (slot - bw) / 2
            if (d.kcal == null) {
                // jour non renseigné : petit tiret gris (trou, pas zéro)
                drawLine(Color.LightGray, Offset(x, h - 2f), Offset(x + bw, h - 2f), 2f)
            } else {
                val bh = h * (d.kcal / maxV)
                val c = if (goal > 0 && d.kcal > goal) RED else GREEN
                drawRoundRect(if (selected == null || selected == d.date) c else c.copy(alpha = 0.35f),
                    Offset(x, h - bh), Size(bw, bh), CornerRadius(3f, 3f))
                if (selected == d.date) drawRect(Color.Black, Offset(x - 2f, h - bh - 2f), Size(bw + 4f, bh + 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
        }
        if (goal > 0) {
            val gy = h - h * (goal / maxV)
            drawLine(GOAL, Offset(left, gy), Offset(size.width, gy), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
        }
    }
    XLabels(days.map { it.date }, weeks)
}

@Composable
private fun MacroBars(days: List<DayStat>, weeks: Int, selected: LocalDate?, onTap: (LocalDate) -> Unit) {
    val totals = days.map { (it.carbs ?: 0.0) + (it.proteins ?: 0.0) + (it.fat ?: 0.0) }
    val maxV = ((totals.maxOrNull() ?: 0.0) * 1.1).toFloat().coerceAtLeast(10f)
    val tap by rememberUpdatedState(onTap)
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    if (days.none { it.carbs != null || it.proteins != null || it.fat != null }) {
        Text("Aucune macro renseignée sur cette période.", color = Color.Gray, style = MaterialTheme.typography.bodySmall); return
    }
    Canvas(Modifier.fillMaxWidth().height(160.dp).pointerInput(days) {
        detectTapGestures { p -> indexAt(p.x, 36.dp.toPx(), size.width.toFloat(), days.size)?.let { tap(days[it].date) } }
    }) {
        val left = 36.dp.toPx(); val h = size.height
        yGrid(maxV, left, h, { f1(it.toDouble()) }, labelPaint(dens))
        val slot = (size.width - left) / days.size
        val bw = (slot * 0.7f).coerceAtLeast(1.5f)
        days.forEachIndexed { i, d ->
            val x = left + i * slot + (slot - bw) / 2
            var y = h
            val alpha = if (selected == null || selected == d.date) 1f else 0.35f
            listOf(d.carbs to C_CARBS, d.proteins to C_PROT, d.fat to C_FAT).forEach { (v, c) ->
                if (v != null && v > 0) {
                    val bh = (h * (v / maxV)).toFloat()
                    drawRect(c.copy(alpha = alpha), Offset(x, y - bh), Size(bw, bh)); y -= bh
                }
            }
        }
    }
    XLabels(days.map { it.date }, weeks)
}

@Composable
private fun WeeklyBars(weeks: List<WeekStat>, goal: Int) {
    val maxV = max(goal * 1.15f, (weeks.mapNotNull { it.avgKcal }.maxOrNull() ?: 0) * 1.05f).coerceAtLeast(100f)
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val left = 36.dp.toPx(); val h = size.height
        val paint = labelPaint(dens)
        yGrid(maxV, left, h, { f1(it.toDouble()) }, paint)
        val slot = (size.width - left) / weeks.size
        val bw = slot * 0.6f
        weeks.forEachIndexed { i, w ->
            val x = left + i * slot + (slot - bw) / 2
            w.avgKcal?.let { a ->
                val bh = h * (a / maxV)
                drawRoundRect(if (goal > 0 && a > goal) RED else GREEN, Offset(x, h - bh), Size(bw, bh), CornerRadius(4f, 4f))
                drawContext.canvas.nativeCanvas.drawText("$a", x, h - bh - 4f, paint)
            }
        }
        if (goal > 0) {
            val gy = h - h * (goal / maxV)
            drawLine(GOAL, Offset(left, gy), Offset(size.width, gy), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 36.dp)) {
        weeks.forEach { w ->
            Text("dès ${Stats.dayMonth(w.start)}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, softWrap = false)
        }
    }
}

/** Détail d'un jour : total vs objectif, macros, aliments. */
@Composable
private fun DayDetail(d: LocalDate, entries: List<Entry>, goal: Int, onClose: () -> Unit) {
    val list = entries.filter { it.date == d.toString() }.sortedBy { it.time }
    val total = list.sumOf { it.kcal }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Stats.longDate(d), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Fermer") }
            }
            if (list.isEmpty()) { Text("Aucun aliment noté ce jour-là.", color = Color.Gray); return@Column }
            val diff = total - goal
            Text("$total kcal / objectif $goal kcal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (goal > 0) Text(if (diff > 0) "Au-dessus de l'objectif de $diff kcal" else "Dans l'objectif (reste ${-diff} kcal)",
                color = if (diff > 0) RED else GREEN, style = MaterialTheme.typography.bodyMedium)
            val m = list.macros()
            if (list.any { it.hasMacros }) Text("Glucides ${f1(m.carbs)} g · Protéines ${f1(m.proteins)} g · Lipides ${f1(m.fat)} g",
                style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            list.forEach { e ->
                Row(Modifier.fillMaxWidth()) {
                    Text(e.time, Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text("${e.name} · ${if (e.grams % 1.0 == 0.0) e.grams.toInt() else e.grams} g", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${e.kcal} kcal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
