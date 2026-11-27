package com.wolnerman.calories

import org.junit.Assert.*
import org.junit.Test

/** v2.21 : extraction d'étiquette (nom, dose, oméga-3, kcal) et non-régression du scan. */
class V221Test {

    private val vitamineD = """
        SOLGAR
        VITAMINE D3
        1000 UI
        Complément alimentaire
        Posologie : 1 gélule par jour
        Ne pas dépasser la dose journalière recommandée.
        Tenir hors de portée des enfants.
        Ingrédients : huile d'olive, gélule, cholécalciférol.
        À conserver à l'abri de la chaleur.
    """.trimIndent()

    private val omega3 = """
        NUTRI&CO
        OMÉGA-3 EPA DHA
        Huile de poissons sauvages
        2 capsules par jour
        EPA 400 mg
        DHA 300 mg
        Valeur énergétique : 10 kcal par capsule
        Complément alimentaire
        À consommer de préférence avant fin 2027
        Ne se substitue pas à une alimentation variée.
    """.trimIndent()

    @Test fun vitamineDNameDoseFromRealisticLabel() {
        val e = LabelOcrLogic.extract(vitamineD)
        assertTrue(e.usable)
        assertEquals("VITAMINE D3 (SOLGAR)", e.name)
        assertEquals("1 gélule par jour", e.dose)
        assertNull(e.kcal)
        assertNull(e.omega3Mg)
        assertFalse(e.nameCandidates.isEmpty())
        assertTrue(e.rawText.contains("SOLGAR"))
    }

    @Test fun vitamineDPrefersLargestLineAsName() {
        val e = LabelOcrLogic.extract(listOf(
            OcrLine("SOLGAR", height = 20, top = 10),
            OcrLine("VITAMINE D3", height = 42, top = 36),
            OcrLine("Complément alimentaire", height = 14, top = 90),
            OcrLine("1 gélule par jour", height = 16, top = 120),
        ))
        assertEquals("VITAMINE D3 (SOLGAR)", e.name)
        assertEquals("1 gélule par jour", e.dose)
    }

    @Test fun omega3EpaDhaKcalAndDose() {
        val e = LabelOcrLogic.extract(omega3)
        assertTrue(e.usable)
        assertTrue(e.name!!.contains("OMÉGA-3", ignoreCase = true) || e.name!!.contains("OMEGA-3", ignoreCase = true))
        assertTrue(e.name!!.contains("NUTRI", ignoreCase = true))
        assertEquals("2 capsules par jour", e.dose)
        assertEquals(700.0, e.omega3Mg!!, 0.0)
        assertEquals(10, e.kcal)
    }

    @Test fun omega3PrefersEpaDhaSumOverTotal() {
        val e = LabelOcrLogic.extract(
            """
            Oméga-3 1000 mg dont EPA 400 mg et DHA 300 mg
            1 gélule par jour
            """.trimIndent()
        )
        assertEquals(700.0, e.omega3Mg!!, 0.0)
        assertEquals("1 gélule par jour", e.dose)
    }

    @Test fun doesNotInventPer100gValues() {
        val e = LabelOcrLogic.extract(
            """
            Huile de lin
            Valeurs nutritionnelles pour 100 g
            Énergie 900 kcal
            Oméga-3 50 g
            Posologie : 1 cuillère à café par jour
            Complément alimentaire
            """.trimIndent()
        )
        assertEquals("1 c. à café par jour", e.dose)
        assertNull("900 kcal/100 g ne doit pas être pris pour une prise", e.kcal)
        assertNull("50 g d'oméga-3 / 100 g ne doit pas être pris pour une prise", e.omega3Mg)
        assertTrue(e.name!!.contains("Huile de lin", ignoreCase = true))
    }

    @Test fun doseVariantsUiUgMgAndTablets() {
        assertEquals("1 comprimé par jour", LabelOcrLogic.extract("Vitamine D3\n25 µg\n1 comprimé par jour").dose)
        assertEquals("2 comprimés", LabelOcrLogic.extract("Magnésium marin\n500 mg\n2 comprimés\nComplément alimentaire").dose)
        assertEquals("1000 UI", LabelOcrLogic.extract("Vitamine D3 1000 UI\nComplément alimentaire").dose)
        assertEquals("25 µg", LabelOcrLogic.extract("Vitamine D3 25 µg\nComplément alimentaire").dose)
        assertEquals("500 mg", LabelOcrLogic.extract("Magnésium 500 mg\nComplément alimentaire").dose)
        assertEquals("2 gélules par jour", LabelOcrLogic.extract("Prendre 2 gelules par jour").dose)
        assertEquals("1000 UI", LabelOcrLogic.extract("Vitamin D 1000 IU").dose)
    }

    @Test fun ignoresRegulatoryMentionsAsName() {
        val e = LabelOcrLogic.extract(
            """
            Complément alimentaire
            Ne pas dépasser la dose journalière recommandée
            Tenir hors de portée des enfants
            Ingrédients : cellulose
            www.exemple.fr
            5412360000227
            """.trimIndent()
        )
        assertNull(e.name)
        assertFalse(e.usable)
    }

    @Test fun unreadableWhenEmptyOrNoise() {
        val empty = LabelOcrLogic.extract("")
        assertFalse(empty.usable)
        assertNull(empty.name)
        assertNull(empty.dose)
        val noise = LabelOcrLogic.extract("||| \n ### \n 123")
        assertFalse(noise.usable)
    }

    @Test fun neverOverwritesFilledFields() {
        val e = LabelOcrLogic.extract(vitamineD)
        val fill = LabelOcrLogic.applyToForm(
            name = "Mon produit",
            dose = "déjà saisi",
            kcalText = "",
            omegaText = "",
            omegaInGrams = false,
            extraction = e,
        )
        assertEquals("Mon produit", fill.name)
        assertEquals("déjà saisi", fill.dose)
        assertFalse(fill.applied.contains("nom"))
        assertFalse(fill.applied.contains("dose"))
        assertEquals(LabelOcrLogic.KEPT, fill.message)

        val blank = LabelOcrLogic.applyToForm("", "", "", "", false, e)
        assertEquals("VITAMINE D3 (SOLGAR)", blank.name)
        assertEquals("1 gélule par jour", blank.dose)
        assertTrue(blank.applied.contains("nom"))
        assertTrue(blank.applied.contains("dose"))
        assertEquals(LabelOcrLogic.READ_OK, blank.message)

        val none = LabelOcrLogic.applyToForm("", "", "", "", false, LabelOcrLogic.extract(""))
        assertEquals(LabelOcrLogic.UNREADABLE, none.message)
        assertTrue(none.applied.isEmpty())
    }

    @Test fun userCanPickAnotherLineAsName() {
        val e = LabelOcrLogic.extract(vitamineD)
        assertTrue(e.nameCandidates.any { it.contains("SOLGAR", ignoreCase = true) || it.contains("VITAMINE", ignoreCase = true) })
        val fill = LabelOcrLogic.applyToForm("", "", "", "", false, e)
        assertTrue(fill.candidates.isNotEmpty())
        assertFalse(fill.candidates.any { it.equals(fill.name, ignoreCase = true) })
    }

    @Test fun savedBarcodeStillFindsTheSupplement() {
        val list = listOf(
            Supplement(1L, "Produit inconnu d'OFF", "1 gélule", barcode = "5412360000227"),
            Supplement(2L, "Vitamine D", "1000 UI", barcode = "3270000000000"),
        )
        assertEquals("Produit inconnu d'OFF", SupplementScanLogic.findByBarcode(list, "5412360000227")?.name)
        assertEquals("Produit inconnu d'OFF", SupplementScanLogic.findByBarcode(list, "541 2360 000227")?.name)
        assertEquals("Vitamine D", SupplementScanLogic.findByBarcode(list, "327 0000 000000")?.name)
        val json = SupplementLogic.listToJson(list)
        val back = SupplementLogic.listFromJson(json)
        assertEquals("5412360000227", back[0].barcode)
        assertEquals("Produit inconnu d'OFF", SupplementScanLogic.findByBarcode(back, "5412360000227")?.name)
    }

    @Test fun unrecognizedMessageSuggestsPhoto() {
        val msg = SupplementScanLogic.unrecognized("5412360000227").message!!
        assertTrue(msg.contains("photo", ignoreCase = true))
        assertTrue(msg.contains("étiquette") || msg.contains("etiquette"))
        assertEquals(msg, SupplementScanLogic.UNRECOGNIZED)
        assertFalse(SupplementScanLogic.unrecognized("5412360000227").recognized)
    }

    @Test fun searchRegressionsOliveLinCocoUntouched() {
        assertEquals("Olives", findFood("olive")?.name)
        assertEquals("Graines de lin", findFood("lin")?.name)
        assertEquals("Noix de coco (chair fraîche)", findFood("coco")?.name)
        assertEquals("Flocons d'avoine", findFood("flocons d avoine")?.name)
    }
}
