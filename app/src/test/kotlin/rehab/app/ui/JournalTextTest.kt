package rehab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.domain.model.BlockReason
import rehab.domain.model.Event
import rehab.domain.model.TargetId
import rehab.domain.model.UsageInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class JournalTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 9, d, h, m, s, 0, zone).toInstant()
    private val dayOf: (Instant) -> LocalDate = { it.atZone(zone).minusHours(7).minusMinutes(30).toLocalDate() }
    private val now = at(20, 23, 0)
    private fun row(e: Event) = JournalText.rows(listOf(e), emptyList(), 2, dayOf, now, zone).single()

    @Test fun relapseAndJoker() {
        assertEquals(JournalRow(at(20, 21, 47).toEpochMilli(), "21:47", "Relapse · déblocage 15 min", "Événement", "21:47 → 22:02", JournalTone.Danger),
            row(Event.Relapse(at(20, 21, 47), at(20, 22, 2))))
        val jokers = JournalText.rows(listOf(Event.Joker(at(20, 14, 0), at(20, 14, 5)), Event.Joker(at(20, 20, 10), at(20, 20, 15))), emptyList(), 2, dayOf, now, zone)
        assertEquals(listOf("0 restant", "1 restant"), jokers.map { it.detail })   // tri décroissant : le plus récent d'abord
        assertEquals("Joker · déblocage 5 min", jokers[0].label)
        assertEquals(JournalTone.Accent, jokers[0].tone)
    }

    @Test fun blockProducesStartAndEndOnlyWhenPast() {
        val rows = JournalText.rows(listOf(Event.Block(at(20, 22, 0), BlockReason.Quota, at(20, 22, 18))), emptyList(), 2, dayOf, now, zone)
        assertEquals(listOf("Fin du blocage quota", "Blocage quota"), rows.map { it.label })
        assertEquals(listOf("—", "→ 22:18"), rows.map { it.detail })
        assertEquals("Blocage · Quota", rows[0].type)
        val ongoing = JournalText.rows(listOf(Event.Block(at(20, 22, 50), BlockReason.Night, at(21, 7, 30))), emptyList(), 2, dayOf, now, zone)
        assertEquals(listOf("Blocage nuit"), ongoing.map { it.label })
        assertEquals("Blocage · Nuit", ongoing[0].type)
    }

    @Test fun serviceRulesAndErrors() {
        assertEquals(JournalTone.Warn, row(Event.ServiceOff(at(20, 10, 0))).tone)
        assertEquals("Service désactivé", row(Event.ServiceOff(at(20, 10, 0))).label)
        val on = JournalText.rows(listOf(Event.ServiceOff(at(20, 9, 0)), Event.ServiceOn(at(20, 10, 0))), emptyList(), 2, dayOf, now, zone)
        assertEquals("Service réactivé", on[0].label)
        assertEquals("Service activé", row(Event.ServiceOn(at(20, 10, 0))).label)
        assertEquals(JournalTone.Neutral, on[0].tone)
        val rules = row(Event.RulesOutOfRange(at(20, 19, 40), "com.instagram.android", "412.0.0.35.104"))
        assertEquals(listOf("Règles hors plage · Instagram 412.0", "Règles", "notifié"), listOf(rules.label, rules.type, rules.detail))
        assertEquals("Erreur", row(Event.Error(at(20, 9, 0), "boom")).type)
    }

    @Test fun usage() {
        val u = JournalText.rows(emptyList(), listOf(
            UsageInterval(1, TargetId("InstagramReels"), at(20, 21, 32), at(20, 21, 36, 12), open = false),
            UsageInterval(2, TargetId("TwitterHome"), at(20, 18, 2), at(20, 18, 2, 48), open = false),
            UsageInterval(3, TargetId("InstagramSuggested"), at(20, 22, 59), at(20, 23, 0), open = true),
        ), 2, dayOf, now, zone)
        assertEquals(listOf("Instagram · Suggéré", "Instagram · Reels", "X · Accueil"), u.map { it.label })
        assertEquals(listOf("en cours", "4 min 12 s", "48 s"), u.map { it.detail })
        assertEquals("Usage", u[0].type)
    }

    @Test fun groupsByRehabDayNotCivilDay() {
        val rows = JournalText.rows(listOf(Event.ServiceOff(at(20, 1, 0)), Event.ServiceOn(at(20, 12, 0))), emptyList(), 2, dayOf, now, zone)
        val days = JournalText.days(rows, dayOf, today = LocalDate.of(2026, 9, 20))
        assertEquals(listOf("Aujourd'hui · dim. 20 sept.", "Hier · sam. 19 sept."), days.map { it.header })
        assertEquals("01:00", days[1].rows.single().time)
    }

    @Test fun olderDayHeader() {
        assertEquals("ven. 18 sept.", JournalText.dayHeader(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 20)))
    }
}
