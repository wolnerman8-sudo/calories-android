package com.wolnerman.calories

/**
 * Lecture tolérante d'un nombre : « 150 », « 150.0 », « 150,5 », « 150 g », « ~150 », « 1 200 kcal », « 1 200,5 ».
 * La virgule est décimale (usage français). Renvoie null s'il n'y a aucun chiffre.
 */
fun parseLooseNumber(s: String?): Double? {
    if (s == null) return null
    // espaces (normales, insécables, fines) entre chiffres = séparateurs de milliers
    val t = s.replace(Regex("(?<=\\d)[\\s\\u00A0\\u202F]+(?=\\d)"), "")
    val m = Regex("-?\\d+(?:[.,]\\d+)?").find(t) ?: return null
    return m.value.replace(',', '.').toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
}

/** Valeurs à mettre dans le formulaire « Ajouter un aliment » après le choix d'une suggestion photo. */
data class FormFill(
    val name: String,
    val grams: String,
    val kcal100: String,          // "" si inconnues
    val carbs100: String,
    val prot100: String,
    val fat100: String,
    val food: Food?,              // aliment de la base intégrée (macros, nutriments), si reconnu
    val gramsSource: String,      // « balance », « estimation » ou « par défaut »
) {
    val kcalMissing: Boolean get() = kcal100.isBlank()
}

private fun fmtNum(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else String.format(java.util.Locale.US, "%.1f", d)

/**
 * Remplissage du formulaire depuis une suggestion. Quantité : poids stable de la balance > estimation > 100 g.
 * kcal : base intégrée si l'aliment y figure, sinon la valeur proposée (arrondie), sinon vide.
 */
fun fillFromGuess(g: FoodGuess, scaleStableGrams: Double?): FormFill {
    val (grams, src) = when {
        scaleStableGrams != null && scaleStableGrams > 0 -> fmtNum(Math.round(scaleStableGrams * 10.0) / 10.0) to "balance"
        g.grams != null && g.grams > 0 -> fmtNum(Math.round(g.grams).toDouble()) to "estimation"
        else -> "100" to "par défaut"
    }
    val f = g.food
    return if (f != null) FormFill(g.name, grams, f.kcalPer100g.toString(), f.carbs?.let { fmtNum(it) } ?: "", f.proteins?.let { fmtNum(it) } ?: "", f.fat?.let { fmtNum(it) } ?: "", f, src)
    else FormFill(g.name, grams, g.kcal100?.takeIf { it >= 0 }?.let { fmtNum(Math.round(it).toDouble()) } ?: "", "", "", "", null, src)
}

/** Raison (en rouge) pour laquelle « Ajouter » ne peut pas ajouter ; null si tout est bon. Couvre tous les cas. */
fun addProblem(name: String, grams: String, kcal100: String): String? {
    val g = parseNum(grams)
    val k = parseNum(kcal100)
    return when {
        name.isBlank() -> "Saisis ou choisis un aliment."
        kcal100.isBlank() -> kcalMissingMessage(name)
        k == null -> "kcal/100 g illisibles : tape un nombre (ex. 250)."
        k < 0 -> "Les kcal/100 g ne peuvent pas être négatives."
        k > 1000 -> "kcal/100 g trop élevées (max 900 environ) : vérifie la valeur."
        grams.isBlank() || g == null -> "Indique la quantité en grammes."
        g <= 0 -> "La quantité doit être supérieure à 0 g."
        g > 10000 -> "Quantité trop grande (max 10 kg)."
        else -> null
    }
}

/** Message rouge affiché sous « Ajouter » quand les calories sont inconnues. */
fun kcalMissingMessage(name: String): String =
    "Calories inconnues pour « ${name.trim()} » : choisis un aliment de la liste ou saisis les kcal pour 100 g."
