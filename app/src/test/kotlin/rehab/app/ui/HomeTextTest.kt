package rehab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.model.QuotaWindow
import rehab.domain.policy.SlidingQuota
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val t = ZonedDateTime.of(2026, 9, 22, 7, 30, 0, 0, zone).toInstant()

    @Test fun durations() {
        assertEquals("30 min", HomeText.duration(Duration.ofMinutes(30)))
        assertEquals("6 h", HomeText.duration(Duration.ofHours(6)))
        assertEquals("1 h 30 min", HomeText.duration(Duration.ofMinutes(90)))
        assertEquals("0 min", HomeText.duration(Duration.ofSeconds(20)))
    }

    @Test fun quotaLine() {
        val u = SlidingQuota.WindowUsage(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5)), Duration.ofMinutes(3).plusSeconds(20))
        assertEquals("3 / 5 min sur 30 min", HomeText.quotaLine(u))
    }

    @Test fun status() {
        assertEquals("Libre", HomeText.status(Decision.Allow, null, zone))
        assertEquals("Déblocage en cours jusqu'à 07:30", HomeText.status(Decision.Allow, t, zone))
        assertEquals("Bloqué (nuit) jusqu'à 07:30", HomeText.status(Decision.Block(BlockReason.Night, t), null, zone))
        assertEquals("Bloqué (quota) jusqu'à 07:30", HomeText.status(Decision.Block(BlockReason.Quota, t), null, zone))
    }
}
