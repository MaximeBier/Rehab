package rehab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rehab.domain.model.Settings
import java.time.DayOfWeek

class SettingsFormTest {
    @Test fun roundTrip() {
        val form = SettingsForm.from(Settings.DEFAULT)
        assertEquals("23:00" to "07:30", form.nights[DayOfWeek.MONDAY])
        assertEquals(listOf("30" to "5", "360" to "30"), form.quotas)
        assertEquals(Settings.DEFAULT, form.toSettings().getOrThrow())
    }

    @Test fun invalidTimeIsReported() {
        val form = SettingsForm.from(Settings.DEFAULT).copy(nights = SettingsForm.from(Settings.DEFAULT).nights + (DayOfWeek.MONDAY to ("25:00" to "07:30")))
        val r = form.toSettings()
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("Lundi"))
    }

    @Test fun capAboveDurationIsReported() {
        val form = SettingsForm.from(Settings.DEFAULT).copy(quotas = listOf("30" to "45"))
        assertTrue(form.toSettings().isFailure)
    }

    @Test fun emptyFieldIsReported() {
        val form = SettingsForm.from(Settings.DEFAULT).copy(jokerMinutes = "")
        val r = form.toSettings()
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("joker", ignoreCase = true))
    }

    @Test fun nonNumericFieldIsReported() {
        val form = SettingsForm.from(Settings.DEFAULT).copy(relapseMinutes = "abc")
        assertTrue(form.toSettings().isFailure)
    }

    @Test fun negativeFieldIsReported() {
        val form = SettingsForm.from(Settings.DEFAULT).copy(holdSeconds = "-5")
        val r = form.toSettings()
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("appui"))
    }

    @Test fun negativeJokersPerDayIsReported() {
        val form = SettingsForm.from(Settings.DEFAULT).copy(jokersPerDay = "-1")
        assertTrue(form.toSettings().isFailure)
    }

    @Test fun zeroJokersPerDayIsAllowed() {
        val form = SettingsForm.from(Settings.DEFAULT).copy(jokersPerDay = "0")
        assertTrue(form.toSettings().isSuccess)
    }

    @Test fun invalidHourIsReported() {
        val form = SettingsForm.from(Settings.DEFAULT)
        val bad = form.copy(nights = form.nights + (DayOfWeek.TUESDAY to ("23:00" to "25:00")))
        val r = bad.toSettings()
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("Mardi"))
    }

    @Test fun zeroDurationQuotaIsReported() {
        val form = SettingsForm.from(Settings.DEFAULT).copy(quotas = listOf("0" to "0"))
        assertTrue(form.toSettings().isFailure)
    }

    // Verrouille la convention (durée, plafond) de `quotas` : un appel d'UI qui écrirait dans le
    // mauvais slot ferait échouer ces deux tests plutôt que de permuter silencieusement les valeurs.
    @Test fun withQuotaCapKeepsDurationUnchanged() {
        val quotas = listOf("30" to "5", "360" to "30")
        assertEquals(listOf("30" to "7", "360" to "30"), SettingsForm.withQuotaCap(quotas, 0, "7"))
    }

    @Test fun withQuotaDurationKeepsCapUnchanged() {
        val quotas = listOf("30" to "5", "360" to "30")
        assertEquals(listOf("30" to "5", "400" to "30"), SettingsForm.withQuotaDuration(quotas, 1, "400"))
    }
}
