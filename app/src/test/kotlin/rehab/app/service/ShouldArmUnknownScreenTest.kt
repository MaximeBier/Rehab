package rehab.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rehab.domain.degraded.DegradedModeTracker
import rehab.rules.Bounds
import rehab.rules.Detection
import java.time.Instant

/**
 * Verrouille la correction du fix de clôture : le compteur d'écran inconnu
 * ([DegradedModeTracker]) ne doit s'armer, sur un écran non reconnu, que lorsque la barre de
 * navigation est présente dans le snapshot ([Detection.navBarBounds] non nul). Sans cette garde,
 * une vue plein écran ordinaire d'Instagram sans barre d'onglets (stories, détail de publication,
 * commentaires, réglages, caméra) — absente des `knownScreens` du catalogue — arme le compteur
 * comme un vrai changement de structure de l'app, et bascule à tort en `degradedByUnknown` au
 * bout de 30 s (état qui ne se vide jamais, voir DegradedModeTrackerTest).
 *
 * La troncature (`Snapshot.truncated`) doit continuer d'armer le compteur indépendamment de cette
 * garde, y compris quand elle fait disparaître la barre de navigation de l'arbre capturé.
 */
class ShouldArmUnknownScreenTest {
    private val pkg = "com.instagram.android"
    private val t0 = Instant.parse("2026-09-21T10:00:00Z")
    private val bounds = Bounds(0, 100, 1080, 200)

    private fun detection(unknownScreen: Boolean, navBarBounds: Bounds?) = Detection(
        target = null,
        screenId = if (unknownScreen) null else "profile",
        navBarBounds = navBarBounds,
        unknownScreen = unknownScreen,
        homeTabSelected = false,
    )

    @Test fun `ecran plein ecran sans barre de navigation n arme pas le signal`() {
        val d = detection(unknownScreen = true, navBarBounds = null)
        assertFalse(shouldArmUnknownScreen(d, truncated = false))
    }

    @Test fun `ecran plein ecran sans barre de navigation ne bascule jamais en degrade meme apres 30s`() {
        val tracker = DegradedModeTracker()
        val d = detection(unknownScreen = true, navBarBounds = null)
        // Simule des ticks répétés (comme le ticker du service, jusqu'à toutes les secondes) sur
        // une visionneuse de stories regardée 40s d'affilée : aucun tick n'arme le compteur.
        var t = t0
        repeat(40) {
            tracker.onDetection(pkg, shouldArmUnknownScreen(d, truncated = false), t)
            t = t.plusSeconds(1)
        }
        assertFalse(tracker.isDegraded(pkg))
    }

    @Test fun `ecran inconnu avec barre de navigation arme le signal`() {
        val d = detection(unknownScreen = true, navBarBounds = bounds)
        assertTrue(shouldArmUnknownScreen(d, truncated = false))
    }

    @Test fun `ecran inconnu avec barre de navigation bascule en degrade au bout de 30s`() {
        val tracker = DegradedModeTracker()
        val d = detection(unknownScreen = true, navBarBounds = bounds)
        tracker.onDetection(pkg, shouldArmUnknownScreen(d, truncated = false), t0)
        assertFalse(tracker.isDegraded(pkg))
        tracker.onDetection(pkg, shouldArmUnknownScreen(d, truncated = false), t0.plusSeconds(30))
        assertTrue(tracker.isDegraded(pkg))
    }

    @Test fun `troncature arme le signal meme sans barre de navigation ni ecran inconnu`() {
        // Écran par ailleurs reconnu (unknownScreen = false) : seule la troncature du snapshot
        // doit armer le compteur ici (fail-closed, spec §2.8).
        val d = detection(unknownScreen = false, navBarBounds = null)
        assertTrue(shouldArmUnknownScreen(d, truncated = true))
    }

    @Test fun `troncature bascule en degrade au bout de 30s meme quand elle fait disparaitre la barre de navigation`() {
        val tracker = DegradedModeTracker()
        val d = detection(unknownScreen = true, navBarBounds = null)
        tracker.onDetection(pkg, shouldArmUnknownScreen(d, truncated = true), t0)
        tracker.onDetection(pkg, shouldArmUnknownScreen(d, truncated = true), t0.plusSeconds(30))
        assertTrue(tracker.isDegraded(pkg))
    }

    @Test fun `ecran reconnu sans troncature ne arme jamais le signal`() {
        val d = detection(unknownScreen = false, navBarBounds = bounds)
        assertFalse(shouldArmUnknownScreen(d, truncated = false))
    }
}
