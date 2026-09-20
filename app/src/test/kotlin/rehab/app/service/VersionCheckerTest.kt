package rehab.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rehab.domain.degraded.DegradedModeTracker
import rehab.domain.fakes.InMemoryEventLog
import rehab.domain.model.Event
import rehab.domain.model.TargetId
import rehab.domain.time.FakeClock
import rehab.rules.Matcher
import rehab.rules.PackageRules
import rehab.rules.RuleCatalog
import rehab.rules.TargetRule
import rehab.rules.VersionRange
import java.time.Instant

class VersionCheckerTest {
    private val catalog = RuleCatalog(listOf(
        PackageRules("com.instagram.android", VersionRange("340", "360"), listOf(TargetRule(TargetId("R"), "r", listOf(Matcher.ViewId("x"))))),
        PackageRules("com.twitter.android", VersionRange("10", "11"), listOf(TargetRule(TargetId("H"), "h", listOf(Matcher.ViewId("y"))))),
    ))
    private val degraded = DegradedModeTracker()
    private val events = InMemoryEventLog()
    private val notified = mutableListOf<Pair<String, String>>()
    private val notifier = RulesNotifier { p, v -> notified += p to v }
    private val clock = FakeClock(Instant.parse("2026-09-21T10:00:00Z"))

    private fun checker(vararg versions: Pair<String, String?>) =
        VersionChecker({ pkg -> versions.toMap()[pkg] }, catalog, degraded, events, notifier, clock)

    @Test fun inRangeDoesNothing() {
        val statuses = checker("com.instagram.android" to "350.0", "com.twitter.android" to "10.5").checkAll()
        assertTrue(statuses.all { it.installed && it.inRange })
        assertFalse(degraded.isDegraded("com.instagram.android"))
        assertTrue(notified.isEmpty())
    }

    @Test fun outOfRangeDegradesLogsAndNotifiesOnce() {
        val c = checker("com.instagram.android" to "400.0", "com.twitter.android" to "10.5")
        c.checkAll()
        c.checkAll()
        assertTrue(degraded.isDegraded("com.instagram.android"))
        assertEquals(1, events.all().filterIsInstance<Event.RulesOutOfRange>().size)
        assertEquals(listOf("com.instagram.android" to "400.0"), notified)
    }

    @Test fun notInstalledIsReportedWithoutDegrading() {
        val statuses = checker("com.instagram.android" to "350.0", "com.twitter.android" to null).checkAll()
        val x = statuses.single { it.packageName == "com.twitter.android" }
        assertFalse(x.installed)
        assertFalse(degraded.isDegraded("com.twitter.android"))
        assertTrue(notified.isEmpty())
    }
}
