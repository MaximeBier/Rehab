package rehab.domain.policy

import rehab.domain.model.NightWindow
import rehab.domain.model.Settings
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

    @Test fun `passage a l heure d ete avec un lever dans l heure inexistante`() {
        // 2026-03-29 : 02:00 → 03:00 à Paris. Un lever à 02:30 n'existe pas ; java.time le décale à 03:30.
        val nights = DayOfWeek.entries.associateWith { NightWindow(LocalTime.of(23, 0), LocalTime.of(2, 30)) }
        val s = Schedule({ nights }, zone)
        val p = s.activeNight(at(2026, 3, 29, 1, 30))!!
        assertEquals(at(2026, 3, 29, 3, 30), p.end)
        assertNull(s.activeNight(at(2026, 3, 29, 3, 30)))
        assertEquals(LocalDate.of(2026, 3, 29), s.dayOf(at(2026, 3, 29, 3, 30)))
    }

    @Test fun `reglages par defaut mixtes vendredi soir libre samedi matin bloque`() {
        val s = Schedule({ Settings.DEFAULT.nights }, zone)
        // 2026-09-25 est un vendredi : la ligne vendredi commence samedi 00:30.
        assertNull(s.activeNight(at(2026, 9, 25, 23, 30)))
        assertNotNull(s.activeNight(at(2026, 9, 26, 0, 45)))
        assertEquals(at(2026, 9, 26, 9, 0), s.activeNight(at(2026, 9, 26, 0, 45))!!.end)
    }

    @Test fun `nextNight a midi renvoie la nuit du soir meme`() {
        val n = schedule.nextNight(at(2026, 9, 21, 12, 0))!!
        assertEquals(at(2026, 9, 21, 23, 0), n.start)
        assertEquals(at(2026, 9, 22, 7, 30), n.end)
    }

    @Test fun `nextNight pendant la nuit renvoie la nuit en cours`() {
        val n = schedule.nextNight(at(2026, 9, 22, 3, 0))!!
        assertEquals(at(2026, 9, 21, 23, 0), n.start)
    }

    @Test fun `nextNight a 7h30 pile passe a la nuit suivante`() {
        val n = schedule.nextNight(at(2026, 9, 22, 7, 30))!!
        assertEquals(at(2026, 9, 22, 23, 0), n.start)
    }

    @Test fun `nextNight saute les nuits desactivees`() {
        val nights = weekNights + (DayOfWeek.MONDAY to NightWindow(LocalTime.of(0, 0), LocalTime.of(0, 0)))
        val n = Schedule({ nights }, zone).nextNight(at(2026, 9, 21, 12, 0))!!
        assertEquals(at(2026, 9, 22, 23, 0), n.start)
    }

    @Test fun `nextNight sans aucune nuit renvoie null`() {
        val none = DayOfWeek.entries.associateWith { NightWindow(LocalTime.of(0, 0), LocalTime.of(0, 0)) }
        assertNull(Schedule({ none }, zone).nextNight(at(2026, 9, 21, 12, 0)))
    }
}
