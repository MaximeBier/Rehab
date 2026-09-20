package rehab.rules

import rehab.domain.model.TargetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenDetectorTest {
    private val reels = TargetId("Reels")
    private val suggested = TargetId("Suggested")
    private val homeTab = Matcher.Selected(Matcher.ViewId("feed_tab"))
    private val rules = PackageRules(
        packageName = "pkg",
        testedVersions = VersionRange("1", "2"),
        targets = listOf(
            TargetRule(reels, "reels", listOf(Matcher.ViewId("clips_viewer")), priority = 10),
            TargetRule(suggested, "home", listOf(homeTab), triggerMatchers = listOf(Matcher.Text(Regex("Suggestions"))), degradedFallback = true, priority = 5),
        ),
        knownScreens = listOf(KnownScreen("dm", listOf(Matcher.ViewId("inbox")))),
        navBarMatcher = Matcher.ViewId("tab_bar"),
        homeTabMatcher = homeTab,
    )
    private val detector = ScreenDetector(RuleCatalog(listOf(rules)))
    private val navBar = Node(id = "pkg:id/tab_bar", bounds = Bounds(0, 2200, 1080, 2340))
    private val feedTab = Node(id = "pkg:id/feed_tab", selected = true)

    private fun snap(vararg nodes: Node) = Snapshot("pkg", "1.5", 0L, nodes.toList())

    @Test fun `package inconnu`() {
        assertEquals(Detection.NONE, detector.detect(Snapshot("other", "1", 0L, emptyList())))
    }

    @Test fun `reels prioritaire meme si feed selectionne`() {
        val d = detector.detect(snap(navBar, feedTab, Node(id = "pkg:id/clips_viewer")))
        assertEquals(reels, d.target)
        assertEquals(Bounds(0, 2200, 1080, 2340), d.navBarBounds)
    }

    @Test fun `feed sans suggere n est pas une cible mais un ecran connu`() {
        val d = detector.detect(snap(navBar, feedTab, Node(text = "Photo de vacances")))
        assertNull(d.target)
        assertEquals("home", d.screenId)
        assertFalse(d.unknownScreen)
        assertTrue(d.homeTabSelected)
    }

    @Test fun `feed avec suggere est une cible`() {
        val d = detector.detect(snap(navBar, feedTab, Node(text = "Suggestions pour vous")))
        assertEquals(suggested, d.target)
    }

    @Test fun `mode degrade cible le feed entier`() {
        val d = detector.detect(snap(navBar, feedTab), degraded = true)
        assertEquals(suggested, d.target)
    }

    @Test fun `ecran connu non cible`() {
        val d = detector.detect(snap(navBar, Node(id = "pkg:id/inbox")))
        assertNull(d.target)
        assertEquals("dm", d.screenId)
        assertFalse(d.unknownScreen)
        assertFalse(d.homeTabSelected)
    }

    @Test fun `ecran inconnu`() {
        val d = detector.detect(snap(Node(text = "n'importe quoi")))
        assertNull(d.target)
        assertTrue(d.unknownScreen)
        assertNull(d.navBarBounds)
    }
}
