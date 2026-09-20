package rehab.rules

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

object UiAutomatorXml {
    private val boundsRegex = Regex("""\[(\d+),(\d+)]\[(\d+),(\d+)]""")

    fun toSnapshot(xml: String, packageName: String, appVersion: String = "0"): Snapshot {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val nodes = mutableListOf<Node>()

        fun visit(el: Element, depth: Int) {
            val isNode = el.tagName == "node"
            if (isNode) {
                nodes += Node(
                    id = el.attr("resource-id"),
                    className = el.attr("class"),
                    text = el.attr("text"),
                    contentDesc = el.attr("content-desc"),
                    bounds = parseBounds(el.getAttribute("bounds")),
                    selected = el.getAttribute("selected") == "true",
                    depth = depth,
                )
            }
            val children = el.childNodes
            for (i in 0 until children.length) {
                val c = children.item(i)
                if (c is Element) visit(c, if (isNode) depth + 1 else depth)
            }
        }

        visit(doc.documentElement, 0)
        return Snapshot(packageName, appVersion, 0L, nodes)
    }

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

    private fun parseBounds(s: String): Bounds {
        val m = boundsRegex.find(s) ?: return Bounds(0, 0, 0, 0)
        val (l, t, r, b) = m.destructured
        return Bounds(l.toInt(), t.toInt(), r.toInt(), b.toInt())
    }
}
