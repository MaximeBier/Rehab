package rehab.domain.policy

import rehab.domain.model.NightWindow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScheduleTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Instant =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant()

    private val weekNights = DayOfWeek.entries.associateWith { NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30)) }
    private val schedule = Schedule({ weekNights }, zone)

    // 2026-09-21 est un lundi.
    @Test fun `plage traversant minuit couvre 23h et 3h`() {
        assertNotNull(schedule.activeNight(at(2026, 9, 21, 23, 30)))
        assertNotNull(schedule.activeNight(at(2026, 9, 22, 3, 0)))
    }

    @Test fun `hors plage a 12h et a 7h30 pile`() {
        assertNull(schedule.activeNight(at(2026, 9, 21, 12, 0)))
        assertNull(schedule.activeNight(at(2026, 9, 22, 7, 30)))
    }

    @Test fun `fin de plage est le lever du lendemain`() {
        val p = schedule.activeNight(at(2026, 9, 21, 23, 30))!!
        assertEquals(LocalDate.of(2026, 9, 21), p.row)
        assertEquals(at(2026, 9, 22, 7, 30), p.end)
    }

    @Test fun `coucher apres minuit demarre le lendemain`() {
        val nights = DayOfWeek.entries.associateWith { NightWindow(LocalTime.of(0, 30), LocalTime.of(9, 0)) }
        val s = Schedule({ nights }, zone)
        assertNull(s.activeNight(at(2026, 9, 22, 0, 10)))
        val p = s.activeNight(at(2026, 9, 22, 0, 45))!!
        assertEquals(LocalDate.of(2026, 9, 21), p.row)
        assertEquals(at(2026, 9, 22, 0, 30), p.start)
        assertEquals(at(2026, 9, 22, 9, 0), p.end)
    }

    @Test fun `ligne vide ne bloque jamais`() {
        val nights = DayOfWeek.entries.associateWith { NightWindow(LocalTime.of(8, 0), LocalTime.of(8, 0)) }
        assertNull(Schedule({ nights }, zone).activeNight(at(2026, 9, 21, 23, 30)))
    }

    @Test fun `dayOf bascule au lever`() {
        assertEquals(LocalDate.of(2026, 9, 21), schedule.dayOf(at(2026, 9, 22, 7, 29)))
        assertEquals(LocalDate.of(2026, 9, 22), schedule.dayOf(at(2026, 9, 22, 7, 30)))
        assertEquals(LocalDate.of(2026, 9, 21), schedule.dayOf(at(2026, 9, 22, 1, 0)))
    }

    @Test fun `dayStart utilise le lever de la ligne precedente`() {
        val nights = weekNights + (DayOfWeek.SUNDAY to NightWindow(LocalTime.of(0, 30), LocalTime.of(10, 0)))
        val s = Schedule({ nights }, zone)
        assertEquals(at(2026, 9, 21, 10, 0), s.dayStart(LocalDate.of(2026, 9, 21))) // lundi : lever de la ligne dimanche
    }

    @Test fun `changement d heure DST ne casse pas activeNight`() {
        // 2026-10-25 : passage à l'heure d'hiver à 3h → 2h.
        assertNotNull(schedule.activeNight(at(2026, 10, 25, 2, 30)))
        assertNull(schedule.activeNight(at(2026, 10, 25, 8, 0)))
    }
}
