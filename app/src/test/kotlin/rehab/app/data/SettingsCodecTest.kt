package rehab.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.model.Settings
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

class SettingsCodecTest {
    @Test fun roundTripDefault() {
        assertEquals(Settings.DEFAULT, SettingsCodec.decode(SettingsCodec.encode(Settings.DEFAULT)))
    }

    @Test fun roundTripCustom() {
        val s = Settings.DEFAULT.copy(
            nights = Settings.DEFAULT.nights + (DayOfWeek.TUESDAY to NightWindow(LocalTime.of(0, 15), LocalTime.of(6, 45))),
            quotaWindows = listOf(QuotaWindow(Duration.ofMinutes(45), Duration.ofMinutes(7))),
            jokersPerDay = 3,
            holdDuration = Duration.ofSeconds(12),
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test fun decodeGarbageFallsBackToDefault() {
        assertEquals(Settings.DEFAULT, SettingsCodec.decodeOrDefault("{not json"))
    }

    /** Schéma antérieur ou migration ratée : JSON syntaxiquement valide mais champ manquant. */
    @Test fun decodeValidJsonMissingFieldFallsBackToDefault() {
        val encoded = SettingsCodec.encode(Settings.DEFAULT)
        val missingField = encoded.replaceFirst(Regex(""","jokersPerDay":\d+"""), "")
        assertEquals(Settings.DEFAULT, SettingsCodec.decodeOrDefault(missingField))
    }
}
