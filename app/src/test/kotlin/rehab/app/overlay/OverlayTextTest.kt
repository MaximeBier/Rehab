package rehab.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.domain.model.BlockReason
import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.policy.PressOutcome
import rehab.domain.policy.StreakSummary
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class OverlayTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 9, d, h, m, 0, 0, zone).toInstant()
    private val record = StreakSummary(24, 24, 23)
    private val offRecord = StreakSummary(12, 23, 23)
    private fun state(reason: BlockReason, unlock: Instant, outcome: PressOutcome = PressOutcome.Joker(1), streak: StreakSummary = record) =
        OverlayState(reason, unlock.toEpochMilli(), "détail", streak, outcome, 10_000, 5, 15, null, zone)

    @Test fun titlesAreStatic() {
        assertEquals("Nuit · déblocage à 07:30", OverlayText.title(state(BlockReason.Night, at(21, 7, 30)), at(20, 23, 30).toEpochMilli()))
        val q = state(BlockReason.Quota, at(20, 22, 18))
        assertEquals("Quota atteint · 18 min", OverlayText.title(q, at(20, 22, 0).toEpochMilli()))
        assertEquals("Quota atteint · 18 min", OverlayText.title(q, at(20, 22, 0).toEpochMilli() + 20_000))
    }

    @Test fun details() {
        assertEquals("Plage nocturne du dimanche : 23:00 → 07:30", OverlayText.nightDetail(DayOfWeek.SUNDAY, NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30))))
        assertEquals("5 min sur les 30 dernières minutes, toutes cibles", OverlayText.quotaDetail(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))))
        assertEquals("30 min sur les 6 dernières heures, toutes cibles", OverlayText.quotaDetail(QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30))))
        assertEquals("10 min sur la dernière heure, toutes cibles", OverlayText.quotaDetail(QuotaWindow(Duration.ofHours(1), Duration.ofMinutes(10))))
        assertEquals("Jokers du jour épuisés (2/2)", OverlayText.jokersExhausted(2))
        assertEquals("Aucun joker prévu (0/0)", OverlayText.jokersExhausted(0))
    }

    @Test fun streakLines() {
        assertEquals("Record en cours depuis 1 jour", OverlayText.streakLine(record))
        assertEquals("Sans relapse depuis 12 jours · record 23", OverlayText.streakLine(offRecord))
        assertEquals("Série remise à zéro · record 24 conservé", OverlayText.doneStreakLine(record))
    }

    @Test fun restCaptions() {
        assertEquals(Caption("Maintenir 10 s · ", "Joker +5 min", " · 1 restant aujourd’hui"), OverlayText.caption(state(BlockReason.Quota, at(20, 22, 18), PressOutcome.Joker(1))))
        assertEquals(Caption("Maintenir 10 s · ", "Joker +5 min", " · dernier joker du jour"), OverlayText.caption(state(BlockReason.Quota, at(20, 22, 18), PressOutcome.Joker(0))))
        assertEquals(Caption("Maintenir 10 s · ", "RELAPSE", " · ton streak de 24 jours tombe"), OverlayText.caption(state(BlockReason.Night, at(21, 7, 30), PressOutcome.Relapse(24))))
        assertEquals(Caption("Maintenir 10 s · ", "RELAPSE", " · ta série de 12 jours tombe"), OverlayText.caption(state(BlockReason.Night, at(21, 7, 30), PressOutcome.Relapse(12), offRecord)))
        assertEquals(Caption("Maintenir 10 s · ", "RELAPSE", " · ta série de 1 jour tombe"), OverlayText.caption(state(BlockReason.Night, at(21, 7, 30), PressOutcome.Relapse(1), StreakSummary(1, 5, 5))))
    }

    @Test fun holdingAndDoneCaptions() {
        assertEquals("Encore 4 s et ton streak de 24 jours tombe", OverlayText.holdingCaption(state(BlockReason.Night, at(21, 7, 30), PressOutcome.Relapse(24)), secondsLeft = 4))
        assertEquals("Encore 4 s · Joker +5 min", OverlayText.holdingCaption(state(BlockReason.Quota, at(20, 22, 18)), secondsLeft = 4))
        assertEquals("Relapse enregistré · déblocage 15 min", OverlayText.doneCaption(state(BlockReason.Night, at(21, 7, 30), PressOutcome.Relapse(24))))
        assertEquals("Joker activé · déblocage 5 min", OverlayText.doneCaption(state(BlockReason.Quota, at(20, 22, 18))))
    }
}
