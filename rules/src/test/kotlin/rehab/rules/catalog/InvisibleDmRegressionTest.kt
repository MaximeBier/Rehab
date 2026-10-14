package rehab.rules.catalog

import rehab.rules.Bounds
import rehab.rules.FakeTreeNode
import rehab.rules.RuleCatalog
import rehab.rules.ScreenDetector
import rehab.rules.SnapshotBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Régression v0.7.0 : X garde l'écran Messages en mémoire derrière l'accueil. Ses conteneurs (`message_list_v2`,
 * `xchat_conversation_list`) restaient dans l'arbre d'accessibilité, invisibles, et suffisaient à classer tout X
 * en DM (ni compté ni bloqué). Les captures uiautomator omettent les nœuds invisibles : seul un arbre construit
 * par SnapshotBuilder reproduit le bug.
 */
class InvisibleDmRegressionTest {
    private val detector = ScreenDetector(RuleCatalog(listOf(TwitterRules.PACKAGE_RULES)))

    @Test fun `accueil x avec conteneurs dm invisibles reste la cible`() {
        val full = Bounds(0, 0, 1080, 2400)
        val root = FakeTreeNode(className = "android.widget.FrameLayout", bounds = full, children = listOf(
            FakeTreeNode(id = "scaffold_home_tabbed", bounds = full),
            FakeTreeNode(id = "RootDm", bounds = full, visible = false, children = listOf(
                FakeTreeNode(id = "message_list_v2", bounds = Bounds(0, 200, 1080, 2200)),
                FakeTreeNode(id = "xchat_conversation_list", bounds = Bounds(0, 300, 1080, 2400)),
            )),
        ))
        val snapshot = SnapshotBuilder().build(root, TwitterRules.PACKAGE, "12.28.0", 0L)
        assertEquals(TwitterRules.APP, detector.detect(snapshot).target)
    }
}
