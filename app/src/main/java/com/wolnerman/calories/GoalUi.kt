package com.wolnerman.calories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Sélecteur d'objectif : Maintien / Perte / Prise (et Personnalisé s'il est déjà le mode),
 * plus puces 300 / 400 / 500 kcal pour le déficit ou le surplus.
 */
@Composable
fun GoalSelector(outcome: GoalOutcome, onChange: (GoalPlan) -> Unit) {
    val plan = outcome.plan
    val modes = buildList {
        add(EnergyTarget.MAINTAIN); add(EnergyTarget.LOSS); add(EnergyTarget.GAIN)
        if (plan.target == EnergyTarget.CUSTOM) add(EnergyTarget.CUSTOM)
    }
    Text("Ton objectif", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        modes.forEach { t ->
            FilterChip(
                selected = plan.target == t,
                onClick = { onChange(plan.copy(target = t, customKcal = if (t == EnergyTarget.CUSTOM) outcome.kcal.takeIf { it > 0 } else plan.customKcal)) },
                label = { Text(t.shortTitle, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (plan.target == EnergyTarget.LOSS || plan.target == EnergyTarget.GAIN) {
        val sign = if (plan.target == EnergyTarget.LOSS) "−" else "+"
        Text("Ajustement ($sign kcal / jour)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        var customOn by remember { mutableStateOf(plan.delta !in EnergyLogic.DELTAS) }
        var customTxt by remember { mutableStateOf(if (plan.delta !in EnergyLogic.DELTAS) plan.delta.toString() else "") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EnergyLogic.DELTAS.forEach { d ->
                FilterChip(
                    selected = !customOn && plan.delta == d,
                    onClick = { customOn = false; onChange(plan.copy(delta = d)) },
                    label = { Text("$sign$d") }
                )
            }
            FilterChip(selected = customOn, onClick = { customOn = true }, label = { Text("Autre") })
        }
        if (customOn) {
            OutlinedTextField(
                customTxt,
                { v ->
                    customTxt = v.filter(Char::isDigit).take(4)
                    customTxt.toIntOrNull()?.takeIf { it > 0 }?.let { onChange(plan.copy(delta = it)) }
                },
                label = { Text("kcal / jour (100 à 1000)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    if (outcome.kcal > 0) {
        Text(EnergyLogic.goalLine(outcome), fontWeight = FontWeight.SemiBold)
    } else if (plan.target != EnergyTarget.CUSTOM) {
        Text("Remplis ton profil pour calculer l'objectif.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
    if (outcome.clamped && outcome.floor != null) {
        Text(EnergyLogic.lossFloorNote(outcome.floor, outcome.energy?.bmr),
            style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
    }
}

@Composable
fun GoalDialog(outcome: GoalOutcome, male: Boolean?, onChange: (GoalPlan) -> Unit, onDismiss: () -> Unit) {
    var plan by remember { mutableStateOf(outcome.plan) }
    var customTxt by remember { mutableStateOf((outcome.plan.customKcal ?: outcome.kcal).takeIf { it > 0 }?.toString() ?: "") }
    fun previewOf(p: GoalPlan): GoalOutcome {
        val withCustom = if (p.target == EnergyTarget.CUSTOM) p.copy(customKcal = customTxt.toIntOrNull()?.takeIf { it > 0 } ?: p.customKcal) else p
        return EnergyLogic.plan(outcome.energy, withCustom, male)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Objectif") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                outcome.tdee?.let { Text("Maintien (TDEE) : $it kcal / jour", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(EnergyTarget.MAINTAIN, EnergyTarget.LOSS, EnergyTarget.GAIN, EnergyTarget.CUSTOM).forEach { t ->
                        FilterChip(
                            selected = plan.target == t,
                            onClick = { plan = plan.copy(target = t) },
                            label = { Text(t.shortTitle, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (plan.target == EnergyTarget.LOSS || plan.target == EnergyTarget.GAIN) {
                    val sign = if (plan.target == EnergyTarget.LOSS) "−" else "+"
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EnergyLogic.DELTAS.forEach { d ->
                            FilterChip(selected = plan.delta == d, onClick = { plan = plan.copy(delta = d) }, label = { Text("$sign$d") })
                        }
                    }
                }
                if (plan.target == EnergyTarget.CUSTOM) {
                    OutlinedTextField(customTxt, { customTxt = it.filter(Char::isDigit).take(5) },
                        label = { Text("kcal par jour") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth())
                }
                val preview = previewOf(plan)
                if (preview.kcal > 0) Text(EnergyLogic.goalLine(preview), fontWeight = FontWeight.SemiBold)
                if (preview.clamped && preview.floor != null)
                    Text(EnergyLogic.lossFloorNote(preview.floor, preview.energy?.bmr),
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = previewOf(plan).plan.let { if (plan.target == EnergyTarget.CUSTOM) it.copy(customKcal = customTxt.toIntOrNull()?.takeIf { n -> n > 0 } ?: it.customKcal) else it }
                if (p.target != EnergyTarget.CUSTOM || (p.customKcal != null && p.customKcal!! > 0)) onChange(p)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
