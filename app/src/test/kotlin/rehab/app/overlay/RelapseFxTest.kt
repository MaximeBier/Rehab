package rehab.app.overlay

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class RelapseFxTest {
    @Test fun background() {
        assertEquals(Color(0xFF171513), RelapseFx.background(0f))
        assertEquals(Color(0xFF5A1A15), RelapseFx.background(1f))
        // Canal par canal avec arrondi, comme Overlay-Proto : 0x17 + (0x5A - 0x17) × 0.5 = 56.5 → 57 (0x39).
        assertEquals(Color(0xFF391814), RelapseFx.background(0.5f))
    }

    @Test fun days() {
        assertEquals(24, RelapseFx.daysShown(24, 0f)); assertEquals(12, RelapseFx.daysShown(24, 0.5f)); assertEquals(0, RelapseFx.daysShown(24, 1f))
    }

    @Test fun periods() {
        assertEquals(0.9f - 0.5f * 0.4f, RelapseFx.pulsePeriodSec(0.4f), 1e-4f)
        assertEquals(0.14f - 0.2f * 0.2f, RelapseFx.shakePeriodSec(0.8f), 1e-4f)
    }

    @Test fun scalesAndThresholds() {
        assertEquals(1f, RelapseFx.buttonScale(0f, pulsePhase = 0.25f), 1e-4f)          // pas de pulsation sous 0.3
        assertEquals(1.18f, RelapseFx.buttonScale(1f, pulsePhase = 0f), 1e-4f)
        assertEquals(1.18f * 1.06f, RelapseFx.buttonScale(1f, pulsePhase = 0.5f), 1e-4f)
        assertEquals(0f, RelapseFx.shakeDp(0.5f, shakePhase = 0.25f), 1e-4f)            // pas de tremblement sous 0.6
        assertEquals(2f, RelapseFx.shakeDp(0.8f, shakePhase = 0.25f), 1e-4f)
        assertEquals(1.12f, RelapseFx.captionScale(1f), 1e-4f)
    }
}
