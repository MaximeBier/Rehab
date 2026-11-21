package rehab.rules.catalog

import rehab.rules.DefaultCatalog
import rehab.rules.Fixtures
import rehab.rules.ScreenDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Captures Rehab (JSON, nœuds visibles seulement) prises sur le Pixel le 2026-11-21, Instagram 456.1 et X 12.45.1,
 * anonymisées (voir VERSIONS.md). Verrouille les deux sens : hors DM → cible de l'app, DM → aucune cible.
 */
class CapturesV07Test {
    private val detector = ScreenDetector(DefaultCatalog.create())

    private fun ig(name: String) = Fixtures.load("$name.json", InstagramRules.PACKAGE)
    private fun x(name: String) = Fixtures.load("$name.json", TwitterRules.PACKAGE)

    @Test fun `instagram hors dm compte`() {
        for (name in listOf("v07_ig_home", "v07_ig_explore", "v07_ig_reel")) {
            val s = ig(name)
            assertFalse(s.truncated, name)
            val d = detector.detect(s)
            assertEquals(InstagramRules.APP, d.target, name)
            assertNotNull(d.navBarBounds, name)
            assertEquals(2148, d.navBarBounds!!.top, name)
        }
    }

    /** Reel partagé ouvert depuis une conversation, puis Reel suivant après un balayage (« Suggestions ») : comptés. */
    @Test fun `reel ouvert depuis une conversation compte`() {
        for (name in listOf("v07_ig_dm_reel", "v07_ig_dm_reel_swipe")) {
            assertEquals(InstagramRules.APP, detector.detect(ig(name)).target, name)
        }
    }

    /** Profil ouvert depuis une conversation (nouvelle ModalActivity par-dessus) : compté, la conversation n'est plus à l'écran. */
    @Test fun `profil ouvert depuis une conversation compte`() {
        assertEquals(InstagramRules.APP, detector.detect(ig("v07_ig_dm_profile")).target)
    }

    @Test fun `instagram liste et conversation ne comptent pas`() {
        for (name in listOf("v07_ig_dm_list", "v07_ig_dm_thread")) {
            val d = detector.detect(ig(name))
            assertNull(d.target, name)
            assertEquals("instagram.dm", d.screenId, name)
        }
    }

    @Test fun `x hors dm compte`() {
        for (name in listOf("v07_x_home", "v07_x_search")) {
            val s = x(name)
            assertFalse(s.truncated, name)
            assertEquals(TwitterRules.APP, detector.detect(s).target, name)
        }
    }

    @Test fun `x liste et conversation ne comptent pas`() {
        for (name in listOf("v07_x_dm_list", "v07_x_dm_thread")) {
            val d = detector.detect(x(name))
            assertNull(d.target, name)
            assertEquals("twitter.dm", d.screenId, name)
        }
    }
}
