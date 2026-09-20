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

    /** Le nœud doit matcher tous les [options] (contrairement à [AnyOf], qui n'en exige qu'un). */
    data class AllOf(val options: List<Matcher>) : Matcher {
        override fun matches(node: Node, all: List<Node>) = options.all { it.matches(node, all) }
    }

    data object Any : Matcher {
        override fun matches(node: Node, all: List<Node>) = true
    }

    /**
     * Le haut du nœud est situé dans la fraction basse de l'écran. Sert à distinguer une barre de navigation
     * basse d'un contrôle de même forme (onglets internes, sélecteurs) situé ailleurs à l'écran.
     *
     * La hauteur de référence est celle du nœud de profondeur 0 (`depth == 0`), c'est-à-dire la racine du
     * snapshot — jamais le maximum de `bounds.bottom` sur tous les nœuds : ce maximum serait faussé par un
     * nœud scrollable hors-viewport dont les bounds dépassent la hauteur réelle de l'écran (il gonflerait la
     * hauteur estimée et déplacerait le seuil vers le bas, faisant rater la vraie barre de navigation).
     * La racine, elle, est un choix sûr même sous troncature (`Snapshot.truncated`) : `SnapshotBuilder.visit`
     * l'ajoute toujours en premier, avant tout test de la limite `maxNodes`, donc elle n'est jamais elle-même
     * tronquée — voir `SnapshotBuilderTest` (« le nœud racine (profondeur 0) survit toujours à la troncature »).
     * Elle est absente uniquement si l'appelant fournit un snapshot sans nœud de profondeur 0, auquel cas le
     * matcher répond `false` plutôt que de deviner.
     *
     * [minFraction] par défaut (0,75) : observé sur les captures Instagram/X (Pixel 6a, 2400 px de haut), la
     * barre de navigation basse commence vers 2127-2148 px (≈ 0,89), largement au-delà des contrôles internes
     * en haut d'écran (≈ 0,12-0,17) — voir `TwitterRules.bottomTabActive`.
     */
    data class NearBottom(val minFraction: Double = 0.75) : Matcher {
        override fun matches(node: Node, all: List<Node>): Boolean {
            val screenHeight = all.firstOrNull { it.depth == 0 }?.bounds?.bottom ?: return false
            if (screenHeight <= 0) return false
            return node.bounds.top >= minFraction * screenHeight
        }
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
