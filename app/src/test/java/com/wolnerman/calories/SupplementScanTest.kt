package com.wolnerman.calories

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** v2.18 : préremplissage d'un complément à partir d'une fiche Open Food Facts (sans inventer). */
class SupplementScanTest {

    private fun product(
        name: String? = "Vitamine D3 1000 UI",
        nameFr: String? = null,
        brand: String? = "Oxybiane",
        quantity: String? = "60 gélules",
        serving: String? = "1 gélule",
        nutr: JSONObject? = null,
        generic: String? = null,
    ): JSONObject = JSONObject().apply {
        if (name != null) put("product_name", name)
        if (nameFr != null) put("product_name_fr", nameFr)
        if (generic != null) put("generic_name_fr", generic)
        if (brand != null) put("brands", brand)
        if (quantity != null) put("quantity", quantity)
        if (serving != null) put("serving_size", serving)
        if (nutr != null) put("nutriments", nutr)
    }

    @Test fun displayNameAddsBrandUnlessAlreadyPresent() {
        assertEquals("Vitamine D3 (Oxybiane)", SupplementScanLogic.displayName("Vitamine D3", "Oxybiane"))
        assertEquals("Vitamine D3 Oxybiane", SupplementScanLogic.displayName("Vitamine D3 Oxybiane", "Oxybiane"))
        assertEquals("Oxybiane", SupplementScanLogic.displayName("", "Oxybiane, Autre"))
        assertEquals("", SupplementScanLogic.displayName(null, "  "))
    }

    @Test fun doseFromServingAndPackCount() {
        assertEquals("1 gélule", SupplementScanLogic.doseFromServing("1 gélule"))
        assertEquals("2 capsules", SupplementScanLogic.doseFromServing("2 capsules"))
        assertEquals("5 ml", SupplementScanLogic.doseFromServing("5 ml"))
        assertNull(SupplementScanLogic.doseFromServing("2"))
        assertNull(SupplementScanLogic.doseFromServing("  "))
        assertEquals("1 gélule", SupplementScanLogic.doseFromPackCount("60 gélules"))
        assertEquals("1 capsule", SupplementScanLogic.doseFromPackCount("30 Capsules"))
        assertEquals("1 comprimé", SupplementScanLogic.doseFromPackCount("90 comprimés"))
        assertNull(SupplementScanLogic.doseFromPackCount("60 g"))
        assertNull(SupplementScanLogic.doseFromPackCount("15 ml"))
    }

    @Test fun doseFromNameIsConservative() {
        assertEquals("1000 UI", SupplementScanLogic.doseFromName("Vitamine D3 1000 UI"))
        assertEquals("2000 UI", SupplementScanLogic.doseFromName("Vitamin D 2000 IU"))
        assertEquals("25 µg", SupplementScanLogic.doseFromName("Vitamine D 25 µg"))
        assertEquals("300 mg", SupplementScanLogic.doseFromName("Magnésium 300 mg"))
        assertEquals("1000 mg", SupplementScanLogic.doseFromName("Oméga-3 1000 mg"))
        assertEquals("1 gélule", SupplementScanLogic.doseFromName("Complexe vitaminé 60 gélules"))
        // plusieurs nutriments chiffrés : on n'invente pas laquelle est « la » dose
        assertNull(SupplementScanLogic.doseFromName("Vitamine C 500 mg + Zinc 15 mg"))
        assertNull(SupplementScanLogic.doseFromName("Complexe vitaminé"))
    }

    @Test fun deriveDosePrefersServingThenPackThenName() {
        assertEquals("1 gélule", SupplementScanLogic.deriveDose("1 gélule", "60 gélules", "Vitamine D 1000 UI"))
        assertEquals("1 capsule", SupplementScanLogic.deriveDose(null, "30 capsules", "Vitamine D 1000 UI"))
        assertEquals("1000 UI", SupplementScanLogic.deriveDose(null, "60 g", "Vitamine D3 1000 UI"))
        assertNull(SupplementScanLogic.deriveDose(null, "60 g", "Complexe vitaminé"))
    }

    @Test fun nutrimentsOnlyAtServingNeverInventedFrom100g() {
        val full = JSONObject()
            .put("energy-kcal_serving", 10)
            .put("energy-kcal_100g", 500)
            .put("omega-3-fat_serving", 1.0)
            .put("omega-3-fat_100g", 50.0)
        assertEquals(10, SupplementScanLogic.kcalFromNutriments(full))
        assertEquals(1000.0, SupplementScanLogic.omega3MgFromNutriments(full)!!, 0.0)

        val only100 = JSONObject().put("energy-kcal_100g", 500).put("omega-3-fat_100g", 50.0)
        assertNull(SupplementScanLogic.kcalFromNutriments(only100))
        assertNull(SupplementScanLogic.omega3MgFromNutriments(only100))

        val mgUnit = JSONObject().put("omega-3-fat_serving", 500.0).put("omega-3-fat_serving_unit", "mg")
        assertEquals(500.0, SupplementScanLogic.omega3MgFromNutriments(mgUnit)!!, 0.0)

        val absurdGrams = JSONObject().put("omega-3-fat_serving", 1000.0) // pas d'unité, 1000 g impossible
        assertNull(SupplementScanLogic.omega3MgFromNutriments(absurdGrams))

        val epaDha = JSONObject()
            .put("eicosapentaenoic-acid_serving", 0.3)
            .put("docosahexaenoic-acid_serving", 0.2)
        assertEquals(500.0, SupplementScanLogic.omega3MgFromNutriments(epaDha)!!, 0.01)
    }

    @Test fun parseProductPrefillsNameDoseKcalOmega3() {
        val nutr = JSONObject().put("energy-kcal_serving", 2).put("omega-3-fat_serving", 1.0)
        val draft = SupplementScanLogic.parseProduct(
            product(nameFr = "Oméga-3 EPA/DHA", name = "Omega-3", brand = "Nutripure", quantity = "60 gélules", serving = "1 gélule", nutr = nutr),
            "3760267701234", "Open Food Facts")
        assertTrue(draft.recognized)
        assertEquals("Oméga-3 EPA/DHA (Nutripure)", draft.name)
        assertEquals("1 gélule", draft.dose)
        assertEquals(2, draft.kcal)
        assertEquals(1000.0, draft.omega3Mg!!, 0.0)
        assertEquals("3760267701234", draft.barcode)
        assertNull(draft.message)
    }

    @Test fun parseFallsBackToNameDoseWhenPackIsOnlyWeight() {
        val draft = SupplementScanLogic.parseProduct(
            product(name = "Vitamine D3 2000 IU", nameFr = null, brand = null, quantity = "60 g", serving = null),
            "3270000000000")
        assertEquals("Vitamine D3 2000 IU", draft.name)
        assertEquals("2000 UI", draft.dose)
    }

    @Test fun unrecognizedAndBarcodeHelpers() {
        assertEquals(SupplementScanLogic.UNRECOGNIZED, SupplementScanLogic.unrecognized("3270000000000").message)
        assertFalse(SupplementScanLogic.unrecognized("3270000000000").recognized)
        assertEquals("3270000000000", SupplementScanLogic.normalizeBarcode(" 327 0000 000000 "))
        assertNull(SupplementScanLogic.normalizeBarcode("123"))
        val list = listOf(Supplement(1L, "Vitamine D", "1000 UI", barcode = "3270000000000"))
        assertEquals("Vitamine D", SupplementScanLogic.findByBarcode(list, "327 0000 000000")?.name)
        assertNull(SupplementScanLogic.findByBarcode(list, "3010000000000"))
        val round = SupplementLogic.listFromJson(SupplementLogic.listToJson(list))
        assertEquals("3270000000000", round[0].barcode)
    }
}
