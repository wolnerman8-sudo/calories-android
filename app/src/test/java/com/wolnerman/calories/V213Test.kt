package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

/** v2.13 : format du poids/taille du profil, mise à jour auto depuis une pesée enregistrée. */
class V213Test {

    @Test fun profileNumberFrenchNoTrailingZeros() {
        assertEquals("68,8", ProfileFmt.number(68.8))
        assertEquals("68,8", ProfileFmt.number(68.8000))
        assertEquals("68,8", ProfileFmt.number(68.8f.toDouble()))   // artefact float SharedPreferences
        assertEquals("68,8", ProfileFmt.number(68.80000305175781))
        assertEquals("69", ProfileFmt.number(69.0))
        assertEquals("69", ProfileFmt.number(69.00))
        assertEquals("69", ProfileFmt.number(68.95))                // arrondi à 0,1
        assertEquals("175", ProfileFmt.number(175.0))
        assertEquals("175,5", ProfileFmt.number(175.5))
        assertEquals("", ProfileFmt.numberOrEmpty(null))
        assertEquals("68,8", ProfileFmt.numberOrEmpty(68.8))
    }

    @Test fun profileFieldsParseCommaAndDot() {
        assertEquals(68.8, parseNum("68,8")!!, 0.0)
        assertEquals(68.8, parseNum("68.8")!!, 0.0)
        assertEquals(69.0, parseNum("69")!!, 0.0)
        assertEquals(175.5, parseNum("175,5")!!, 0.0)
        assertEquals(175.5, parseNum("175.5")!!, 0.0)
        // un poids formaté puis relu
        assertEquals(68.8, parseNum(ProfileFmt.number(68.8000))!!, 0.0)
        assertEquals(69.0, parseNum(ProfileFmt.number(69.0))!!, 0.0)
    }

    @Test fun weighInUpdatesProfileWhenNewestAndPlausible() {
        val older = listOf(WeighIn(1_000, 70.0, "test"))
        assertEquals(69.0, ProfileWeight.fromWeighIn(68.95, 2_000, older)!!, 0.0)
        assertEquals(68.8, ProfileWeight.fromWeighIn(68.8, 2_000, older)!!, 0.0)
        assertEquals(20.0, ProfileWeight.fromWeighIn(20.0, 2_000, emptyList())!!, 0.0)
        assertEquals(300.0, ProfileWeight.fromWeighIn(300.0, 2_000, emptyList())!!, 0.0)
        // hors plage 20–300 kg
        assertNull(ProfileWeight.fromWeighIn(19.9, 2_000, emptyList()))
        assertNull(ProfileWeight.fromWeighIn(15.0, 2_000, emptyList()))
        assertNull(ProfileWeight.fromWeighIn(300.1, 2_000, emptyList()))
        assertNull(ProfileWeight.fromWeighIn(0.0, 2_000, emptyList()))
        // pas la pesée la plus récente
        assertNull(ProfileWeight.fromWeighIn(68.8, 500, older))
        // une pesée plus récente déjà présente
        val newer = listOf(WeighIn(3_000, 71.0, "test"))
        assertNull(ProfileWeight.fromWeighIn(68.8, 2_000, newer))
    }

    @Test fun profileCopyKeepsOtherFields() {
        val p = Profile(age = 35, weightKg = 68.8, heightCm = 170.0, male = true, activity = 2)
        val n = p.copy(weightKg = 69.0)
        assertEquals(35, n.age)
        assertEquals(170.0, n.heightCm!!, 0.0)
        assertEquals(true, n.male)
        assertEquals(2, n.activity)
        assertEquals(69.0, n.weightKg!!, 0.0)
        val bmrOld = p.bmr()!!; val bmrNew = n.bmr()!!
        assertEquals(10.0 * 68.8 + 6.25 * 170 - 5 * 35 + 5, bmrOld, 0.05)
        assertTrue(bmrNew > bmrOld)   // Mifflin suit le nouveau poids
        assertEquals(96, ProteinLogic.proteinGoal(n.weightKg))
    }
}
