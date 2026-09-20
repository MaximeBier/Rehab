package rehab.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.domain.model.BlockReason
import rehab.domain.policy.PressOutcome
import java.time.ZoneId
import java.time.ZonedDateTime

class OverlayTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val unlock = ZonedDateTime.of(2026, 9, 22, 7, 30, 0, 0, zone).toInstant().toEpochMilli()
    private fun state(reason: BlockReason, outcome: PressOutcome) =
        OverlayState(reason, unlock, streak = 12, best = 23, outcome = outcome, holdMillis = 10_000, navBarTop = null, zone = zone)

    @Test fun nightReason() {
        val now = ZonedDateTime.of(2026, 9, 21, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Nuit · déblocage à 07:30", OverlayText.reason(state(BlockReason.Night, PressOutcome.Relapse(12)), now))
    }

    @Test fun quotaReasonCountsDown() {
        val now = unlock - 18 * 60_000L - 5_000L
        assertEquals("Quota atteint · 18 min 05 s", OverlayText.reason(state(BlockReason.Quota, PressOutcome.Joker(1)), now))
    }

    @Test fun buttonLabels() {
        assertEquals("Maintenir 10 s · Joker +5 min · 1 restant", OverlayText.button(PressOutcome.Joker(1), holdSeconds = 10, jokerMinutes = 5))
        assertEquals("Maintenir 10 s · Joker +5 min · dernier", OverlayText.button(PressOutcome.Joker(0), holdSeconds = 10, jokerMinutes = 5))
        assertEquals("Maintenir 10 s · RELAPSE · ton streak de 12 jours tombe", OverlayText.button(PressOutcome.Relapse(12), holdSeconds = 10, jokerMinutes = 5))
    }

    @Test fun streakLine() {
        assertEquals("12 jours · record 23", OverlayText.streak(state(BlockReason.Night, PressOutcome.Relapse(12))))
    }
}
