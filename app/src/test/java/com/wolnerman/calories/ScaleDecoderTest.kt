package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

class ScaleDecoderTest {
    private fun b(vararg x: Int) = ByteArray(x.size) { x[it].toByte() }

    /** Exemple construit selon le manuel T1L/T2L : 123,4 g stable. */
    private val plain123 = b(0x01, 0x05, 0x00, 0x04, 0xD2, 0x00, 0x01, 0x00, 0x02) // 0x04D2 = 1234, 1 décimale, g

    @Test fun poidsEnGrammesStable() {
        val r = ScaleDecoder.decodeWeight(plain123)!!
        assertEquals(5, r.seq)
        assertEquals(123.4, r.value, 1e-9)
        assertEquals("g", r.unitLabel)
        assertTrue(r.stable)
        assertEquals(123.4, r.grams!!, 1e-9)
    }

    @Test fun poidsTempsReelNegatif() {
        val r = ScaleDecoder.decodeWeight(b(0x01, 0x01, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x01, 0x01))!!
        assertEquals(-10.0, r.value, 1e-9)
        assertFalse(r.stable)
    }

    @Test fun grosPoidsSur3Octets() {
        // 10 000 g = 0x002710
        val r = ScaleDecoder.decodeWeight(b(0x01, 0x02, 0x00, 0x27, 0x10, 0x00, 0x00, 0x00, 0x02))!!
        assertEquals(10000.0, r.value, 1e-9)
    }

    @Test fun conversions() {
        assertEquals(28.35, ScaleDecoder.decodeWeight(b(0x01, 0, 0, 0, 100, ScaleUnits.OZ, 2, 0, 2))!!.grams!!, 0.01) // 1,00 oz
        assertEquals(453.59, ScaleUnits.toGrams(1.0, ScaleUnits.LB)!!, 0.01)
        assertEquals(1500.0, ScaleUnits.toGrams(1.5, ScaleUnits.KG)!!, 1e-9)
        assertEquals(250.0, ScaleUnits.toGrams(250.0, ScaleUnits.ML_WATER)!!, 1e-9)
        assertEquals(103.0, ScaleUnits.toGrams(100.0, ScaleUnits.ML_MILK)!!, 1e-9)
        assertNull(ScaleUnits.toGrams(1.0, 0x55))
    }

    @Test fun rejetteLesPayloadsIncoherents() {
        assertNull(ScaleDecoder.decodeWeight(b(0x03, 0x00)))                              // résultat de changement d'unité
        assertNull(ScaleDecoder.decodeWeight(b(0x01, 0, 0, 0, 1, 0x20, 0, 0, 2)))          // unité inconnue
        assertNull(ScaleDecoder.decodeWeight(b(0x01, 0, 0, 0, 1, 0, 7, 0, 2)))             // décimales impossibles
        assertNull(ScaleDecoder.decodeWeight(b(0x01, 0, 0, 0, 1, 0, 0, 0, 9)))             // type de donnée inconnu
    }

    @Test fun trameA7ChecksumEtDecoupage() {
        val f = ScaleDecoder.buildA7(0x0034, plain123)
        assertEquals("A7 00 34 09 01 05 00 04 D2 00 01 00 02 1C 7A", ScaleDecoder.hex(f))
        val frames = ScaleDecoder.splitA7(f + f)
        assertEquals(2, frames.size)
        assertTrue(frames.all { it.checksumOk && it.cid == 0x34 })
        f[5] = 0x06
        assertFalse(ScaleDecoder.splitA7(f)[0].checksumOk)
    }

    @Test fun dechiffrementPuisRepliEnClair() {
        val key = b(0x11, 0x22, 0x33)
        val xor: (Int, ByteArray) -> ByteArray? = { _, p -> ByteArray(p.size) { (p[it].toInt() xor key[it % 3].toInt()).toByte() } }
        val enc = xor(0x34, plain123)!!
        val frame = ScaleDecoder.buildA7(0x0034, enc)
        assertEquals(123.4, ScaleDecoder.decodeNotification(frame, xor).single().second!!.value, 1e-9)
        // Balance non chiffrée : le déchiffrement donne n'importe quoi, on retombe sur le payload brut
        val clear = ScaleDecoder.buildA7(0x0034, plain123)
        assertEquals(123.4, ScaleDecoder.decodeNotification(clear, xor).single().second!!.value, 1e-9)
        // Bibliothèque absente
        assertEquals(123.4, ScaleDecoder.decodeNotification(clear, null).single().second!!.value, 1e-9)
    }

    @Test fun annonceAiLink() {
        // Exemple du manuel : 6e49 0001 0001 0001 126134231102 -> MAC 02:11:23:34:61:12
        val mfr = b(0x6E, 0x49, 0x00, 0x34, 0x00, 0x01, 0x00, 0x02, 0x12, 0x61, 0x34, 0x23, 0x11, 0x02)
        val rec = b(0x02, 0x01, 0x06, 0x03, 0x03, 0xE0, 0xFF, mfr.size + 1, 0xFF) + mfr + b(0x00, 0x00)
        val adv = ScaleDecoder.parseAdv(rec)!!
        assertEquals(0x34, adv.cid); assertEquals(1, adv.vid); assertEquals(2, adv.pid)
        assertEquals("02:11:23:34:61:12", adv.mac)
        assertEquals(listOf("FFE0"), ScaleDecoder.advServices16(rec))
        assertTrue(ScaleDecoder.looksLikeAiLink(null, rec))
        assertTrue(ScaleDecoder.looksLikeAiLink("AiLink_1A2B", null))
        assertFalse(ScaleDecoder.looksLikeAiLink("Galaxy Buds", b(0x02, 0x01, 0x06)))
    }

    @Test fun typeA6() {
        assertEquals(0x23, ScaleDecoder.a6Type(b(0xA6, 0x11, 0x23, 1, 2, 3)))
        assertNull(ScaleDecoder.a6Type(b(0xA7, 0x00, 0x34)))
    }
}
