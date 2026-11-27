package com.wolnerman.calories

/**
 * v2.21 : extraction prudente (français) du nom, de la dose, des kcal et des oméga-3 EPA+DHA
 * à partir du texte lu sur une étiquette. Aucune valeur n'est inventée.
 */
data class OcrLine(
    val text: String,
    val height: Int? = null,
    val width: Int? = null,
    val top: Int? = null,
)

data class OcrExtraction(
    val name: String? = null,
    val nameCandidates: List<String> = emptyList(),
    val dose: String? = null,
    val kcal: Int? = null,
    val omega3Mg: Double? = null,
    val rawText: String = "",
    val usable: Boolean = false,
)

data class OcrFill(
    val name: String,
    val dose: String,
    val kcalText: String,
    val omegaText: String,
    val omegaInGrams: Boolean,
    val applied: List<String>,
    val message: String,
    val candidates: List<String>,
    val rawText: String,
    val usable: Boolean,
)

object LabelOcrLogic {
    const val READ_OK = "Lu sur l'étiquette, vérifie"
    const val UNREADABLE = "Texte non lisible : saisis le nom et la dose"
    const val KEPT = "Lu sur l'étiquette (champs déjà remplis, rien n'a été écrasé). Vérifie."

    internal val REGULATORY = listOf(
        "complement alimentaire", "complément alimentaire", "dietary supplement", "food supplement",
        "tenir hors de portee", "hors de portee des enfants", "keep out of reach",
        "ne pas depasser", "ne pas dépasser", "dose journaliere", "dose journalière recommandee",
        "ne se substitue pas", "alimentation variee", "alimentation variée", "mode de vie sain",
        "a conserver", "à conserver", "conserver a", "conserver à", "conserver au",
        "fabricant", "fabrique par", "fabriqué par", "manufactured", "made in", "produit en",
        "ingredients", "ingrédients", "allergene", "allergène", "allergenes",
        "informations nutritionnelles", "valeurs nutritionnelles", "valeur nutritionnelle",
        "apports nutritionnels", "tableau nutritionnel", "composition",
        "a consommer de preference", "à consommer de préférence", "best before", "a consommer de préférence",
        "date de durabilite", "dluo", "numero de lot", "numéro de lot", "n° de lot", "lot n",
        "notification", "deconseille", "déconseillé", "femmes enceintes", "allaitement",
        "consultez un medecin", "consultez un médecin", "avis medical", "avis médical",
        "poids net", "net wt", "net weight", "contenu net",
        "mode d'emploi", "mode d’emploi", "conseils d'utilisation", "conseils d’utilisation",
        "posologie", "utilisation", "voir notice", "lire la notice",
        "pour 100 g", "pour 100g", "pour 100 ml", "pour 100ml", "par 100 g",
        "% vnr", "vnr", "nrv", "recycling", "trier", "triman",
        "www.", "http://", "https://",
    )

    private val COUNTABLE_ALT = listOf(
        "gelule", "gélule", "gelules", "gélules",
        "capsule", "capsules",
        "comprime", "comprimé", "comprimes", "comprimés", "cps",
        "softgel", "softgels",
        "tablette", "tablettes",
        "perle", "perles",
        "sachet", "sachets",
        "ampoule", "ampoules",
        "gummy", "gummies", "gomme", "gommes",
        "cuillere", "cuillère", "cuilleres", "cuillères",
    )

    fun extract(rawText: String): OcrExtraction =
        extract(rawText.replace("\r\n", "\n").split('\n').map { OcrLine(it.trim()) }.filter { it.text.isNotBlank() })

    fun extract(lines: List<OcrLine>): OcrExtraction {
        val cleaned = lines.map { it.copy(text = it.text.replace(Regex("\\s+"), " ").trim()) }
            .filter { it.text.isNotBlank() }
        val raw = cleaned.joinToString("\n") { it.text }
        if (raw.isBlank()) return OcrExtraction(rawText = raw, usable = false)

        val nameCands = nameCandidates(cleaned)
        val name = pickName(cleaned, nameCands)
        val dose = pickDose(cleaned, name)
        val kcal = pickKcal(cleaned)
        val omega = pickOmega3Mg(cleaned)
        val usable = !name.isNullOrBlank() || !dose.isNullOrBlank() || kcal != null || omega != null
        val others = nameCands.filter { !it.equals(name, ignoreCase = true) }
        return OcrExtraction(name, others, dose, kcal, omega, raw, usable)
    }

    /**
     * Remplit uniquement les champs encore vides. N'écrase jamais une saisie existante.
     */
    fun applyToForm(
        name: String,
        dose: String,
        kcalText: String,
        omegaText: String,
        omegaInGrams: Boolean,
        extraction: OcrExtraction,
    ): OcrFill {
        val applied = mutableListOf<String>()
        var newName = name
        var newDose = dose
        var newKcal = kcalText
        var newOmega = omegaText
        var newInGrams = omegaInGrams
        if (name.isBlank() && !extraction.name.isNullOrBlank()) {
            newName = extraction.name; applied += "nom"
        }
        if (dose.isBlank() && !extraction.dose.isNullOrBlank()) {
            newDose = extraction.dose; applied += "dose"
        }
        if (kcalText.isBlank() && extraction.kcal != null) {
            newKcal = extraction.kcal.toString(); applied += "kcal"
        }
        if (omegaText.isBlank() && extraction.omega3Mg != null) {
            val mg = extraction.omega3Mg
            val grams = mg >= 1000.0 && mg % 1000.0 == 0.0
            newInGrams = grams
            newOmega = fmt(if (grams) mg / 1000.0 else mg)
            applied += "oméga-3"
        }
        val message = when {
            !extraction.usable -> UNREADABLE
            applied.isEmpty() -> KEPT
            else -> READ_OK
        }
        val cands = extraction.nameCandidates.filter { !it.equals(newName, ignoreCase = true) }
        return OcrFill(newName, newDose, newKcal, newOmega, newInGrams, applied, message, cands, extraction.rawText, extraction.usable)
    }

    internal fun isRegulatory(text: String): Boolean {
        val f = fold(text)
        if (f.length <= 1) return true
        if (Regex("^\\d{8,14}$").matches(text.filter(Char::isDigit)) && text.filter { it.isLetter() }.length < 3) return true
        return REGULATORY.any { f.contains(fold(it)) }
    }

    internal fun isNutrientRow(text: String): Boolean {
        val f = fold(text)
        if (Regex("""(?i)%\s*(vnr|nrv|ajr)""").containsMatchIn(text)) return true
        if (Regex("""(?i)\b(epa|dha|kcal|kj)\b""").containsMatchIn(f) && Regex("""\d""").containsMatchIn(text)) {
            // « OMÉGA-3 EPA DHA » comme titre (sans quantité) n'est pas une ligne de tableau
            if (!Regex("""\d+(?:[.,]\d+)?\s*(mg|g|kcal|kj|µg|ug|mcg)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return false
            return true
        }
        // « Vitamine D 5 µg 100 % » typique d'un tableau, pas d'un titre
        if (Regex("""(?i)vitamine[s]?\s+.+\d+(?:[.,]\d+)?\s*(µg|ug|mcg|mg|ui|iu)\b""").containsMatchIn(text)
            && (text.contains('%') || Regex("""\d+\s+\d+""").containsMatchIn(text))) return true
        return false
    }

    private fun isNameWorthy(line: OcrLine): Boolean {
        val t = line.text
        if (t.length < 2) return false
        if (isRegulatory(t) || isNutrientRow(t)) return false
        if (looksLikeDoseOnly(t)) return false
        if (t.count { it.isLetter() } < 3) return false
        return true
    }

    private fun nameScore(line: OcrLine, index: Int): Double {
        var s = (line.height ?: 0) * 2.0
        val words = line.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val letters = line.text.count { it.isLetter() }
        val caps = line.text.count { it.isUpperCase() }
        if (letters > 0 && caps >= letters * 0.6) s += 6
        val f = fold(line.text)
        if (listOf("vitamine", "omega", "magnesium", "zinc", "calcium", "probiotique", "collagene", "fer", "huile de lin").any { f.contains(it) }) s += 8
        if (line.height == null) {
            if (words.size in 1..5) s += 8
            if (words.size >= 6) s -= 6
            if (index <= 1) s += 6 else if (index <= 3) s += 2
            if (f.contains("sauvage") || f.contains("extrait de") || f.contains("huile de poissons")) s -= 4
        }
        return s
    }

    private fun nameCandidates(lines: List<OcrLine>): List<String> {
        val worthy = lines.mapIndexed { i, l -> i to l }.filter { isNameWorthy(it.second) }
        val ranked = worthy.sortedWith(
            compareByDescending<Pair<Int, OcrLine>> { nameScore(it.second, it.first) }
                .thenBy { it.second.top ?: Int.MAX_VALUE }
        )
        return ranked.map { tidyName(it.second.text) }.filter { it.isNotBlank() }.distinctBy { fold(it) }
    }

    private fun pickName(lines: List<OcrLine>, candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        val product = candidates.first()
        val productLine = lines.filter { isNameWorthy(it) }
            .maxWithOrNull(compareBy<OcrLine> { it.height ?: 0 }.thenByDescending { it.text.length })
        val brand = findBrand(lines, productLine, product)
        return SupplementScanLogic.displayName(product, brand).ifBlank { null }
    }

    private fun findBrand(lines: List<OcrLine>, productLine: OcrLine?, product: String): String? {
        val above = if (productLine?.top != null) {
            lines.filter { isNameWorthy(it) && it.top != null && it.top < productLine.top && it.text != productLine.text }
                .maxByOrNull { it.top!! }
        } else {
            val idx = lines.indexOfFirst { tidyName(it.text).equals(product, ignoreCase = true) || it.text.contains(product, true) }
            if (idx > 0) lines.take(idx).lastOrNull { isNameWorthy(it) } else lines.firstOrNull { isNameWorthy(it) && !tidyName(it.text).equals(product, ignoreCase = true) }
        } ?: return null
        val brand = tidyName(above.text)
        if (brand.isBlank() || fold(product).contains(fold(brand))) return null
        val words = brand.split(Regex("\\s+"))
        if (words.size > 4) return null
        if (brand.length > 40) return null
        return brand
    }

    private fun tidyName(s: String) = s.trim().trim(',', '.', ':', ';', '-', '–', '•').replace(Regex("\\s+"), " ")

    private fun looksLikeDoseOnly(text: String): Boolean {
        val t = text.trim()
        return Regex("""(?i)^(?:posologie|dose|prendre|prendre\s*:)?\s*\d+(?:[.,]\d+)?\s*(?:à|-|–)\s*\d+(?:[.,]\d+)?\s*[a-zµμ.]|^(?:posologie|dose|prendre)\s*:?\s*\d""").matches(t)
            || Regex("""(?i)^\d+(?:[.,]\d+)?\s*(u\.?\s*i\.?|i\.?\s*u\.?|mg|µg|ug|mcg|ml|g|gelules?|gélules?|capsules?|comprimes?|comprimés?)\s*(par\s*jour|\/\s*j(our)?|par\s*prise)?$""").matches(t)
    }

    private fun pickDose(lines: List<OcrLine>, name: String?): String? {
        val text = lines.joinToString("\n") { it.text }
        posology(text)?.let { return it }
        lines.forEach { posology(it.text)?.let { d -> return d } }
        // « 2 comprimés » sans « par jour », si le nombre est une prise raisonnable (1–10)
        simpleCountDose(text)?.let { return it }
        name?.let { SupplementScanLogic.doseFromName(it)?.let { d -> return d } }
        lines.forEach { SupplementScanLogic.doseFromName(it.text)?.let { d -> return d } }
        return null
    }

    private fun countablePattern(): String {
        val plain = (
            listOf("cuillère à café", "cuillere a cafe", "cuillère à soupe", "cuillere a soupe") +
                SupplementScanLogic.COUNTABLE.map { it.first } + COUNTABLE_ALT
            ).distinct().sortedByDescending { it.length }.map { Regex.escape(it) }
        val shorthand = listOf("c\\.\\s*à\\s*café", "c\\.\\s*a\\s*cafe", "c\\.\\s*à\\s*soupe", "c\\.\\s*a\\s*soupe")
        return (plain + shorthand).joinToString("|")
    }

    internal fun posology(text: String): String? {
        val units = countablePattern()
        val re = Regex(
            """(?i)(?:prendre|posologie|dose|usage)\s*:?\s*(?:de\s*)?(\d+)\s*(?:à|-|–)\s*(\d+)\s*($units)\b(?:\s*(par\s*jour|par\s*prise|\/\s*j(?:our)?|chaque\s*jour))?\s*""" +
                """|(?i)(?:prendre|posologie|dose)\s*:?\s*(?:de\s*)?(\d+)\s*($units)\b(?:\s*(par\s*jour|par\s*prise|\/\s*j(?:our)?|chaque\s*jour|le\s*matin|le\s*soir))?""" +
                """|(?i)\b(\d+)\s*($units)\s*(par\s*jour|par\s*prise|\/\s*j(?:our)?|chaque\s*jour)\b""",
        )
        val m = re.find(text) ?: return null
        return formatPosologyMatch(m)
    }

    private fun formatPosologyMatch(m: MatchResult): String? {
        val g = m.groupValues
        // groups: 1-4 range+unit+freq, 5-7 n+unit+freq, 8-10 n+unit+freq
        fun freq(raw: String) = when {
            raw.isBlank() -> ""
            fold(raw).contains("prise") -> " par prise"
            fold(raw).contains("matin") -> " le matin"
            fold(raw).contains("soir") -> " le soir"
            else -> " par jour"
        }
        return when {
            g[1].isNotBlank() -> "${g[1]} à ${counted(g[2], g[3])}${freq(g[4])}"
            g[5].isNotBlank() -> "${counted(g[5], g[6])}${freq(g[7])}"
            g[8].isNotBlank() -> "${counted(g[8], g[9])}${freq(g[10])}"
            else -> null
        }
    }

    private fun simpleCountDose(text: String): String? {
        val units = countablePattern()
        val matches = Regex("""(?i)\b(\d+)\s*($units)\b""").findAll(text).toList()
        val reasonable = matches.mapNotNull { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (n in 1..10) counted(n.toString(), m.groupValues[2]) else null
        }.distinct()
        return reasonable.singleOrNull()
    }

    private fun singularCount(unit: String): String {
        val u = unit.lowercase().trim()
        SupplementScanLogic.COUNTABLE.firstOrNull { it.first.equals(u, ignoreCase = true) }?.second?.let { return it }
        return when {
            u.startsWith("gél") || u.startsWith("gel") -> "gélule"
            u.startsWith("compr") || u == "cps" -> "comprimé"
            u.startsWith("caps") -> "capsule"
            u.startsWith("soft") -> "softgel"
            u.startsWith("perl") -> "perle"
            u.startsWith("sach") -> "sachet"
            u.startsWith("amp") -> "ampoule"
            u.startsWith("gumm") || u.startsWith("gomm") -> "gomme"
            u.contains("cafe") || u.contains("café") -> "c. à café"
            u.contains("soupe") -> "c. à soupe"
            u.startsWith("cuill") -> "cuillère"
            u.startsWith("tabl") -> "tablette"
            else -> u
        }
    }

    private fun counted(n: String, unit: String): String {
        val sing = singularCount(unit)
        val k = n.toIntOrNull() ?: return "$n $sing"
        val word = if (k <= 1) sing else when (sing) {
            "gélule" -> "gélules"
            "comprimé" -> "comprimés"
            "capsule" -> "capsules"
            "softgel" -> "softgels"
            "perle" -> "perles"
            "sachet" -> "sachets"
            "ampoule" -> "ampoules"
            "gomme" -> "gommes"
            "tablette" -> "tablettes"
            "cuillère" -> "cuillères"
            else -> sing
        }
        return "$k $word"
    }

    internal fun pickKcal(lines: List<OcrLine>): Int? {

        data class Hit(val value: Int, val line: String, val idx: Int)
        val hits = mutableListOf<Hit>()
        lines.forEachIndexed { i, line ->
            Regex("""(?i)(?:energie|énergie|energy)?\s*(?:de\s*)?(\d+)\s*(?:kJ|kj)\s*(?:\/|,)?\s*(\d+)\s*kcal\b""")
                .findAll(line.text).forEach { hits += Hit(it.groupValues[2].toInt(), line.text, i) }
            if (hits.none { it.idx == i }) {
                Regex("""(?i)(\d+)\s*kcal\b""").findAll(line.text).forEach { hits += Hit(it.groupValues[1].toInt(), line.text, i) }
            }
        }
        fun per100(s: String) = fold(s).contains("pour 100") || fold(s).contains("par 100")
        fun perServing(s: String) = Regex("""(?i)(par|pour)\s*(gelule|gélule|capsule|comprime|comprimé|prise|dose|portion|softgel|perle)""").containsMatchIn(s)
        val kept = hits.filter { h ->
            val ctx = contextAround(lines, h.idx)
            when {
                per100(h.line) && !perServing(h.line) -> false
                per100(ctx) && !perServing(ctx) && !perServing(h.line) -> false
                h.value > 80 && per100(ctx) -> false
                h.value > 200 -> false // une prise de complément dépasse rarement 200 kcal ; on n'invente pas
                else -> true
            }
        }
        return kept.map { it.value }.distinct().singleOrNull()
    }

    internal fun pickOmega3Mg(lines: List<OcrLine>): Double? {
        val full = lines.joinToString("\n") { it.text }
        if (per100WithoutServing(lines, full)) return null
        val epa = fattyAcidMg(full, "epa")
        val dha = fattyAcidMg(full, "dha")
        if (epa != null && dha != null) {
            val sum = epa + dha
            return sum.takeIf { it in 1.0..10000.0 }
        }
        val combined = Regex(
            """(?i)(?:epa\s*(?:\+|et|&)\s*dha|dha\s*(?:\+|et|&)\s*epa|omega[-\s]?3(?:\s*(?:epa|dha|\+|et))*)\s*[:=]?\s*(\d+(?:[.,]\d+)?)\s*(mg|g|µg|ug|mcg)\b"""
        ).find(full)
        if (combined != null) {
            val mg = toMg(combined.groupValues[1], combined.groupValues[2]) ?: return null
            return mg.takeIf { it in 1.0..10000.0 }
        }
        return listOfNotNull(epa, dha).singleOrNull()?.takeIf { it in 1.0..10000.0 }
    }

    private fun fattyAcidMg(text: String, name: String): Double? {
        val re = Regex("""(?i)\b$name\b(?:[^0-9]{0,24})(\d+(?:[.,]\d+)?)\s*(mg|g|µg|ug|mcg)\b""")
        val m = re.find(text) ?: return null
        return toMg(m.groupValues[1], m.groupValues[2])
    }

    private fun toMg(n: String, unit: String): Double? {
        val v = n.replace(',', '.').toDoubleOrNull() ?: return null
        if (v <= 0.0) return null
        return when (unit.lowercase()) {
            "g" -> if (v > 10.0) null else v * 1000.0 // > 10 g : typiquement pour 100 g d'huile, pas une prise
            "mg" -> v
            "µg", "ug", "mcg" -> v / 1000.0
            else -> null
        }
    }

    private fun per100WithoutServing(lines: List<OcrLine>, full: String): Boolean {
        val f = fold(full)
        val has100 = f.contains("pour 100") || f.contains("par 100")
        val serving = Regex("""(?i)(par|pour)\s*(gelule|gélule|capsule|comprime|comprimé|prise|dose|portion)""").containsMatchIn(full)
        return has100 && !serving
    }

    private fun contextAround(lines: List<OcrLine>, idx: Int): String {
        val from = (idx - 1).coerceAtLeast(0)
        val to = (idx + 1).coerceAtMost(lines.lastIndex)
        return lines.subList(from, to + 1).joinToString(" ") { it.text }
    }
}
