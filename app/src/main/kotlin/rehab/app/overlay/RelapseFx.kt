package rehab.app.overlay

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * DESIGN §5, tableau de l'animation relapse. `p` ∈ [0, 1] ; les phases (pulsation, tremblement) sont
 * intégrées image par image par [BlockOverlay], ces fonctions restent pures.
 */
object RelapseFx {
    private const val FROM = 0x171513
    private const val TO = 0x5A1A15

    /**
     * Interpolation canal par canal en sRGB avec arrondi, exactement comme `Overlay-Proto.dc.html`
     * (`Math.round(a + (b - a) * t)`). Pas `androidx.compose.ui.graphics.lerp`, qui interpole en Oklab
     * et ne retombe pas exactement sur les teintes de la maquette.
     */
    fun background(p: Float): Color {
        val t = p.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (FROM shr shift) and 0xFF
            val b = (TO shr shift) and 0xFF
            return (a + (b - a) * t).roundToInt()
        }
        return Color(red = channel(16), green = channel(8), blue = channel(0))
    }

    fun daysShown(streak: Int, p: Float): Int = (streak * (1f - p)).roundToInt().coerceAtLeast(0)

    /** Période de la pulsation du bouton, en secondes (s'accélère avec p). */
    fun pulsePeriodSec(p: Float) = 0.9f - 0.5f * p

    /** Période du tremblement de l'écran, en secondes (s'accélère au-delà de p = 0.6). */
    fun shakePeriodSec(p: Float) = 0.14f - 0.2f * (p - 0.6f)

    /** 1 + 0.18 p, multiplié par une pulsation 1 → 1.06 → 1 au-delà de p = 0.3. */
    fun buttonScale(p: Float, pulsePhase: Float): Float {
        val base = 1f + 0.18f * p
        if (p <= 0.3f) return base
        return base * (1f + 0.06f * (0.5f - 0.5f * cos(2f * PI.toFloat() * pulsePhase)))
    }

    /** Décalage horizontal ±2 dp au-delà de p = 0.6. */
    fun shakeDp(p: Float, shakePhase: Float): Float = if (p <= 0.6f) 0f else 2f * sin(2f * PI.toFloat() * shakePhase)

    fun captionScale(p: Float) = 1f + 0.12f * p
}
