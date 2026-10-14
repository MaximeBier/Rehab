package rehab.domain.degraded

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DegradedModeTrackerTest {
    private val ig = "com.instagram.android"

    @Test fun `version hors plage degrade immediatement`() {
        val tracker = DegradedModeTracker()
        tracker.onVersionCheck(ig, inRange = false)
        assertTrue(tracker.isDegraded(ig))
        assertEquals("version", tracker.reason(ig))
    }

    @Test fun `version revenue dans la plage leve le mode version`() {
        val tracker = DegradedModeTracker()
        tracker.onVersionCheck(ig, inRange = false)
        tracker.onVersionCheck(ig, inRange = true)
        assertFalse(tracker.isDegraded(ig))
    }

    @Test fun `seule la version hors plage degrade`() {
        val t = DegradedModeTracker()
        assertNull(t.reason("pkg"))
        t.onVersionCheck("pkg", inRange = false)
        assertEquals("version", t.reason("pkg"))
        t.onVersionCheck("pkg", inRange = true)
        assertNull(t.reason("pkg"))
    }
}
