package rehab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.policy.GuardResult
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SettingsTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val now = ZonedDateTime.of(2026, 9, 20, 22, 0, 0, 0, zone).toInstant()

    @Test fun values() {
        assertEquals("23:00 → 07:30", SettingsText.nightValue(NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30))))
        assertEquals("désactivée", SettingsText.nightValue(NightWindow(LocalTime.of(0, 0), LocalTime.of(0, 0))))
        assertEquals("Sur 30 min", SettingsText.windowLabel(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))))
        assertEquals("Sur 6 h", SettingsText.windowLabel(QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30))))
        assertEquals("5 min max", SettingsText.windowValue(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))))
        assertEquals("10 s", SettingsText.seconds(Duration.ofSeconds(10)))
    }

    @Test fun lockReasons() {
        assertEquals("Nuit en cours — modifiable à partir de 07:30", SettingsText.nightLockReason(ZonedDateTime.of(2026, 9, 21, 7, 30, 0, 0, zone).toInstant(), zone))
        assertEquals("Quota dépassé — modifiable dans 18 min", SettingsText.quotaLockReason(now.plusSeconds(18 * 60 - 20), now))
    }

    @Test fun rejection() {
        val r = GuardResult.Rejected("Quota en cours", now.plusSeconds(18 * 60))
        assertEquals("Quota en cours : modifiable à partir de 22:18.", SettingsText.rejection(r, zone))
    }
}
