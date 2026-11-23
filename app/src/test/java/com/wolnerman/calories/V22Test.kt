package com.wolnerman.calories

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class V22Test {
    private fun e(id: Long, date: String, time: String, name: String, g: Double, k: Double,
                  c: Double? = null, p: Double? = null, f: Double? = null, ferm: Boolean = false) =
        Entry(id, date, time, name, g, k, null, c, p, f, emptyMap(), ferm)

    // ---------- Rappel du soir ----------
    @Test fun reminderBelowGoal() {
        assertEquals("Il te manque 650 kcal pour atteindre ton objectif de 2000 kcal aujourd'hui.", ReminderLogic.message(1350, 2000))
        assertNotNull(ReminderLogic.message(0, 1800))
    }
    @Test fun reminderGoalReached() {
        assertNull(ReminderLogic.message(2000, 2000))
        assertNull(ReminderLogic.message(2600, 2000))
    }
    @Test fun reminderNoGoal() {
        assertNull(ReminderLogic.message(500, 0))
        assertNull(ReminderLogic.message(0, -1))
    }
    @Test fun nextTriggerLaterToday() {
        assertEquals(LocalDateTime.of(2026, 11, 23, 20, 0), ReminderLogic.nextTrigger(LocalDateTime.of(2026, 11, 23, 8, 59), 20, 0))
    }
    @Test fun nextTriggerTomorrowWhenPassedOrExact() {
        assertEquals(LocalDateTime.of(2026, 11, 24, 20, 0), ReminderLogic.nextTrigger(LocalDateTime.of(2026, 11, 23, 20, 0), 20, 0))
        assertEquals(LocalDateTime.of(2026, 11, 24, 20, 0), ReminderLogic.nextTrigger(LocalDateTime.of(2026, 11, 23, 22, 15), 20, 0))
    }
    @Test fun nextTriggerMonthAndYearRollover() {
        assertEquals(LocalDateTime.of(2027, 1, 1, 7, 30), ReminderLogic.nextTrigger(LocalDateTime.of(2026, 12, 31, 23, 0), 7, 30))
        assertEquals(LocalDateTime.of(2026, 12, 1, 20, 0), ReminderLogic.nextTrigger(LocalDateTime.of(2026, 11, 30, 21, 0), 20, 0))
    }
    @Test fun todayTotalOnlyToday() {
        val list = listOf(e(1, "2026-11-23", "08:00", "Pain", 100.0, 250.0), e(2, "2026-11-22", "08:00", "Riz", 200.0, 130.0),
            e(3, "2026-11-23", "12:00", "Riz", 150.0, 130.0))
        assertEquals(250 + 195, ReminderLogic.todayTotal(list, LocalDate.of(2026, 11, 23)))
    }

    // ---------- Graphiques ----------
    @Test fun oneWeekWindowIsMondayToSunday() {
        val today = LocalDate.of(2026, 11, 23) // lundi
        assertEquals(LocalDate.of(2026, 11, 23) to LocalDate.of(2026, 11, 29), Stats.window(1, 0, today))
        assertEquals(LocalDate.of(2026, 11, 16) to LocalDate.of(2026, 11, 22), Stats.window(1, 1, today))
        val sunday = LocalDate.of(2026, 11, 29)
        assertEquals(LocalDate.of(2026, 11, 23), Stats.window(1, 0, sunday).first)
    }
    @Test fun multiWeekWindowEndsToday() {
        val today = LocalDate.of(2026, 11, 23)
        val (s, end) = Stats.window(4, 0, today)
        assertEquals(today, end); assertEquals(today.minusDays(27), s)
        assertEquals(today.minusDays(28), Stats.window(4, 1, today).second)
    }
    @Test fun dayLabelsFrench() {
        assertEquals("lun.", Stats.dayName(LocalDate.of(2026, 11, 23)))
        assertEquals("dim.", Stats.dayName(LocalDate.of(2026, 11, 29)))
    }
    @Test fun dailyHasGapsNotZeros() {
        val list = listOf(e(1, "2026-11-23", "08:00", "Pain", 100.0, 250.0, 50.0, 9.0, 3.0),
            e(2, "2026-11-25", "12:00", "Riz", 200.0, 130.0))
        val d = Stats.daily(list, LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 29))
        assertEquals(7, d.size)
        assertEquals(250, d[0].kcal); assertEquals(50.0, d[0].carbs!!, 1e-9)
        assertNull(d[1].kcal); assertFalse(d[1].logged)
        assertEquals(260, d[2].kcal); assertNull(d[2].carbs) // pas de macros connues → null, pas 0
        val s = Stats.summary(d, 255)
        assertEquals(2, s.daysLogged); assertEquals(255, s.avgKcal); assertEquals(1, s.daysInGoal); assertEquals(1, s.daysOver)
        assertEquals(7, s.days)
        val w = Stats.weekly(Stats.daily(list, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 29)))
        assertEquals(2, w.size); assertNull(w[0].avgKcal); assertEquals(255, w[1].avgKcal)
    }
    @Test fun emptySummary() {
        val s = Stats.summary(Stats.daily(emptyList(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14)), 2000)
        assertEquals(0, s.daysLogged); assertNull(s.avgKcal); assertEquals(14, s.days)
    }

    // ---------- Export ----------
    @Test fun numberFormatFrench() {
        assertEquals("12,5", ExportData.num(12.5)); assertEquals("150", ExportData.num(150.0, 1))
        assertEquals("100", ExportData.num(100.0, 0)); assertEquals("", ExportData.num(null)); assertEquals("0,33", ExportData.num(1.0 / 3))
    }
    @Test fun csvEscapesAndBom() {
        val csv = ExportData.entriesCsv(listOf(
            e(2, "2026-11-23", "12:00", "Riz; \"basmati\"", 150.0, 130.0, 28.0, 2.7, 0.3, ferm = true),
            e(1, "2026-11-22", "08:00", "Pain", 100.0, 250.0)))
        assertTrue(csv.startsWith("\uFEFFDate;Heure;Aliment;Quantité (g);kcal/100 g;kcal;"))
        val lines = csv.removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("2026-11-22;08:00;Pain;100;250;250;;;;")) // tri chronologique, macros inconnues vides
        assertTrue(lines[2].startsWith("2026-11-23;12:00;\"Riz; \"\"basmati\"\"\";150;130;195;42;4,05;0,45;"))
        assertTrue(lines[2].endsWith(";oui;non"))
        assertEquals(ExportData.ENTRY_HEADER.size, lines[1].split(";").size)
    }
    @Test fun emptyExport() {
        assertEquals("\uFEFF" + ExportData.ENTRY_HEADER.joinToString(";") + "\r\n", ExportData.entriesCsv(emptyList()))
        assertEquals(0, JSONObject(ExportData.json(emptyList(), 2000, "2.2", "x")).getJSONArray("entrees").length())
    }
    @Test fun dailyCsvAndJson() {
        val list = listOf(e(1, "2026-11-23", "08:00", "Pain", 100.0, 250.0), e(2, "2026-11-23", "12:00", "Riz", 1000.0, 200.0),
            e(3, "2026-11-20", "12:00", "Riz", 100.0, 130.0))
        val lines = ExportData.dailyCsv(list, 2000).removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, lines.size) // en-tête + 2 jours renseignés (pas les jours vides)
        assertEquals("2026-11-20;1;130;2000;-1870;oui;;;", lines[1])
        assertEquals("2026-11-23;2;2250;2000;250;non;;;", lines[2])
        val j = JSONObject(ExportData.json(list, 2000, "2.2", "2026-11-23T09:00"))
        assertEquals(3, j.getJSONArray("entrees").length())
        assertEquals("2026-11-20", j.getJSONArray("entrees").getJSONObject(0).getString("date"))
        assertTrue(j.getJSONArray("entrees").getJSONObject(0).isNull("glucides_g"))
        assertEquals(2, j.getJSONArray("jours").length())
    }
}
