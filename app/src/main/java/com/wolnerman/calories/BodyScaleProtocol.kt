package com.wolnerman.calories

import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/*
 * v2.5 — Balance pèse-personne (OKOK International / Chipsea et apparentées). Kotlin pur, testable sans Android.
 *
 * Formats réimplémentés à partir de la documentation publique du projet openScale
 * (github.com/oliexdev/openScale, commit 2f5eeb7, nov. 2026) :
 *  - OkOkHandler.kt            : annonces OKOK « V20 » (0x20CA), « V11 » (0x11CA), « VF0 » (0xF0FF), « C0 »
 *  - libs/ChipseaBleLib.kt     : protocole GATT « Chipsea-BLE » (service FFF0, FFF1 notif., FFF2 écriture)
 *  - QNHandlerBroadcast.kt     : annonces QN « AABB » (identifiant 0xFFFF), testées sur des trames réelles FITINDEX FT-26R
 * plus la norme Bluetooth SIG Weight Scale (0x181D) / Body Composition (0x181B), déjà dans ScaleDrivers.
 * Aucun code d'openScale (GPL) n'est copié : seules les descriptions d'octets sont reprises.
 *
 * NB : dans Android, les « données fabricant » d'une annonce sont indexées par un identifiant 16 bits
 * petit-boutiste (2 premiers octets) ; le tableau de données commence juste après.
 */

/** Une mesure de balance pèse-personne, toujours en kg. [stable] null = la balance n'envoie pas d'indicateur. */
data class BodyReading(
    val kg: Double,
    val stable: Boolean?,
    val protocol: String,
    val impedanceOhm: Double? = null,
    val extra: String? = null,
    val fatPct: Double? = null,      // v2.6 : masse grasse % calculée PAR LA BALANCE (null si elle ne l'envoie pas)
    val musclePct: Double? = null,   // v2.6 : masse musculaire %
)

/** Données fabricant d'une annonce : identifiant (petit-boutiste, comme Android) + octets suivants. */
class ManufData(val companyId: Int, val data: ByteArray) {
    override fun toString() = "%04X:%s".format(companyId, ScaleDecoder.hex(data))
}

object BodyUnits {
    const val LB_TO_KG = 0.45359237
    const val ST_TO_KG = 6.35029318
    const val JIN_TO_KG = 0.5

    /** Diviseur selon les bits 1-2 de l'octet « attributs » OKOK / Chipsea : 01 → 1, 10 → 100, sinon 10. */
    fun divider(attr: Int): Double = when ((attr shr 1) and 0x03) { 1 -> 1.0; 2 -> 100.0; else -> 10.0 }

    /**
     * Poids brut (2 octets gros-boutistes) → kg selon les bits 3-4 : 00 kg, 01 jin (livre chinoise = 0,5 kg),
     * 10 lb, 11 st:lb (octet haut = stones, octet bas = livres / diviseur).
     */
    fun toKg(hi: Int, lo: Int, attr: Int): Double {
        val div = divider(attr)
        val raw = (hi shl 8) or lo
        return when ((attr shr 3) and 0x03) {
            0 -> raw / div
            1 -> raw / div * JIN_TO_KG
            2 -> raw / div * LB_TO_KG
            else -> hi * ST_TO_KG + (lo / div) * LB_TO_KG
        }
    }

    fun round2(kg: Double) = Math.round(kg * 100.0) / 100.0
}

private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
private fun u16be(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)
private fun u16le(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)

/** Lecture brute d'une annonce BLE (structures longueur/type/valeur). */
object BleAdv {
    /** Toutes les données fabricant (type 0xFF). */
    fun manufacturer(scanRecord: ByteArray?): List<ManufData> {
        val b = scanRecord ?: return emptyList()
        val out = mutableListOf<ManufData>()
        var i = 0
        while (i < b.size) {
            val len = u8(b, i)
            if (len == 0 || i + len >= b.size) break
            val type = u8(b, i + 1)
            if (type == 0xFF && len >= 3) {
                val id = u8(b, i + 2) or (u8(b, i + 3) shl 8)
                out += ManufData(id, b.copyOfRange(i + 4, i + 1 + len))
            }
            i += 1 + len
        }
        return out
    }
}

/** Annonces OKOK (balances sans connexion : le poids est dans l'annonce). */
object OkokAdv {
    const val V20 = 0x20CA
    const val V11 = 0x11CA
    const val VF0 = 0xF0FF

    /** V20 : 19 octets ; [6] bit0 = mesure finale, bit2 = ÷100 ; poids [8..9] BE ; impédance [10..11] ÷10 ; [12] = XOR(0x20, octets 0..11). */
    fun parseV20(d: ByteArray): BodyReading? {
        if (d.size != 19) return null
        var x = 0x20
        for (i in 0 until 12) x = x xor u8(d, i)
        if ((x and 0xFF) != u8(d, 12)) return null
        val flags = u8(d, 6)
        val div = if (flags and 0x04 != 0) 100.0 else 10.0
        val imp = u16be(d, 10) / 10.0
        return BodyReading(BodyUnits.round2(u16be(d, 8) / div), flags and 0x01 != 0, "OKOK V20", imp.takeIf { it > 0 })
    }

    /** V11 : 23 octets ; poids [3..4] BE ; attributs [9] (diviseur, unité) ; [16] = XOR(0xCA^0x11, octets 0..15). Pas d'indicateur stable. */
    fun parseV11(d: ByteArray): BodyReading? {
        if (d.size != 23) return null
        var x = 0xCA xor 0x11
        for (i in 0 until 16) x = x xor u8(d, i)
        if ((x and 0xFF) != u8(d, 16)) return null
        return BodyReading(BodyUnits.round2(BodyUnits.toKg(u8(d, 3), u8(d, 4), u8(d, 9))), null, "OKOK V11")
    }

    /** VF0 : poids ÷10 en petit-boutiste aux octets [2..3]. Pas d'indicateur stable ni de contrôle. */
    fun parseVF0(d: ByteArray): BodyReading? {
        if (d.size < 4) return null
        return BodyReading(BodyUnits.round2(u16le(d, 2) / 10.0), null, "OKOK VF0")
    }

    /** C0 (identifiant dont l'octet bas vaut 0xC0) : poids [0..1] BE, impédance [2..3] ÷10, attributs [6] (bit0 stable), MAC [7..12]. */
    fun parseC0(d: ByteArray): BodyReading? {
        if (d.size < 13) return null
        val attr = u8(d, 6)
        val kg = BodyUnits.toKg(u8(d, 0), u8(d, 1), attr)
        val imp = u16be(d, 2) / 10.0
        return BodyReading(BodyUnits.round2(kg), attr and 0x01 != 0, "OKOK C0", imp.takeIf { it > 0 })
    }

    fun isC0(m: ManufData) = (m.companyId and 0xFF) == 0xC0

    /** La trame C0 contient-elle l'adresse Bluetooth de l'émetteur (octets 7..12, même ordre que la chaîne) ? */
    fun c0EmbedsMac(m: ManufData, address: String?): Boolean {
        if (!isC0(m) || m.data.size < 13 || address == null) return false
        val parts = address.split(':'); if (parts.size != 6) return false
        return parts.indices.all { parts[it].toIntOrNull(16) == u8(m.data, 7 + it) }
    }

    fun hasOkokData(ms: List<ManufData>) = ms.any { it.companyId == V20 || it.companyId == V11 || it.companyId == VF0 || isC0(it) }

    /** Décode la première trame OKOK valide de l'annonce (ordre : V20, V11, VF0, C0). */
    fun parse(ms: List<ManufData>): BodyReading? {
        ms.firstOrNull { it.companyId == V20 }?.let { m -> parseV20(m.data)?.let { return it } }
        ms.firstOrNull { it.companyId == V11 }?.let { m -> parseV11(m.data)?.let { return it } }
        ms.firstOrNull { it.companyId == VF0 }?.let { m -> parseVF0(m.data)?.let { return it } }
        ms.firstOrNull { isC0(it) }?.let { m -> parseC0(m.data)?.let { return it } }
        return null
    }
}

/** Annonces QN « AABB » (identifiant 0xFFFF) : poids ÷100 LE [17..18], état [15] (bit5 stable ; bit0 sur la famille FT-26R). */
object QnAdv {
    const val COMPANY = 0xFFFF
    private val FT26R_SIG = intArrayOf(0x51, 0x0E, 0x03)

    fun isAabb(m: ManufData) = m.companyId == COMPANY && m.data.size >= 2 && u8(m.data, 0) == 0xAA && u8(m.data, 1) == 0xBB

    fun parse(d: ByteArray): BodyReading? {
        if (d.size < 19 || u8(d, 0) != 0xAA || u8(d, 1) != 0xBB) return null
        val kg = u16le(d, 17) / 100.0
        if (kg < 0.5 || kg > 300) return null
        val st = u8(d, 15)
        val ft26r = d.size >= 22 && FT26R_SIG.indices.all { u8(d, 19 + it) == FT26R_SIG[it] }
        val mask = if (ft26r) 0x21 else 0x20
        return BodyReading(BodyUnits.round2(kg), st and mask != 0, "QN AABB")
    }
}

/**
 * Protocole GATT « Chipsea-BLE » (appli OKOK International) : service FFF0, notifications FFF1, écritures FFF2.
 * Toutes les trames commencent par 0xCA. Classe [1] : 0x10 trame simple, 0x11 trames v1.1 (sous-type [3]).
 * Octet « propriété » : bit0 verrouillé (stable), bits1-2 décimales, bits3-4 unité (voir BodyUnits).
 */
class ChipseaGatt {
    private val live = HashMap<Int, ByteArray>()
    private val history = HashMap<Int, ByteArray>()

    fun feed(f: ByteArray): BodyReading? {
        if (f.size < 4 || u8(f, 0) != 0xCA) return null
        return when (u8(f, 1)) {
            0x10 -> single(f)
            0x11 -> v11(f)
            else -> null
        }
    }

    /** 0x10 : propriété [4], poids [5..6], composition [7..17] si verrouillé (graisse ‰, eau, muscle, métabolisme, viscéral, os). */
    private fun single(f: ByteArray): BodyReading? {
        if (f.size < 7) return null
        val p = u8(f, 4)
        val kg = BodyUnits.round2(BodyUnits.toKg(u8(f, 5), u8(f, 6), p))
        val locked = p and 1 != 0
        val extra = if (locked && f.size >= 18) String.format(Locale.FRENCH, "graisse %.1f %% · eau %.1f %% · muscle %.1f %%",
            u16be(f, 7) / 10.0, u16be(f, 9) / 10.0, u16be(f, 11) / 10.0) else null
        val comp = locked && f.size >= 18
        return BodyReading(kg, locked, "Chipsea-BLE", extra = extra,
            fatPct = if (comp) u16be(f, 7) / 10.0 else null, musclePct = if (comp) u16be(f, 11) / 10.0 else null)
    }

    private fun v11(f: ByteArray): BodyReading? = when (val sub = u8(f, 3)) {
        0, 1 -> if (f.size < 12) null else BodyReading(BodyUnits.round2(BodyUnits.toKg(u8(f, 5), u8(f, 6), u8(f, 11))), sub == 1, "Chipsea-BLE v1.1")
        18, 19 -> reassemble(f, if (sub == 18) live else history)?.let { assembled(it, sub == 19) }
        else -> null
    }

    /** Charge utile assemblée : [0..3] horodatage (historique), [4] propriété, [10..11] poids, [12..13] graisse… */
    private fun assembled(p: ByteArray, isHistory: Boolean): BodyReading? {
        if (p.size < 23) return null
        val prop = u8(p, 4)
        val kg = BodyUnits.round2(BodyUnits.toKg(u8(p, 10), u8(p, 11), prop))
        val locked = prop and 1 != 0
        val extra = buildString {
            if (isHistory) append("historique")
            if (locked) { if (isNotEmpty()) append(" · "); append(String.format(Locale.FRENCH, "graisse %.1f %% · muscle %.1f %%", u16be(p, 12) / 10.0, u16be(p, 16) / 10.0)) }
        }.ifEmpty { null }
        // Une mesure d'historique (ancienne) n'est jamais considérée comme une pesée en cours.
        return BodyReading(kg, if (isHistory) false else locked, "Chipsea-BLE v1.1", extra = extra,
            fatPct = if (locked) u16be(p, 12) / 10.0 else null, musclePct = if (locked) u16be(p, 16) / 10.0 else null)
    }

    /** [2] = longueur du morceau + 2, [4] = (index << 4) | total (index à partir de 1), données à partir de [5]. */
    private fun reassemble(f: ByteArray, chunks: HashMap<Int, ByteArray>): ByteArray? {
        if (f.size < 5) return null
        val len = u8(f, 2) - 2
        if (len < 0 || f.size < 5 + len) return null
        val idx = u8(f, 4) ushr 4; val total = u8(f, 4) and 0x0F
        if (total == 0 || idx == 0 || idx > total) return null
        if (idx == 1) chunks.clear()
        chunks[idx] = f.copyOfRange(5, 5 + len)
        if (idx != total) return null
        val ok = chunks.size == total && (1..total).all { it in chunks }
        val out = if (ok) (1..total).fold(ByteArray(0)) { acc, i -> acc + chunks.getValue(i) } else null
        chunks.clear()
        return out
    }

    companion object {
        const val SERVICE = "FFF0"; const val NOTIFY = "FFF1"; const val WRITE = "FFF2"

        /** Synchronisation utilisateur/heure v1.0 envoyée à la connexion (20 octets, XOR des octets 1..16 en [17]). */
        fun userSync(userId: Int, male: Boolean, age: Int, heightCm: Int, now: Date): ByteArray {
            val c = Calendar.getInstance().apply { time = now }
            val f = ByteArray(20)
            val v = intArrayOf(0xCA, 0x10, 14, 1, c.get(Calendar.YEAR) % 100, c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
                userId ushr 24, userId ushr 16, userId ushr 8, userId, if (male) 1 else 0, age, heightCm)
            v.forEachIndexed { i, x -> f[i] = x.toByte() }
            var x = 0; for (i in 1..16) x = x xor u8(f, i)
            f[17] = x.toByte()
            return f
        }
    }
}

/** Norme Bluetooth SIG (0x2A9D poids, 0x2A9C composition) → kg ; mesure finale (toujours stable). */
object BodySig {
    fun decode(char16: String, v: ByteArray): BodyReading? = when (char16.uppercase(Locale.ROOT)) {
        "2A9D" -> SigScale.decodeWeightMeasurement(v)?.grams?.let { BodyReading(BodyUnits.round2(it / 1000.0), true, "Bluetooth SIG 0x2A9D") }
        "2A9C" -> SigScale.decodeBodyComposition(v)?.let { w -> w.grams?.let { BodyReading(BodyUnits.round2(it / 1000.0), true, "Bluetooth SIG 0x2A9C", extra = w.extra,
            fatPct = ((v[2].toInt() and 0xFF) or ((v[3].toInt() and 0xFF) shl 8)) / 10.0) } }
        else -> null
    }
}

/** Famille détectée d'après l'annonce. */
enum class BodyKind(val label: String, val gatt: Boolean) {
    OKOK_ADV("OKOK / Chipsea (poids dans l'annonce)", false),
    QN_ADV("QN / FITINDEX (poids dans l'annonce)", false),
    CHIPSEA_GATT("Chipsea-BLE (connexion, appli OKOK International)", true),
    SIG_WEIGHT("Balance Bluetooth standard (0x181D)", true),
    SIG_BODY("Balance standard composition corporelle (0x181B)", true),
    CANDIDATE("Balance possible (format inconnu : journal)", false),
}

object BodyScaleDetect {
    private val NAME_PREFIXES = listOf("chipsea", "okok", "yoda0", "yoda1", "electronic scale", "health scale", "qn-scale", "body fat scale", "bodyfat", "scale")

    /** Balances de cuisine connues (gérées par ScaleDrivers) : jamais revendiquées ici. */
    private val KITCHEN = listOf("decent", "bookoo", "felicita", "acaia", "etekcity", "esn00", "kitchen", "ailink", "lunar", "pearl", "skale")

    fun nameLooksLikeBodyScale(name: String?): Boolean {
        val n = name?.trim()?.lowercase(Locale.ROOT) ?: return false
        if (KITCHEN.any { n.contains(it) }) return false
        return n == "adv" || NAME_PREFIXES.any { n.startsWith(it) } || n.contains("scale") && !n.contains("kitchen")
    }

    /**
     * Ne revendique JAMAIS une balance de cuisine AiLink ([aiLink] = annonce AiLink reconnue, ou nom « AiLink… »).
     * [services] = UUID 16 bits annoncés (« FFF0 »…).
     */
    fun detect(name: String?, address: String?, services: List<String>, ms: List<ManufData>, aiLink: Boolean): BodyKind? {
        val n = name?.trim()?.lowercase(Locale.ROOT) ?: ""
        if (aiLink || KITCHEN.any { n.contains(it) }) return null
        if (ms.any { QnAdv.isAabb(it) }) return BodyKind.QN_ADV
        val okName = n.isEmpty() || n == "adv" || n == "chipsea-ble" || n.startsWith("yoda0") || n.startsWith("yoda1") || nameLooksLikeBodyScale(name)
        if (OkokAdv.hasOkokData(ms)) {
            if (okName || ms.any { OkokAdv.c0EmbedsMac(it, address) }) return BodyKind.OKOK_ADV
        }
        if (n == "chipsea-ble") return BodyKind.CHIPSEA_GATT
        if (services.any { it.equals("181D", true) }) return BodyKind.SIG_WEIGHT
        if (services.any { it.equals("181B", true) }) return BodyKind.SIG_BODY
        if (nameLooksLikeBodyScale(name) && services.any { it.equals("FFF0", true) }) return BodyKind.CHIPSEA_GATT
        if (nameLooksLikeBodyScale(name)) return BodyKind.CANDIDATE
        return null
    }
}

/** Plage vraisemblable pour un poids humain ; 0 ou presque = balance vide. */
object BodyPlausible {
    const val MIN_KG = 2.0
    const val MAX_KG = 250.0
    fun ok(kg: Double) = kg in MIN_KG..MAX_KG
}

/**
 * Stabilité : on suit l'indicateur de la balance s'il existe ; sinon « stable » = même poids (±0,05 kg) depuis 2 s.
 * Retourne null pour une valeur nulle ou invraisemblable (balance vide, trame parasite).
 */
class BodyStability(private val holdMs: Long = 2000) {
    private var lastKg: Double? = null
    private var since = 0L

    fun feed(r: BodyReading, now: Long): BodyReading? {
        if (!BodyPlausible.ok(r.kg)) { lastKg = null; return null }
        val prev = lastKg
        if (prev == null || abs(prev - r.kg) > 0.05) { lastKg = r.kg; since = now }
        val stable = r.stable ?: (now - since >= holdMs)
        return r.copy(stable = stable)
    }
}

/**
 * Une seule pesée automatique par montée sur la balance : réarmée quand la balance repasse à vide
 * (poids < 2 kg) ou après [rearmMs] sans aucune trame.
 */
class WeighInSession(private val rearmMs: Long = 30_000) {
    private var armed = true
    private var lastFrame = 0L

    /** [kg] = valeur brute (même nulle) ; renvoie le poids à enregistrer automatiquement, ou null. */
    fun onFrame(kg: Double, stable: Boolean, now: Long): Double? {
        if (lastFrame != 0L && now - lastFrame > rearmMs) armed = true
        lastFrame = now
        if (kg < BodyPlausible.MIN_KG) { armed = true; return null }
        if (!BodyPlausible.ok(kg) || !stable || !armed) return null
        armed = false
        return kg
    }

    fun markSaved() { armed = false }
}

data class WeighIn(val timeMs: Long, val kg: Double, val protocol: String, val health: String = "",
                   val fatPct: Double? = null, val musclePct: Double? = null)

object WeighInRules {
    const val DEDUPE_MS = 2 * 60_000L
    /** Doublon = même poids (écart < 0,05 kg) à moins de 2 minutes. */
    fun isDuplicate(kg: Double, timeMs: Long, existing: List<WeighIn>): Boolean =
        existing.any { abs(it.timeMs - timeMs) <= DEDUPE_MS && abs(it.kg - kg) < 0.05 }

    fun fmtKg(kg: Double): String = String.format(Locale.FRENCH, "%.1f kg", kg)
}
