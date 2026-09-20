package rehab.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatcherTest {
    private val tab = Node(id = "com.instagram.android:id/feed_tab", className = "android.widget.FrameLayout", contentDesc = "Accueil", selected = true, bounds = Bounds(0, 2200, 216, 2340))
    private val label = Node(text = "Suggestions pour vous", className = "android.widget.TextView")
    private val all = listOf(tab, label)

    @Test fun `ViewId matche le suffixe apres le slash`() {
        assertTrue(Matcher.ViewId("feed_tab").matches(tab, all))
        assertFalse(Matcher.ViewId("tab").matches(tab, all))
        assertTrue(Matcher.ViewId("feed_tab").matches(Node(id = "feed_tab"), all))
    }

    @Test fun `ContentDesc et Text utilisent une regex partielle`() {
        assertTrue(Matcher.ContentDesc(Regex("^(Accueil|Home)$")).matches(tab, all))
        assertTrue(Matcher.Text(Regex("Suggestions? pour vous")).matches(label, all))
        assertFalse(Matcher.Text(Regex("Suggestions? pour vous")).matches(tab, all))
    }

    @Test fun `ClassName accepte le nom simple`() {
        assertTrue(Matcher.ClassName("TextView").matches(label, all))
        assertTrue(Matcher.ClassName("android.widget.TextView").matches(label, all))
    }

    @Test fun `Selected exige selected`() {
        assertTrue(Matcher.Selected(Matcher.ViewId("feed_tab")).matches(tab, all))
        assertFalse(Matcher.Selected(Matcher.ViewId("feed_tab")).matches(tab.copy(selected = false), all))
    }

    @Test fun `AnyOf et firstMatch`() {
        val m = Matcher.AnyOf(listOf(Matcher.ViewId("nope"), Matcher.Text(Regex("Suggestions"))))
        assertEquals(label, m.firstMatch(all))
    }

    @Test fun `AllOf exige tous les matchers, vrai par vacuite sur liste vide`() {
        val node = Node(id = "x", selected = true)
        assertTrue(Matcher.AllOf(emptyList()).matches(node, emptyList()))
        assertTrue(Matcher.AllOf(listOf(Matcher.Selected(Matcher.Any))).matches(node, emptyList()))
        assertFalse(Matcher.AllOf(listOf(Matcher.Selected(Matcher.Any), Matcher.ViewId("y"))).matches(node, emptyList()))
    }

    @Test fun `NearBottom seuil inclusif et fraction personnalisee`() {
        val root = Node(bounds = Bounds(0, 0, 1080, 2400), depth = 0)
        val pileAuSeuil = Node(bounds = Bounds(0, 1800, 200, 1900), depth = 1) // top = 0.75 * 2400 pile
        val justEnDessous = Node(bounds = Bounds(0, 1799, 200, 1900), depth = 1)
        val nodes = listOf(root, pileAuSeuil, justEnDessous)
        assertTrue(Matcher.NearBottom().matches(pileAuSeuil, nodes), "le seuil est inclusif (>=)")
        assertFalse(Matcher.NearBottom().matches(justEnDessous, nodes))
        // fraction personnalisee : 0.5 * 2400 = 1200, un nœud à top=1300 passe alors qu'il échouerait à 0.75
        val milieu = Node(bounds = Bounds(0, 1300, 200, 1400), depth = 1)
        assertFalse(Matcher.NearBottom(minFraction = 0.75).matches(milieu, listOf(root, milieu)))
        assertTrue(Matcher.NearBottom(minFraction = 0.5).matches(milieu, listOf(root, milieu)))
    }

    @Test fun `NearBottom ignore un noeud hors ecran qui gonflerait le maximum des bounds`() {
        // Cas qui faisait échouer l'ancienne implémentation (max de bounds.bottom sur tous les nœuds) :
        // un nœud scrollable hors-viewport avec un bottom très supérieur à la hauteur réelle de l'écran
        // (root, profondeur 0) ne doit pas déplacer le seuil et faire rater la vraie barre de navigation.
        val root = Node(bounds = Bounds(0, 0, 1080, 2400), depth = 0)
        val navBar = Node(bounds = Bounds(0, 2127, 200, 2274), depth = 1) // top/hauteur ≈ 0.886, doit matcher
        val horsEcran = Node(bounds = Bounds(0, 4000, 200, 4400), depth = 2) // bottom > hauteur d'écran réelle
        val nodes = listOf(root, navBar, horsEcran)
        assertTrue(Matcher.NearBottom().matches(navBar, nodes))
    }

    @Test fun `NearBottom sans noeud de profondeur 0 repond faux plutot que de deviner`() {
        val orphan = Node(bounds = Bounds(0, 2200, 200, 2300), depth = 1)
        assertFalse(Matcher.NearBottom().matches(orphan, listOf(orphan)))
    }

    @Test fun `Within exige un conteneur englobant qui matche outer`() {
        // Cas Instagram réel : l'onglet n'est pas selected, son icône enfant l'est.
        val feedTab = Node(id = "com.instagram.android:id/feed_tab", contentDesc = "Home", selected = false, bounds = Bounds(0, 2148, 216, 2274))
        val icon = Node(id = "com.instagram.android:id/tab_icon", selected = true, bounds = Bounds(76, 2179, 139, 2242))
        val otherIcon = Node(id = "com.instagram.android:id/tab_icon", selected = false, bounds = Bounds(292, 2179, 355, 2242))
        val nodes = listOf(feedTab, icon, otherIcon)
        val m = Matcher.Within(Matcher.ViewId("feed_tab"), Matcher.Selected(Matcher.ViewId("tab_icon")))
        assertEquals(icon, m.firstMatch(nodes))
        assertFalse(m.matches(otherIcon, nodes))
        // Cas X réel : conteneur selected sans libellé qui contient le libellé « Accueil ».
        val container = Node(className = "android.view.View", selected = true, bounds = Bounds(0, 2127, 200, 2274))
        val home = Node(className = "android.view.View", contentDesc = "Accueil", bounds = Bounds(68, 2169, 131, 2232))
        val explore = Node(className = "android.view.View", contentDesc = "Explorer", bounds = Bounds(289, 2169, 352, 2232))
        val xm = Matcher.Within(Matcher.Selected(Matcher.Any), Matcher.ContentDesc(Regex("^Accueil$")))
        assertEquals(home, xm.firstMatch(listOf(container, home, explore)))
        assertFalse(Matcher.Within(Matcher.Selected(Matcher.Any), Matcher.ContentDesc(Regex("^Explorer$"))).matches(explore, listOf(container, home, explore)))
    }
}
