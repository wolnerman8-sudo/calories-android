package com.wolnerman.calories

import java.util.Locale

/**
 * Chiffrement des payloads A7 AiLink, réimplémenté en Kotlin.
 *
 * CONFIRMÉ par désassemblage de la fonction native officielle
 * Java_com_pinwang_ailinkble_AiLinkPwdUtil_mcuEncrypt (libAILinkBle-lib.so, AILinkSecretAndroid 2.0.0) :
 *   - les octets du CID valant 0x00 sont remplacés par 0x01 ;
 *   - out[i] = in[i] XOR mac[i % 6] XOR cid[i % 2]
 * où mac = les 6 octets du MAC dans l'ordre PETIT-BOUTISTE (AiLinkBleCheckUtil inverse la chaîne « AA:BB:… »).
 * L'opération est symétrique (chiffrer = déchiffrer).
 */
object AiLinkCipher {
    fun xor(cid: Int, macLE: ByteArray, data: ByteArray): ByteArray {
        val c0 = ((cid shr 8) and 0xFF).let { if (it == 0) 1 else it }
        val c1 = (cid and 0xFF).let { if (it == 0) 1 else it }
        return ByteArray(data.size) { i ->
            val m = macLE[i % 6].toInt()
            val c = if (i % 2 == 0) c0 else c1
            (data[i].toInt() xor m xor c).toByte()
        }
    }

    /** "AA:BB:CC:DD:EE:FF" → octets dans l'ordre de la chaîne (gros-boutiste). */
    fun macBytes(mac: String): ByteArray? {
        val p = mac.split(":", "-").filter { it.isNotBlank() }
        if (p.size != 6) return null
        return runCatching { ByteArray(6) { p[it].toInt(16).toByte() } }.getOrNull()
    }

    fun macStr(b: ByteArray) = b.joinToString(":") { String.format(Locale.US, "%02X", it) }
}

/** Champs bruts d'un payload poids (pour le journal), même s'ils sont incohérents. */
data class AiLinkFields(val type: Int, val seq: Int, val raw: Int, val unit: Int, val dec: Int, val sign: Int, val kind: Int) {
    override fun toString() = (if (seq >= 0) "type=%02X seq=%d " else "type=%02X ").format(type, seq) +
        "poids=%d unité=%02X déc=%d signe=%d état=%s".format(raw, unit, dec, sign, when (kind) { 1 -> "1 (en mesure)"; 2 -> "2 (stable)"; else -> kind.toString() })
    companion object {
        fun of(p: ByteArray, cid: Int = -1): AiLinkFields? {
            if (p.size < 8) return null
            fun u(i: Int) = p[i].toInt() and 0xFF
            val noSeq = cid == ScaleDecoder.CID_KITCHEN_8A || (cid != AiLinkProtocol.CID_NUTRITION && cid != AiLinkProtocol.CID_NUTRITION_NUTRIENT && p.size == 8)
            return if (noSeq) AiLinkFields(u(0), -1, (u(1) shl 16) or (u(2) shl 8) or u(3), u(4), u(5), u(6), u(7))
            else AiLinkFields(u(0), u(1), (u(2) shl 16) or (u(3) shl 8) or u(4), u(5), u(6), u(7), if (p.size >= 9) u(8) else -1)
        }
    }
}

/**
 * Choix automatique de la bonne clé de déchiffrement AiLink.
 *
 * Plusieurs hypothèses sont testées en parallèle sur les premières trames : MAC de l'annonce (petit-boutiste, ordre officiel),
 * MAC de l'annonce inversé, adresse Bluetooth Android (dans les deux ordres) et « pas de chiffrement ».
 * Chaque hypothèse gagne des points si la trame décodée est cohérente : structure valide, poids plausible (≤ 10,5 kg),
 * signe positif, numéro de séquence qui avance de 0 ou 1, nombre de décimales constant. La meilleure est verrouillée.
 */
class AiLinkKeyPicker(candidates: List<Pair<String, ByteArray?>>) {
    class Cand(val label: String, val macLE: ByteArray?) {
        var score = 0
        var frames = 0
        var lastSeq = -1
        var lastDec = -1
    }
    val cands: List<Cand> = candidates.distinctBy { it.second?.toList() }.map { Cand(it.first, it.second) }
    var locked: Cand? = null; private set

    fun clear(c: Cand, cid: Int, payload: ByteArray): ByteArray = c.macLE?.let { AiLinkCipher.xor(cid, it, payload) } ?: payload

    data class Eval(val cand: Cand, val clear: ByteArray, val fields: AiLinkFields?, val reading: AiLinkReading?, val delta: Int)

    /** Évalue une trame pour toutes les hypothèses (met à jour les scores tant que rien n'est verrouillé). */
    fun evaluate(cid: Int, payload: ByteArray): List<Eval> {
        val list = (locked?.let { listOf(it) } ?: cands).map { c ->
            val clear = clear(c, cid, payload)
            val f = AiLinkFields.of(clear, cid)
            val r = ScaleDecoder.decodeWeight(clear, cid)
            var d = 0
            if (f == null || f.type != AiLinkProtocol.TYPE_WEIGHT) {
                d = 0 // autre type de trame : neutre
            } else if (r == null) d -= 3
            else {
                d += 2
                val g = r.grams
                if (g != null && kotlin.math.abs(g) <= 10_500) d += 1 else d -= 3
                if (!r.negative) d += 1 else d -= 1
                if (c.lastSeq >= 0 && r.seq >= 0) { val ds = (r.seq - c.lastSeq + 256) % 256; d += if (ds <= 1) 2 else -1 }
                if (c.lastDec >= 0) d += if (c.lastDec == r.decimals) 1 else -2
            }
            Eval(c, clear, f, r, d)
        }
        if (locked == null) {
            list.forEach { e ->
                e.cand.score += e.delta
                if (e.fields?.type == AiLinkProtocol.TYPE_WEIGHT) {
                    e.cand.frames++
                    e.reading?.let { e.cand.lastSeq = it.seq; e.cand.lastDec = it.decimals }
                }
            }
            val sorted = cands.sortedByDescending { it.score }
            val best = sorted.first()
            val second = sorted.getOrNull(1)?.score ?: Int.MIN_VALUE
            if (best.frames >= 6 && best.score >= 20 && best.score - second >= 6) locked = best
            // Verrouillage rapide : 3 trames valides pour une hypothèse (la première de la liste gagne les égalités, ex. « MAC annonce ») et aucune autre crédible
            else if (best.frames >= 3 && best.score >= 12 && second <= 0) locked = best
        } else {
            list.forEach { e -> e.reading?.let { e.cand.lastSeq = it.seq; e.cand.lastDec = it.decimals } }
        }
        return list
    }

    /** Hypothèse en tête (verrouillée ou provisoire), seulement si elle est franchement crédible. */
    fun best(): Cand? = locked ?: cands.maxByOrNull { it.score }?.takeIf { it.frames >= 3 && it.score >= 9 }
}
