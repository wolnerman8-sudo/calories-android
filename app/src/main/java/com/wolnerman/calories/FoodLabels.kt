package com.wolnerman.calories

/**
 * Traduction des étiquettes anglaises du modèle hors-ligne (Google AIY Food V1, 2023 plats)
 * vers un nom français + kcal approximatives pour 100 g.
 * Si le nom français correspond exactement à un aliment intégré, ses valeurs (et macros) sont utilisées.
 */
object FoodLabels {
    /** Étiquette anglaise exacte → (nom français, kcal/100 g). */
    val TABLE: Map<String, Pair<String, Int>> = mapOf(
        "White rice" to ("Riz blanc cuit" to 130),
        "Coconut rice" to ("Riz au coco" to 180),
        "Fried rice" to ("Riz frit" to 175),
        "American fried rice" to ("Riz frit" to 175),
        "Thai fried rice" to ("Riz frit thaï" to 170),
        "Kimchi fried rice" to ("Riz frit au kimchi" to 165),
        "Yangzhou fried rice" to ("Riz cantonais" to 170),
        "Hokkien fried rice" to ("Riz frit" to 175),
        "Red beans and rice" to ("Riz aux haricots rouges" to 140),
        "Dirty rice" to ("Riz créole à la viande" to 170),
        "Spanish rice" to ("Riz à l'espagnole" to 140),
        "Jollof rice" to ("Riz jollof" to 150),
        "Hainanese chicken rice" to ("Riz au poulet hainanais" to 170),
        "Duck rice" to ("Riz au canard" to 180),
        "Paella" to ("Paella" to 160),
        "Risotto" to ("Risotto" to 165),
        "Sushi" to ("Sushi" to 150),
        "Poke" to ("Poke bowl" to 150),
        "Bibimbap" to ("Bibimbap" to 130),
        "Onigiri" to ("Onigiri" to 170),
        "Bento" to ("Bento" to 160),
        "Omurice" to ("Omurice (omelette au riz)" to 160),
        "Couscous" to ("Couscous" to 150),
        "Tajine" to ("Tajine" to 130),
        "Spaghetti" to ("Spaghetti" to 160),
        "Spaghetti aglio e olio" to ("Spaghetti ail et huile" to 200),
        "Spaghetti alle vongole" to ("Spaghetti aux palourdes" to 160),
        "Carbonara" to ("Pâtes carbonara" to 210),
        "Lasagne" to ("Lasagnes" to 150),
        "Macaroni and cheese" to ("Macaroni au fromage" to 165),
        "Pasta salad" to ("Salade de pâtes" to 160),
        "Macaroni salad" to ("Salade de macaronis" to 200),
        "Pad thai" to ("Pad thaï" to 170),
        "Ramen" to ("Ramen" to 90),
        "Pho" to ("Phở (soupe de nouilles)" to 60),
        "Beef noodle soup" to ("Soupe de nouilles au bœuf" to 65),
        "Dumpling" to ("Raviolis chinois" to 220),
        "Dim sum" to ("Dim sum" to 220),
        "Pepperoni" to ("Pizza pepperoni" to 290),
        "Neapolitan pizza" to ("Pizza napolitaine" to 250),
        "New York-style pizza" to ("Pizza" to 265),
        "Chicago-style pizza" to ("Pizza épaisse (Chicago)" to 280),
        "Detroit-style pizza" to ("Pizza épaisse (Detroit)" to 280),
        "California-style pizza" to ("Pizza" to 250),
        "Hamburger" to ("Hamburger" to 250),
        "Cheeseburger" to ("Cheeseburger" to 265),
        "Hot dog" to ("Hot-dog" to 260),
        "Sandwich" to ("Sandwich" to 250),
        "Club sandwich" to ("Club sandwich" to 240),
        "Chicken sandwich" to ("Sandwich au poulet" to 240),
        "Tuna fish sandwich" to ("Sandwich au thon" to 230),
        "Steak sandwich" to ("Sandwich au steak" to 250),
        "Fried chicken" to ("Poulet frit" to 260),
        "Chicken nugget" to ("Nuggets de poulet" to 290),
        "Chicken fingers" to ("Aiguillettes de poulet panées" to 270),
        "Chicken curry" to ("Curry de poulet" to 150),
        "Chicken karahi" to ("Poulet karahi" to 160),
        "Coq au vin" to ("Coq au vin" to 150),
        "Satay" to ("Brochettes saté" to 220),
        "Roast beef" to ("Rôti de bœuf" to 190),
        "Steak tartare" to ("Steak tartare" to 180),
        "Meatball" to ("Boulettes de viande" to 230),
        "Sausage" to ("Saucisse" to 300),
        "Boudin" to ("Boudin créole" to 300),
        "Chili con carne" to ("Chili con carne" to 120),
        "Cassoulet" to ("Cassoulet" to 160),
        "Gyro" to ("Gyros (pita)" to 220),
        "Shawarma" to ("Chawarma" to 220),
        "Fried fish" to ("Poisson frit" to 230),
        "Fish ball" to ("Boulettes de poisson" to 120),
        "Ceviche" to ("Ceviche" to 90),
        "Moules-frites" to ("Moules-frites" to 180),
        "Shrimp Creole" to ("Crevettes à la créole" to 110),
        "Shrimp and grits" to ("Crevettes et semoule de maïs" to 140),
        "Crab cake" to ("Galette de crabe" to 200),
        "Lobster roll" to ("Sandwich au homard" to 220),
        "Bouillabaisse" to ("Bouillabaisse" to 70),
        "Omelette" to ("Omelette" to 155),
        "Eggs Benedict" to ("Œufs Bénédicte" to 230),
        "Quiche" to ("Quiche" to 260),
        "French toast" to ("Pain perdu" to 230),
        "Pancake" to ("Pancakes" to 230),
        "Crêpe" to ("Crêpe" to 220),
        "Waffle" to ("Gaufre" to 290),
        "Muesli" to ("Muesli" to 370),
        "Pain au chocolat" to ("Pain au chocolat" to 420),
        "Doughnut" to ("Beignet (donut)" to 420),
        "Éclair" to ("Éclair" to 260),
        "Macaron" to ("Macaron" to 400),
        "Crème brûlée" to ("Crème brûlée" to 280),
        "Flan" to ("Flan" to 150),
        "Cheesecake" to ("Cheesecake" to 320),
        "Apple pie" to ("Tarte aux pommes" to 240),
        "Tarte Tatin" to ("Tarte Tatin" to 260),
        "Tart" to ("Tarte" to 300),
        "Cupcake" to ("Cupcake" to 370),
        "Chocolate chip cookie" to ("Cookie aux pépites de chocolat" to 480),
        "Ice cream cake" to ("Gâteau glacé" to 250),
        "Coconut cake" to ("Gâteau au coco" to 370),
        "Red velvet cake" to ("Gâteau red velvet" to 370),
        "Pound cake" to ("Quatre-quarts" to 400),
        "Tres leches cake" to ("Gâteau tres leches" to 260),
        "Fried plantain" to ("Banane plantain frite" to 250),
        "Mashed potato" to ("Purée de pommes de terre" to 90),
        "Baked potato" to ("Pomme de terre au four" to 95),
        "Potato wedges" to ("Potatoes" to 200),
        "Potato salad" to ("Salade de pommes de terre" to 140),
        "Gratin dauphinois" to ("Gratin dauphinois" to 140),
        "Tartiflette" to ("Tartiflette" to 200),
        "Raclette" to ("Raclette" to 300),
        "Ratatouille" to ("Ratatouille" to 60),
        "Greek salad" to ("Salade grecque" to 110),
        "Caprese salad" to ("Salade caprese" to 160),
        "Cobb salad" to ("Salade Cobb" to 150),
        "Garden salad" to ("Salade composée" to 40),
        "Waldorf salad" to ("Salade Waldorf" to 190),
        "Hummus" to ("Houmous" to 170),
        "Falafel" to ("Falafels" to 330),
        "Burrito" to ("Burrito" to 200),
        "Taco" to ("Taco" to 220),
        "Quesadilla" to ("Quesadilla" to 280),
        "Nachos" to ("Nachos" to 340),
        "Empanada" to ("Empanada" to 290),
        "Samosa" to ("Samoussa" to 260),
        "Miso soup" to ("Soupe miso" to 35),
        "French onion soup" to ("Soupe à l'oignon" to 60),
        "Pea soup" to ("Soupe de pois" to 75),
        "Leek soup" to ("Soupe aux poireaux" to 40),
        "Cabbage soup" to ("Soupe au chou" to 30),
        "Peppersoup" to ("Soupe pimentée" to 50),
        "Kimchi" to ("Kimchi" to 25),
    )

    /** Mots-clés anglais → famille française (pour les plats absents de la table). */
    private val KEYWORDS: List<Triple<String, String, Int>> = listOf(
        Triple("pizza", "Pizza", 260),
        Triple("fried rice", "Riz frit", 175),
        Triple("rice", "Plat de riz", 150),
        Triple("noodle", "Nouilles", 140),
        Triple("spaghetti", "Spaghetti", 160),
        Triple("pasta", "Pâtes", 160),
        Triple("burger", "Burger", 250),
        Triple("sandwich", "Sandwich", 250),
        Triple("salad", "Salade", 120),
        Triple("soup", "Soupe", 50),
        Triple("stew", "Ragoût", 110),
        Triple("curry", "Curry", 150),
        Triple("steak", "Steak", 220),
        Triple("chicken", "Poulet", 190),
        Triple("fish", "Poisson", 150),
        Triple("shrimp", "Crevettes", 110),
        Triple("cheesecake", "Cheesecake", 320),
        Triple("cake", "Gâteau", 370),
        Triple("pie", "Tarte", 260),
        Triple("cookie", "Biscuit", 480),
        Triple("bread", "Pain", 260),
    )

    data class Mapped(val name: String, val kcal100: Double?, val food: Food?, val translated: Boolean)

    /**
     * Traduit une étiquette du modèle. Ordre : table exacte > mot-clé (« Soupe (Lettuce soup) ») > étiquette anglaise
     * telle quelle (kcal inconnues). Les kcal viennent de la base intégrée si le nom français y figure exactement.
     */
    fun map(label: String, foods: List<Food> = BUILTIN_FOODS): Mapped {
        val l = label.trim()
        TABLE[l]?.let { (fr, kcal) ->
            val local = foods.firstOrNull { fold(it.name) == fold(fr) }
            return Mapped(fr, (local?.kcalPer100g ?: kcal).toDouble(), local, true)
        }
        // Mots entiers seulement (« pie » ne doit pas correspondre à « pierogi »), pluriel en -s accepté
        val padded = " " + l.lowercase().replace(Regex("[^a-zà-ÿ]+"), " ").trim() + " "
        KEYWORDS.firstOrNull { padded.contains(" ${it.first} ") || padded.contains(" ${it.first}s ") }?.let { (_, fr, kcal) ->
            return Mapped("$fr ($l)", kcal.toDouble(), null, true)
        }
        val local = foods.firstOrNull { fold(it.name) == fold(l) }
        return Mapped(l, local?.kcalPer100g?.toDouble(), local, false)
    }
}
