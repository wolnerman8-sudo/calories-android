package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

/** Trames RÉELLES de la balance FS5H_2ca4 (journal Calories 1.5 du 23/11/2026), MAC 01:BA:01:0C:2C:A4, CID 0x008A. */
class Fs5hRealFramesTest {
    private fun hex(s: String) = s.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()
    private val advLE = hex("A4 2C 0C 01 BA 01")   // MAC de l'annonce (petit-boutiste)

    private fun decode(frame: String): AiLinkReading? {
        val f = ScaleDecoder.splitA7(hex(frame)).single()
        assertTrue(f.checksumOk); assertEquals(0x008A, f.cid)
        return ScaleDecoder.decodeWeight(AiLinkCipher.xor(f.cid, advLE, f.payload), f.cid)
    }

    @Test fun zeroEnMesure() {
        val r = decode("A7 00 8A 08 A4 A6 0D 8B BB 8B A5 A7 06 7A")!!
        assertEquals(0.0, r.value, 1e-9); assertFalse(r.negative); assertFalse(r.stable); assertEquals("g", r.unitLabel)
    }
    @Test fun g143EnMesure() {
        val r = decode("A7 00 8A 08 A4 A6 0D 04 BB 8B A5 A7 7F 7A")!!
        assertEquals(143.0, r.value, 1e-9); assertFalse(r.stable); assertEquals(143.0, r.grams!!, 1e-9)
    }
    @Test fun g143Stable() {
        val r = decode("A7 00 8A 08 A4 A6 0D 04 BB 8B A5 A4 7C 7A")!!
        assertEquals(143.0, r.value, 1e-9); assertTrue(r.stable)
    }
    @Test fun g9Stable() {
        val r = decode("A7 00 8A 08 A4 A6 0D 82 BB 8B A5 A4 FA 7A")!!
        assertEquals(9.0, r.value, 1e-9); assertTrue(r.stable)
    }
    @Test fun dechiffrementExact() {
        val f = ScaleDecoder.splitA7(hex("A7 00 8A 08 A4 A6 0D 04 BB 8B A5 A7 7F 7A")).single()
        assertEquals("01 00 00 8F 00 00 00 01", ScaleDecoder.hex(AiLinkCipher.xor(0x8A, advLE, f.payload)))
        assertEquals("type=01 poids=143 unité=00 déc=0 signe=0 état=1 (en mesure)",
            AiLinkFields.of(AiLinkCipher.xor(0x8A, advLE, f.payload), 0x8A).toString())
    }
    @Test fun autresValeursDuJournal() {
        // 0x09, 0x0A, 0x11, 0x12, 0x8E, 0x97 grammes vus dans le journal
        mapOf("82 BB 8B A5 A7 FD" to 9.0, "81 BB 8B A5 A7 FC" to 10.0, "9A BB 8B A5 A7 15" to 17.0,
              "99 BB 8B A5 A7 14" to 18.0, "05 BB 8B A5 A7 80" to 142.0, "1C BB 8B A5 A7 97" to 151.0).forEach { (tail, g) ->
            assertEquals(g, decode("A7 00 8A 08 A4 A6 0D $tail 7A")!!.value, 1e-9)
        }
    }

    /** Pilote complet : la clé « MAC annonce » est verrouillée vite et les poids sortent justes, jamais négatifs. */
    @Test fun piloteSurLeJournal() {
        val s = object : DriverSession {
            val logs = mutableListOf<String>()
            override fun write(charUuid: String, data: ByteArray) {}
            override fun log(s: String) { logs += s }
            override val cryptMac = "01:BA:01:0C:2C:A4"
            override val advMacLE = advLE
            override val address = "01:BA:01:0C:2C:A4"
        }
        val drv = AiLinkDriver(null, 0x8A)
        val frames = listOf(
            "A7 00 8A 08 A4 A6 0D 8B BB 8B A5 A7 06 7A", "A7 00 8A 08 A4 A6 0D 8B BB 8B A5 A7 06 7A",
            "A7 00 8A 08 A4 A6 0D 8B BB 8B A5 A4 03 7A", "A7 00 8A 08 A4 A6 0D 99 BB 8B A5 A7 14 7A",
            "A7 00 8A 08 A4 A6 0D 0B BB 8B A5 A7 86 7A", "A7 00 8A 08 A4 A6 0D 04 BB 8B A5 A7 7F 7A",
            "A7 00 8A 08 A4 A6 0D 04 BB 8B A5 A4 7C 7A")
        val got = frames.flatMap { drv.onNotify("FFE2", hex(it), s) }
        assertTrue(s.logs.any { it.contains("Clé retenue : MAC annonce") })
        assertTrue(got.isNotEmpty())
        assertTrue(got.all { it.value >= 0 && it.value <= 143 })
        assertEquals(143.0, got.last().value, 1e-9); assertTrue(got.last().stable)
    }
}
