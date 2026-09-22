package rehab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.app.service.AppStatus

class OnboardingTextTest {
    @Test fun appValues() {
        assertEquals("412.0 · reconnue" to PillTone.Accent, OnboardingText.appValue(AppStatus("com.instagram.android", installed = true, version = "412.0.0.35.104", inRange = true)))
        assertEquals("413.1 · hors plage" to PillTone.Warn, OnboardingText.appValue(AppStatus("com.instagram.android", installed = true, version = "413.1.0.1", inRange = false)))
        assertEquals("non installée" to PillTone.Muted, OnboardingText.appValue(AppStatus("com.twitter.android", installed = false, version = null, inRange = false)))
    }
}
