package rehab.rules

import rehab.rules.catalog.InstagramRules
import rehab.rules.catalog.TwitterRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Onglet de repli touché par la bascule au blocage quota (v0.3.0). */
class RedirectBoundsTest {
    private val detector = ScreenDetector(DefaultCatalog.create())

    @Test fun `instagram messages sur le fil et les reels`() {
        for (name in listOf("ig_feed_followed.xml", "ig_reels.xml")) {
            val snapshot = Fixtures.load(name, InstagramRules.PACKAGE)
            assertEquals(Bounds(432, 2148, 648, 2274), detector.redirectBounds(snapshot, RedirectTab.Messages), name)
        }
    }

    @Test fun `instagram recherche sur le fil`() {
        val snapshot = Fixtures.load("ig_feed_followed.xml", InstagramRules.PACKAGE)
        assertEquals(Bounds(648, 2148, 864, 2274), detector.redirectBounds(snapshot, RedirectTab.Search))
    }

    @Test fun `x messages en bas de l accueil`() {
        val snapshot = Fixtures.load("x_home_foryou.xml", TwitterRules.PACKAGE)
        assertEquals(Bounds(949, 2169, 1012, 2232), detector.redirectBounds(snapshot, RedirectTab.Messages))
    }

    @Test fun `x recherche retient l onglet du bas pas le selecteur du haut`() {
        // x_search.xml porte aussi « Explorer » en [21,300][254,426] (sélecteur interne) : NearBottom l'exclut.
        val snapshot = Fixtures.load("x_search.xml", TwitterRules.PACKAGE)
        assertEquals(Bounds(289, 2169, 352, 2232), detector.redirectBounds(snapshot, RedirectTab.Search))
    }

    @Test fun `barres masquees aucun onglet`() {
        val snapshot = Snapshot(
            TwitterRules.PACKAGE, "12.28", 0L,
            listOf(
                Node(className = "android.widget.FrameLayout", bounds = Bounds(0, 0, 1080, 2400)),
                Node(id = "scaffold_home_tabbed", bounds = Bounds(0, 0, 1080, 2400), depth = 8),
            ),
        )
        assertNull(detector.redirectBounds(snapshot, RedirectTab.Messages))
        assertNull(detector.redirectBounds(snapshot, RedirectTab.Search))
    }

    @Test fun `bounds vides ignores`() {
        val snapshot = Snapshot(
            InstagramRules.PACKAGE, "447", 0L,
            listOf(
                Node(bounds = Bounds(0, 0, 1080, 2400)),
                Node(id = "com.instagram.android:id/direct_tab", bounds = Bounds(432, 2148, 432, 2274), depth = 5),
            ),
        )
        assertNull(detector.redirectBounds(snapshot, RedirectTab.Messages))
    }

    @Test fun `package inconnu`() {
        assertNull(detector.redirectBounds(Snapshot("other", "1", 0L, emptyList()), RedirectTab.Messages))
    }
}
