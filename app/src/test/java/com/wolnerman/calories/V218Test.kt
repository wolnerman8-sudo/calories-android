package com.wolnerman.calories

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** v2.18 : compléments alimentaires (ajout, édition, suppression, coche du jour, kcal, oméga-3, export, rappel). */
class V218Test {

    private val vitD = Supplement(1L, "Vitamine D", "1000 UI", hour = 8, minute = 0, reminderEnabled = true, createdDate = "2026-11-24")
    private val omega = Supplement(2L, "Oméga-3", "2 gélules", hour = 12, minute = 30, reminderEnabled = true, kcal = 20, omega3Mg = 1000.0, createdDate = "2026-11-24")
    private val mag = Supplement(3L, "Magnésium", "300 mg", createdDate = "2026-11-25")

    @Test fun crudAndSanitize() {
        val list = SupplementLogic.upsert(emptyList(), Supplement(1L, "  Vitamine D  ", " 1000 UI ", hour = 8, minute = 15, reminderEnabled = true))
        assertEquals(1, list.size)
        assertEquals("Vitamine D", list[0].name)
        assertEquals("1000 UI", list[0].dose)
        assertTrue(list[0].reminderEnabled)

        // Rappel désactivé si aucune heure n'est définie
        val noTime = SupplementLogic.sanitize(Supplement(2L, "Fer", reminderEnabled = true))
        assertFalse(noTime.reminderEnabled)

        // Édition et suppression
        val edited = SupplementLogic.upsert(list, list[0].copy(dose = "2000 UI"))
        assertEquals(1, edited.size)
        assertEquals("2000 UI", edited[0].dose)
        assertEquals(2L, SupplementLogic.nextId(edited, 1L))
        assertTrue(SupplementLogic.delete(edited, 1L).isEmpty())
    }

    @Test fun takenLogsAndDailyTotals() {
        val list = listOf(vitD, omega, mag)
        var logs = emptyMap<String, Set<Long>>()
        logs = SupplementLogic.markTaken(logs, "2026-11-25", 1L, true)
        logs = SupplementLogic.markTaken(logs, "2026-11-25", 2L, true)
        logs = SupplementLogic.markTaken(logs, "2026-11-24", 1L, true)

        assertTrue(SupplementLogic.isTaken(logs, "2026-11-25", 1L))
        assertFalse(SupplementLogic.isTaken(logs, "2026-11-25", 3L))
        assertEquals(20, SupplementLogic.kcalForDate(list, logs, "2026-11-25"))
        assertEquals(0, SupplementLogic.kcalForDate(list, logs, "2026-11-24")) // Oméga-3 non pris le 24
        assertEquals(1000.0, SupplementLogic.omega3MgForDate(list, logs, "2026-11-25"), 0.0)
        assertEquals(0.0, SupplementLogic.omega3MgForDate(list, logs, "2026-11-24"), 0.0)

        // Historique : le magnésium créé le 25 n'est pas compté « Non pris » le 24
        assertEquals(listOf("Oméga-3"), SupplementLogic.notTakenForDate(list, logs, "2026-11-24").map { it.name })
        assertEquals(listOf("Magnésium"), SupplementLogic.notTakenForDate(list, logs, "2026-11-25").map { it.name })

        // Décocher + purge suppression
        logs = SupplementLogic.markTaken(logs, "2026-11-25", 2L, false)
        assertEquals(0, SupplementLogic.kcalForDate(list, logs, "2026-11-25"))
        val purged = SupplementLogic.purgeFromLogs(logs, 1L)
        assertTrue(purged.isEmpty())
    }

    @Test fun omega3ParsingAndFormatting() {
        assertEquals(500.0, SupplementLogic.parseOmega3ToMg("500", unitIsGrams = false)!!, 0.0)
        assertEquals(1250.0, SupplementLogic.parseOmega3ToMg("1,25", unitIsGrams = true)!!, 0.0)
        assertNull(SupplementLogic.parseOmega3ToMg("", unitIsGrams = false))
        assertEquals("500 mg", SupplementLogic.formatOmega3(500.0))
        assertEquals("1000 mg (1 g)", SupplementLogic.formatOmega3(1000.0))
        assertEquals("1250 mg (1,25 g)", SupplementLogic.formatOmega3(1250.0))
    }

    @Test fun notificationTextClearWithDose() {
        assertEquals("💊 Vitamine D – 1000 UI", SupplementLogic.notificationTitle(vitD))
        val body = SupplementLogic.notificationBody(omega)
        assertTrue(body.contains("12:30"))
        assertTrue(body.contains("20 kcal"))
        assertTrue(body.contains("1000 mg (1 g)"))
        assertTrue(body.contains("Pris"))
    }

    @Test fun jsonRoundTrip() {
        val list = listOf(vitD, omega)
        val logs = mapOf("2026-11-25" to setOf(1L, 2L), "2026-11-24" to setOf(1L))
        val backList = SupplementLogic.listFromJson(SupplementLogic.listToJson(list))
        val backLogs = SupplementLogic.logsFromJson(SupplementLogic.logsToJson(logs))
        assertEquals(list, backList)
        assertEquals(logs, backLogs)
    }

    @Test fun statsAndCsvIncludeSupplements() {
        val list = listOf(vitD, omega)
        val logs = mapOf("2026-11-25" to setOf(1L, 2L))
        val entries = listOf(Entry(1L, "2026-11-25", "12:00", "Riz", 100.0, 130.0))
        val extra = SupplementLogic.kcalByDate(list, logs)
        assertEquals(mapOf("2026-11-25" to 20), extra)

        val stats = Stats.daily(entries, LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 25), extra)
        assertEquals(150, stats[0].kcal)

        val csvSupp = ExportData.supplementsCsv(list, logs).removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, csvSupp.size)
        assertTrue(csvSupp[1].startsWith("2026-11-25;Vitamine D;1000 UI;08:00;oui;"))
        assertTrue(csvSupp[2].startsWith("2026-11-25;Oméga-3;2 gélules;12:30;oui;20;1000"))

        val daily = ExportData.dailyCsv(entries, 1910, list, logs).removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertTrue(daily[1].startsWith("2026-11-25;1;150;1910;-1760;oui;;;;"))
        assertTrue(daily[1].endsWith(";Vitamine D, Oméga-3;1000"))

        val json = JSONObject(ExportData.json(entries, 1910, "2.18", "2026-11-25T16:00", list, logs))
        assertEquals(2, json.getJSONArray("complements").length())
        assertEquals(2, json.getJSONObject("prises_complements").getJSONArray("2026-11-25").length())
    }
}
