package rehab.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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

    @Test fun `xml masque sur place, libelles d interface, ids, bounds et etats conserves`() {
        val xml = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation=\"0\">" +
            "<node index=\"0\" text=\"Coucou Julie &amp; Léo &#128512;\" resource-id=\"\" class=\"android.widget.FrameLayout\" package=\"pkg\" content-desc=\"\" selected=\"false\" bounds=\"[0,0][1080,2400]\">" +
            "<node index=\"0\" text=\"\" resource-id=\"pkg:id/tab\" class=\"android.widget.Button\" package=\"pkg\" content-desc=\"Messages\" selected=\"true\" bounds=\"[0,2200][540,2400]\" />" +
            "<node index=\"1\" text=\"Rechercher et explorer\" hint=\"Écrire à julie_92\" resource-id=\"pkg:id/search\" class=\"android.widget.EditText\" package=\"pkg\" content-desc=\"Photo de profil de julie_92&#10;\" selected=\"false\" bounds=\"[540,2200][1080,2400]\" />" +
            "</node></hierarchy>"
        val a = CaptureAnonymizer.anonymizeXml(xml)
        val expected = xml
            .replace("text=\"Coucou Julie &amp; Léo &#128512;\"", "text=\"t0\"")
            .replace("hint=\"Écrire à julie_92\"", "hint=\"h2\"")
            .replace("content-desc=\"Photo de profil de julie_92&#10;\"", "content-desc=\"d2\"")
        assertEquals(expected, a)
        assertFalse("julie" in a.lowercase())
        val before = UiAutomatorXml.toSnapshot(xml, "pkg").nodes
        val after = UiAutomatorXml.toSnapshot(a, "pkg").nodes
        assertEquals(before.map { listOf(it.id, it.className, it.bounds, it.selected, it.depth) }, after.map { listOf(it.id, it.className, it.bounds, it.selected, it.depth) })
        assertEquals(listOf("t0", null, "Rechercher et explorer"), after.map { it.text })
        assertEquals(listOf(null, "Messages", "d2"), after.map { it.contentDesc })
    }
}
