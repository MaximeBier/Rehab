package rehab.rules.catalog

import rehab.rules.Fixtures
import rehab.rules.RuleCatalog
import rehab.rules.ScreenDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
