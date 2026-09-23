package rehab.rules.catalog

import rehab.rules.Bounds
import rehab.rules.Fixtures
import rehab.rules.Node
import rehab.rules.RuleCatalog
import rehab.rules.ScreenDetector
import rehab.rules.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitterRulesTest {
    private val detector = ScreenDetector(RuleCatalog(listOf(TwitterRules.PACKAGE_RULES)))
    private fun detect(name: String) = detector.detect(Fixtures.load(name, TwitterRules.PACKAGE))

    @Test fun `accueil pour vous et abonnements`() {
        for (name in listOf("x_home_foryou.xml", "x_home_following.xml")) {
            val d = detect(name)
            assertEquals(TwitterRules.HOME, d.target, name)
            assertNotNull(d.navBarBounds, name)
        }
    }

    @Test fun `recherche et messages non cibles`() {
        for (name in listOf("x_search.xml", "x_dm.xml")) {
            val d = detect(name)
            assertNull(d.target, name)
            assertFalse(d.unknownScreen, name)
        }
    }

    /**
     * Régression (bug « le temps n'avance pas sur X », 2026-09-23) : en faisant défiler « Pour vous », X 12.28
     * masque la barre d'onglets du bas et le sélecteur « Pour vous / Abonnements ». Il ne reste alors aucun onglet
     * sélectionné ; seul l'identifiant Compose `scaffold_home_tabbed` (présent sur l'accueil uniquement, absent de
     * x_dm.xml et x_search.xml) signale le fil. Structure reproduite depuis une capture réelle (contenu personnel
     * non versionné) : racine, `MainLanding`, `scaffold_home_tabbed`, des `timeline_post`, aucun nœud sélectionné.
     */
    @Test fun `accueil defile barres masquees reste la cible`() {
        val snapshot = Snapshot(
            TwitterRules.PACKAGE, "12.28.0-prod.01", 0L,
            listOf(
                Node(className = "android.widget.FrameLayout", bounds = Bounds(0, 0, 1080, 2400)),
                Node(id = "MainLanding", className = "android.view.View", bounds = Bounds(0, 0, 1080, 2400), depth = 7),
                Node(id = "scaffold_home_tabbed", className = "android.view.View", bounds = Bounds(0, 0, 1080, 2400), depth = 8),
                Node(id = "timeline_post", className = "android.view.View", bounds = Bounds(0, 120, 1080, 900), depth = 12),
                Node(id = "timeline_post", className = "android.view.View", bounds = Bounds(0, 900, 1080, 1800), depth = 12),
            ),
        )

        val d = detector.detect(snapshot)

        assertEquals(TwitterRules.HOME, d.target)
        assertFalse(d.unknownScreen)
    }

    /**
     * Régression : sur x_search.xml, le sélecteur d'onglets interne en haut d'écran (bounds [21,300][254,426])
     * porte selected=true et contient un nœud content-desc="Explorer" au même titre qu'une icône de la barre de
     * navigation basse. Ce test reproduit ce schéma avec un libellé "Grok" pour vérifier que le matcher ne
     * confond pas un sélecteur interne sélectionné (haut d'écran) avec la vraie barre de navigation (bas
     * d'écran) : sans la contrainte de position (`Matcher.NearBottom`), ce sélecteur ferait passer l'écran pour
     * `twitter.grok`.
     */
    @Test fun `selecteur interne selectionne en haut d'ecran n'est pas pris pour un onglet de nav`() {
        val root = Node(bounds = Bounds(0, 0, 1080, 2400))
        val topSelector = Node(className = "android.view.View", selected = true, bounds = Bounds(21, 300, 254, 426))
        val topLabel = Node(contentDesc = "Grok", bounds = Bounds(21, 300, 254, 426))
        val snapshot = Snapshot(TwitterRules.PACKAGE, "12.27", 0L, listOf(root, topSelector, topLabel))

        val d = detector.detect(snapshot)

        assertNull(d.target, "un sélecteur interne en haut d'écran ne doit pas déclencher HOME")
        assertTrue(d.unknownScreen, "ne doit pas être reconnu comme twitter.grok : le sélecteur n'est pas dans la barre de navigation basse")
        assertNull(d.screenId)
    }
}
