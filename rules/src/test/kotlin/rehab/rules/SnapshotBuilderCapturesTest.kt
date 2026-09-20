package rehab.rules

import rehab.rules.catalog.InstagramRules
import rehab.rules.catalog.TwitterRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Régression du CRITIQUE 1 / CRITIQUE 2 de la revue finale : les 10 captures réelles font 17 à 32 niveaux de
 * profondeur. Avec l'ancien `maxDepth = 12`, `SnapshotBuilder` amputait l'arbre bien avant les nœuds ciblés par
 * les règles, mais aucun test ne pouvait le voir car `Fixtures`/`UiAutomatorXml` construisaient un snapshot à
 * plat, sans passer par `SnapshotBuilder`. Ce test fait explicitement ce que la production fait : il vérifie
 * qu'aucune des 10 captures n'est tronquée par les bornes par défaut, et que la détection (catalogue complet,
 * comme dans `AppGraph`) obtient les mêmes résultats qu'un arbre non borné.
 */
class SnapshotBuilderCapturesTest {
    private val detector = ScreenDetector(DefaultCatalog.create())

    private fun load(name: String, packageName: String) = Fixtures.load(name, packageName)

    private val instagramCaptures = listOf(
        "ig_reels.xml", "ig_feed_followed.xml", "ig_feed_suggested.xml", "ig_dm.xml", "ig_profile.xml", "ig_search.xml",
    )
    private val twitterCaptures = listOf("x_home_foryou.xml", "x_home_following.xml", "x_search.xml", "x_dm.xml")

    @Test fun `aucune des 10 captures n'est tronquee par les bornes de production`() {
        for (name in instagramCaptures) {
            val s = load(name, InstagramRules.PACKAGE)
            assertFalse(s.truncated, "$name ne devrait pas être tronqué par SnapshotBuilder (défauts de production)")
        }
        for (name in twitterCaptures) {
            val s = load(name, TwitterRules.PACKAGE)
            assertFalse(s.truncated, "$name ne devrait pas être tronqué par SnapshotBuilder (défauts de production)")
        }
    }

    @Test fun `ig_reels est detecte a travers les bornes de production`() {
        val d = detector.detect(load("ig_reels.xml", InstagramRules.PACKAGE))
        assertEquals(InstagramRules.REELS, d.target)
    }

    @Test fun `ig_feed_suggested est detecte a travers les bornes de production`() {
        // Avant la correction du CRITIQUE 1 (maxDepth = 12), le libellé "Suggestions..." (profondeur 21-22) et
        // inline_follow_button étaient hors de portée : target=null au lieu de InstagramSuggested.
        val d = detector.detect(load("ig_feed_suggested.xml", InstagramRules.PACKAGE))
        assertEquals(InstagramRules.SUGGESTED, d.target)
    }

    @Test fun `ig_feed_followed reste un ecran connu non cible avec la barre de nav`() {
        val d = detector.detect(load("ig_feed_followed.xml", InstagramRules.PACKAGE))
        assertNull(d.target)
        assertEquals("instagram.home", d.screenId)
        assertTrue(d.homeTabSelected)
        assertNotNull(d.navBarBounds)
    }

    @Test fun `ecrans instagram connus non cibles restent reconnus`() {
        for (name in listOf("ig_dm.xml", "ig_profile.xml", "ig_search.xml")) {
            val d = detector.detect(load(name, InstagramRules.PACKAGE))
            assertNull(d.target, name)
            assertFalse(d.unknownScreen, "$name devrait rester un écran connu")
        }
    }

    @Test fun `x_home est detecte a travers les bornes de production, avec la barre de nav`() {
        // Avant la correction : maxDepth = 12 amputait le conteneur `selected` de la barre du bas de X
        // (profondeur 15-16) -> target=null, unknownScreen=true, navBarBounds=null.
        for (name in listOf("x_home_foryou.xml", "x_home_following.xml")) {
            val d = detector.detect(load(name, TwitterRules.PACKAGE))
            assertEquals(TwitterRules.HOME, d.target, name)
            assertFalse(d.unknownScreen, name)
            assertNotNull(d.navBarBounds, name)
        }
    }

    @Test fun `ecrans x connus non cibles sont reconnus a travers les bornes de production`() {
        val search = detector.detect(load("x_search.xml", TwitterRules.PACKAGE))
        assertNull(search.target)
        assertEquals("twitter.search", search.screenId)
        assertFalse(search.unknownScreen)

        val dm = detector.detect(load("x_dm.xml", TwitterRules.PACKAGE))
        assertNull(dm.target)
        assertEquals("twitter.dm", dm.screenId)
        assertFalse(dm.unknownScreen)
    }
}
