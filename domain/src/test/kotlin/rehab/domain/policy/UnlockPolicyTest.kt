package rehab.domain.policy

import rehab.domain.fakes.InMemoryEventLog
import rehab.domain.fakes.InMemorySettingsRepo
import rehab.domain.fakes.InMemoryStreakRecordRepo
import rehab.domain.model.Event
import rehab.domain.model.NightWindow
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UnlockPolicyTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(2026, 9, d, h, min, 0, 0, zone).toInstant()
    private val nights = DayOfWeek.entries.associateWith { NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30)) }
    private val schedule = Schedule({ nights }, zone)
    private val events = InMemoryEventLog()
    private val settings = InMemorySettingsRepo()
    private val streak = Streak(schedule, events, InMemoryStreakRecordRepo(at(10, 12)))
    private val policy = UnlockPolicy(settings, schedule, events, streak)

    @Test fun `premier appui de jour est un joker avec 1 restant`() {
        assertEquals(PressOutcome.Joker(remainingAfter = 1), policy.preview(at(21, 15)))
    }

    @Test fun `commit joker debloque 5 minutes`() {
        val e = policy.commit(at(21, 15))
        assertIs<Event.Joker>(e)
        assertEquals(at(21, 15, 5), e.unlockUntil)
        assertEquals(at(21, 15, 5), policy.activeUnlockUntil(at(21, 15, 3)))
        assertNull(policy.activeUnlockUntil(at(21, 15, 5)))
    }

    @Test fun `troisieme appui du jour est un relapse`() {
        policy.commit(at(21, 10))
        policy.commit(at(21, 12))
        val p = policy.preview(at(21, 15))
        assertIs<PressOutcome.Relapse>(p)
        assertEquals(12, p.streakLost)
        val e = policy.commit(at(21, 15))
        assertIs<Event.Relapse>(e)
        assertEquals(at(21, 15, 15), e.unlockUntil)
    }

    @Test fun `la nuit tout appui est un relapse meme avec jokers restants`() {
        assertIs<PressOutcome.Relapse>(policy.preview(at(21, 23, 30)))
    }

    @Test fun `jokers se reinitialisent au lever et non a minuit`() {
        policy.commit(at(21, 10))
        policy.commit(at(21, 12))
        assertEquals(2, policy.jokersUsed(schedule.dayOf(at(22, 1))))   // 01:00 : toujours la journée du 21
        assertEquals(0, policy.jokersUsed(schedule.dayOf(at(22, 8))))
    }

    @Test fun `deblocage actif est le plus tardif`() {
        events.append(Event.Joker(at(21, 10), at(21, 10, 5)))
        events.append(Event.Relapse(at(21, 10, 2), at(21, 10, 17)))
        assertEquals(at(21, 10, 17), policy.activeUnlockUntil(at(21, 10, 4)))
    }

    @Test fun `activeUnlock joker en cours`() {
        val t = at(21, 10)
        events.append(Event.Joker(t, t.plusSeconds(300)))
        assertEquals(ActiveUnlock(ActiveUnlock.Kind.Joker, t.plusSeconds(300)), policy.activeUnlock(t.plusSeconds(60)))
    }

    @Test fun `activeUnlock relapse en cours`() {
        val t = at(21, 10)
        events.append(Event.Relapse(t, t.plusSeconds(900)))
        assertEquals(ActiveUnlock(ActiveUnlock.Kind.Relapse, t.plusSeconds(900)), policy.activeUnlock(t.plusSeconds(900 - 60)))
    }

    @Test fun `activeUnlock expire renvoie null`() {
        val t = at(21, 10)
        events.append(Event.Relapse(t, t.plusSeconds(900)))
        assertNull(policy.activeUnlock(t.plusSeconds(900 + 60)))
    }

    @Test fun `activeUnlock joker et relapse superposes garde le plus tardif`() {
        events.append(Event.Joker(at(21, 10), at(21, 10, 5)))
        events.append(Event.Relapse(at(21, 10, 2), at(21, 10, 17)))
        assertEquals(ActiveUnlock(ActiveUnlock.Kind.Relapse, at(21, 10, 17)), policy.activeUnlock(at(21, 10, 4)))
    }
}
