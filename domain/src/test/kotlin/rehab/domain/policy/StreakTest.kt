package rehab.domain.policy

import rehab.domain.fakes.InMemoryEventLog
import rehab.domain.fakes.InMemoryStreakRecordRepo
import rehab.domain.model.Event
import rehab.domain.model.NightWindow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(2026, 9, d, h, min, 0, 0, zone).toInstant()
    private val nights = DayOfWeek.entries.associateWith { NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30)) }
    private val schedule = Schedule({ nights }, zone)

    private fun streak(installedAt: Instant, events: List<Event> = emptyList()): Pair<Streak, InMemoryStreakRecordRepo> {
        val log = InMemoryEventLog().apply { events.forEach(::append) }
        val record = InMemoryStreakRecordRepo(installedAt)
        return Streak(schedule, log, record) to record
    }

    @Test fun `jour d installation compte 1`() {
        val (s, _) = streak(at(21, 12))
        assertEquals(1, s.current(at(21, 18)))
    }

    @Test fun `trois jours sans relapse`() {
        val (s, _) = streak(at(19, 12))
        assertEquals(3, s.current(at(21, 18)))
    }

    @Test fun `relapse a 1h du matin appartient a la journee precedente`() {
        // Relapse le 21 à 01:00 → journée Rehab du 20. Le 21 et le 22 sont propres.
        val (s, _) = streak(at(15, 12), listOf(Event.Relapse(at(21, 1), at(21, 1, 15))))
        assertEquals(2, s.current(at(22, 18)))
    }

    @Test fun `relapse aujourd hui remet a zero`() {
        val (s, _) = streak(at(15, 12), listOf(Event.Relapse(at(21, 15), at(21, 15, 15))))
        assertEquals(0, s.current(at(21, 18)))
    }

    @Test fun `joker ne casse pas le streak`() {
        val (s, _) = streak(at(19, 12), listOf(Event.Joker(at(21, 15), at(21, 15, 5))))
        assertEquals(3, s.current(at(21, 18)))
    }

    @Test fun `best persiste le record et ne descend jamais`() {
        val (s, record) = streak(at(15, 12))
        assertEquals(7, s.recordAndGetBest(at(21, 18)))
        assertEquals(7, record.bestDays())
        record.setBestDays(23)
        assertEquals(23, s.recordAndGetBest(at(21, 18)))
    }

    @Test fun `summary sans relapse jour d installation est un record naissant`() {
        val (s, record) = streak(at(21, 12))
        val sum = s.summary(at(21, 18))
        assertEquals(StreakSummary(current = 1, best = 1, previousBest = 0), sum)
        assertEquals(true, sum.inRecord)
        assertEquals(1, record.bestDays())
    }

    @Test fun `summary record en cours depasse l ancienne serie`() {
        // Installé le 1er ; relapse le 5 (série passée : 1..4 = 4 jours) ; propre du 6 au 21 = 16 jours.
        val (s, _) = streak(at(1, 12), listOf(Event.Relapse(at(5, 15), at(5, 15, 15))))
        val sum = s.summary(at(21, 18))
        assertEquals(16, sum.current)
        assertEquals(4, sum.previousBest)
        assertEquals(16, sum.best)
        assertEquals(true, sum.inRecord)
    }

    @Test fun `summary hors record quand une serie passee est plus longue`() {
        // Propre du 1 au 15 (15 jours), relapse le 16, propre du 17 au 21 (5 jours).
        val (s, _) = streak(at(1, 12), listOf(Event.Relapse(at(16, 15), at(16, 15, 15))))
        val sum = s.summary(at(21, 18))
        assertEquals(5, sum.current)
        assertEquals(15, sum.previousBest)
        assertEquals(15, sum.best)
        assertEquals(false, sum.inRecord)
    }

    @Test fun `summary egalite avec l ancienne serie n est pas un record`() {
        // Propre du 1 au 5 (5 jours), relapse le 6, propre du 7 au 11 (5 jours).
        val (s, _) = streak(at(1, 12), listOf(Event.Relapse(at(6, 15), at(6, 15, 15))))
        assertEquals(false, s.summary(at(11, 18)).inRecord)
    }

    @Test fun `summary relapse aujourd hui serie a zero`() {
        val (s, _) = streak(at(15, 12), listOf(Event.Relapse(at(21, 15), at(21, 15, 15))))
        val sum = s.summary(at(21, 18))
        assertEquals(0, sum.current)
        assertEquals(6, sum.previousBest)
        assertEquals(false, sum.inRecord)
    }

    @Test fun `summary respecte un record persiste plus grand`() {
        val (s, record) = streak(at(19, 12))
        record.setBestDays(40)
        val sum = s.summary(at(21, 18))
        assertEquals(3, sum.current)
        assertEquals(40, sum.best)
        assertEquals(false, sum.inRecord)
    }
}
