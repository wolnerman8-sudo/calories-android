package com.wolnerman.calories

import java.util.Locale

/*
 * Architecture « pilotes de balance » (v1.4). Kotlin pur, testable sans Android.
 *
 * Chaque famille de balance = un ScaleDriverInfo (détection à partir de l'annonce Bluetooth ou des services GATT)
 * + une classe ScaleDriver créée à chaque connexion (abonnements, commandes, décodage).
 * Les protocoles ont été réimplémentés à partir de leurs descriptions publiques (aucun code GPL copié).
 */

/** Mesure générique, quelle que soit la balance. */
data class WeightReading(
    val value: Double,          // valeur dans l'unité de la balance
    val unitLabel: String,
    val grams: Double?,         // converti en grammes (null si inconvertible)
    val decimals: Int,
    val stable: Boolean,
    val seq: Int = -1,
    val extra: String? = null,  // ex. « masse grasse 21,3 % »
)

fun AiLinkReading.toWeight() = WeightReading(value, unitLabel, grams, decimals, stable, seq)

/** Ce qu'on sait d'un appareil pendant le scan. */
data class AdvData(val name: String?, val services: List<String>, val aiLink: AiLinkAdv?) {
    fun has(s16: String) = services.any { it.equals(s16, ignoreCase = true) }
    val lname: String get() = name?.lowercase(Locale.ROOT) ?: ""
}

/** Ce que le pilote peut faire sur la connexion (implémenté par ScaleManager, ou par un faux dans les tests). */
interface DriverSession {
    /** Écrit sur une caractéristique (UUID court « FFE1 » ou complet). */
    fun write(charUuid: String, data: ByteArray)
    fun log(s: String)
    /** MAC à utiliser pour le chiffrement AiLink (annonce si disponible, sinon adresse Android). */
    val cryptMac: String
    /** MAC lu dans l'annonce AiLink, dans l'ordre des octets reçus (petit-boutiste), ou null. */
    val advMacLE: ByteArray? get() = null
    /** Adresse Bluetooth vue par Android ("AA:BB:CC:DD:EE:FF"). */
    val address: String get() = cryptMac
}

/** Chiffrement AiLink (implémenté par la bibliothèque officielle ; remplaçable dans les tests). */
interface AiLinkCryptoApi {
    fun handshakeStart(): ByteArray?
    fun handshakeAnswer(fromBle: ByteArray): ByteArray?
    fun xorPayload(cid: Int, payload: ByteArray, mac: String): ByteArray?
}

abstract class ScaleDriver {
    /** Caractéristiques à abonner (notify/indicate). Vide = toutes (pilote inconnu). */
    abstract val subscribe: List<String>
    open fun onReady(s: DriverSession) {}
    /** Données reçues → mesures (éventuellement aucune). */
    abstract fun onNotify(char: String, data: ByteArray, s: DriverSession): List<WeightReading>
    /** Tare ; false si non disponible. */
    open fun tare(s: DriverSession): Boolean = false
    /** Commande à renvoyer périodiquement (maintien de connexion), sinon null. */
    open val heartbeat: ByteArray? get() = null
    open val heartbeatChar: String? get() = null
    open val heartbeatMs: Long get() = 0
}

class ScaleDriverInfo(
    val id: String,
    val label: String,
    /** true = protocole publié par le fabricant / la norme ; false = rétro-ingénierie publique (déduit). */
    val confirmed: Boolean,
    val kitchen: Boolean,
    val serviceHints: List<String>,
    /** Score de détection à partir de l'annonce (0 = non). */
    val detect: (AdvData) -> Int,
    val create: (AiLinkCryptoApi?, Int) -> ScaleDriver,
)

object ScaleDrivers {
    private fun s16(u: String): String {
        val l = u.lowercase(Locale.ROOT)
        return if (l.length == 36 && l.startsWith("0000") && l.endsWith("-0000-1000-8000-00805f9b34fb")) l.substring(4, 8).uppercase(Locale.ROOT) else u.uppercase(Locale.ROOT)
    }
    fun short(u: String) = s16(u)

    val AILINK = ScaleDriverInfo("ailink", "AiLink / Elink (balance nutrition)", true, true, listOf("FFE0", "F0A0"),
        detect = { a -> when {
            a.aiLink != null -> 100
            a.lname.startsWith("ailink") -> 90
            a.has("FFE0") && !a.lname.contains("felicita") -> 60
            else -> 0 } },
        create = { c, cid -> AiLinkDriver(c, cid) })

    val ETEKCITY = ScaleDriverInfo("esn00", "Etekcity Smart Nutrition (ESN00)", false, true, listOf("1910"),
        detect = { a -> when { a.has("1910") -> 90; a.lname.contains("etekcity") || a.lname.contains("esn00") -> 70; else -> 0 } },
        create = { _, _ -> EtekcityEsn00Driver() })

    val DECENT = ScaleDriverInfo("decent", "Decent Scale / Half Decent", true, true, listOf("FFF0"),
        detect = { a -> if (a.lname.startsWith("decent scale")) 95 else 0 },
        create = { _, _ -> DecentDriver() })

    val BOOKOO = ScaleDriverInfo("bookoo", "BOOKOO Themis (Mini / Ultra)", true, true, listOf("0FFE"),
        detect = { a -> when { a.has("0FFE") -> 90; a.lname.startsWith("bookoo") -> 85; else -> 0 } },
        create = { _, _ -> BookooDriver() })

    val SIG_WEIGHT = ScaleDriverInfo("sig-wss", "Balance Bluetooth standard (Weight Scale 0x181D)", true, false, listOf("181D"),
        detect = { a -> if (a.has("181D")) 80 else 0 },
        create = { _, _ -> SigWeightDriver() })

    val SIG_BODY = ScaleDriverInfo("sig-bcs", "Balance standard Composition corporelle (0x181B)", true, false, listOf("181B"),
        detect = { a -> if (a.has("181B")) 75 else 0 },
        create = { _, _ -> SigBodyCompositionDriver() })

    val UNKNOWN = ScaleDriverInfo("unknown", "Appareil inconnu (journal seulement)", false, false, emptyList(),
        detect = { 0 }, create = { _, _ -> UnknownDriver() })

    val ALL = listOf(AILINK, ETEKCITY, DECENT, BOOKOO, SIG_WEIGHT, SIG_BODY)

    /** Meilleur pilote d'après l'annonce, ou null. */
    fun detect(a: AdvData): ScaleDriverInfo? = ALL.map { it to it.detect(a) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first

    /** Après découverte GATT : pilote d'après les services réellement présents (UUID courts). */
    fun detectByServices(services: Collection<String>, name: String?): ScaleDriverInfo? {
        val set = services.map { s16(it) }.toSet()
        if (name?.lowercase(Locale.ROOT)?.startsWith("decent scale") == true) return DECENT
        return ALL.firstOrNull { d -> d.serviceHints.any { it in set } }
    }

    fun byId(id: String?) = (ALL + UNKNOWN).firstOrNull { it.id == id }
}

private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
private fun u16le(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)
private fun u16be(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)
private fun u24be(b: ByteArray, i: Int) = (u8(b, i) shl 16) or (u8(b, i + 1) shl 8) or u8(b, i + 2)
private fun xorAll(b: ByteArray, from: Int, toEx: Int): Int { var x = 0; for (i in from until toEx) x = x xor u8(b, i); return x }
private fun bytes(vararg x: Int) = ByteArray(x.size) { x[it].toByte() }

// ---------------------------------------------------------------- AiLink
/**
 * AiLink / Elink : FFE0, poignée de main A6 sur FFE3 (bibliothèque officielle), trames A7 sur FFE2.
 * v1.5 : déchiffrement XOR en Kotlin (voir AiLinkCipher) avec choix automatique de la clé (AiLinkKeyPicker),
 * et journal détaillé (brut, clair et champs pour chaque hypothèse).
 */
class AiLinkDriver(private val crypto: AiLinkCryptoApi?, private var cid: Int) : ScaleDriver() {
    override val subscribe = listOf("FFE2", "FFE3")
    init { if (cid == 0) cid = AiLinkProtocol.CID_NUTRITION }
    private var picker: AiLinkKeyPicker? = null
    private var announcedLock = false

    private fun picker(s: DriverSession): AiLinkKeyPicker = picker ?: run {
        val c = mutableListOf<Pair<String, ByteArray?>>()
        s.advMacLE?.let { c += "MAC annonce" to it; c += "MAC annonce inversé" to it.reversedArray() }
        AiLinkCipher.macBytes(s.address)?.let { c += "adresse Android" to it.reversedArray(); c += "adresse Android inversée" to it }
        c += "sans chiffrement" to null
        s.log("Hypothèses de clé : " + c.joinToString(" | ") { (l, m) -> "$l=${m?.let { ScaleDecoder.hex(it) } ?: "—"}" })
        AiLinkKeyPicker(c).also { picker = it }
    }

    override fun onReady(s: DriverSession) {
        picker(s)
        val hs = crypto?.handshakeStart()
        if (hs == null) s.log("Bibliothèque AiLink indisponible : pas de poignée de main")
        else { s.log("→ Poignée de main FFE3 : ${ScaleDecoder.hex(hs)}"); s.write("FFE3", hs) }
    }

    override fun onNotify(char: String, data: ByteArray, s: DriverSession): List<WeightReading> {
        val a6 = ScaleDecoder.a6Type(data)
        if (a6 != null) {
            if (a6 == AiLinkProtocol.SET_HANDSHAKE) {
                val ans = crypto?.handshakeAnswer(data)
                if (ans != null) { s.log("→ Réponse poignée de main : ${ScaleDecoder.hex(ans)}"); s.write("FFE3", ans) }
                else s.log("Impossible de répondre à la poignée de main")
            } else if (a6 == AiLinkProtocol.GET_HANDSHAKE) s.log("Poignée de main acceptée par la balance")
            else s.log("A6 type=%02X (info module BLE)".format(a6))
            return emptyList()
        }
        val pk = picker(s)
        val frames = ScaleDecoder.splitA7(data)
        if (frames.isEmpty()) { s.log("Trame non reconnue (ni A6 ni A7)"); return emptyList() }
        val out = mutableListOf<WeightReading>()
        for (f in frames) {
            if (f.cid != 0) cid = f.cid
            s.log("A7 CID=%04X long=%d somme=%s payload chiffré=%s".format(f.cid, f.payload.size, if (f.checksumOk) "ok" else "FAUSSE", ScaleDecoder.hex(f.payload)))
            if (!f.checksumOk) { s.log("   trame ignorée (somme de contrôle fausse)"); continue }
            val evals = pk.evaluate(f.cid, f.payload)
            evals.forEach { e ->
                s.log("   [%s] clair=%s | %s → %s (score %+d = %d)".format(e.cand.label, ScaleDecoder.hex(e.clear), e.fields ?: "trop court",
                    e.reading?.let { "${it.value} ${it.unitLabel}${if (it.stable) " stable" else ""}" } ?: "pas un poids valide", e.delta, e.cand.score))
            }
            val best = pk.best()
            if (pk.locked != null && !announcedLock) { announcedLock = true; s.log("✔ Clé retenue : ${pk.locked!!.label}") }
            if (best == null) { s.log("   (clé pas encore déterminée : aucune valeur affichée)"); continue }
            evals.firstOrNull { it.cand === best }?.reading?.let { out += it.toWeight() }
        }
        return out
    }

    override fun tare(s: DriverSession): Boolean {
        val plain = byteArrayOf(AiLinkProtocol.TYPE_ZERO.toByte(), 0x01)
        val key = picker?.best()
        val enc = key?.let { picker!!.clear(it, cid, plain) } ?: crypto?.xorPayload(cid, plain.copyOf(), s.cryptMac) ?: plain
        s.write("FFE1", ScaleDecoder.buildA7(cid, enc))
        return true
    }
}

// ---------------------------------------------------------------- Bluetooth SIG
object SigScale {
    /** Champ poids SIG : kg × 0,005 (SI) ou lb × 0,01 (impérial). */
    fun weight(raw: Int, imperial: Boolean): WeightReading =
        if (imperial) { val lb = raw * 0.01; WeightReading(lb, "lb", lb * 453.59237, 2, true) }
        else { val kg = raw * 0.005; WeightReading(kg, "kg", kg * 1000.0, 3, true) }

    /** Weight Measurement 0x2A9D : flags | poids uint16 LE | [horodatage 7] [ID 1] [IMC 2 + taille 2]. */
    fun decodeWeightMeasurement(b: ByteArray): WeightReading? {
        if (b.size < 3) return null
        val flags = u8(b, 0)
        return weight(u16le(b, 1), flags and 0x01 != 0)
    }

    /** Body Composition Measurement 0x2A9C : flags uint16 | masse grasse % ×0,1 | champs optionnels… | poids (bit 10). */
    fun decodeBodyComposition(b: ByteArray): WeightReading? {
        if (b.size < 4) return null
        val flags = u16le(b, 0)
        val imperial = flags and 0x0001 != 0
        val fat = u16le(b, 2) * 0.1
        var i = 4
        if (flags and (1 shl 1) != 0) i += 7     // horodatage
        if (flags and (1 shl 2) != 0) i += 1     // ID utilisateur
        for (bit in 3..9) if (flags and (1 shl bit) != 0) i += 2  // métabolisme, % muscle, masse musculaire, masse maigre, masse maigre molle, eau, impédance
        if (flags and (1 shl 10) == 0 || i + 2 > b.size) return null
        val fatTxt = String.format(Locale.FRENCH, "masse grasse %.1f %%", fat)
        return weight(u16le(b, i), imperial).copy(extra = fatTxt)
    }
}

class SigWeightDriver : ScaleDriver() {
    override val subscribe = listOf("2A9D")
    override fun onNotify(char: String, data: ByteArray, s: DriverSession) =
        if (ScaleDrivers.short(char) == "2A9D") listOfNotNull(SigScale.decodeWeightMeasurement(data)) else emptyList()
}

class SigBodyCompositionDriver : ScaleDriver() {
    override val subscribe = listOf("2A9C", "2A9D")
    override fun onNotify(char: String, data: ByteArray, s: DriverSession) = when (ScaleDrivers.short(char)) {
        "2A9C" -> listOfNotNull(SigScale.decodeBodyComposition(data))
        "2A9D" -> listOfNotNull(SigScale.decodeWeightMeasurement(data))
        else -> emptyList()
    }
}

// ---------------------------------------------------------------- Etekcity ESN00
/**
 * Etekcity Smart Nutrition Scale ESN00 (rétro-ingénierie publique « metekcity », MIT).
 * Service 0x1910, notifications 0x2C12, commandes 0x2C11.
 * Paquet : FE EF C0 A2 | type | longueur | payload | somme(type..payload) & 0xFF
 * Mesure (type 0xD0) : signe | valeur ×10 (uint16 BE) | unité | stable
 */
object Esn00 {
    val HEADER = bytes(0xFE, 0xEF, 0xC0, 0xA2)
    const val MEASUREMENT = 0xD0
    const val SET_TARE = 0xC1

    fun packet(type: Int, payload: ByteArray): ByteArray {
        val out = ByteArray(4 + 2 + payload.size + 1)
        HEADER.copyInto(out); out[4] = type.toByte(); out[5] = payload.size.toByte(); payload.copyInto(out, 6)
        var s = 0; for (i in 4 until out.size - 1) s += u8(out, i)
        out[out.size - 1] = s.toByte()
        return out
    }

    /** Découpe les paquets (plusieurs possibles par notification) : (type, payload, somme ok). */
    fun split(b: ByteArray): List<Triple<Int, ByteArray, Boolean>> {
        val out = mutableListOf<Triple<Int, ByteArray, Boolean>>()
        var i = 0
        while (i + 7 <= b.size) {
            if (!(u8(b, i) == 0xFE && u8(b, i + 1) == 0xEF && u8(b, i + 2) == 0xC0 && u8(b, i + 3) == 0xA2)) { i++; continue }
            val type = u8(b, i + 4); val len = u8(b, i + 5)
            if (i + 6 + len + 1 > b.size) break
            var s = 0; for (k in i + 4 until i + 6 + len) s += u8(b, k)
            out += Triple(type, b.copyOfRange(i + 6, i + 6 + len), (s and 0xFF) == u8(b, i + 6 + len))
            i += 6 + len + 1
        }
        return out
    }

    fun unitLabel(u: Int) = when (u) { 0 -> "g"; 1 -> "lb:oz"; 2 -> "ml"; 3 -> "fl oz"; 4 -> "ml (lait)"; 5 -> "fl oz (lait)"; 6 -> "oz"; else -> "unité $u" }
    fun toGrams(v: Double, u: Int): Double? = when (u) {
        0 -> v; 2 -> v; 4 -> v * 1.03
        1, 6 -> v * 28.349523125            // lb:oz : HYPOTHÈSE total en onces
        3 -> v * 29.5735295625; 5 -> v * 29.5735295625 * 1.03
        else -> null
    }

    fun decodeMeasurement(p: ByteArray): WeightReading? {
        if (p.size < 4) return null
        val neg = u8(p, 0) == 1
        val v = u16be(p, 1) / 10.0 * (if (neg) -1 else 1)
        val unit = u8(p, 3)
        val stable = p.size >= 5 && u8(p, 4) == 1
        return WeightReading(v, unitLabel(unit), toGrams(v, unit), 1, stable)
    }

    fun decode(b: ByteArray): List<WeightReading> =
        split(b).filter { it.third && it.first == MEASUREMENT }.mapNotNull { decodeMeasurement(it.second) }
}

class EtekcityEsn00Driver : ScaleDriver() {
    override val subscribe = listOf("2C12")
    override fun onNotify(char: String, data: ByteArray, s: DriverSession) = Esn00.decode(data)
    override fun tare(s: DriverSession): Boolean { s.write("2C11", Esn00.packet(Esn00.SET_TARE, bytes(0x01))); return true }
}

// ---------------------------------------------------------------- Decent Scale
/**
 * Decent Scale (API ouverte publiée par Decent Espresso).
 * Notifications FFF4 : 03 | CE (stable) / CA (en mouvement) | poids ×10 int16 BE | … | XOR. Écriture 36F5.
 * Il faut écrire une commande (LED) pour que la balance commence à envoyer le poids.
 */
object Decent {
    fun cmd(vararg six: Int): ByteArray { val b = ByteArray(7); six.forEachIndexed { i, v -> b[i] = v.toByte() }; b[6] = xorAll(b, 0, 6).toByte(); return b }
    val LED_ON_GRAMS = cmd(0x03, 0x0A, 0x01, 0x01, 0x00, 0x00)   // octet 6 = 00 : pas d'exigence de battement de cœur
    val TARE = cmd(0x03, 0x0F, 0x00, 0x00, 0x00, 0x00)
    val HEARTBEAT = bytes(0x03, 0x0A, 0x03, 0xFF, 0xFF, 0x00, 0x0A)

    fun decode(b: ByteArray): WeightReading? {
        if (b.size < 7 || u8(b, 0) != 0x03) return null
        val t = u8(b, 1)
        if (t != 0xCE && t != 0xCA) return null
        val raw = u16be(b, 2).toShort().toInt()
        val g = raw / 10.0
        return WeightReading(g, "g", g, 1, t == 0xCE)
    }
}

class DecentDriver : ScaleDriver() {
    override val subscribe = listOf("FFF4")
    override fun onReady(s: DriverSession) { s.write("36F5", Decent.LED_ON_GRAMS) }
    override fun onNotify(char: String, data: ByteArray, s: DriverSession) = listOfNotNull(Decent.decode(data))
    override fun tare(s: DriverSession): Boolean { s.write("36F5", Decent.TARE); return true }
    override val heartbeat get() = Decent.HEARTBEAT
    override val heartbeatChar get() = "36F5"
    override val heartbeatMs get() = 4000L
}

// ---------------------------------------------------------------- BOOKOO
/**
 * BOOKOO Themis Mini / Ultra (protocole publié par BOOKOO, MIT).
 * Service 0x0FFE, poids sur FF11, commandes sur FF12.
 * Poids : 03 0B | ms (3) | unité | signe | grammes ×100 (uint24 BE) | … | XOR. Le poids est toujours en grammes.
 */
object Bookoo {
    fun cmd(c: Int, d1: Int = 0, d2: Int = 0): ByteArray { val b = bytes(0x03, 0x0A, c, d1, d2, 0); b[5] = xorAll(b, 0, 5).toByte(); return b }
    val TARE = cmd(0x01)

    fun decode(b: ByteArray): WeightReading? {
        if (b.size < 10 || u8(b, 0) != 0x03 || u8(b, 1) != 0x0B) return null
        val sign = u8(b, 6)
        val neg = sign == 0x2D || sign == 0x01      // HYPOTHÈSE : '-' ASCII ou 1
        val g = u24be(b, 7) / 100.0 * (if (neg) -1 else 1)
        return WeightReading(g, "g", g, 1, false)   // pas d'indicateur stable : déduit par l'appli
    }
}

class BookooDriver : ScaleDriver() {
    override val subscribe = listOf("FF11")
    override fun onNotify(char: String, data: ByteArray, s: DriverSession) = listOfNotNull(Bookoo.decode(data))
    override fun tare(s: DriverSession): Boolean { s.write("FF12", Bookoo.TARE); return true }
}

// ---------------------------------------------------------------- Inconnu
/** Abonne toutes les caractéristiques notify/indicate et journalise tout, sans décoder. */
class UnknownDriver : ScaleDriver() {
    override val subscribe = emptyList<String>()
    override fun onNotify(char: String, data: ByteArray, s: DriverSession) = emptyList<WeightReading>()
}
