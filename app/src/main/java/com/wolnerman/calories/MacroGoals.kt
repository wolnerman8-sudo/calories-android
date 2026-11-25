package com.wolnerman.calories

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Repères de macros dérivés de l'objectif kcal effectif (Maintien / Perte / Prise / Perso).
 *
 * - Protéines : même règle qu'en v2.7, poids × 1,4 g/kg (min 50 g ; 96 g si le poids est inconnu).
 * - Lipides : 30 % des kcal de l'objectif / 9, avec un plancher de 0,8 g/kg s'il y a un poids
 *   (apport lipidique minimal courant, pas une prescription médicale).
 * - Glucides : le reste des kcal / 4, jamais en dessous de 0 (si protéines + lipides dépassent
 *   déjà l'objectif, par ex. un déficit très bas avec le plancher lipidique).
 *
 * Les grammes sont arrondis à l'entier le plus proche ; le total G×4 + P×4 + L×9 peut donc
 * différer de 1 ou 2 kcal de l'objectif. Pas de personnalisation pour l'instant.
 */
data class MacroTargets(val carbsG: Int, val proteinG: Int, val fatG: Int, val kcal: Int)

object MacroGoalLogic {
    const val FAT_PCT = 0.30
    const val FAT_MIN_G_PER_KG = 0.8

    fun targets(kcal: Int, weightKg: Double?): MacroTargets {
        val protein = ProteinLogic.proteinGoal(weightKg)
        if (kcal <= 0) return MacroTargets(0, protein, 0, kcal)
        val fatMin = if (weightKg != null && weightKg > 0) weightKg * FAT_MIN_G_PER_KG else 0.0
        val fat = max(kcal * FAT_PCT / 9.0, fatMin).roundToInt().coerceAtLeast(0)
        val remain = kcal - protein * 4 - fat * 9
        val carbs = (remain / 4.0).roundToInt().coerceAtLeast(0)
        return MacroTargets(carbs, protein, fat, kcal)
    }

    fun remainingG(consumed: Double, target: Int): Int =
        if (target <= 0) 0 else (target - consumed.roundToInt()).coerceAtLeast(0)

    fun reached(consumed: Double, target: Int): Boolean = target <= 0 || consumed.roundToInt() >= target
}

private val GREEN = Color(0xFF2E7D32)
private val ORANGE = Color(0xFFEF6C00)

/** Trois petites barres G / P / L par rapport aux repères du jour (Aujourd'hui). */
@Composable
fun MacroGoalsProgress(consumed: Macros, targets: MacroTargets) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MacroTargetBar("Glucides", consumed.carbs, targets.carbsG)
        MacroTargetBar("Protéines", consumed.proteins, targets.proteinG)
        MacroTargetBar("Lipides", consumed.fat, targets.fatG)
        Text("Repères : protéines = poids × 1,4 g (min 50 g) ; lipides ≈ 30 % des kcal (min 0,8 g/kg) ; glucides = le reste.",
            style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
private fun MacroTargetBar(label: String, consumed: Double, target: Int) {
    if (target <= 0 && consumed <= 0.0) return
    val col = if (target > 0 && ProteinLogic.onTrack(consumed, target)) GREEN else ORANGE
    val shown = if (target > 0) "$label : ${consumed.roundToInt()} g / $target g"
    else "$label : ${consumed.roundToInt()} g"
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(shown, fontWeight = FontWeight.SemiBold, color = col, style = MaterialTheme.typography.bodyMedium)
        if (target > 0) LinearProgressIndicator(
            progress = { ProteinLogic.progress(consumed, target) },
            modifier = Modifier.fillMaxWidth().height(8.dp), color = col)
    }
}
