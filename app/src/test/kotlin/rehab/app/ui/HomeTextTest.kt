package rehab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.model.QuotaWindow
import rehab.domain.policy.ActiveUnlock
import rehab.domain.policy.Schedule
import rehab.domain.policy.SlidingQuota
import rehab.domain.policy.StreakSummary
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 9, 20, h, m, 0, 0, zone).toInstant()
    private val w30 = QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))
    private val w6h = QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30))

    @Test fun durations() {
        assertEquals("30 min", HomeText.duration(Duration.ofMinutes(30)))
        assertEquals("6 h", HomeText.duration(Duration.ofHours(6)))
        assertEquals("1 h 30 min", HomeText.duration(Duration.ofMinutes(90)))
        assertEquals("0 min", HomeText.duration(Duration.ofSeconds(20)))
    }

    @Test fun pluralAccordFrancais() {
        assertEquals("0 jour", HomeText.plural(0, "jour", "jours"))
        assertEquals("1 jour", HomeText.plural(1, "jour", "jours"))
        assertEquals("2 jours", HomeText.plural(2, "jour", "jours"))
        assertEquals("0 joker restant", HomeText.plural(0, "joker restant", "jokers restants"))
        assertEquals("1 joker restant", HomeText.plural(1, "joker restant", "jokers restants"))
        assertEquals("2 jokers restants", HomeText.plural(2, "joker restant", "jokers restants"))
    }

    @Test fun pillStates() {
        assertEquals(Pill("Service inactif", PillTone.Warn), HomeText.pill(false, Decision.Allow, null, zone))
        assertEquals(Pill("Libre", PillTone.Accent), HomeText.pill(true, Decision.Allow, null, zone))
        assertEquals(Pill("Bloqué · quota", PillTone.Muted), HomeText.pill(true, Decision.Block(BlockReason.Quota, at(22, 18)), null, zone))
        assertEquals(Pill("Bloqué · nuit", PillTone.Muted), HomeText.pill(true, Decision.Block(BlockReason.Night, at(7, 30)), null, zone))
        assertEquals(Pill("Joker · jusqu'à 22:05", PillTone.Accent), HomeText.pill(true, Decision.Allow, ActiveUnlock(ActiveUnlock.Kind.Joker, at(22, 5)), zone))
        assertEquals(Pill("Relapse · jusqu'à 22:17", PillTone.Accent), HomeText.pill(true, Decision.Allow, ActiveUnlock(ActiveUnlock.Kind.Relapse, at(22, 17)), zone))
    }

    @Test fun statusLine() {
        val now = at(22, 0)
        assertEquals("Quota atteint · déblocage dans 18 min (22:18)", HomeText.statusLine(Decision.Block(BlockReason.Quota, at(22, 18)), null, now, zone))
        // 17 min 30 s restantes : arrondi au-dessus (18 min), l'heure affichée reste celle de l'échéance.
        assertEquals("Quota atteint · déblocage dans 18 min (22:17)", HomeText.statusLine(Decision.Block(BlockReason.Quota, at(22, 17).plusSeconds(30)), null, now, zone))
        assertEquals("Nuit · déblocage à 07:30", HomeText.statusLine(Decision.Block(BlockReason.Night, at(7, 30)), null, now, zone))
        assertEquals("Joker actif · blocage suspendu jusqu'à 22:05", HomeText.statusLine(Decision.Allow, ActiveUnlock(ActiveUnlock.Kind.Joker, at(22, 5)), now, zone))
        assertEquals("Relapse · blocage suspendu jusqu'à 22:15", HomeText.statusLine(Decision.Allow, ActiveUnlock(ActiveUnlock.Kind.Relapse, at(22, 15)), now, zone))
        assertNull(HomeText.statusLine(Decision.Allow, null, now, zone))
    }

    @Test fun quotaWaitRoundsUpAndSwitchesToHours() {
        val now = at(20, 0)
        assertEquals("Quota atteint · déblocage dans 1 h 05 min (21:05)", HomeText.statusLine(Decision.Block(BlockReason.Quota, at(21, 5)), null, now, zone))
        assertEquals("Quota atteint · déblocage dans 1 min (20:00)", HomeText.statusLine(Decision.Block(BlockReason.Quota, now.plusSeconds(20)), null, now, zone))
    }

    @Test fun recordLines() {
        assertEquals("Record en cours depuis 1 jour", HomeText.recordLine(StreakSummary(24, 24, 23)))
        assertEquals("Record en cours depuis 3 jours", HomeText.recordLine(StreakSummary(26, 26, 23)))
        assertNull(HomeText.recordLine(StreakSummary(12, 23, 23)))
        assertEquals("ancien record 23 · jokers 2/2", HomeText.metaLine(StreakSummary(24, 24, 23), 2, 2))
        assertEquals("record 23 · jokers 1/2", HomeText.metaLine(StreakSummary(12, 23, 23), 1, 2))
        assertEquals("record 0 · jokers 0/0", HomeText.metaLine(StreakSummary(0, 0, 0), 0, 0))
    }

    @Test fun ringFraction() {
        assertEquals(1f, HomeText.ringFraction(StreakSummary(24, 24, 23)))
        assertEquals(12f / 23f, HomeText.ringFraction(StreakSummary(12, 23, 23)))
        assertEquals(0f, HomeText.ringFraction(StreakSummary(0, 0, 0)))
    }

    @Test fun gauges() {
        val g30 = HomeText.gauge(SlidingQuota.WindowUsage(w30, Duration.ofMinutes(3).plusSeconds(20)))
        assertEquals("Fenêtre 30 min", g30.label); assertEquals("3 / 5 min", g30.value); assertEquals(200f / 300f, g30.fraction, 0.001f); assertFalse(g30.exceeded)
        val g = HomeText.gauge(SlidingQuota.WindowUsage(w6h, Duration.ofMinutes(12)))
        assertEquals("Fenêtre 6 h", g.label); assertEquals("12 / 30 min", g.value); assertEquals(0.4f, g.fraction, 0.001f); assertFalse(g.exceeded)
        val full = HomeText.gauge(SlidingQuota.WindowUsage(w30, Duration.ofMinutes(6)))
        assertEquals("6 / 5 min", full.value); assertEquals(1f, full.fraction); assertTrue(full.exceeded)
    }

    @Test fun nightLine() {
        val p = Schedule.NightPeriod(LocalDate.of(2026, 9, 20), at(23, 0), at(23, 0).plus(Duration.ofMinutes(510)))
        assertEquals(NightLine("Prochaine nuit", "23:00 → 07:30"), HomeText.night(p, at(12, 0), zone))
        assertEquals(NightLine("Nuit en cours", "→ 07:30"), HomeText.night(p, at(23, 30), zone))
        assertEquals(NightLine("Prochaine nuit", "aucune"), HomeText.night(null, at(12, 0), zone))
    }

    @Test fun alerts() {
        assertEquals(HomeAlert("Service d'accessibilité désactivé — aucun blocage actif", AlertAction.OpenAccessibility), HomeText.serviceAlert())
        assertEquals(HomeAlert("Instagram 412.0 hors plage testée — tout l'onglet Accueil compte comme cible", AlertAction.OpenDebug),
            HomeText.outOfRangeAlert("com.instagram.android", "412.0.0.35.104"))
        assertEquals(HomeAlert("X 10.60 hors plage testée — la détection peut être incomplète", AlertAction.OpenDebug),
            HomeText.outOfRangeAlert("com.twitter.android", "10.60.0-release.0"))
        assertEquals(HomeAlert("Instagram n'est pas installée", null), HomeText.notInstalledAlert("com.instagram.android"))
        assertEquals(HomeAlert("Instagram : écrans non reconnus — mode dégradé actif", AlertAction.OpenDebug), HomeText.unknownScreensAlert("com.instagram.android"))
    }

    @Test fun unit() {
        assertEquals("JOUR", HomeText.unit(1))
        assertEquals("JOURS", HomeText.unit(24))
    }
}
