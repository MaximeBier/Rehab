package rehab.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SettingsTest {
    @Test fun `defauts conformes a la spec`() {
        val s = Settings.DEFAULT
        assertEquals(NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30)), s.nights[DayOfWeek.MONDAY])
        assertEquals(NightWindow(LocalTime.of(0, 30), LocalTime.of(9, 0)), s.nights[DayOfWeek.FRIDAY])
        assertEquals(NightWindow(LocalTime.of(0, 30), LocalTime.of(9, 0)), s.nights[DayOfWeek.SATURDAY])
        assertEquals(NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30)), s.nights[DayOfWeek.SUNDAY])
        assertEquals(listOf(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5)), QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30))), s.quotaWindows)
        assertEquals(Duration.ofMinutes(5), s.jokerDuration)
        assertEquals(Duration.ofMinutes(15), s.relapseDuration)
        assertEquals(2, s.jokersPerDay)
        assertEquals(Duration.ofSeconds(10), s.holdDuration)
    }

    @Test fun `plage vide quand coucher egale lever`() {
        assertTrue(NightWindow(LocalTime.of(8, 0), LocalTime.of(8, 0)).isEmpty)
    }

    @Test fun `fenetre de quota invalide si plafond superieur a la duree`() {
        assertFailsWith<IllegalArgumentException> { QuotaWindow(Duration.ofMinutes(5), Duration.ofMinutes(10)) }
    }
}
