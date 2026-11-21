package com.wolnerman.calories

/** Aliment de référence : valeurs approximatives pour 100 g (kcal, glucides, protéines, lipides en g). */
data class Food(
    val name: String, val kcalPer100g: Int, val carbs: Double, val proteins: Double, val fat: Double,
    val fermented: Boolean = false,
    val prebiotic: Boolean = false,
) {
    /** Nutriments pour 100 g (clés de NUTRIENTS), vide si inconnus. */
    val nutrients: Map<String, Double> get() = BUILTIN_NUTRIENTS[name]?.let { arr ->
        NUTRIENTS.mapIndexed { i, n -> n.key to arr[i] }.toMap()
    } ?: emptyMap()
}

/** Petite base intégrée (valeurs approximatives, d'après les tables nutritionnelles usuelles type Ciqual). */
val BUILTIN_FOODS = listOf(
    Food("Riz blanc cuit", 130, 28.0, 2.7, 0.3),
    Food("Pâtes cuites", 150, 30.0, 5.5, 0.9),
    Food("Pain (baguette)", 270, 56.0, 9.0, 1.2),
    Food("Pain complet", 250, 44.0, 9.5, 3.4),
    Food("Pomme de terre cuite", 85, 19.0, 2.0, 0.1),
    Food("Frites", 300, 38.0, 3.5, 15.0),
    Food("Banane", 90, 20.0, 1.1, 0.3),
    Food("Pomme", 52, 12.0, 0.3, 0.2),
    Food("Orange", 47, 10.0, 0.9, 0.1),
    Food("Mangue", 60, 13.5, 0.8, 0.4),
    Food("Ananas", 50, 12.0, 0.5, 0.1),
    Food("Avocat", 160, 2.0, 2.0, 15.0),
    Food("Tomate", 18, 3.0, 0.9, 0.2),
    Food("Carotte", 41, 7.0, 0.9, 0.2),
    Food("Salade verte", 15, 1.5, 1.3, 0.2),
    Food("Haricots rouges cuits", 125, 18.0, 9.0, 0.5),
    Food("Lentilles cuites", 115, 17.0, 9.0, 0.4, prebiotic = true),
    Food("Blanc de poulet cuit", 165, 0.0, 31.0, 3.6),
    Food("Bœuf haché 5% cuit", 170, 0.0, 27.0, 7.0),
    Food("Saumon cuit", 200, 0.0, 22.0, 12.0),
    Food("Thon en conserve (eau)", 115, 0.0, 26.0, 1.0),
    Food("Poisson blanc cuit", 100, 0.0, 21.0, 1.5),
    Food("Œuf", 145, 0.7, 12.5, 10.0),
    Food("Lait demi-écrémé", 46, 4.8, 3.3, 1.6),
    Food("Yaourt nature", 60, 4.5, 4.0, 3.0, fermented = true),
    Food("Fromage (emmental)", 380, 0.5, 28.0, 29.0),
    Food("Beurre", 740, 0.6, 0.7, 82.0),
    Food("Huile d'olive", 900, 0.0, 0.0, 100.0),
    Food("Sucre", 400, 100.0, 0.0, 0.0),
    Food("Chocolat noir", 550, 33.0, 8.0, 42.0),
    Food("Amandes", 600, 8.0, 21.0, 53.0),
    Food("Flocons d'avoine", 370, 59.0, 13.5, 7.0, prebiotic = true),
    Food("Jus d'orange", 45, 10.0, 0.7, 0.2),
    Food("Soda sucré", 42, 10.6, 0.0, 0.0),
    // Aliments fermentés (cultures vivantes possibles si non pasteurisés)
    Food("Kéfir de lait", 55, 4.5, 3.3, 2.0, fermented = true),
    Food("Choucroute crue", 20, 3.0, 1.0, 0.1, fermented = true),
    Food("Kimchi", 25, 3.5, 1.5, 0.5, fermented = true),
    Food("Miso", 200, 26.0, 12.0, 6.0, fermented = true),
    Food("Fromage affiné au lait cru", 300, 0.5, 21.0, 24.0, fermented = true),
    Food("Tempeh", 190, 9.0, 19.0, 11.0, fermented = true),
    Food("Kombucha", 15, 3.5, 0.0, 0.0, fermented = true),
    // Aliments riches en prébiotiques (inuline, FOS, GOS, amidon résistant)
    Food("Ail", 150, 30.0, 6.4, 0.5, prebiotic = true),
    Food("Oignon", 40, 7.5, 1.1, 0.1, prebiotic = true),
    Food("Poireau", 30, 5.0, 1.5, 0.3, prebiotic = true),
    Food("Asperge", 20, 2.0, 2.2, 0.1, prebiotic = true),
    Food("Artichaut cuit", 50, 6.0, 2.9, 0.3, prebiotic = true),
    Food("Topinambour", 73, 13.0, 2.0, 0.0, prebiotic = true),
    Food("Banane peu mûre", 90, 20.0, 1.1, 0.3, prebiotic = true),
    Food("Pois chiches cuits", 140, 18.0, 8.0, 2.6, prebiotic = true),
    Food("Chicorée soluble (poudre)", 350, 75.0, 5.0, 0.5, prebiotic = true),
    // Antilles et compléments (v1.2)
    Food("Riz complet cuit", 123, 25.6, 2.7, 1.0),
    Food("Pâtes complètes cuites", 150, 27.0, 6.0, 1.0),
    Food("Patate douce cuite", 90, 20.0, 2.0, 0.2),
    Food("Igname cuite", 115, 27.0, 1.5, 0.1),
    Food("Banane plantain cuite", 120, 30.0, 1.0, 0.2),
    Food("Christophine cuite", 22, 4.0, 0.6, 0.5),
    Food("Fruit à pain cuit", 105, 25.0, 1.1, 0.2),
    Food("Dachine / madère cuite", 140, 33.0, 0.5, 0.1),
    Food("Morue dessalée cuite", 105, 0.0, 23.0, 0.9),
    Food("Accras de morue", 250, 20.0, 9.0, 15.0),
    Food("Poisson grillé", 130, 0.0, 24.0, 3.5),
    Food("Maïs doux", 90, 17.0, 3.0, 1.3),
    Food("Goyave", 68, 14.0, 2.6, 1.0),
    Food("Noix de coco (chair)", 354, 6.0, 3.3, 33.0),
    Food("Eau de coco", 19, 3.7, 0.7, 0.2),
    Food("Lait de coco", 200, 3.0, 2.0, 21.0),
    Food("Papaye", 43, 11.0, 0.5, 0.3),
    Food("Fruit de la passion (maracudja)", 97, 13.0, 2.2, 0.7),
    Food("Pois d'Angole cuits", 120, 15.0, 7.0, 0.4, prebiotic = true),
    Food("Colombo de poulet", 150, 4.0, 17.0, 7.5),
    Food("Boudin créole", 300, 10.0, 12.0, 24.0),
    Food("Cuisse de poulet cuite", 210, 0.0, 26.0, 11.0),
    Food("Courgette", 17, 2.0, 1.2, 0.3),
    Food("Concombre", 12, 2.0, 0.7, 0.1),
    Food("Citron", 29, 3.0, 1.1, 0.3),
    Food("Pastèque", 30, 7.5, 0.6, 0.2),
    Food("Lambi cuit", 130, 1.7, 26.0, 1.0),
    Food("Ouassous / crevettes cuites", 100, 0.0, 24.0, 0.3),
    Food("Pain au chocolat", 420, 45.0, 7.5, 23.0),
)

/**
 * Nutriments approximatifs pour 100 g, dans l'ordre de NUTRIENTS :
 * fibres g, sucres g, sel g, graisses saturées g, calcium mg, fer mg, vitamine C mg, vitamine D µg.
 */
private val BUILTIN_NUTRIENTS: Map<String, DoubleArray> = mapOf(
    "Riz blanc cuit" to doubleArrayOf(0.4, 0.1, 0.0, 0.1, 10.0, 0.2, 0.0, 0.0),
    "Pâtes cuites" to doubleArrayOf(1.8, 0.6, 0.0, 0.2, 7.0, 0.5, 0.0, 0.0),
    "Pain (baguette)" to doubleArrayOf(2.7, 2.5, 1.3, 0.3, 20.0, 1.0, 0.0, 0.0),
    "Pain complet" to doubleArrayOf(7.0, 3.0, 1.2, 0.7, 30.0, 2.0, 0.0, 0.0),
    "Pomme de terre cuite" to doubleArrayOf(1.8, 0.8, 0.0, 0.0, 6.0, 0.4, 10.0, 0.0),
    "Frites" to doubleArrayOf(3.0, 0.3, 0.5, 2.5, 12.0, 0.8, 5.0, 0.0),
    "Banane" to doubleArrayOf(2.6, 15.0, 0.0, 0.1, 5.0, 0.3, 9.0, 0.0),
    "Pomme" to doubleArrayOf(2.4, 10.0, 0.0, 0.0, 5.0, 0.1, 5.0, 0.0),
    "Orange" to doubleArrayOf(2.2, 9.0, 0.0, 0.0, 40.0, 0.1, 53.0, 0.0),
    "Mangue" to doubleArrayOf(1.6, 13.5, 0.0, 0.1, 11.0, 0.2, 36.0, 0.0),
    "Ananas" to doubleArrayOf(1.4, 10.0, 0.0, 0.0, 13.0, 0.3, 48.0, 0.0),
    "Avocat" to doubleArrayOf(6.7, 0.7, 0.0, 2.1, 12.0, 0.6, 10.0, 0.0),
    "Tomate" to doubleArrayOf(1.2, 2.6, 0.0, 0.0, 10.0, 0.3, 14.0, 0.0),
    "Carotte" to doubleArrayOf(2.8, 4.7, 0.1, 0.0, 33.0, 0.3, 6.0, 0.0),
    "Salade verte" to doubleArrayOf(1.3, 0.8, 0.0, 0.0, 36.0, 0.9, 9.0, 0.0),
    "Haricots rouges cuits" to doubleArrayOf(6.4, 0.3, 0.0, 0.1, 35.0, 2.9, 1.0, 0.0),
    "Lentilles cuites" to doubleArrayOf(7.9, 1.8, 0.0, 0.1, 19.0, 3.3, 1.5, 0.0),
    "Blanc de poulet cuit" to doubleArrayOf(0.0, 0.0, 0.2, 1.0, 15.0, 0.7, 0.0, 0.1),
    "Bœuf haché 5% cuit" to doubleArrayOf(0.0, 0.0, 0.2, 3.0, 15.0, 2.7, 0.0, 0.2),
    "Saumon cuit" to doubleArrayOf(0.0, 0.0, 0.15, 2.5, 15.0, 0.5, 0.0, 11.0),
    "Thon en conserve (eau)" to doubleArrayOf(0.0, 0.0, 0.8, 0.3, 15.0, 1.3, 0.0, 2.7),
    "Poisson blanc cuit" to doubleArrayOf(0.0, 0.0, 0.2, 0.3, 20.0, 0.3, 0.0, 1.0),
    "Œuf" to doubleArrayOf(0.0, 0.4, 0.35, 3.1, 55.0, 1.8, 0.0, 2.0),
    "Lait demi-écrémé" to doubleArrayOf(0.0, 4.8, 0.1, 1.0, 120.0, 0.0, 1.0, 0.0),
    "Yaourt nature" to doubleArrayOf(0.0, 4.5, 0.15, 2.0, 150.0, 0.1, 1.0, 0.0),
    "Fromage (emmental)" to doubleArrayOf(0.0, 0.0, 0.6, 18.0, 1000.0, 0.2, 0.0, 0.5),
    "Beurre" to doubleArrayOf(0.0, 0.6, 0.03, 54.0, 15.0, 0.0, 0.0, 1.2),
    "Huile d'olive" to doubleArrayOf(0.0, 0.0, 0.0, 14.0, 1.0, 0.4, 0.0, 0.0),
    "Sucre" to doubleArrayOf(0.0, 100.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0),
    "Chocolat noir" to doubleArrayOf(10.0, 28.0, 0.02, 25.0, 70.0, 11.0, 0.0, 0.0),
    "Amandes" to doubleArrayOf(12.0, 4.4, 0.0, 4.0, 270.0, 3.7, 0.0, 0.0),
    "Flocons d'avoine" to doubleArrayOf(10.0, 1.0, 0.01, 1.3, 55.0, 4.0, 0.0, 0.0),
    "Jus d'orange" to doubleArrayOf(0.2, 8.5, 0.0, 0.0, 11.0, 0.2, 40.0, 0.0),
    "Soda sucré" to doubleArrayOf(0.0, 10.6, 0.02, 0.0, 2.0, 0.0, 0.0, 0.0),
    "Kéfir de lait" to doubleArrayOf(0.0, 4.5, 0.1, 1.3, 120.0, 0.1, 1.0, 0.0),
    "Choucroute crue" to doubleArrayOf(2.9, 1.8, 1.5, 0.0, 30.0, 1.5, 15.0, 0.0),
    "Kimchi" to doubleArrayOf(2.4, 1.1, 1.5, 0.1, 33.0, 0.5, 18.0, 0.0),
    "Miso" to doubleArrayOf(5.4, 6.0, 11.0, 1.0, 57.0, 2.5, 0.0, 0.0),
    "Fromage affiné au lait cru" to doubleArrayOf(0.0, 0.5, 1.5, 15.0, 400.0, 0.3, 0.0, 0.3),
    "Tempeh" to doubleArrayOf(5.0, 0.5, 0.02, 2.2, 110.0, 2.7, 0.0, 0.0),
    "Kombucha" to doubleArrayOf(0.0, 3.0, 0.01, 0.0, 2.0, 0.0, 0.0, 0.0),
    "Ail" to doubleArrayOf(2.1, 1.0, 0.04, 0.1, 180.0, 1.7, 31.0, 0.0),
    "Oignon" to doubleArrayOf(1.7, 4.2, 0.0, 0.0, 23.0, 0.2, 7.0, 0.0),
    "Poireau" to doubleArrayOf(2.3, 3.0, 0.02, 0.0, 59.0, 1.0, 12.0, 0.0),
    "Asperge" to doubleArrayOf(2.1, 1.9, 0.0, 0.0, 24.0, 2.1, 6.0, 0.0),
    "Artichaut cuit" to doubleArrayOf(5.4, 1.0, 0.1, 0.0, 44.0, 0.6, 7.0, 0.0),
    "Topinambour" to doubleArrayOf(2.0, 9.6, 0.0, 0.0, 14.0, 3.4, 4.0, 0.0),
    "Banane peu mûre" to doubleArrayOf(3.1, 10.0, 0.0, 0.1, 5.0, 0.3, 9.0, 0.0),
    "Pois chiches cuits" to doubleArrayOf(7.6, 0.5, 0.0, 0.3, 49.0, 2.9, 1.0, 0.0),
    "Chicorée soluble (poudre)" to doubleArrayOf(10.0, 20.0, 0.1, 0.1, 100.0, 3.0, 0.0, 0.0),
    "Riz complet cuit" to doubleArrayOf(1.8, 0.4, 0.0, 0.2, 10.0, 0.5, 0.0, 0.0),
    "Pâtes complètes cuites" to doubleArrayOf(4.0, 0.8, 0.0, 0.2, 15.0, 1.2, 0.0, 0.0),
    "Patate douce cuite" to doubleArrayOf(3.3, 6.5, 0.1, 0.0, 38.0, 0.7, 20.0, 0.0),
    "Igname cuite" to doubleArrayOf(3.9, 0.5, 0.0, 0.0, 14.0, 0.5, 12.0, 0.0),
    "Banane plantain cuite" to doubleArrayOf(2.3, 13.0, 0.0, 0.1, 2.0, 0.6, 11.0, 0.0),
    "Christophine cuite" to doubleArrayOf(2.8, 1.9, 0.0, 0.1, 13.0, 0.2, 8.0, 0.0),
    "Fruit à pain cuit" to doubleArrayOf(4.9, 11.0, 0.0, 0.0, 17.0, 0.5, 20.0, 0.0),
    "Dachine / madère cuite" to doubleArrayOf(5.0, 0.5, 0.0, 0.0, 18.0, 0.7, 5.0, 0.0),
    "Morue dessalée cuite" to doubleArrayOf(0.0, 0.0, 0.6, 0.2, 15.0, 0.5, 0.0, 1.0),
    "Accras de morue" to doubleArrayOf(1.0, 1.0, 1.5, 2.0, 30.0, 1.0, 0.0, 0.5),
    "Poisson grillé" to doubleArrayOf(0.0, 0.0, 0.3, 0.8, 20.0, 0.5, 0.0, 3.0),
    "Maïs doux" to doubleArrayOf(2.4, 4.5, 0.0, 0.2, 2.0, 0.5, 6.0, 0.0),
    "Goyave" to doubleArrayOf(5.4, 9.0, 0.0, 0.3, 18.0, 0.3, 228.0, 0.0),
    "Noix de coco (chair)" to doubleArrayOf(9.0, 6.0, 0.0, 30.0, 14.0, 2.4, 3.0, 0.0),
    "Eau de coco" to doubleArrayOf(1.1, 2.6, 0.1, 0.2, 24.0, 0.3, 2.0, 0.0),
    "Lait de coco" to doubleArrayOf(0.0, 2.0, 0.0, 18.0, 15.0, 1.6, 1.0, 0.0),
    "Papaye" to doubleArrayOf(1.7, 7.8, 0.0, 0.1, 20.0, 0.3, 61.0, 0.0),
    "Fruit de la passion (maracudja)" to doubleArrayOf(10.0, 11.0, 0.0, 0.1, 12.0, 1.6, 30.0, 0.0),
    "Pois d'Angole cuits" to doubleArrayOf(6.7, 1.0, 0.0, 0.1, 43.0, 1.1, 0.0, 0.0),
    "Colombo de poulet" to doubleArrayOf(1.0, 1.5, 0.8, 2.0, 25.0, 1.2, 4.0, 0.1),
    "Boudin créole" to doubleArrayOf(0.5, 1.0, 1.8, 9.0, 20.0, 6.0, 0.0, 0.5),
    "Cuisse de poulet cuite" to doubleArrayOf(0.0, 0.0, 0.25, 3.0, 12.0, 1.1, 0.0, 0.2),
    "Courgette" to doubleArrayOf(1.1, 1.7, 0.0, 0.1, 16.0, 0.4, 18.0, 0.0),
    "Concombre" to doubleArrayOf(0.6, 1.4, 0.0, 0.0, 16.0, 0.3, 3.0, 0.0),
    "Citron" to doubleArrayOf(2.8, 2.5, 0.0, 0.0, 26.0, 0.6, 53.0, 0.0),
    "Pastèque" to doubleArrayOf(0.4, 6.0, 0.0, 0.0, 7.0, 0.2, 8.0, 0.0),
    "Lambi cuit" to doubleArrayOf(0.0, 0.0, 0.5, 0.3, 98.0, 1.5, 0.0, 0.0),
    "Ouassous / crevettes cuites" to doubleArrayOf(0.0, 0.0, 0.6, 0.1, 70.0, 0.5, 0.0, 0.0),
    "Pain au chocolat" to doubleArrayOf(2.5, 12.0, 0.8, 13.0, 50.0, 1.3, 0.0, 0.0),
)

/** Définition d'un nutriment suivi. isLimit = à ne pas dépasser (sucres, sel, graisses saturées). */
data class Nutrient(val key: String, val label: String, val unit: String, val isLimit: Boolean = false, val short: String = label)

val NUTRIENTS = listOf(
    Nutrient("fiber", "Fibres", "g"),
    Nutrient("sugars", "Sucres", "g", isLimit = true),
    Nutrient("salt", "Sel", "g", isLimit = true),
    Nutrient("satfat", "Graisses saturées", "g", isLimit = true, short = "AG saturés"),
    Nutrient("calcium", "Calcium", "mg"),
    Nutrient("iron", "Fer", "mg"),
    Nutrient("vitc", "Vitamine C", "mg", short = "Vit. C"),
    Nutrient("vitd", "Vitamine D", "µg", short = "Vit. D"),
)

/** Repères journaliers adultes (indicatifs). Limites à 10 % des kcal calculées sur l'objectif. */
fun referenceIntake(key: String, male: Boolean?, goalKcal: Int): Double = when (key) {
    "fiber" -> 30.0
    "sugars" -> goalKcal * 0.10 / 4.0
    "salt" -> 5.0
    "satfat" -> goalKcal * 0.10 / 9.0
    "calcium" -> 950.0
    "iron" -> if (male == true) 11.0 else 16.0
    "vitc" -> 110.0
    "vitd" -> 15.0
    else -> 0.0
}
