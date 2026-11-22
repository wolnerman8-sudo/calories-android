package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

class ScaleDriversTest {
    private fun b(vararg x: Int) = ByteArray(x.size) { x[it].toByte() }
    private fun hex(s: String) = s.replace(" ", "").replace("_", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private class FakeSession : DriverSession {
        val writes = mutableListOf<Pair<String, ByteArray>>()
        override fun write(charUuid: String, data: ByteArray) { writes += charUuid to data }
        override fun log(s: String) {}
        override val cryptMac = "02:11:23:34:61:12"
    }

    // ---------- Détection
    @Test fun detection() {
        assertEquals(ScaleDrivers.AILINK, ScaleDrivers.detect(AdvData("AiLink_1A2B", emptyList(), null)))
        assertEquals(ScaleDrivers.AILINK, ScaleDrivers.detect(AdvData(null, emptyList(), AiLinkAdv(0x34, 1, 1, ByteArray(6)))))
        assertEquals(ScaleDrivers.ETEKCITY, ScaleDrivers.detect(AdvData("Etekcity Nutrition Scale", emptyList(), null)))
        assertEquals(ScaleDrivers.DECENT, ScaleDrivers.detect(AdvData("Decent Scale", emptyList(), null)))
        assertEquals(ScaleDrivers.BOOKOO, ScaleDrivers.detect(AdvData("BOOKOO_SC 1234", emptyList(), null)))
        assertEquals(ScaleDrivers.SIG_WEIGHT, ScaleDrivers.detect(AdvData("Scale", listOf("181D"), null)))
        assertEquals(ScaleDrivers.SIG_BODY, ScaleDrivers.detect(AdvData(null, listOf("181B"), null)))
        assertNull(ScaleDrivers.detect(AdvData("Galaxy Buds", listOf("FD69"), null)))
        assertEquals(ScaleDrivers.SIG_WEIGHT, ScaleDrivers.detectByServices(listOf("00001800-0000-1000-8000-00805f9b34fb", "0000181d-0000-1000-8000-00805f9b34fb"), null))
        assertEquals(ScaleDrivers.ETEKCITY, ScaleDrivers.detectByServices(listOf("00001910-0000-1000-8000-00805f9b34fb"), "x"))
        assertNull(ScaleDrivers.detectByServices(listOf("00001800-0000-1000-8000-00805f9b34fb"), "x"))
    }

    @Test fun uuid128DansAnnonce() {
        // Liste complète de services 128 bits (type 0x07) contenant 0x181D
        val u = b(0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, 0x00, 0x10, 0x00, 0x00, 0x1D, 0x18, 0x00, 0x00)
        val rec = b(17, 0x07) + u
        assertEquals(listOf("181D"), ScaleDecoder.advServices16(rec))
    }

    // ---------- Bluetooth SIG
    @Test fun sigWeightKg() {
        // flags 0 (SI), 0x3A98 = 15000 × 0,005 = 75 kg
        val r = SigScale.decodeWeightMeasurement(b(0x00, 0x98, 0x3A))!!
        assertEquals(75.0, r.value, 1e-9); assertEquals("kg", r.unitLabel); assertEquals(75000.0, r.grams!!, 1e-6); assertTrue(r.stable)
    }
    @Test fun sigWeightLbAvecHorodatage() {
        // flags 0x03 (impérial + horodatage), 16540 × 0,01 = 165,40 lb
        val r = SigScale.decodeWeightMeasurement(b(0x03, 0x9C, 0x40, 0xE8, 0x07, 1, 2, 3, 4, 5))!!
        assertEquals(165.40, r.value, 1e-9); assertEquals("lb", r.unitLabel); assertEquals(75024.0, r.grams!!, 1.0)
    }
    @Test fun sigBodyComposition() {
        // flags = poids présent (bit 10) + impédance (bit 9) ; masse grasse 213 = 21,3 % ; impédance ; poids 14000 → 70 kg
        val flags = (1 shl 10) or (1 shl 9)
        val r = SigScale.decodeBodyComposition(b(flags and 0xFF, flags shr 8, 213, 0, 0x10, 0x27, 0xB0, 0x36))!!
        assertEquals(70.0, r.value, 1e-9)
        assertTrue(r.extra!!.contains("21,3"))
        assertNull(SigScale.decodeBodyComposition(b(0, 0, 213, 0)))  // pas de poids
    }

    // ---------- Etekcity ESN00 (échantillons publics « metekcity »)
    @Test fun esn00Echantillons() {
        val r1 = Esn00.decode(hex("feefc0a2_d0_05_01146e0001_59")).single()
        assertEquals(-523.0, r1.value, 1e-9); assertEquals("g", r1.unitLabel); assertTrue(r1.stable)
        val r2 = Esn00.decode(hex("feefc0a2_d0_05_002a260200_27")).single()
        assertEquals(1079.0, r2.value, 1e-9); assertEquals("ml", r2.unitLabel); assertFalse(r2.stable)
        assertEquals(1079.0, r2.grams!!, 1e-9)
        // somme fausse → ignoré ; deux paquets collés → deux mesures
        assertTrue(Esn00.decode(hex("feefc0a2_d0_05_01146e0001_58")).isEmpty())
        assertEquals(2, Esn00.decode(hex("feefc0a2d00501146e000159feefc0a2d005002a26020027")).size)
    }
    @Test fun esn00Commande() {
        assertEquals("FE EF C0 A2 C1 01 01 C3", ScaleDecoder.hex(Esn00.packet(Esn00.SET_TARE, b(1))))
        val s = FakeSession(); assertTrue(EtekcityEsn00Driver().tare(s)); assertEquals("2C11", s.writes.single().first)
    }

    // ---------- Decent (exemples de la documentation officielle)
    @Test fun decentExemplesDoc() {
        assertEquals(0.0, Decent.decode(hex("03CE00000000CD"))!!.value, 1e-9)
        assertEquals(10.1, Decent.decode(hex("03CE00650000A8"))!!.value, 1e-9)
        assertEquals(194.0, Decent.decode(hex("03CE079400005E"))!!.value, 1e-9)
        assertEquals(705.9, Decent.decode(hex("03CE1B9300005E"))!!.value, 1e-9)
        assertEquals(1118.0, Decent.decode(hex("03CE2BAC010207000 04A".replace(" ", "")))!!.value, 1e-9)
        assertTrue(Decent.decode(hex("03CE00650000A8"))!!.stable)
        assertFalse(Decent.decode(hex("03CA00650000A8"))!!.stable)
        assertEquals(-1.0, Decent.decode(hex("03CEFFF60000"+"00"))!!.value, 1e-9)
        assertNull(Decent.decode(hex("03AA0100000000")))
    }
    @Test fun decentCommandes() {
        assertEquals("03 0F 00 00 00 00 0C", ScaleDecoder.hex(Decent.TARE))       // séquence « toujours valide » de la doc
        assertEquals("03 0A 01 01 00 00 09", ScaleDecoder.hex(Decent.LED_ON_GRAMS)) // identique au journal LightBlue de la doc
        val s = FakeSession(); DecentDriver().onReady(s); assertEquals("36F5", s.writes.single().first)
    }

    // ---------- BOOKOO (exemple de la documentation officielle : 10 g)
    @Test fun bookoo() {
        val pkt = ByteArray(20).also { hex("030B00000001000003E8").copyInto(it) }  // 0x0003E8 = 1000 → 10,00 g
        assertEquals(10.0, Bookoo.decode(pkt)!!.value, 1e-9)
        pkt[6] = 0x2D
        assertEquals(-10.0, Bookoo.decode(pkt)!!.value, 1e-9)
        assertNull(Bookoo.decode(hex("030D00000000000000000000")))
        assertEquals("03 0A 01 00 00 08", ScaleDecoder.hex(Bookoo.TARE))
    }

    // ---------- AiLink via l'architecture de pilotes
    @Test fun ailinkPilote() {
        val key = b(0x5A, 0xC3)
        val crypto = object : AiLinkCryptoApi {
            override fun handshakeStart() = b(0xA6, 0x11, 0x23)
            override fun handshakeAnswer(fromBle: ByteArray) = b(0xA6, 0x11, 0x24)
            override fun xorPayload(cid: Int, payload: ByteArray, mac: String) = ByteArray(payload.size) { (payload[it].toInt() xor key[it % 2].toInt()).toByte() }
        }
        val drv = AiLinkDriver(crypto, 0x34)
        val s = FakeSession()
        drv.onReady(s); assertEquals("FFE3", s.writes.last().first)
        assertTrue(drv.onNotify("FFE3", b(0xA6, 0x11, 0x23, 1, 2), s).isEmpty()); assertEquals(0x24, s.writes.last().second[2].toInt())
        val plain = b(0x01, 0x05, 0x00, 0x04, 0xD2, 0x00, 0x01, 0x00, 0x02)
        val frame = ScaleDecoder.buildA7(0x34, crypto.xorPayload(0x34, plain, ""))
        val r = drv.onNotify("FFE2", frame, s).single()
        assertEquals(123.4, r.value, 1e-9); assertTrue(r.stable); assertEquals(123.4, r.grams!!, 1e-9)
        assertTrue(drv.tare(s)); assertEquals("FFE1", s.writes.last().first)
    }

    @Test fun piloteInconnuNeDecodeRien() {
        val d = ScaleDrivers.UNKNOWN.create(null, 0)
        assertTrue(d.subscribe.isEmpty())
        assertTrue(d.onNotify("FFF1", b(1, 2, 3), FakeSession()).isEmpty())
    }
}
