package com.wolnerman.calories

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** D'où vient la valeur de « Quantité g » (pour ne jamais écraser une saisie ou la balance sans action explicite). */
enum class GramsSource { DEFAULT, USER, SCALE, CHIP, PHOTO }

/**
 * État du haut du formulaire « Ajouter un aliment » (v2.4), logique pure et testable.
 * [name] est un TextFieldValue : le texte, le curseur ET la zone de composition du clavier (Samsung, Gboard…)
 * sont conservés tels que le clavier les envoie ; seul un choix explicite dans la liste remplace le texte.
 */
data class FoodForm(
    val name: TextFieldValue = TextFieldValue(""),
    val grams: String = "100",
    val gramsBy: GramsSource = GramsSource.DEFAULT,
    val kcal100: String = "",
    val carbs100: String = "",
    val prot100: String = "",
    val fat100: String = "",
    val autoFrom: String? = null,      // aliment de la liste dont viennent kcal/macros (null = saisie libre / scan)
    val picked: Boolean = false,       // choisi explicitement dans la liste
    val pendingQty: QtyParse? = null,  // quantité tapée avant un choix dans la liste (« 2 œufs » → Œuf dur)
) {
    val text: String get() = name.text
}

object FoodFormLogic {
    private fun num(d: Double?): String = d?.let { fmt(it) } ?: ""

    /** Copie kcal et macros d'un aliment de la liste (ne touche ni au nom ni à la quantité). */
    fun fill(s: FoodForm, f: Food) = s.copy(kcal100 = if (f.kcalPer100g >= 0) f.kcalPer100g.toString() else "", carbs100 = num(f.carbs),
        prot100 = num(f.proteins), fat100 = num(f.fat), autoFrom = f.name)

    /** Retire les valeurs venues de la liste (pas celles tapées à la main). */
    fun unfill(s: FoodForm) = if (s.autoFrom == null) s
        else s.copy(kcal100 = "", carbs100 = "", prot100 = "", fat100 = "", autoFrom = null, picked = false)

    /**
     * Frappe dans « Aliment » : le TextFieldValue du clavier est gardé tel quel (jamais réécrit).
     * Si le texte correspond à un aliment, seules kcal/macros sont remplies. La quantité n'est JAMAIS modifiée.
     * [locked] = valeurs venant d'un scan ou tapées à la main : on n'y touche pas.
     */
    fun type(s: FoodForm, v: TextFieldValue, locked: Boolean, foods: List<Food> = FoodCatalog.all()): FoodForm {
        if (v.text == s.name.text) return s.copy(name = v)            // simple déplacement du curseur / composition
        var n = s.copy(name = v, picked = false, pendingQty = null)
        val manual = s.autoFrom == null && (s.kcal100.isNotBlank() || locked)
        if (manual) return n
        val m = findFood(v.text, foods)
        n = if (m != null) { if (m.name != n.autoFrom) fill(n, m) else n } else unfill(n)
        return n
    }

    /** Choix explicite dans la liste : remplace TOUT le texte par le nom, curseur à la fin, sans composition. */
    fun pick(s: FoodForm, f: Food): FoodForm {
        val typedQty = parseQuantity(s.text).takeIf { it.hasQuantity } ?: s.pendingQty
        return fill(s, f).copy(name = TextFieldValue(f.name, TextRange(f.name.length)), picked = true, pendingQty = typedQty)
    }

    /** Remplace le nom par programme (scan, photo) : même règle que pick, sans toucher aux valeurs. */
    fun setName(s: FoodForm, text: String) = s.copy(name = TextFieldValue(text, TextRange(text.length)), pendingQty = null)

    /** Bouton ✕ : vide le nom et les valeurs venues de la liste ; garde la quantité (tapée ou balance). */
    fun clear(s: FoodForm) = unfill(s).copy(name = TextFieldValue(""), picked = false, pendingQty = null)

    fun typeGrams(s: FoodForm, txt: String) = s.copy(grams = txt, gramsBy = GramsSource.USER)
    fun scale(s: FoodForm, g: Double) = s.copy(grams = fmt(Math.round(g * 10.0) / 10.0), gramsBy = GramsSource.SCALE)

    /** Proposition facultative « Utiliser 2 × 50 g = 100 g » ; null si rien à proposer ou déjà appliqué. */
    fun chip(s: FoodForm, foods: List<Food> = FoodCatalog.all()): QtyResult? {
        val q = parseQuantity(s.text).takeIf { it.hasQuantity } ?: s.pendingQty ?: return null
        val food = s.autoFrom?.let { foodByName(it, foods) }
        val r = quantityFor(q, food) ?: return null
        return if (parseNum(s.grams)?.let { kotlin.math.abs(it - r.grams) < 0.05 } == true) null else r
    }

    /** Appui sur la proposition : seule façon pour un nombre du nom de changer « Quantité g ». */
    fun applyChip(s: FoodForm, foods: List<Food> = FoodCatalog.all()): FoodForm {
        val r = chip(s, foods) ?: return s
        return s.copy(grams = fmt(Math.round(r.grams * 10.0) / 10.0), gramsBy = GramsSource.CHIP, pendingQty = null)
    }

    /**
     * Nom enregistré (v2.8) : l'aliment choisi explicitement dans la liste ; sinon LE NOM TAPÉ (sans la quantité
     * en tête, « 2 Œuf » → « Œuf »), même si des valeurs ont été reprises automatiquement d'un aliment proche.
     */
    fun entryName(s: FoodForm): String {
        if (s.picked && s.autoFrom != null) return s.autoFrom
        val typed = stripLeadingQuantity(s.text)
        if (typed.isBlank()) return s.autoFrom ?: s.text.trim()
        // même nom que l'aliment trouvé (aux accents / majuscules près) : on garde l'écriture de la liste
        return s.autoFrom?.takeIf { fold(it) == fold(typed) } ?: typed
    }

    /** « 2 Œuf » → « Œuf », « 200 g de riz » → « riz » ; le texte du champ, lui, n'est jamais modifié. */
    fun stripLeadingQuantity(t: String): String =
        t.trim().replace(Regex("""^\d+(?:[.,]\d+)?\s*(?:(?:g|kg|gr|grammes?|ml|cl|l)\b\.?|[x×])?\s*(?:(?:de|du|des)\s+|d['’]\s*)?""", RegexOption.IGNORE_CASE), "").trim()
            .ifEmpty { t.trim() }
}

/**
 * v2.8 : remplissage automatique de « Quantité g » par la balance de cuisine, quel que soit le champ Aliment.
 * Une pesée stable remplit la quantité dès que c'est une NOUVELLE pesée (écart ≥ 0,5 g avec la dernière appliquée,
 * ou plateau revenu à vide entre-temps). Une quantité tapée à la main n'est pas écrasée tant que le poids ne change pas,
 * et l'article déjà ajouté resté sur le plateau ne re-remplit pas le formulaire suivant.
 */
class ScaleAutoFill {
    var lastApplied: Double? = null
        private set

    /** [grams] = poids lu (stable ou non). Renvoie le poids à mettre dans « Quantité g », ou null. */
    fun onReading(grams: Double?, stable: Boolean): Double? {
        if (grams == null) return null
        if (grams < 0.5) { lastApplied = null; return null }        // plateau vide : la prochaine pesée sera appliquée
        if (!stable) return null
        val last = lastApplied
        if (last != null && kotlin.math.abs(grams - last) < 0.5) return null
        lastApplied = grams
        return grams
    }
}

object KcalLogic {
    /** kcal/100 g estimées depuis G/P/L (4 / 4 / 9 kcal par g) si au moins une valeur est connue, sinon null. */
    fun fromMacros(carbs: Double?, proteins: Double?, fat: Double?): Double? {
        if (carbs == null && proteins == null && fat == null) return null
        val k = (carbs ?: 0.0) * 4 + (proteins ?: 0.0) * 4 + (fat ?: 0.0) * 9
        return Math.round(k * 10.0) / 10.0
    }

    /** Ajout « poids seul » possible : nom et quantité valides, seules les kcal manquent. */
    fun canAddWeightOnly(name: String, grams: String, kcal100: String): Boolean {
        val g = parseNum(grams)
        return name.isNotBlank() && kcal100.isBlank() && g != null && g > 0 && g <= 10000
    }
}
