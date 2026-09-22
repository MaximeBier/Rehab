package rehab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.model.QuotaWindow
import rehab.domain.policy.ActiveUnlock
import rehab.domain.policy.SlidingQuota
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class DebugTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 9, 20, h, m, 0, 0, zone).toInstant()
    private val u30 = SlidingQuota.WindowUsage(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5)), Duration.ofMinutes(3))
    private val u6h = SlidingQuota.WindowUsage(QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30)), Duration.ofMinutes(12))

    @Test fun decision() {
        assertEquals("Allow · quota 3/5 min", DebugText.decision(Decision.Allow, null, listOf(u30, u6h), zone))
        assertEquals("Allow", DebugText.decision(Decision.Allow, null, emptyList(), zone))
        assertEquals("Allow · joker → 22:05", DebugText.decision(Decision.Allow, ActiveUnlock(ActiveUnlock.Kind.Joker, at(22, 5)), listOf(u30), zone))
        assertEquals("Block · nuit → 07:30", DebugText.decision(Decision.Block(BlockReason.Night, at(7, 30)), null, listOf(u30), zone))
        assertEquals("Block · quota → 22:18", DebugText.decision(Decision.Block(BlockReason.Quota, at(22, 18)), null, listOf(u30), zone))
    }

    @Test fun ago() {
        assertEquals("3 s", DebugText.ago(10_000, 7_000))
        assertEquals("2 min 05 s", DebugText.ago(125_000, 0))
        assertEquals("1 h 02 min", DebugText.ago(3_720_000, 0))
        assertEquals("0 s", DebugText.ago(0, 5_000))
    }

    @Test fun size() {
        assertEquals("38 Ko", DebugText.size(38 * 1024L))
        assertEquals("1 Ko", DebugText.size(10))
        assertEquals("1,5 Mo", DebugText.size(1536 * 1024L))
    }
}
