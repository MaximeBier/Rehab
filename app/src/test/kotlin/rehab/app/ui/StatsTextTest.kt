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
        val c = StatsText.card("Instagram", Duration.ofMinutes(45), "Android", Duration.ofMinutes(12), Duration.ofMinutes(3), hasPermission = true)
        assertEquals("45 min/j · Android", c.before)
        assertEquals("12 min/j", c.now)
        assertEquals("3 min/j", c.rehab)
        assertEquals("-73 %", c.change)
        assertEquals(StatTone.Accent, c.changeTone)
    }

    @Test fun cardHausseIsDanger() {
        val c = StatsText.card("X", Duration.ofMinutes(20), "saisi", Duration.ofMinutes(40), Duration.ZERO, hasPermission = true)
        assertEquals("20 min/j · saisi", c.before)
        assertEquals("+100 %", c.change)
        assertEquals(StatTone.Danger, c.changeTone)
    }

    @Test fun cardEcartInconnuWhenBeforeMissing() {
        val c = StatsText.card("X", null, null, Duration.ofMinutes(10), Duration.ofMinutes(2), hasPermission = true)
        assertEquals("à renseigner", c.before)
        assertEquals("—", c.change)
        assertEquals(StatTone.Neutral, c.changeTone)
    }

    @Test fun cardShowsPermissionRequiredWhenNoAccess() {
        val c = StatsText.card("X", null, null, null, Duration.ofMinutes(2), hasPermission = false)
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
