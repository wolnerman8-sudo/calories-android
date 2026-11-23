package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

/** v2.6 : repères de composition corporelle et lecture de la masse grasse / musculaire des balances. */
class CompositionGuideTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun rangesByAge() {
        assertEquals(CompositionRanges.Range(22.0, 33.0, "Repères généraux"), CompositionRanges.forAge(null))
        assertEquals(20.0, CompositionRanges.forAge(39).minFat, 0.0)
        assertEquals(30.0, CompositionRanges.forAge(25).maxFat, 0.0)
        assertEquals(CompositionRanges.Range(25.0, 35.0, "Zone indicative autour de 48 ans"), CompositionRanges.forAge(40))
        assertEquals(25.0, CompositionRanges.forAge(49).minFat, 0.0)
        assertEquals(CompositionRanges.Range(26.0, 36.0, "Zone indicative en périménopause"), CompositionRanges.forAge(50))
        assertEquals(36.0, CompositionRanges.forAge(59).maxFat, 0.0)
        assertEquals(CompositionRanges.Range(27.0, 37.0, "Repères adaptés à votre âge"), CompositionRanges.forAge(60))
        assertEquals("25–35 %", CompositionRanges.rangeText(CompositionRanges.forAge(48)))   // plus de gabarit corrompu
    }

    @Test fun fatMessages() {
        assertNull(CompositionRanges.fatMessage(null, 48))
        assertNull(CompositionRanges.fatMessage(30.0, 48))
        assertNull(CompositionRanges.fatMessage(35.0, 48))          // bornes incluses
        assertEquals(CompositionRanges.ABOVE, CompositionRanges.fatMessage(35.1, 48))
        assertEquals(CompositionRanges.BELOW, CompositionRanges.fatMessage(24.9, 48))
        assertTrue(CompositionRanges.ABOVE.startsWith("Votre masse grasse est un peu au-dessus"))
        assertEquals(CompositionRanges.ABOVE, CompositionRanges.fatMessage(31.0, 30))
        assertNull(CompositionRanges.fatMessage(31.0, null))
    }

    @Test fun parseCompositionText() {
        assertEquals(23.4 to 32.8, parseComposition("graisse 23,4 % · eau 55,1 % · muscle 32,8 %"))
        assertEquals(21.3 to null, parseComposition("masse grasse 21,3 %"))
        assertEquals(null to null, parseComposition("historique"))
        assertEquals(null to null, parseComposition(null))
        assertEquals(19.5 to null, parseComposition("Graisse 19.5%"))
    }

    @Test fun chipseaFramesExposeFatAndMuscle() {
        // [OPENSCALE] ChipseaBleLibTest : trame verrouillée 75,5 kg, graisse 23,4 %, muscle 32,8 %
        val r = ChipseaGatt().feed(hex("CA 10 12 00 01 02 F3 00 EA 02 27 01 48 06 72 00 5A 1D"))!!
        assertEquals(23.4, r.fatPct!!, 1e-9); assertEquals(32.8, r.musclePct!!, 1e-9)
        assertEquals(23.4 to 32.8, compositionOf(r))
        // trame non verrouillée : pas de composition
        assertEquals(null to null, compositionOf(ChipseaGatt().feed(hex("CA 10 12 00 00 02 F3"))))
        // [OPENSCALE] multi-paquets verrouillé
        val g = ChipseaGatt()
        g.feed(hex("CA 11 10 12 12 00 00 00 00 01 00 00 00 00 01 02 F3 00 EA"))
        val m = g.feed(hex("CA 11 0B 12 22 02 27 01 48 06 72 00 5A 1D"))!!
        assertEquals(23.4 to 32.8, compositionOf(m))
    }

    @Test fun broadcastScalesGiveNoComposition() {
        // [OPENSCALE] OkOkHandlerTest : la trame C0 ne contient que poids + impédance
        val c0 = OkokAdv.parseC0(hex("02BC 1194 0000 21 0C1122334455"))!!
        assertEquals(null to null, compositionOf(c0))
        assertNotNull(c0.impedanceOhm)
    }

    @Test fun sigBodyCompositionFat() {
        // [SYNTHÈSE] 0x2A9C : drapeaux 0x0400 (poids présent), graisse 234 → 23,4 %, poids 13760 × 0,005 = 68,8 kg
        val r = BodySig.decode("2A9C", hex("00 04 EA 00 C0 35"))!!
        assertEquals(68.8, r.kg, 0.01)
        assertEquals(23.4, compositionOf(r).first!!, 1e-9)
    }

    @Test fun implausibleValuesIgnored() {
        assertEquals(null to null, compositionOf(BodyReading(70.0, true, "t", fatPct = 0.0, musclePct = 120.0)))
        assertEquals(null to null, compositionOf(null))
    }
}
