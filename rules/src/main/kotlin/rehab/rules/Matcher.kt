package rehab.rules

sealed interface Matcher {
    /** [all] est la liste plate du snapshot, pour les matchers qui ont besoin du contexte (Within). */
    fun matches(node: Node, all: List<Node>): Boolean

    data class ViewId(val idSuffix: String) : Matcher {
        override fun matches(node: Node, all: List<Node>): Boolean {
            val id = node.id ?: return false
            return id == idSuffix || id.endsWith("/$idSuffix")
        }
    }

    class ContentDesc(val regex: Regex) : Matcher {
        override fun matches(node: Node, all: List<Node>) = node.contentDesc?.let(regex::containsMatchIn) == true
        override fun toString() = "ContentDesc(${regex.pattern})"
    }

    class Text(val regex: Regex) : Matcher {
        override fun matches(node: Node, all: List<Node>) = node.text?.let(regex::containsMatchIn) == true
        override fun toString() = "Text(${regex.pattern})"
    }

    data class ClassName(val name: String) : Matcher {
        override fun matches(node: Node, all: List<Node>): Boolean {
            val c = node.className ?: return false
            return c == name || c.endsWith(".$name")
        }
    }

    data class Selected(val inner: Matcher) : Matcher {
        override fun matches(node: Node, all: List<Node>) = node.selected && inner.matches(node, all)
    }

    data class AnyOf(val options: List<Matcher>) : Matcher {
        override fun matches(node: Node, all: List<Node>) = options.any { it.matches(node, all) }
    }

    data object Any : Matcher {
        override fun matches(node: Node, all: List<Node>) = true
    }

    /** Le nœud matche [inner] et il existe un autre nœud matchant [outer] dont les bounds englobent les siens. */
    data class Within(val outer: Matcher, val inner: Matcher) : Matcher {
        override fun matches(node: Node, all: List<Node>): Boolean =
            inner.matches(node, all) && all.any { o -> o !== node && o.bounds.contains(node.bounds) && outer.matches(o, all) }
    }
}

fun Bounds.contains(other: Bounds): Boolean =
    left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

fun Matcher.firstMatch(nodes: List<Node>): Node? = nodes.firstOrNull { matches(it, nodes) }
