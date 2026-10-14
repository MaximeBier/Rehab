package rehab.rules.catalog

import rehab.rules.Fixtures
import rehab.rules.RuleCatalog
import rehab.rules.ScreenDetector
import rehab.rules.SnapshotBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** v0.7.0 : tout Instagram compte, sauf les DM. */
class InstagramRulesTest {
    private val detector = ScreenDetector(RuleCatalog(listOf(InstagramRules.PACKAGE_RULES)))
    private fun detect(name: String) = detector.detect(Fixtures.load(name, InstagramRules.PACKAGE))

    @Test fun `tous les ecrans hors dm sont la cible`() {
        for (name in listOf("ig_reels.xml", "ig_feed_followed.xml", "ig_feed_suggested.xml", "ig_profile.xml", "ig_search.xml")) {
            assertEquals(InstagramRules.APP, detect(name).target, name)
        }
    }

    @Test fun `liste des dm non cible`() {
        val d = detect("ig_dm.xml")
        assertNull(d.target)
        assertEquals("instagram.dm", d.screenId)
    }

    @Test fun `barre d onglets reperee pour borner l overlay`() {
        assertNotNull(detect("ig_feed_followed.xml").navBarBounds)
    }

    @Test fun `snapshot illisible compte`() {
        assertEquals(InstagramRules.APP, detector.detect(SnapshotBuilder.unreadable(InstagramRules.PACKAGE, "447.0", 0L)).target)
    }
}
