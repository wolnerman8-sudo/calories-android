package com.wolnerman.calories

import java.util.Locale

/**
 * Protocole AiLink / Elink (Inet) pour balances de cuisine / nutrition Bluetooth LE.
 *
 * Sources (elinkthings) :
 *  - "T1L&T2L ailink 营养秤应用手册 V1.1" (2025-08) : trame A7, CID 0x0034, payload poids.
 *  - AILinkSDKParsingLibraryAndroid : BleNutritionData (CID 0x0034) et BleNutritionNutrientData (CID 0x006C).
 *  - AiLinkSecretFlutter / AILinkSecretToolDemo : UUID FFE0/FFE1/FFE2/FFE3, poignée de main A6 0x23/0x24,
 *    payload A7 chiffré (XOR dépendant du MAC et du CID) via la bibliothèque officielle AILinkSecretAndroid.
 *
 * Ce fichier est 100 % Kotlin pur (sans Android) pour pouvoir être testé.
 */
object AiLinkProtocol {
    const val SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb"
    const val CHAR_WRITE = "0000ffe1-0000-1000-8000-00805f9b34fb"        // APP -> MCU (A7)
    const val CHAR_NOTIFY = "0000ffe2-0000-1000-8000-00805f9b34fb"       // MCU -> APP (A7)
    const val CHAR_WRITE_NOTIFY = "0000ffe3-0000-1000-8000-00805f9b34fb" // APP <-> module BLE (A6, poignée de main)
    const val BROADCAST_SERVICE = "0000f0a0-0000-1000-8000-00805f9b34fb"
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"

    const val A6_HEAD = 0xA6
    const val A6_TAIL = 0x6A
    const val A7_HEAD = 0xA7
    const val A7_TAIL = 0x7A
    const val SET_HANDSHAKE = 0x23
    const val GET_HANDSHAKE = 0x24

    const val CID_NUTRITION = 0x0034
    const val CID_NUTRITION_NUTRIENT = 0x006C

    const val TYPE_WEIGHT = 0x01
    const val TYPE_UNIT_RESULT = 0x03
    const val TYPE_ZERO = 0x04
    const val TYPE_ERR = 0x05
}

/** Trame A7 brute découpée (payload encore éventuellement chiffré). */
data class A7Frame(val cid: Int, val payload: ByteArray, val checksumOk: Boolean)

/** Une mesure de poids AiLink décodée (unités AiLink). */
data class AiLinkReading(
    val seq: Int,
    val rawValue: Int,
    val unit: Int,
    val decimals: Int,
    val negative: Boolean,
    val stable: Boolean,
) {
    /** Valeur affichée par la balance, dans son unité. */
    val value: Double get() {
        var d = rawValue.toDouble()
        repeat(decimals) { d /= 10.0 }
        return if (negative) -d else d
    }
    val unitLabel: String get() = ScaleUnits.label(unit)
    /** Valeur convertie en grammes (null si unité inconnue). */
    val grams: Double? get() = ScaleUnits.toGrams(value, unit)
}

/** Advertising AiLink : company "In" (0x6E 0x49) + CID + VID + PID + MAC (petit-boutiste). */
data class AiLinkAdv(val cid: Int, val vid: Int, val pid: Int, val macLittleEndian: ByteArray) {
    /** MAC au format AA:BB:CC:DD:EE:FF (ordre normal). */
    val mac: String get() = macLittleEndian.reversedArray().joinToString(":") { String.format(Locale.US, "%02X", it) }
}

object ScaleUnits {
    const val G = 0x00; const val ML = 0x01; const val LB_OZ = 0x02; const val OZ = 0x03; const val KG = 0x04
    const val JIN = 0x05; const val ML_MILK = 0x06; const val ML_WATER = 0x07; const val FLOZ_MILK = 0x08
    const val FLOZ_WATER = 0x09; const val LB = 0x0A

    private const val OZ_G = 28.349523125
    private const val LB_G = 453.59237
    private const val FLOZ_ML = 29.5735295625
    private const val MILK_DENSITY = 1.03

    fun label(unit: Int): String = when (unit) {
        G -> "g"; ML -> "ml"; LB_OZ -> "lb:oz"; OZ -> "oz"; KG -> "kg"; JIN -> "jin"
        ML_MILK -> "ml (lait)"; ML_WATER -> "ml (eau)"; FLOZ_MILK -> "fl oz (lait)"; FLOZ_WATER -> "fl oz (eau)"; LB -> "lb"
        else -> "unité 0x%02X".format(unit)
    }

    /**
     * Conversion en grammes. ml/fl oz : densité de l'eau (1 g/ml) ou du lait (1,03 g/ml).
     * lb:oz : HYPOTHÈSE — la valeur transmise est le total en onces.
     */
    fun toGrams(value: Double, unit: Int): Double? = when (unit) {
        G -> value
        KG -> value * 1000.0
        OZ, LB_OZ -> value * OZ_G
        LB -> value * LB_G
        JIN -> value * 500.0
        ML, ML_WATER -> value
        ML_MILK -> value * MILK_DENSITY
        FLOZ_WATER -> value * FLOZ_ML
        FLOZ_MILK -> value * FLOZ_ML * MILK_DENSITY
        else -> null
    }
}

object ScaleDecoder {
    fun hex(b: ByteArray?): String = b?.joinToString(" ") { String.format(Locale.US, "%02X", it) } ?: "—"

    fun sum(b: ByteArray, from: Int, toExclusive: Int): Int {
        var s = 0
        for (i in from until toExclusive) s += b[i].toInt() and 0xFF
        return s and 0xFF
    }

    /**
     * Extrait toutes les trames A7 d'une notification (plusieurs trames peuvent être collées).
     * Format : A7 | CID(2, gros-boutiste) | len | payload(len) | somme(octets 1..n) | 7A
     */
    fun splitA7(data: ByteArray): List<A7Frame> {
        val out = mutableListOf<A7Frame>()
        var i = 0
        while (i < data.size) {
            if ((data[i].toInt() and 0xFF) != AiLinkProtocol.A7_HEAD || i + 4 > data.size) { i++; continue }
            val len = data[i + 3].toInt() and 0xFF
            val end = i + 4 + len + 2 // exclusif
            if (end > data.size || (data[end - 1].toInt() and 0xFF) != AiLinkProtocol.A7_TAIL) { i++; continue }
            val cid = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
            val payload = data.copyOfRange(i + 4, i + 4 + len)
            val ok = sum(data, i + 1, i + 4 + len) == (data[i + 4 + len].toInt() and 0xFF)
            out += A7Frame(cid, payload, ok)
            i = end
        }
        return out
    }

    /** Une trame A6 (module BLE) : A6 | len | type | données… | somme [| 6A]. Retourne le type ou null. */
    fun a6Type(data: ByteArray): Int? =
        if (data.size >= 3 && (data[0].toInt() and 0xFF) == AiLinkProtocol.A6_HEAD) data[2].toInt() and 0xFF else null

    /**
     * Décode un payload A7 EN CLAIR de type poids (CID 0x0034 / 0x006C et compatibles) :
     * [0]=0x01 [1]=n° de séquence [2..4]=poids (gros-boutiste) [5]=unité [6]=décimales [7]=signe [8]=1 temps réel / 2 stable
     */
    fun decodeWeight(p: ByteArray): AiLinkReading? {
        if (p.size < 8) return null
        if ((p[0].toInt() and 0xFF) != AiLinkProtocol.TYPE_WEIGHT) return null
        val raw = ((p[2].toInt() and 0xFF) shl 16) or ((p[3].toInt() and 0xFF) shl 8) or (p[4].toInt() and 0xFF)
        val unit = p[5].toInt() and 0xFF
        val dec = p[6].toInt() and 0xFF
        val sign = p[7].toInt() and 0xFF
        val type = if (p.size >= 9) p[8].toInt() and 0xFF else 0
        if (unit > 0x0A || dec > 3 || sign > 1) return null
        if (p.size >= 9 && type != 1 && type != 2) return null
        return AiLinkReading(p[1].toInt() and 0xFF, raw, unit, dec, sign == 1, type == 2)
    }

    /**
     * Décode une notification complète. [decrypt] déchiffre un payload (cid, payload) ; s'il échoue
     * ou donne un résultat incohérent, on essaie le payload tel quel (balances non chiffrées).
     */
    fun decodeNotification(data: ByteArray, decrypt: ((Int, ByteArray) -> ByteArray?)?): List<Pair<A7Frame, AiLinkReading?>> =
        splitA7(data).map { f ->
            val dec = if (f.checksumOk) decrypt?.let { runCatching { it(f.cid, f.payload.copyOf()) }.getOrNull() } else null
            val r = dec?.let { decodeWeight(it) } ?: decodeWeight(f.payload)
            f to r
        }

    /** Construit une trame A7 à partir d'un payload (déjà chiffré si nécessaire). */
    fun buildA7(cid: Int, payload: ByteArray): ByteArray {
        val out = ByteArray(payload.size + 6)
        out[0] = AiLinkProtocol.A7_HEAD.toByte()
        out[1] = (cid shr 8).toByte(); out[2] = cid.toByte()
        out[3] = payload.size.toByte()
        payload.copyInto(out, 4)
        out[out.size - 2] = sum(out, 1, 4 + payload.size).toByte()
        out[out.size - 1] = AiLinkProtocol.A7_TAIL.toByte()
        return out
    }

    /** Parcourt les structures AD d'un paquet d'annonce et retourne l'en-tête AiLink s'il existe. */
    fun parseAdv(scanRecord: ByteArray?): AiLinkAdv? {
        val r = scanRecord ?: return null
        var i = 0
        while (i < r.size) {
            val len = r[i].toInt() and 0xFF
            if (len == 0 || i + 1 + len > r.size) break
            val type = r[i + 1].toInt() and 0xFF
            if (type == 0xFF && len - 1 >= 14) {
                val d = r.copyOfRange(i + 2, i + 1 + len)
                if ((d[0].toInt() and 0xFF) == 0x6E && (d[1].toInt() and 0xFF) == 0x49) {
                    fun u16(o: Int) = ((d[o].toInt() and 0xFF) shl 8) or (d[o + 1].toInt() and 0xFF)
                    return AiLinkAdv(u16(2), u16(4), u16(6), d.copyOfRange(8, 14))
                }
            }
            i += 1 + len
        }
        return null
    }

    /** Liste des UUID de service 16 bits annoncés (pour le journal / le filtre). */
    fun advServices16(scanRecord: ByteArray?): List<String> {
        val r = scanRecord ?: return emptyList()
        val out = mutableListOf<String>()
        var i = 0
        while (i < r.size) {
            val len = r[i].toInt() and 0xFF
            if (len == 0 || i + 1 + len > r.size) break
            val type = r[i + 1].toInt() and 0xFF
            if (type == 0x02 || type == 0x03) {
                var j = i + 2
                while (j + 1 < i + 1 + len) {
                    out += String.format(Locale.US, "%04X", ((r[j + 1].toInt() and 0xFF) shl 8) or (r[j].toInt() and 0xFF))
                    j += 2
                }
            } else if (type == 0x06 || type == 0x07) {
                // UUID 128 bits (petit-boutiste) : on garde la forme courte si c'est la base Bluetooth SIG
                var j = i + 2
                while (j + 15 < i + 1 + len) {
                    val u = r.copyOfRange(j, j + 16).reversedArray()
                    val hex = u.joinToString("") { String.format(Locale.US, "%02X", it) }
                    out += if (hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB")) hex.substring(4, 8) else hex
                    j += 16
                }
            }
            i += 1 + len
        }
        return out
    }

    /** Heuristique : ce périphérique ressemble-t-il à une balance AiLink ? */
    fun looksLikeAiLink(name: String?, scanRecord: ByteArray?): Boolean {
        if (parseAdv(scanRecord) != null) return true
        val s = advServices16(scanRecord)
        if ("FFE0" in s || "F0A0" in s) return true
        val n = name?.lowercase(Locale.ROOT) ?: return false
        return listOf("ailink", "scale", "nutrition", "kitchen", "balance", "elink", "food").any { it in n }
    }
}
