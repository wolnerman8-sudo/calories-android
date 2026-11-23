package com.wolnerman.calories

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** v2.6 : repères indicatifs de masse grasse selon l'âge (pas un avis médical). */
object CompositionRanges {
    data class Range(val minFat: Double, val maxFat: Double, val note: String)

    fun forAge(age: Int?): Range = when {
        age == null -> Range(22.0, 33.0, "Repères généraux")
        age < 40 -> Range(20.0, 30.0, "Zone souvent favorable avant 40 ans")
        age in 40..49 -> Range(25.0, 35.0, "Zone indicative autour de 48 ans")
        age in 50..59 -> Range(26.0, 36.0, "Zone indicative en périménopause")
        else -> Range(27.0, 37.0, "Repères adaptés à votre âge")
    }

    const val ABOVE = "Votre masse grasse est un peu au-dessus de la zone indicative pour votre âge. Ce n’est pas inquiétant sur une seule pesée. " +
        "Surveillez simplement la tendance ces prochaines semaines. Ces repères sont indicatifs."
    const val BELOW = "Votre masse grasse est un peu en dessous de la zone indicative. Une valeur trop basse peut influencer l’équilibre hormonal. " +
        "Parlez-en avec votre médecin si cela se confirme sur plusieurs mesures."

    fun fatMessage(fat: Double?, age: Int?): String? {
        if (fat == null) return null
        val r = forAge(age)
        return when {
            fat > r.maxFat -> ABOVE
            fat < r.minFat -> BELOW
            else -> null
        }
    }

    fun rangeText(r: Range) = "${r.minFat.toInt()}–${r.maxFat.toInt()} %"
}

/**
 * Lit « graisse 23,4 % » / « masse grasse 21,3 % » et « muscle 32,8 % » dans le texte d'une mesure
 * (secours si les champs structurés sont absents).
 */
fun parseComposition(extra: String?): Pair<Double?, Double?> {
    if (extra == null) return null to null
    fun grab(re: Regex) = re.find(extra)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    val fat = grab(Regex("""(?:graisse|grasse)\s+([0-9]+(?:[.,][0-9]+)?)\s*%""", RegexOption.IGNORE_CASE))
    val muscle = grab(Regex("""muscle\s+([0-9]+(?:[.,][0-9]+)?)\s*%""", RegexOption.IGNORE_CASE))
    return fat to muscle
}

/** Masse grasse / musculaire d'une mesure : champs fournis par la balance, sinon lus dans le texte. Valeurs hors 1–80 % ignorées. */
fun compositionOf(r: BodyReading?): Pair<Double?, Double?> {
    if (r == null) return null to null
    val (pf, pm) = parseComposition(r.extra)
    fun ok(v: Double?) = v?.takeIf { it in 1.0..80.0 }
    return ok(r.fatPct ?: pf) to ok(r.musclePct ?: pm)
}

private fun pct(v: Double) = String.format(Locale.FRENCH, "%.1f", v)

@Composable
fun CompositionGuideCard(age: Int?, fat: Double?, muscle: Double?, measuredAt: Long? = null) {
    val range = CompositionRanges.forAge(age)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Composition corporelle – repères", style = MaterialTheme.typography.titleMedium)
            Text("Adaptés à votre âge${age?.let { " ($it ans)" } ?: ""}. Ces valeurs sont indicatives et ne remplacent pas un avis médical.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Masse grasse", fontWeight = FontWeight.SemiBold)
                Text(CompositionRanges.rangeText(range), color = MaterialTheme.colorScheme.primary)
            }
            fat?.let {
                val when_ = measuredAt?.let { t -> " (pesée du ${SimpleDateFormat("d MMM", Locale.FRENCH).format(Date(t))})" } ?: ""
                Text("Votre valeur : ${pct(it)} %$when_",
                    color = if (it < range.minFat || it > range.maxFat) Color(0xFFEF6C00) else Color(0xFF2E7D32))
            }
            muscle?.let { Text("Masse musculaire : ${pct(it)} %") }
            if (fat == null && muscle == null)
                Text("Votre balance n’a pas encore transmis de masse grasse à Calories : seuls les repères sont affichés.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(range.note, style = MaterialTheme.typography.bodySmall)
            CompositionRanges.fatMessage(fat, age)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF6C00))
            }
            Text("L’essentiel est la tendance sur plusieurs semaines. Dans un parcours de préservation de fertilité, un équilibre stable soutient l’équilibre hormonal.",
                style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}
