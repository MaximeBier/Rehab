package rehab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.model.Settings
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

class SettingsEditsTest {
    private val s = Settings.DEFAULT

    @Test fun night() {
        val r = SettingsEdits.withNight(s, DayOfWeek.SUNDAY, LocalTime.of(22, 30), LocalTime.of(7, 0))
        assertEquals(NightWindow(LocalTime.of(22, 30), LocalTime.of(7, 0)), r.nights[DayOfWeek.SUNDAY])
        assertEquals(s.nights[DayOfWeek.MONDAY], r.nights[DayOfWeek.MONDAY])
    }

    @Test fun windowEditKeepsDurationAndCapApart() {
        val r = SettingsEdits.withWindow(s, 0, durationText = "45", capText = "10").getOrThrow()
        assertEquals(QuotaWindow(Duration.ofMinutes(45), Duration.ofMinutes(10)), r.quotaWindows[0])
        assertEquals(s.quotaWindows[1], r.quotaWindows[1])
    }

    @Test fun windowValidation() {
        assertEquals("Plafond : doit être supérieur à 0", SettingsEdits.withWindow(s, 0, "30", "0").exceptionOrNull()?.message)
        assertEquals("Durée : nombre entier attendu", SettingsEdits.withWindow(s, 0, "abc", "5").exceptionOrNull()?.message)
        assertEquals("Le plafond dépasse la durée de la fenêtre", SettingsEdits.withWindow(s, 0, "30", "40").exceptionOrNull()?.message)
        assertEquals("Durée : valeur manquante", SettingsEdits.addWindow(s, " ", "5").exceptionOrNull()?.message)
    }

    @Test fun addAndRemoveWindows() {
        val added = SettingsEdits.addWindow(s, "60", "10").getOrThrow()
        assertEquals(3, added.quotaWindows.size)
        val none = SettingsEdits.removeWindow(SettingsEdits.removeWindow(s, 0), 0)
        assertTrue(none.quotaWindows.isEmpty())
    }

    @Test fun unlockFields() {
        assertEquals(Duration.ofMinutes(7), SettingsEdits.withJokerMinutes(s, "7").getOrThrow().jokerDuration)
        assertEquals(Duration.ofMinutes(20), SettingsEdits.withRelapseMinutes(s, "20").getOrThrow().relapseDuration)
        assertEquals(0, SettingsEdits.withJokersPerDay(s, "0").getOrThrow().jokersPerDay)
        assertEquals("Jokers par jour : ne peut pas être négatif", SettingsEdits.withJokersPerDay(s, "-1").exceptionOrNull()?.message)
        assertEquals(Duration.ofSeconds(15), SettingsEdits.withHoldSeconds(s, "15").getOrThrow().holdDuration)
        assertEquals("Durée d’appui : doit être supérieur à 0", SettingsEdits.withHoldSeconds(s, "0").exceptionOrNull()?.message)
    }
}
