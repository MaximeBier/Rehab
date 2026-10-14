package rehab.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureAnonymizerTest {
    @Test fun `textes masques, libelles d interface, ids et bounds conserves`() {
        val s = Snapshot("pkg", "1", 5L, listOf(
            Node(id = "a", text = "Coucou Julie", contentDesc = "Photo de profil de julie_92", bounds = Bounds(1, 2, 3, 4), selected = true, depth = 0),
            Node(id = "tab", contentDesc = "Messages", depth = 1),
            Node(id = "home", contentDesc = "Accueil", text = "", depth = 1),
        ))
        val a = CaptureAnonymizer.anonymize(s)
        assertEquals("t0", a.nodes[0].text)
        assertEquals("d0", a.nodes[0].contentDesc)
        assertEquals("Messages", a.nodes[1].contentDesc)
        assertEquals("Accueil", a.nodes[2].contentDesc)
        assertEquals("", a.nodes[2].text)
        assertEquals(s.nodes.map { it.id to it.bounds }, a.nodes.map { it.id to it.bounds })
        assertEquals(s.nodes.map { it.selected to it.depth }, a.nodes.map { it.selected to it.depth })
        assertEquals(Triple("pkg", "1", 5L), Triple(a.packageName, a.appVersion, a.capturedAt))
    }
}
