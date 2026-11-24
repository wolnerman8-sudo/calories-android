package com.wolnerman.calories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** v2.8 : carte « Mes aliments » (Profil) : aliments hors liste mémorisés, modifiables et supprimables. */
@Composable
fun MyFoodsCard() {
    val items = MyFoods.items
    var editing by remember { mutableStateOf<CustomFood?>(null) }
    var deleting by remember { mutableStateOf<CustomFood?>(null) }
    var showAll by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mes aliments", style = MaterialTheme.typography.titleMedium)
            Text("Les aliments hors liste que tu ajoutes (nom tapé + poids) sont mémorisés ici et proposés en premier (⭐) dans la recherche.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (items.isEmpty()) Text("Aucun pour l'instant.", color = Color.Gray)
            (if (showAll) items else items.take(8)).forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold)
                        Text(foodLine(MyFoodsLogic.toFood(c)), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    IconButton(onClick = { editing = c }) { Icon(Icons.Filled.Edit, contentDescription = "Modifier") }
                    IconButton(onClick = { deleting = c }) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer") }
                }
            }
            if (items.size > 8) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Afficher moins" else "Tout afficher (${items.size})") }
            OutlinedButton(onClick = { editing = CustomFood("") }) { Text("+ Ajouter un aliment") }
        }
    }

    editing?.let { c -> MyFoodDialog(c, onDismiss = { editing = null }) { new ->
        if (c.name.isBlank()) MyFoods.remember(new) else MyFoods.edit(c.name, new); editing = null } }
    deleting?.let { c ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Supprimer ?") },
            text = { Text("« ${c.name} » ne sera plus proposé. Les repas déjà enregistrés ne changent pas.") },
            confirmButton = { TextButton(onClick = { MyFoods.delete(c.name); deleting = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } })
    }
}

@Composable
private fun MyFoodDialog(c: CustomFood, onDismiss: () -> Unit, onSave: (CustomFood) -> Unit) {
    fun t(d: Double?) = d?.let { fmt(it) } ?: ""
    var name by remember { mutableStateOf(c.name) }
    var kcal by remember { mutableStateOf(t(c.kcal100)) }
    var carbs by remember { mutableStateOf(t(c.carbs)) }
    var prot by remember { mutableStateOf(t(c.proteins)) }
    var fat by remember { mutableStateOf(t(c.fat)) }
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (c.name.isBlank()) "Nouvel aliment" else "Modifier l'aliment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(kcal, { kcal = it.take(8) }, label = { Text("kcal / 100 g (facultatif)") }, singleLine = true, keyboardOptions = dec)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(carbs, { carbs = it.take(6) }, label = { Text("G") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
                    OutlinedTextField(prot, { prot = it.take(6) }, label = { Text("P") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
                    OutlinedTextField(fat, { fat = it.take(6) }, label = { Text("L") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
                }
                val derived = if (kcal.isBlank()) KcalLogic.fromMacros(parseNum(carbs), parseNum(prot), parseNum(fat)) else null
                derived?.let { Text("≈ ${fmt(it)} kcal/100 g d'après G/P/L", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32)) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val c2 = parseNum(carbs)?.takeIf { it >= 0 }; val p2 = parseNum(prot)?.takeIf { it >= 0 }; val f2 = parseNum(fat)?.takeIf { it >= 0 }
                val k = parseNum(kcal)?.takeIf { it in 0.0..1000.0 } ?: KcalLogic.fromMacros(c2, p2, f2)
                onSave(CustomFood(name.trim(), k, c2, p2, f2, System.currentTimeMillis()))
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}
