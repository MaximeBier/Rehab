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
            assertEquals(TwitterRules.APP, d.target, name)
            assertNotNull(d.navBarBounds, name)
        }
    }

    // v0.6.0 : tout X compte, sauf les DM (liste et conversation). Voir TwitterRules.
    @Test fun `recherche cible, liste des messages non cible`() {
        assertEquals(TwitterRules.APP, detect("x_search.xml").target)
        val dm = detect("x_dm.xml")
        assertNull(dm.target)
        assertEquals("twitter.dm", dm.screenId)
        assertFalse(dm.unknownScreen)
    }

    /**
     * Structures reproduites depuis deux captures réelles de X 12.28 (2026-09-25, contenu personnel non versionné) :
     * liste des conversations (`xchat_conversation_list`, barre du bas éventuellement masquée) et conversation
     * ouverte (`RootDm` / `message_list_v2`, jamais de barre d'onglets).
     */
    @Test fun `conversation dm ouverte et liste sans barre ne sont pas des cibles`() {
        val root = Node(className = "android.widget.FrameLayout", bounds = Bounds(0, 0, 1080, 2400))
        val conversation = Snapshot(
            TwitterRules.PACKAGE, "12.28.0-prod.01", 0L,
            listOf(root, Node(id = "RootDm", bounds = Bounds(0, 0, 1080, 2400), depth = 7), Node(id = "message_list_v2", bounds = Bounds(0, 200, 1080, 2200), depth = 9)),
        )
        val inbox = Snapshot(
            TwitterRules.PACKAGE, "12.28.0-prod.01", 0L,
            listOf(root, Node(id = "MainLanding", bounds = Bounds(0, 0, 1080, 2400), depth = 7), Node(id = "xchat_conversation_list", bounds = Bounds(0, 300, 1080, 2400), depth = 9)),
        )
        for (s in listOf(conversation, inbox)) {
            val d = detector.detect(s)
            assertNull(d.target)
            assertEquals("twitter.dm", d.screenId)
        }
    }

    @Test fun `ecran x inconnu compte comme cible`() {
        val d = detector.detect(Snapshot(TwitterRules.PACKAGE, "12.28", 0L, listOf(Node(className = "android.widget.FrameLayout", bounds = Bounds(0, 0, 1080, 2400)))))
        assertEquals(TwitterRules.APP, d.target)
        assertFalse(d.unknownScreen)
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

        assertEquals(TwitterRules.APP, d.target)
        assertFalse(d.unknownScreen)
    }

}
