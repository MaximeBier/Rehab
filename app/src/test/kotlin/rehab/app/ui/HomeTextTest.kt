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

    @Test fun quotaLineExceeded() {
        val u = SlidingQuota.WindowUsage(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5)), Duration.ofMinutes(6))
        assertEquals("6 / 5 min sur 30 min", HomeText.quotaLine(u))
    }

    @Test fun durationZero() {
        assertEquals("0 min", HomeText.duration(Duration.ZERO))
    }

    @Test fun pluralAccordFrancais() {
        assertEquals("0 jour", HomeText.plural(0, "jour", "jours"))
        assertEquals("1 jour", HomeText.plural(1, "jour", "jours"))
        assertEquals("2 jours", HomeText.plural(2, "jour", "jours"))
        assertEquals("0 joker restant", HomeText.plural(0, "joker restant", "jokers restants"))
        assertEquals("1 joker restant", HomeText.plural(1, "joker restant", "jokers restants"))
        assertEquals("2 jokers restants", HomeText.plural(2, "joker restant", "jokers restants"))
    }

    @Test fun outOfRangeMessageDependsOnPackage() {
        assertEquals(
            "Instagram 500.0 hors plage testée : Instagram est bloqué en entier en attendant une mise à jour des règles.",
            HomeText.outOfRangeMessage("com.instagram.android", "500.0"),
        )
        assertEquals(
            "X 20.0 hors plage testée : X est bloqué en entier en attendant une mise à jour des règles.",
            HomeText.outOfRangeMessage("com.twitter.android", "20.0"),
        )
    }

    @Test fun status() {
        assertEquals("Libre", HomeText.status(Decision.Allow, null, zone))
        assertEquals("Déblocage en cours jusqu'à 07:30", HomeText.status(Decision.Allow, t, zone))
        assertEquals("Bloqué (nuit) jusqu'à 07:30", HomeText.status(Decision.Block(BlockReason.Night, t), null, zone))
        assertEquals("Bloqué (quota) jusqu'à 07:30", HomeText.status(Decision.Block(BlockReason.Quota, t), null, zone))
    }
}
