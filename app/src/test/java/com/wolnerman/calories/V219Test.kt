package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

/** v2.19 : validation de l'enregistrement d'un complément, édition, virgule, photo JSON. */
class V219Test {

    @Test fun saveRequiresNameOnly() {
        assertFalse(SupplementFormLogic.canSave(""))
        assertFalse(SupplementFormLogic.canSave("   "))
        assertTrue(SupplementFormLogic.canSave("Vitamine D"))
        assertTrue(SupplementFormLogic.canSave("  Magnésium  "))
        assertEquals(SupplementFormLogic.NAME_REQUIRED, SupplementFormLogic.blockedReason(""))
        assertEquals(SupplementFormLogic.NAME_REQUIRED, SupplementFormLogic.blockedReason("  "))
        assertNull(SupplementFormLogic.blockedReason("Vitamine D"))
    }

    @Test fun preparedSavesWithOptionalFieldsMissing() {
        // Cas typique après un scan non reconnu : nom saisi, dose/kcal/oméga-3 vides, heure et rappel facultatifs
        val saved = SupplementFormLogic.fromForm(id = 0L, name = "Vitamine D", dose = "", barcode = "5412360000227")
        assertNotNull(saved)
        assertEquals("Vitamine D", saved!!.name)
        assertEquals("", saved.dose)
        assertNull(saved.hour)
        assertFalse(saved.reminderEnabled)
        assertNull(saved.kcal)
        assertNull(saved.omega3Mg)
        assertEquals("5412360000227", saved.barcode)
        assertNull(SupplementFormLogic.fromForm(id = 0L, name = "  "))
        assertNull(SupplementFormLogic.prepared(Supplement(1L, "   ")))
    }

    @Test fun reminderWithoutHourIsSavedButDisabled() {
        val s = SupplementFormLogic.fromForm(
            id = 0L, name = "Oméga-3", reminderEnabled = true, hour = null, minute = null,
        )!!
        assertFalse(s.reminderEnabled)
        val withTime = SupplementFormLogic.fromForm(
            id = 0L, name = "Oméga-3", reminderEnabled = true, hour = 9, minute = 0,
        )!!
        assertTrue(withTime.reminderEnabled)
        assertEquals(9, withTime.hour)
        assertEquals(0, withTime.minute)
    }

    @Test fun commaDecimalsAndKcalDoNotBlockSave() {
        val s = SupplementFormLogic.fromForm(
            id = 0L, name = "Oméga-3", kcalText = "1,5", omegaText = "1,25", omegaInGrams = true,
        )!!
        assertEquals(2, s.kcal) // 1,5 arrondi
        assertEquals(1250.0, s.omega3Mg!!, 0.0)
        // kcal / oméga-3 invalides : ignorés, l'enregistrement passe
        val loose = SupplementFormLogic.fromForm(id = 0L, name = "Fer", kcalText = "abc", omegaText = "--")!!
        assertEquals("Fer", loose.name)
        assertNull(loose.kcal)
        assertNull(loose.omega3Mg)
        assertEquals(10, SupplementFormLogic.parseKcal("10"))
        assertEquals(2, SupplementFormLogic.parseKcal("1,5"))
        assertNull(SupplementFormLogic.parseKcal(""))
    }

    @Test fun upsertRejectsEmptyNameAndEditsExisting() {
        val first = SupplementFormLogic.fromForm(id = 1L, name = "Vitamine D", dose = "1000 UI", hour = 9, minute = 0, reminderEnabled = true)!!
        var list = SupplementLogic.upsert(emptyList(), first)
        assertEquals(1, list.size)
        // nom vide : la liste ne change pas (pas d'enregistrement silencieux « vide »)
        assertEquals(list, SupplementLogic.upsert(list, first.copy(name = "  ")))
        // édition : même id, champs mis à jour, rappel conservé si l'heure reste
        val edited = SupplementFormLogic.fromForm(
            id = 1L, name = "Vitamine D3", dose = "2000 UI", hour = 8, minute = 30,
            reminderEnabled = true, kcalText = "2", barcode = "5412360000227",
            createdDate = "2026-11-27", photo = "/data/user/0/x/files/photos/compl_1.jpg",
        )!!
        list = SupplementLogic.upsert(list, edited)
        assertEquals(1, list.size)
        assertEquals("Vitamine D3", list[0].name)
        assertEquals("2000 UI", list[0].dose)
        assertEquals(8, list[0].hour)
        assertEquals(30, list[0].minute)
        assertTrue(list[0].reminderEnabled)
        assertEquals(2, list[0].kcal)
        assertEquals("5412360000227", list[0].barcode)
        assertEquals("/data/user/0/x/files/photos/compl_1.jpg", list[0].photo)
        assertEquals(1L, list[0].id)
    }

    @Test fun jsonKeepsPhotoAndStaysBackwardCompatible() {
        val withPhoto = Supplement(2L, "Magnésium", "300 mg", photo = "/tmp/compl_2.jpg", barcode = "3010000000000")
        val round = SupplementLogic.listFromJson(SupplementLogic.listToJson(listOf(withPhoto)))
        assertEquals("/tmp/compl_2.jpg", round[0].photo)
        assertEquals("3010000000000", round[0].barcode)
        // v2.18 : pas de champ ph / photo
        val legacy = SupplementLogic.listFromJson("""[{"id":3,"n":"Fer","d":"14 mg"}]""")
        assertEquals("Fer", legacy[0].name)
        assertEquals("14 mg", legacy[0].dose)
        assertNull(legacy[0].photo)
        assertNull(legacy[0].barcode)
    }
}
