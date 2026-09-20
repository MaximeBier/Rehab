package rehab.app.data

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import rehab.app.data.db.RehabDatabase
import rehab.domain.model.Event
import rehab.domain.model.TargetId
import rehab.domain.time.FakeClock
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class RoomAdaptersTest {
    private lateinit var db: RehabDatabase
    private val t0 = Instant.parse("2026-09-21T10:00:00Z")

    @Before fun setUp() { db = RehabDatabase.inMemory(ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() { db.close() }

    @Test fun usageLogRoundTrip() {
        val log = RoomUsageLog(db.usageIntervals())
        val i = log.open(TargetId("InstagramReels"), t0)
        assertTrue(i.id > 0)
        assertEquals(i, log.openInterval())
        log.update(i.copy(end = t0.plusSeconds(60), open = false))
        assertNull(log.openInterval())
        val all = log.intervalsSince(t0)
        assertEquals(1, all.size)
        assertEquals(TargetId("InstagramReels"), all[0].target)
        assertFalse(all[0].open)
        log.purgeBefore(t0.plusSeconds(120))
        assertTrue(log.intervalsSince(Instant.EPOCH).isEmpty())
    }

    @Test fun eventLogRoundTripAllTypes() {
        val log = RoomEventLog(db.events())
        val events = listOf(
            Event.Joker(t0, t0.plusSeconds(300)),
            Event.Relapse(t0.plusSeconds(1), t0.plusSeconds(901)),
            Event.ServiceOn(t0.plusSeconds(2)),
            Event.ServiceOff(t0.plusSeconds(3)),
            Event.RulesOutOfRange(t0.plusSeconds(4), "com.instagram.android", "400.0"),
            Event.Error(t0.plusSeconds(5), "boom"),
        )
        events.forEach(log::append)
        assertEquals(events, log.all())
        assertEquals(events.drop(2), log.since(t0.plusSeconds(2)))
    }

    @Test fun errorsCappedAt500() {
        val log = RoomEventLog(db.events())
        repeat(505) { log.append(Event.Error(t0.plusSeconds(it.toLong()), "e$it")) }
        val errors = log.all().filterIsInstance<Event.Error>()
        assertEquals(500, errors.size)
        assertEquals("e5", errors.first().message)
    }

    @Test fun streakRecordInitialisesInstalledAt() {
        val clock = FakeClock(t0)
        val repo = RoomStreakRecordRepo(db.streakRecord(), clock)
        assertEquals(t0, repo.installedAt())
        assertEquals(0, repo.bestDays())
        repo.setBestDays(7)
        clock.advance(java.time.Duration.ofDays(3))
        assertEquals(7, repo.bestDays())
        assertEquals(t0, repo.installedAt())
    }
}
