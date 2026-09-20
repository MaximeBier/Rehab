package rehab.domain.degraded

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DegradedModeTrackerTest {
    private val t0 = Instant.parse("2026-09-21T10:00:00Z")
    private val ig = "com.instagram.android"
    private val tracker = DegradedModeTracker()

    @Test fun `version hors plage degrade immediatement`() {
        tracker.onVersionCheck(ig, inRange = false)
        assertTrue(tracker.isDegraded(ig))
        assertEquals("version", tracker.reason(ig))
    }

    @Test fun `inconnu pendant 30 s degrade`() {
        tracker.onDetection(ig, unknownScreen = true, now = t0)
        tracker.onDetection(ig, unknownScreen = true, now = t0.plusSeconds(29))
        assertFalse(tracker.isDegraded(ig))
        tracker.onDetection(ig, unknownScreen = true, now = t0.plusSeconds(30))
        assertTrue(tracker.isDegraded(ig))
        assertEquals("unknown", tracker.reason(ig))
    }

    @Test fun `un ecran connu reinitialise le compteur`() {
        tracker.onDetection(ig, unknownScreen = true, now = t0)
        tracker.onDetection(ig, unknownScreen = false, now = t0.plusSeconds(20))
        tracker.onDetection(ig, unknownScreen = true, now = t0.plusSeconds(40))
        assertFalse(tracker.isDegraded(ig))
    }

    /**
     * Régression du IMPORTANT 3 de la revue finale : le compteur ne doit plus dépendre d'un onglet Accueil
     * sélectionné, sans quoi un écran inconnu en permanence (ex. X, dont les règles ne posent pas de contrainte
     * d'onglet Accueil pour les écrans hors cible) n'armait jamais le mode dégradé.
     */
    @Test fun `inconnu hors accueil compte aussi`() {
        tracker.onDetection(ig, unknownScreen = true, now = t0)
        tracker.onDetection(ig, unknownScreen = true, now = t0.plusSeconds(30))
        assertTrue(tracker.isDegraded(ig))
    }

    @Test fun `version revenue dans la plage leve le mode version`() {
        tracker.onVersionCheck(ig, inRange = false)
        tracker.onVersionCheck(ig, inRange = true)
        assertFalse(tracker.isDegraded(ig))
    }

    @Test fun `l etat degrade par inconnu survit au retour d ecrans connus`() {
        tracker.onDetection(ig, unknownScreen = true, now = t0)
        tracker.onDetection(ig, unknownScreen = true, now = t0.plusSeconds(30))
        tracker.onDetection(ig, unknownScreen = false, now = t0.plusSeconds(40))
        assertTrue(tracker.isDegraded(ig))
        assertEquals("unknown", tracker.reason(ig))
    }
}
