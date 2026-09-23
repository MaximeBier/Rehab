package rehab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class StatsTextTest {
    @Test fun perDayUsesHomeTextDuration() {
        assertEquals("45 min/j", StatsText.perDay(Duration.ofMinutes(45)))
        assertEquals("1 h/j", StatsText.perDay(Duration.ofHours(1)))
    }

    @Test fun cardBaisseIsAccent() {
        val c = StatsText.card(
            "Instagram", "Dont fil et Reels", Duration.ofMinutes(45), "Android", Duration.ofMinutes(12), Duration.ofMinutes(3), hasPermission = true,
        )
        assertEquals("45 min/j · Android", c.before)
        assertEquals("12 min/j", c.now)
        assertEquals("Dont fil et Reels", c.rehabLabel)
        assertEquals("3 min/j", c.rehab)
        // Signe typographique U+2212 (spec §Écran Stats : « −73 % »), pas le trait d'union ASCII.
        assertEquals("−73 %", c.change)
        assertEquals(StatTone.Accent, c.changeTone)
    }

    @Test fun cardHausseIsDanger() {
        val c = StatsText.card("X", "Dont le fil", Duration.ofMinutes(20), "saisi", Duration.ofMinutes(40), Duration.ZERO, hasPermission = true)
        assertEquals("20 min/j · saisi", c.before)
        assertEquals("Dont le fil", c.rehabLabel)
        assertEquals("+100 %", c.change)
        assertEquals(StatTone.Danger, c.changeTone)
    }

    @Test fun cardNoChangeIsNeutral() {
        val c = StatsText.card("X", "Dont le fil", Duration.ofMinutes(20), "saisi", Duration.ofMinutes(20), Duration.ZERO, hasPermission = true)
        assertEquals("0 %", c.change)
        assertEquals(StatTone.Neutral, c.changeTone)
    }

    @Test fun cardEcartInconnuWhenBeforeMissing() {
        val c = StatsText.card("X", "Dont le fil", null, null, Duration.ofMinutes(10), Duration.ofMinutes(2), hasPermission = true)
        assertEquals("à renseigner", c.before)
        assertEquals("—", c.change)
        assertEquals(StatTone.Neutral, c.changeTone)
    }

    @Test fun cardShowsPermissionRequiredWhenNoAccess() {
        val c = StatsText.card("X", "Dont le fil", null, null, null, Duration.ofMinutes(2), hasPermission = false)
        assertEquals("accès requis", c.before)
        assertEquals("accès requis", c.now)
        assertEquals("—", c.change)
    }

    @Test fun settingsBeforeValuePrefersManualWithoutMention() {
        assertEquals("45 min/j", StatsText.settingsBeforeValue(Duration.ofMinutes(45), Duration.ofMinutes(10)))
    }

    @Test fun settingsBeforeValueFallsBackToAndroidWithMention() {
        assertEquals("38 min/j · Android", StatsText.settingsBeforeValue(null, Duration.ofMinutes(38)))
    }

    @Test fun settingsBeforeValueAsksToFillWhenBothUnknown() {
        assertEquals("à renseigner", StatsText.settingsBeforeValue(null, null))
    }
}
