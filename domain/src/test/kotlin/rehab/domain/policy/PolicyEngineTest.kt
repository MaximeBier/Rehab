package rehab.domain.policy

import rehab.domain.fakes.InMemoryEventLog
import rehab.domain.fakes.InMemorySettingsRepo
import rehab.domain.fakes.InMemoryStreakRecordRepo
import rehab.domain.fakes.InMemoryUsageLog
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.model.NightWindow
import rehab.domain.model.TargetId
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PolicyEngineTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(2026, 9, d, h, min, 0, 0, zone).toInstant()
    private val nights = DayOfWeek.entries.associateWith { NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30)) }
    private val settings = InMemorySettingsRepo()
    private val schedule = Schedule({ settings.get().nights }, zone)
    private val events = InMemoryEventLog()
    private val usage = InMemoryUsageLog()
    private val streak = Streak(schedule, events, InMemoryStreakRecordRepo(at(10, 12)))
    private val unlock = UnlockPolicy(settings, schedule, events, streak)
    private val engine = PolicyEngine(settings, schedule, SlidingQuota(), unlock, usage)
    private val reels = TargetId("InstagramReels")

    init { settings.set(settings.get().copy(nights = nights)) }

    @Test fun `libre en journee sans usage`() {
        assertEquals(Decision.Allow, engine.evaluate(at(21, 15)))
    }

    @Test fun `bloque la nuit avec deblocage au lever`() {
        val d = engine.evaluate(at(21, 23, 30))
        assertIs<Decision.Block>(d)
        assertEquals(BlockReason.Night, d.reason)
        assertEquals(at(22, 7, 30), d.unlockAt)
    }

    @Test fun `bloque par quota apres 5 minutes`() {
        val i = usage.open(reels, at(21, 15))
        usage.update(i.copy(end = at(21, 15, 5), open = false))
        val d = engine.evaluate(at(21, 15, 5))
        assertIs<Decision.Block>(d)
        assertEquals(BlockReason.Quota, d.reason)
    }

    @Test fun `deblocage actif prime sur la nuit`() {
        unlock.commit(at(21, 23, 30))
        assertEquals(Decision.Allow, engine.evaluate(at(21, 23, 40)))
        assertIs<Decision.Block>(engine.evaluate(at(21, 23, 46)))
    }

    @Test fun `nuit prime sur quota`() {
        val i = usage.open(reels, at(21, 22, 50))
        usage.update(i.copy(end = at(21, 23, 0), open = false))
        val d = engine.evaluate(at(21, 23, 5))
        assertIs<Decision.Block>(d)
        assertEquals(BlockReason.Night, d.reason)
    }

    @Test fun `unlockAt du blocage quota est propage`() {
        val i = usage.open(reels, at(21, 15))
        usage.update(i.copy(end = at(21, 15, 5), open = false))
        val d = engine.evaluate(at(21, 15, 5))
        assertIs<Decision.Block>(d)
        assertEquals(at(21, 15, 30).plusSeconds(1), d.unlockAt)
    }

    @Test fun `apres un relapse dont le temps a ete compte le quota rebloque aussitot`() {
        unlock.commit(at(21, 15))                                   // joker 15:00 → 15:05
        val i = usage.open(reels, at(21, 15))
        usage.update(i.copy(end = at(21, 15, 5), open = false))     // 5 min de scroll pendant le joker
        assertEquals(Decision.Allow, engine.evaluate(at(21, 15, 4)))
        val d = engine.evaluate(at(21, 15, 5))
        assertIs<Decision.Block>(d)
        assertEquals(BlockReason.Quota, d.reason)
    }
}
