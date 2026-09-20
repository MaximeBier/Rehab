package rehab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.domain.model.Event
import rehab.domain.model.TargetId
import rehab.domain.model.UsageInterval
import java.time.ZoneId
import java.time.ZonedDateTime

class JournalTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val t = ZonedDateTime.of(2026, 9, 21, 15, 4, 0, 0, zone).toInstant()

    @Test fun events() {
        assertEquals("21/09 15:04 · Joker (+5 min)", JournalText.line(Event.Joker(t, t.plusSeconds(300)), zone))
        assertEquals("21/09 15:04 · RELAPSE (+15 min)", JournalText.line(Event.Relapse(t, t.plusSeconds(900)), zone))
        assertEquals("21/09 15:04 · Service activé", JournalText.line(Event.ServiceOn(t), zone))
        assertEquals("21/09 15:04 · Service désactivé", JournalText.line(Event.ServiceOff(t), zone))
        assertEquals("21/09 15:04 · Règles hors plage : com.instagram.android 400.0", JournalText.line(Event.RulesOutOfRange(t, "com.instagram.android", "400.0"), zone))
        assertEquals("21/09 15:04 · Erreur : boom", JournalText.line(Event.Error(t, "boom"), zone))
    }

    @Test fun interval() {
        val i = UsageInterval(1, TargetId("InstagramReels"), t, t.plusSeconds(200), open = false)
        assertEquals("21/09 15:04 · InstagramReels · 3 min 20 s", JournalText.line(i, zone))
    }

    @Test fun openInterval() {
        val i = UsageInterval(1, TargetId("InstagramReels"), t, t.plusSeconds(200), open = true)
        assertEquals("21/09 15:04 · InstagramReels · 3 min 20 s (en cours)", JournalText.line(i, zone))
    }
}
