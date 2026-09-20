package rehab.domain.usage

import rehab.domain.fakes.InMemoryUsageLog
import rehab.domain.model.TargetId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsageTrackerTest {
    private val t0 = Instant.parse("2026-09-21T10:00:00Z")
    private val reels = TargetId("InstagramReels")
    private val twitter = TargetId("TwitterHome")
    private val log = InMemoryUsageLog()
    private val tracker = UsageTracker(log)

    @Test fun `premiere detection ouvre un intervalle`() {
        tracker.onDetected(reels, t0)
        val open = log.openInterval()!!
        assertEquals(reels, open.target)
        assertEquals(t0, open.start)
    }

    @Test fun `detection nulle ferme l intervalle`() {
        tracker.onDetected(reels, t0)
        tracker.onDetected(null, t0.plusSeconds(30))
        assertNull(log.openInterval())
        assertEquals(t0.plusSeconds(30), log.intervalsSince(t0).single().end)
    }

    @Test fun `changement de cible ferme et rouvre`() {
        tracker.onDetected(reels, t0)
        tracker.onDetected(twitter, t0.plusSeconds(30))
        val all = log.intervalsSince(t0)
        assertEquals(2, all.size)
        assertFalse(all[0].open)
        assertTrue(all[1].open)
        assertEquals(twitter, all[1].target)
    }

    @Test fun `intervalle de moins de 2 s est supprime`() {
        tracker.onDetected(reels, t0)
        tracker.closeOpen(t0.plusSeconds(1))
        assertTrue(log.intervalsSince(Instant.EPOCH).isEmpty())
    }

    @Test fun `tick persiste la fin toutes les 10 s`() {
        tracker.onDetected(reels, t0)
        tracker.onDetected(reels, t0.plusSeconds(5))
        assertEquals(t0, log.openInterval()!!.end)
        tracker.onDetected(reels, t0.plusSeconds(10))
        assertEquals(t0.plusSeconds(10), log.openInterval()!!.end)
    }

    @Test fun `recover ferme l intervalle au dernier tick`() {
        tracker.onDetected(reels, t0)
        tracker.onDetected(reels, t0.plusSeconds(10))
        val fresh = UsageTracker(log)
        fresh.recover()
        val i = log.intervalsSince(Instant.EPOCH).single()
        assertFalse(i.open)
        assertEquals(t0.plusSeconds(10), i.end)
    }
}
