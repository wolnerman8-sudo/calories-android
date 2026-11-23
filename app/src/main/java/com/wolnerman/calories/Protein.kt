package com.wolnerman.calories

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** v2.7 : objectif de protéines, répartition conseillée et doses de whey (logique pure, testable). */
object ProteinLogic {
    const val DEFAULT_GOAL = 96
    const val ON_TRACK = 0.85

    /** Objectif quotidien : poids × 1,4 g/kg (au moins 50 g) ; 96 g si le poids est inconnu. */
    fun proteinGoal(weightKg: Double?, coefficient: Double = 1.4): Int =
        if (weightKg == null || weightKg <= 0) DEFAULT_GOAL else (weightKg * coefficient).toInt().coerceAtLeast(50)

    /** Poids courant : celui du profil, sinon la dernière pesée enregistrée. */
    fun currentWeight(profileKg: Double?, lastWeighInKg: Double?): Double? = profileKg ?: lastWeighInKg

    fun progress(todayG: Double, goal: Int): Float = if (goal <= 0) 0f else (todayG / goal).toFloat().coerceIn(0f, 1f)
    fun onTrack(todayG: Double, goal: Int): Boolean = goal > 0 && todayG >= goal * ON_TRACK

    /** Répartition conseillée (g de protéines par repas). */
    val SLOTS = listOf("Petit-déjeuner" to "20-25 g", "Déjeuner" to "25-35 g", "Collation" to "10-15 g", "Dîner" to "25-35 g")

    /**
     * Hypothèse whey (concentré standard, ≈ 80 % de protéines) pour 100 g de poudre :
     * 400 kcal, protéines 80 g, glucides 6,7 g, lipides 5 g. Une dose de 24 g de protéines = 30 g de poudre
     * ≈ 120 kcal (G 2 g · L 1,5 g) ; 25 g de protéines = 31,25 g de poudre ≈ 125 kcal.
     */
    const val WHEY_KCAL_100 = 400.0
    const val WHEY_PROT_100 = 80.0
    const val WHEY_CARBS_100 = 6.7
    const val WHEY_FAT_100 = 5.0
    const val WHEY_NAME = "Whey (1 dose)"

    fun wheyEntry(proteinG: Int, id: Long, date: String, time: String): Entry {
        val powder = Math.round(proteinG / (WHEY_PROT_100 / 100.0) * 100.0) / 100.0
        return Entry(id, date, time, WHEY_NAME, powder, WHEY_KCAL_100, null,
            carbs100 = WHEY_CARBS_100, proteins100 = WHEY_PROT_100, fat100 = WHEY_FAT_100)
    }
}

private val GREEN = Color(0xFF2E7D32)
private val ORANGE = Color(0xFFEF6C00)

/** Ligne « Protéines : X g / Y g » + barre (dans la carte du total du jour). */
@Composable
fun ProteinProgress(todayG: Double, goal: Int) {
    val col = if (ProteinLogic.onTrack(todayG, goal)) GREEN else ORANGE
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Protéines : ${todayG.roundToInt()} g / $goal g", fontWeight = FontWeight.SemiBold, color = col)
        LinearProgressIndicator(progress = { ProteinLogic.progress(todayG, goal) },
            modifier = Modifier.fillMaxWidth().height(8.dp), color = col)
    }
}

/** Carte « Répartition conseillée des protéines » + boutons rapides whey. */
@Composable
fun ProteinDistributionHint(goal: Int, onAddWhey: (Int) -> Unit) {
    var added by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Répartition conseillée des protéines", style = MaterialTheme.typography.titleMedium)
            Text("Pour environ $goal g par jour (objectif actuel selon votre poids)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            ProteinLogic.SLOTS.forEach { (meal, g) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(meal); Text(g, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("Cette répartition aide à mieux préserver votre masse musculaire. Les repères s'adaptent si votre poids change.",
                style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 25).forEach { p ->
                    OutlinedButton(onClick = {
                        onAddWhey(p)
                        val e = ProteinLogic.wheyEntry(p, 0, "", "")
                        added = "✓ Whey ajoutée : $p g de protéines (${e.kcal} kcal). Pour annuler, supprime-la dans « Repas du jour »."
                    }, modifier = Modifier.weight(1f)) { Text("+ Whey $p g") }
                }
            }
            added?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = GREEN)
                LaunchedEffect(it) { kotlinx.coroutines.delay(6000); added = null }
            }
        }
    }
}

