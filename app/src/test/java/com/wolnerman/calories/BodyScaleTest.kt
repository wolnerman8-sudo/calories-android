package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * v2.5 — balance pèse-personne. Provenance des trames (indiquée sur chaque test) :
 *  [RÉEL]      capture d'une vraie balance publiée dans les tests openScale
 *              (QNHandlerBroadcastFT26RTest.kt, FITINDEX FT-26R-W, capturé le 2026-09-07, écran 70,30 kg).
 *  [OPENSCALE] vecteurs de test écrits par les auteurs d'openScale d'après le format documenté
 *              (OkOkHandlerTest.kt, ChipseaBleLibTest.kt — eux-mêmes « synthetic » selon leurs commentaires).
 *  [SYNTHÈSE]  trame construite ici à partir du format documenté dans OkOkHandler.kt (aucune capture réelle publiée).
 * Source : github.com/oliexdev/openScale, commit 2f5eeb7 (android_app/app/src/test/…).
 */
class BodyScaleTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun near(exp: Double, act: Double?, tol: Double = 0.01) = assertEquals(exp, act!!, tol)

    // ------------------------------------------------------------ OKOK C0 [OPENSCALE] OkOkHandlerTest
    private val mac = "0C1122334455"
    private val address = "0C:11:22:33:44:55"
    private val idle = hex("0000 0000 0000 20 $mac")
    private val settling = hex("02BC 0000 0000 20 $mac")
    private val impedance = hex("02BC 1194 0000 20 $mac")
    private val stable = hex("02BC 1194 0000 21 $mac")

    @Test fun c0StableFrameIs70kg() {
        val r = OkokAdv.parseC0(stable)!!
        near(70.0, r.kg); assertEquals(true, r.stable); near(450.0, r.impedanceOhm)
    }

    @Test fun c0UnstableAndIdleFrames() {
        assertEquals(false, OkokAdv.parseC0(settling)!!.stable)
        near(70.0, OkokAdv.parseC0(settling)!!.kg)
        assertEquals(false, OkokAdv.parseC0(impedance)!!.stable)
        near(0.0, OkokAdv.parseC0(idle)!!.kg)
        assertNull(BodyStability().feed(OkokAdv.parseC0(idle)!!, 0))      // 0 kg ignoré
        assertNull(OkokAdv.parseC0(ByteArray(6)))                          // trop court
    }

    @Test fun c0DetectionByOwnMacUnderAnyName() {
        // openScale : « OMIYA-C39-HW » + identifiant 0x4AC0 (l'octet haut change d'une trame à l'autre)
        val ms = listOf(ManufData(0x4AC0, stable))
        assertEquals(BodyKind.OKOK_ADV, BodyScaleDetect.detect("OMIYA-C39-HW", address, emptyList(), ms, false))
        assertEquals(BodyKind.OKOK_ADV, BodyScaleDetect.detect("OMIYA-C39-HW", address, emptyList(), listOf(ManufData(0xF7C0, idle)), false))
        val foreign = listOf(ManufData(0x4AC0, hex("02BC 1194 0000 21 AABBCCDDEEFF")))
        assertNull(BodyScaleDetect.detect("OMIYA-C39-HW", address, emptyList(), foreign, false))
        assertEquals(BodyKind.OKOK_ADV, BodyScaleDetect.detect("ADV", "11:22:33:44:55:66", emptyList(), listOf(ManufData(0x4AC0, ByteArray(13))), false))
    }

    @Test fun scanRecordParsingWrapsCompanyIdLittleEndian() {
        // [SYNTHÈSE] enveloppe d'annonce autour de la trame openScale : AD 0xFF, identifiant 0x4AC0 → octets C0 4A
        val nameAd = byteArrayOf(4, 0x09, 'A'.code.toByte(), 'D'.code.toByte(), 'V'.code.toByte())
        val block = byteArrayOf(0xC0.toByte(), 0x4A) + stable
        val rec = nameAd + byteArrayOf((block.size + 1).toByte(), 0xFF.toByte()) + block + ByteArray(5)
        val ms = BleAdv.manufacturer(rec)
        assertEquals(1, ms.size); assertEquals(0x4AC0, ms[0].companyId)
        near(70.0, OkokAdv.parse(ms)!!.kg)
        assertTrue(BleAdv.manufacturer(byteArrayOf(0x20, 0xFF.toByte(), 1)).isEmpty())   // longueur incohérente : pas de crash
        assertTrue(BleAdv.manufacturer(null).isEmpty())
    }

    // ------------------------------------------------------------ OKOK V20 / V11 / VF0 [SYNTHÈSE]
    private fun v20(flags: Int, raw: Int, imp: Int): ByteArray {
        val d = ByteArray(19)
        d[6] = flags.toByte(); d[8] = (raw shr 8).toByte(); d[9] = raw.toByte(); d[10] = (imp shr 8).toByte(); d[11] = imp.toByte()
        var x = 0x20; for (i in 0 until 12) x = x xor (d[i].toInt() and 0xFF); d[12] = x.toByte()
        return d
    }

    @Test fun v20FinalUnstableAndChecksum() {
        val fin = OkokAdv.parseV20(v20(0x01, 688, 5000))!!               // [SYNTHÈSE] 68,8 kg, ÷10, finale
        near(68.8, fin.kg); assertEquals(true, fin.stable); near(500.0, fin.impedanceOhm)
        val live = OkokAdv.parseV20(v20(0x04, 6880, 0))!!                // [SYNTHÈSE] ÷100, non finale
        near(68.8, live.kg); assertEquals(false, live.stable)
        val bad = v20(0x01, 688, 0).also { it[12] = (it[12] + 1).toByte() }
        assertNull(OkokAdv.parseV20(bad))                                 // somme XOR fausse
        assertNull(OkokAdv.parseV20(ByteArray(16)))                       // mauvaise longueur
        assertEquals(BodyKind.OKOK_ADV, BodyScaleDetect.detect("ADV", "AA:BB:CC:DD:EE:FF", emptyList(), listOf(ManufData(0x20CA, ByteArray(16))), false))
    }

    @Test fun v11UnitsAndChecksum() {
        fun v11(raw: Int, props: Int): ByteArray {
            val d = ByteArray(23); d[3] = (raw shr 8).toByte(); d[4] = raw.toByte(); d[9] = props.toByte()
            var x = 0xCA xor 0x11; for (i in 0 until 16) x = x xor (d[i].toInt() and 0xFF); d[16] = x.toByte(); return d
        }
        near(68.8, OkokAdv.parseV11(v11(688, 0x00))!!.kg)                 // [SYNTHÈSE] kg ÷10
        assertNull(OkokAdv.parseV11(v11(688, 0x00))!!.stable)             // pas d'indicateur
        near(68.8, OkokAdv.parseV11(v11(1376, 0x08))!!.kg)                // jin ÷10 : 137,6 jin = 68,8 kg
        near(1517.0 / 10 * BodyUnits.LB_TO_KG, OkokAdv.parseV11(v11(1517, 0x10))!!.kg)  // lb
        assertNull(OkokAdv.parseV11(v11(688, 0).also { it[16] = 0 }.also { if (it[16].toInt() == 0) it[16] = 1 }))
    }

    @Test fun vf0LittleEndianTenths() {
        near(68.8, OkokAdv.parseVF0(hex("0000 B002"))!!.kg)               // [SYNTHÈSE] 0x02B0 = 688
        assertNull(OkokAdv.parseVF0(hex("00 00")))
    }

    // ------------------------------------------------------------ QN AABB [RÉEL] FT-26R
    private val moving1 = hex("aabb00005e0053016d000000ffffff1400671b510e032100")
    private val settled1 = hex("aabb00005e00530172000000ffffff1500761b510e032300")
    private val settled3 = hex("aabb00005e005301f0000000ffffff15007b1b510e034400")
    private val qnIdle = hex("aabb00005e00530100f09f6affffff15a00000510e03500f")

    @Test fun qnRealCaptures() {
        near(70.30, QnAdv.parse(settled1)!!.kg); assertEquals(true, QnAdv.parse(settled1)!!.stable)
        near(70.35, QnAdv.parse(settled3)!!.kg)
        near(70.15, QnAdv.parse(moving1)!!.kg); assertEquals(false, QnAdv.parse(moving1)!!.stable)
        assertNull(QnAdv.parse(qnIdle))                                    // écran éteint / 0 kg
        assertEquals(BodyKind.QN_ADV, BodyScaleDetect.detect(null, "AA:BB:CC:DD:EE:FF", emptyList(), listOf(ManufData(0xFFFF, settled1)), false))
        // [SYNTHÈSE dérivée] signature FT-26R retirée : seul le bit 5 compte
        val foreign = settled1.copyOf().also { it[19] = 0; it[20] = 0; it[21] = 0 }
        assertEquals(false, QnAdv.parse(foreign)!!.stable)
    }

    // ------------------------------------------------------------ Chipsea-BLE GATT [OPENSCALE] ChipseaBleLibTest
    @Test fun chipseaSingleFrames() {
        val g = ChipseaGatt()
        val live = g.feed(hex("CA 10 12 00 00 02 F3"))!!
        near(75.5, live.kg); assertEquals(false, live.stable)
        val locked = g.feed(hex("CA 10 12 00 01 02 F3 00 EA 02 27 01 48 06 72 00 5A 1D"))!!
        near(75.5, locked.kg); assertEquals(true, locked.stable); assertTrue(locked.extra!!.contains("23,4"))
        near(74.98, g.feed(hex("CA 10 12 00 10 06 75"))!!.kg)             // 165,3 lb
        val v11 = g.feed(hex("CA 11 0A 01 00 02 F3 00 00 00 00 01"))!!
        near(75.5, v11.kg); assertEquals(true, v11.stable)
    }

    @Test fun chipseaMultiPacketAndHistory() {
        val g = ChipseaGatt()
        assertNull(g.feed(hex("CA 11 10 12 12 00 00 00 00 01 00 00 00 00 01 02 F3 00 EA")))
        val m = g.feed(hex("CA 11 0B 12 22 02 27 01 48 06 72 00 5A 1D"))!!
        near(75.5, m.kg); assertEquals(true, m.stable)
        // Morceaux incohérents : abandon sans exception
        assertNull(g.feed(hex("CA 11 06 12 15 01 02 03 04"))); assertNull(g.feed(hex("CA 11 06 12 45 05 06 07 08"))); assertNull(g.feed(hex("CA 11 06 12 33 09 0A 0B 0C")))
        // Historique : jamais compté comme pesée en cours
        val h = g.feed(hex("CA 11 19 13 11 65 53 F1 00 01 00 00 00 00 01 02 F3 00 EA 02 27 01 48 06 72 00 5A 1D"))!!
        near(75.5, h.kg); assertEquals(false, h.stable)
    }

    @Test fun chipseaForeignFramesIgnored() {
        val g = ChipseaGatt()
        assertNull(g.feed(ByteArray(0))); assertNull(g.feed(hex("FA 04 01 02"))); assertNull(g.feed(hex("CA 10 12"))); assertNull(g.feed(hex("CA 11 02 07 00")))
    }

    @Test fun chipseaUserSyncMatchesOpenScale() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 21, 23, 30, 15); set(Calendar.MILLISECOND, 0) }.time
        assertArrayEquals(hex("CA 10 0E 01 1A 0A 15 17 1E 0F 00 00 00 01 01 28 AA 9E 00 00"), ChipseaGatt.userSync(1, true, 40, 170, now))
    }

    // ------------------------------------------------------------ Norme SIG [SYNTHÈSE]
    @Test fun sigWeightMeasurementKg() {
        near(68.8, BodySig.decode("2A9D", hex("00 C0 35"))!!.kg)          // 13760 × 0,005 kg
        near(68.8 , BodySig.decode("2A9D", hex("01 42 3B"))!!.kg, 0.02)    // 151,70 lb
    }

    // ------------------------------------------------------------ Détection / non-interférence
    @Test fun detectionNamesAndKitchenExclusion() {
        assertEquals(BodyKind.CHIPSEA_GATT, BodyScaleDetect.detect("Chipsea-BLE", "AA:BB:CC:DD:EE:FF", listOf("FFF0"), emptyList(), false))
        assertNull(BodyScaleDetect.detect("Decent Scale", "AA:BB:CC:DD:EE:FF", listOf("FFF0"), emptyList(), false))
        assertNull(BodyScaleDetect.detect("AiLink-123", "AA:BB:CC:DD:EE:FF", listOf("FFE0"), listOf(ManufData(0x4AC0, stable)), false))
        assertNull(BodyScaleDetect.detect("ADV", address, emptyList(), listOf(ManufData(0x4AC0, stable)), aiLink = true))
        assertNull(BodyScaleDetect.detect("Galaxy Buds", "AA:BB:CC:DD:EE:FF", emptyList(), listOf(ManufData(0x0075, ByteArray(8))), false))
        assertEquals(BodyKind.CANDIDATE, BodyScaleDetect.detect("Electronic Scale", "AA:BB:CC:DD:EE:FF", emptyList(), emptyList(), false))
        assertEquals(BodyKind.SIG_WEIGHT, BodyScaleDetect.detect("Mi Scale", "AA:BB:CC:DD:EE:FF", listOf("181D"), emptyList(), false))
        // La balance de cuisine garde son pilote : rien n'a changé côté ScaleDrivers
        assertEquals("ailink", ScaleDrivers.detect(AdvData("AiLink-123", listOf("FFE0"), null))?.id)
    }

    // ------------------------------------------------------------ Conversions
    @Test fun unitConversions() {
        near(68.8, BodyUnits.toKg(0x02, 0xB0, 0x00))                       // kg ÷10
        near(68.8, BodyUnits.toKg(0x00, 0x45, 0x02) - 0.2)                 // ÷1 : 69 kg
        near(68.8, BodyUnits.toKg(0x1A, 0xE0, 0x04))                       // ÷100 : 6880
        near(68.8, BodyUnits.toKg(0x05, 0x60, 0x08))                       // jin : 137,6
        near(10 * BodyUnits.ST_TO_KG + 11.7 * BodyUnits.LB_TO_KG, BodyUnits.toKg(10, 117, 0x18))  // 10 st 11,7 lb
        assertEquals(0.45359237, BodyUnits.LB_TO_KG, 1e-9)
    }

    // ------------------------------------------------------------ Stabilité / session / doublons
    @Test fun stabilityInferredWhenScaleHasNoFlag() {
        val s = BodyStability()
        val r = BodyReading(68.8, null, "test")
        assertEquals(false, s.feed(r, 0)!!.stable)
        assertEquals(false, s.feed(r.copy(kg = 68.82), 1500)!!.stable)
        assertEquals(true, s.feed(r.copy(kg = 68.8), 2100)!!.stable)
        assertEquals(false, s.feed(r.copy(kg = 69.4), 2200)!!.stable)      // le poids a bougé
        assertEquals(false, s.feed(BodyReading(68.8, false, "t"), 9000)!!.stable)  // l'indicateur de la balance prime
        assertNull(s.feed(BodyReading(0.0, true, "t"), 9100))
        assertNull(s.feed(BodyReading(400.0, true, "t"), 9200))
    }

    @Test fun oneAutoSavePerWeighIn() {
        val w = WeighInSession()
        assertNull(w.onFrame(68.8, false, 0))
        assertEquals(68.8, w.onFrame(68.8, true, 1000)!!, 0.0)
        assertNull(w.onFrame(68.8, true, 1500))                            // même pesée : une seule fois
        assertNull(w.onFrame(0.0, false, 5000))                            // descend de la balance
        assertEquals(68.7, w.onFrame(68.7, true, 9000)!!, 0.0)             // nouvelle pesée
        assertEquals(68.7, w.onFrame(68.7, true, 60_000)!!, 0.0)           // réarmée après 30 s sans trame
    }

    @Test fun dedupeWithinTwoMinutes() {
        val l = listOf(WeighIn(1_000_000, 68.8, "t"))
        assertTrue(WeighInRules.isDuplicate(68.8, 1_000_000 + 119_000, l))
        assertTrue(WeighInRules.isDuplicate(68.82, 1_000_000 - 60_000, l))
        assertFalse(WeighInRules.isDuplicate(68.8, 1_000_000 + 180_000, l))
        assertFalse(WeighInRules.isDuplicate(68.4, 1_000_000 + 30_000, l))
        assertEquals("68,8 kg", WeighInRules.fmtKg(68.8))
    }
}
