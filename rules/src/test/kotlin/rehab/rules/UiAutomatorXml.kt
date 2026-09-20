package rehab.rules

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reconstruit un [TreeNode] réel depuis un dump UiAutomator et le fait passer par [SnapshotBuilder] avec ses
 * paramètres de production (par défaut). Avant, cette classe aplatissait l'arbre XML directement en
 * `List<Node>` sans aucune borne : les tests de `rules` (InstagramRulesTest, TwitterRulesTest) voyaient donc un
 * arbre que la production ne voit jamais — c'est le trou par lequel `maxDepth = 12` (voir SnapshotBuilder) est
 * passé inaperçu. Voir aussi `SnapshotBuilderCapturesTest`.
 */
object UiAutomatorXml {
    private val boundsRegex = Regex("""\[(\d+),(\d+)]\[(\d+),(\d+)]""")

    fun toSnapshot(xml: String, packageName: String, appVersion: String = "0", builder: SnapshotBuilder = SnapshotBuilder()): Snapshot {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val rootElement = findFirstNodeElement(doc.documentElement)
            ?: error("Aucun nœud <node> dans le XML")
        return builder.build(DomTreeNode(rootElement), packageName, appVersion, 0L)
    }

    private fun findFirstNodeElement(el: Element): Element? {
        if (el.tagName == "node") return el
        val children = el.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            if (c is Element) {
                val found = findFirstNodeElement(c)
                if (found != null) return found
            }
        }
        return null
    }

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

    private fun parseBounds(s: String): Bounds {
        val m = boundsRegex.find(s) ?: return Bounds(0, 0, 0, 0)
        val (l, t, r, b) = m.destructured
        return Bounds(l.toInt(), t.toInt(), r.toInt(), b.toInt())
    }

    private class DomTreeNode(private val el: Element) : TreeNode {
        override val id: String? = el.attr("resource-id")
        override val className: String? = el.attr("class")
        override val text: String? = el.attr("text")
        override val contentDesc: String? = el.attr("content-desc")
        override val bounds: Bounds = parseBounds(el.getAttribute("bounds"))
        override val selected: Boolean = el.getAttribute("selected") == "true"

        private val childElements: List<Element> = run {
            val list = mutableListOf<Element>()
            val nl = el.childNodes
            for (i in 0 until nl.length) {
                val c = nl.item(i)
                if (c is Element && c.tagName == "node") list += c
            }
            list
        }

        override val childCount: Int get() = childElements.size
        override fun child(index: Int): TreeNode = DomTreeNode(childElements[index])
    }
}
