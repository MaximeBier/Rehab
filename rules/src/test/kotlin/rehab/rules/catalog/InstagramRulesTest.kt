package rehab.rules.catalog

import rehab.rules.Fixtures
import rehab.rules.RuleCatalog
import rehab.rules.ScreenDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstagramRulesTest {
    private val detector = ScreenDetector(RuleCatalog(listOf(InstagramRules.PACKAGE_RULES)))
    private fun detect(name: String) = detector.detect(Fixtures.load(name, InstagramRules.PACKAGE))

    @Test fun `reels`() {
        val d = detect("ig_reels.xml")
        assertEquals(InstagramRules.REELS, d.target)
    }

    @Test fun `feed sans suggere`() {
        val d = detect("ig_feed_followed.xml")
        assertNull(d.target)
        assertEquals("instagram.home", d.screenId)
        assertTrue(d.homeTabSelected)
        assertNotNull(d.navBarBounds)
    }

    @Test fun `feed avec suggere`() {
        val d = detect("ig_feed_suggested.xml")
        assertEquals(InstagramRules.SUGGESTED, d.target)
    }

    @Test fun `feed en mode degrade`() {
        val d = detector.detect(Fixtures.load("ig_feed_followed.xml", InstagramRules.PACKAGE), degraded = true)
        assertEquals(InstagramRules.SUGGESTED, d.target)
    }

    @Test fun `ecrans connus non cibles`() {
        for (name in listOf("ig_dm.xml", "ig_profile.xml", "ig_search.xml")) {
            val d = detect(name)
            assertNull(d.target, name)
            assertFalse(d.unknownScreen, "$name devrait être un écran connu")
            assertFalse(d.homeTabSelected, name)
        }
    }
}
