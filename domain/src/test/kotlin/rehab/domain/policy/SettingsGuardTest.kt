package rehab.domain.policy

import rehab.domain.fakes.InMemoryEventLog
import rehab.domain.fakes.InMemorySettingsRepo
import rehab.domain.fakes.InMemoryStreakRecordRepo
import rehab.domain.fakes.InMemoryUsageLog
import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.model.Settings
import rehab.domain.model.TargetId
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettingsGuardTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(2026, 9, d, h, min, 0, 0, zone).toInstant()
    private val nights = DayOfWeek.entries.associateWith { NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30)) }
    private val current = Settings.DEFAULT.copy(nights = nights)
    private val settings = InMemorySettingsRepo(current)
    private val schedule = Schedule({ settings.get().nights }, zone)
    private val events = InMemoryEventLog()
    private val usage = InMemoryUsageLog()
    private val unlock = UnlockPolicy(settings, schedule, events, Streak(schedule, events, InMemoryStreakRecordRepo(at(10, 12))))
    private val engine = PolicyEngine(settings, schedule, SlidingQuota(), unlock, usage)
    private val guard = SettingsGuard(zone, engine)

    private fun withMonday(w: NightWindow) = current.copy(nights = nights + (DayOfWeek.MONDAY to w))

    @Test fun `raccourcir la plage en cours est refuse`() {
        val r = guard.validate(current, withMonday(NightWindow(LocalTime.of(23, 0), LocalTime.of(6, 0))), at(21, 23, 30))
        assertIs<GuardResult.Rejected>(r)
        assertEquals(at(22, 7, 30), r.unlockAt)
    }

    @Test fun `allonger la plage en cours est accepte`() {
        assertEquals(GuardResult.Accepted, guard.validate(current, withMonday(NightWindow(LocalTime.of(22, 0), LocalTime.of(8, 0))), at(21, 23, 30)))
    }

    @Test fun `vider la plage en cours est refuse`() {
        assertIs<GuardResult.Rejected>(guard.validate(current, withMonday(NightWindow(LocalTime.of(8, 0), LocalTime.of(8, 0))), at(21, 23, 30)))
    }

    @Test fun `modifier un autre jour pendant la nuit est accepte`() {
        val proposed = current.copy(nights = nights + (DayOfWeek.WEDNESDAY to NightWindow(LocalTime.of(1, 0), LocalTime.of(5, 0))))
        assertEquals(GuardResult.Accepted, guard.validate(current, proposed, at(21, 23, 30)))
    }

    @Test fun `hors nuit tout changement d horaire est accepte`() {
        assertEquals(GuardResult.Accepted, guard.validate(current, withMonday(NightWindow(LocalTime.of(23, 0), LocalTime.of(6, 0))), at(21, 15)))
    }

    private fun exceedQuota() {
        val i = usage.open(TargetId("InstagramReels"), at(21, 15))
        usage.update(i.copy(end = at(21, 15, 5), open = false))
    }

    @Test fun `relever un plafond pendant un blocage quota est refuse`() {
        exceedQuota()
        val proposed = current.copy(quotaWindows = listOf(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(10)), current.quotaWindows[1]))
        assertIs<GuardResult.Rejected>(guard.validate(current, proposed, at(21, 15, 5)))
    }

    @Test fun `supprimer la fenetre depassee est refuse`() {
        exceedQuota()
        val proposed = current.copy(quotaWindows = listOf(current.quotaWindows[1]))
        assertIs<GuardResult.Rejected>(guard.validate(current, proposed, at(21, 15, 5)))
    }

    @Test fun `baisser un plafond ou ajouter une fenetre pendant le blocage est accepte`() {
        exceedQuota()
        val proposed = current.copy(quotaWindows = listOf(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(3)), current.quotaWindows[1], QuotaWindow(Duration.ofHours(1), Duration.ofMinutes(10))))
        assertEquals(GuardResult.Accepted, guard.validate(current, proposed, at(21, 15, 5)))
    }

    @Test fun `hors blocage quota tout changement est accepte`() {
        val proposed = current.copy(quotaWindows = emptyList())
        assertEquals(GuardResult.Accepted, guard.validate(current, proposed, at(21, 15)))
    }
}
