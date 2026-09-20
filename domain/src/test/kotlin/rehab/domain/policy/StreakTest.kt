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
}
