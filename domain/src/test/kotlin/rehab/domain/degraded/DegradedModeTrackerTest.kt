package rehab.domain.degraded

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DegradedModeTrackerTest {
    private val t0 = Instant.parse("2026-09-21T10:00:00Z")
    private val ig = "com.instagram.android"
    private val tracker = DegradedModeTracker()

    @Test fun `version hors plage degrade immediatement`() {
        tracker.onVersionCheck(ig, inRange = false)
        assertTrue(tracker.isDegraded(ig))
        assertEquals("version", tracker.reason(ig))
    }

    @Test fun `inconnu pendant 30 s avec accueil selectionne degrade`() {
        tracker.onDetection(ig, unknownScreen = true, watching = true, now = t0)
        tracker.onDetection(ig, unknownScreen = true, watching = true, now = t0.plusSeconds(29))
        assertFalse(tracker.isDegraded(ig))
        tracker.onDetection(ig, unknownScreen = true, watching = true, now = t0.plusSeconds(30))
        assertTrue(tracker.isDegraded(ig))
        assertEquals("unknown", tracker.reason(ig))
    }

    @Test fun `un ecran connu reinitialise le compteur`() {
        tracker.onDetection(ig, unknownScreen = true, watching = true, now = t0)
        tracker.onDetection(ig, unknownScreen = false, watching = true, now = t0.plusSeconds(20))
        tracker.onDetection(ig, unknownScreen = true, watching = true, now = t0.plusSeconds(40))
        assertFalse(tracker.isDegraded(ig))
    }

    @Test fun `inconnu hors accueil ne compte pas`() {
        tracker.onDetection(ig, unknownScreen = true, watching = false, now = t0)
        tracker.onDetection(ig, unknownScreen = true, watching = false, now = t0.plusSeconds(60))
        assertFalse(tracker.isDegraded(ig))
    }

    @Test fun `version revenue dans la plage leve le mode version`() {
        tracker.onVersionCheck(ig, inRange = false)
        tracker.onVersionCheck(ig, inRange = true)
        assertFalse(tracker.isDegraded(ig))
    }
}
